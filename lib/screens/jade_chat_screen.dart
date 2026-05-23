import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/groq_service.dart';
import '../services/unified_ai_service.dart';
import '../services/jade_settings_controller.dart';
import '../widgets/animated_jade_avatar.dart';

/// Jade — the in-app AI chat surface.
///
/// Design intent: read like a quiet, tool-grade chat (Linear / iA Writer),
/// not a marketing demo. AI messages render as flat text with a subtle left
/// rule instead of colored bubbles; user messages keep a filled bubble for
/// directionality. Consecutive turns are grouped so the avatar + timestamp
/// only appear once per cluster (iMessage convention). No fake "Online"
/// indicator, no auto-rotating avatar.
class JadeChatScreen extends StatefulWidget {
  const JadeChatScreen({super.key});

  @override
  State<JadeChatScreen> createState() => _JadeChatScreenState();
}

class _JadeChatScreenState extends State<JadeChatScreen>
    with SingleTickerProviderStateMixin {
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final FocusNode _messageFocusNode = FocusNode();

  final List<ChatMessage> _messages = [];
  bool _isTyping = false;
  late final AnimationController _typingDots;

  @override
  void initState() {
    super.initState();
    _typingDots = AnimationController(
      duration: const Duration(milliseconds: 900),
      vsync: this,
    )..repeat();
    // No welcome ChatMessage — the empty state IS the welcome. Pushing a
    // chat bubble that says "I can do X, Y, Z" reads like a marketing
    // banner; the empty state shows the same info as actionable cards.
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    _messageFocusNode.dispose();
    _typingDots.dispose();
    super.dispose();
  }

  /// Reset for "new chat" — clear history, return to empty state.
  void _resetConversation() {
    setState(() => _messages.clear());
  }

  Future<void> _sendMessage() async {
    final message = _messageController.text.trim();
    if (message.isEmpty) return;

    HapticFeedback.lightImpact();

    setState(() {
      _messages.add(ChatMessage(
        content: message,
        isUser: true,
        timestamp: DateTime.now(),
      ));
      _isTyping = true;
    });

    _messageController.clear();
    _scrollToBottom();

    try {
      final settingsResponse =
          await JadeSettingsController.processCommand(message, context);

      String response;
      // settingsResponse non-empty means Jade actually executed something
      // (changed a pref, toggled a theme, opened a system panel). Render it
      // as an "action receipt" so the user sees that Jade has hands, not
      // just a chat box.
      final isActionReceipt = settingsResponse.isNotEmpty;
      if (isActionReceipt) {
        response = settingsResponse;
      } else {
        await GroqService.initialize();
        final recentMessages = _messages
            .skip(_messages.length > 7 ? _messages.length - 7 : 0)
            .map((m) => '${m.isUser ? "User" : "Jade"}: ${m.content}')
            .join('\n');
        final contextualMessage =
            'Conversation so far:\n$recentMessages\n\nUser: $message';

        try {
          final result = await UnifiedAIService.chatWithCustomPrompt(
            contextualMessage,
            // System prompt tuned to NOT produce emoji-bulleted marketing copy.
            'You are Jade, an in-app writing assistant. Reply in plain prose, '
                '2–4 sentences unless asked for detail. No emojis, no bullet '
                'lists unless the user asks. Be direct; skip pleasantries. '
                'When given text, return the improved version followed by one '
                'short line of reasoning. Never invent app features.',
          );
          response = (result['content']?.toString().trim().isNotEmpty == true)
              ? result['content'].toString().trim()
              : _genericFallback;
        } catch (_) {
          response = _genericFallback;
        }
      }

      if (!mounted) return;
      setState(() {
        _messages.add(ChatMessage(
          content: response,
          isUser: false,
          timestamp: DateTime.now(),
          isActionReceipt: isActionReceipt,
        ));
        _isTyping = false;
      });
      _scrollToBottom();
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _messages.add(ChatMessage(
          content: "Couldn't reach the model — check your connection and retry.",
          isUser: false,
          timestamp: DateTime.now(),
          isError: true,
        ));
        _isTyping = false;
      });
      _scrollToBottom();
    }
  }

  static const _genericFallback = "Couldn't generate a reply. Try rephrasing.";

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 240),
          curve: Curves.easeOutCubic,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: cs.surface,
      appBar: AppBar(
        backgroundColor: cs.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: _AppBarBack(onTap: () => Navigator.of(context).maybePop()),
        titleSpacing: 0,
        title: Row(
          children: [
            const AnimatedJadeAvatar(
              size: 28,
              enableRotation: false,
              showBorder: false,
            ),
            const SizedBox(width: 10),
            Text(
              'Jade',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ],
        ),
        actions: [
          if (_messages.isNotEmpty)
            IconButton(
              tooltip: 'New chat',
              icon: const Icon(CupertinoIcons.refresh, size: 20),
              onPressed: () {
                HapticFeedback.selectionClick();
                _resetConversation();
              },
              color: cs.onSurfaceVariant,
            ),
          const SizedBox(width: 4),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: _messages.isEmpty && !_isTyping
                ? _JadeEmptyState(
                    onPickStarter: _handleStarter,
                  )
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                    itemCount: _messages.length + (_isTyping ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index == _messages.length && _isTyping) {
                        return _TypingDots(controller: _typingDots);
                      }
                      final message = _messages[index];
                      final prev = index > 0 ? _messages[index - 1] : null;
                      final next = index + 1 < _messages.length
                          ? _messages[index + 1]
                          : null;
                      final isStartOfGroup =
                          prev == null || prev.isUser != message.isUser;
                      final isEndOfGroup =
                          next == null || next.isUser != message.isUser;
                      return _MessageBlock(
                        message: message,
                        isStartOfGroup: isStartOfGroup,
                        isEndOfGroup: isEndOfGroup,
                      );
                    },
                  ),
          ),
          _MessageInput(
            controller: _messageController,
            focusNode: _messageFocusNode,
            isTyping: _isTyping,
            onSend: _sendMessage,
          ),
        ],
      ),
    );
  }

  void _handleStarter(String text) {
    if (text.endsWith(': ')) {
      _messageController.text = text;
      _messageController.selection =
          TextSelection.collapsed(offset: text.length);
      _messageFocusNode.requestFocus();
    } else {
      _messageController.text = text;
      _sendMessage();
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Message rendering
// ─────────────────────────────────────────────────────────────────────────────

class _MessageBlock extends StatefulWidget {
  final ChatMessage message;
  final bool isStartOfGroup;
  final bool isEndOfGroup;

  const _MessageBlock({
    required this.message,
    required this.isStartOfGroup,
    required this.isEndOfGroup,
  });

  @override
  State<_MessageBlock> createState() => _MessageBlockState();
}

class _MessageBlockState extends State<_MessageBlock>
    with SingleTickerProviderStateMixin {
  late final AnimationController _enter;

  @override
  void initState() {
    super.initState();
    // Subtle fade-up on insert. New messages slide ~6pt with an ease-out so
    // the chat feels alive without the bouncy/synthetic feel of full springs.
    _enter = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 220),
    )..forward();
  }

  @override
  void dispose() {
    _enter.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final body = widget.message.isUser
        ? _userBubble(context)
        : (widget.message.isActionReceipt
            ? _actionReceipt(context)
            : _assistantBlock(context));
    return Padding(
      padding: EdgeInsets.only(
        top: widget.isStartOfGroup ? 18 : 4,
        bottom: widget.isEndOfGroup ? 2 : 0,
      ),
      child: FadeTransition(
        opacity: CurvedAnimation(parent: _enter, curve: Curves.easeOut),
        child: SlideTransition(
          position: Tween<Offset>(
            begin: const Offset(0, 0.08),
            end: Offset.zero,
          ).animate(CurvedAnimation(parent: _enter, curve: Curves.easeOutCubic)),
          child: body,
        ),
      ),
    );
  }

  Widget _actionReceipt(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(right: 24),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: cs.primaryContainer.withValues(alpha: 0.35),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: cs.primary.withValues(alpha: 0.25),
            width: 0.5,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 22,
              height: 22,
              margin: const EdgeInsets.only(top: 1, right: 10),
              decoration: BoxDecoration(
                color: cs.primary,
                shape: BoxShape.circle,
              ),
              child: Icon(
                CupertinoIcons.checkmark_alt,
                size: 14,
                color: cs.onPrimary,
              ),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Done',
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                          color: cs.primary,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.3,
                        ),
                  ),
                  const SizedBox(height: 2),
                  SelectableText(
                    widget.message.content,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: cs.onSurface,
                          height: 1.4,
                        ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _userBubble(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Align(
      alignment: Alignment.centerRight,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.78,
        ),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: cs.primary,
            borderRadius: BorderRadius.only(
              topLeft: const Radius.circular(16),
              topRight: Radius.circular(widget.isStartOfGroup ? 16 : 6),
              bottomLeft: const Radius.circular(16),
              bottomRight: Radius.circular(widget.isEndOfGroup ? 16 : 6),
            ),
          ),
          child: SelectableText(
            widget.message.content,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: cs.onPrimary,
                  height: 1.35,
                ),
          ),
        ),
      ),
    );
  }

  Widget _assistantBlock(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final isError = widget.message.isError;
    final accent = isError ? cs.error : cs.primary.withValues(alpha: 0.6);

    return Padding(
      padding: const EdgeInsets.only(right: 24),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              width: 2,
              margin: const EdgeInsets.only(right: 12),
              decoration: BoxDecoration(
                color: accent.withValues(alpha: widget.isStartOfGroup ? 1.0 : 0.4),
                borderRadius: BorderRadius.circular(1),
              ),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (widget.isStartOfGroup)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Text(
                        isError ? 'Jade — couldn\'t connect' : 'Jade',
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(
                              color: isError ? cs.error : cs.onSurfaceVariant,
                              fontWeight: FontWeight.w600,
                              letterSpacing: 0.2,
                            ),
                      ),
                    ),
                  SelectableText(
                    widget.message.content,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: isError
                              ? cs.onSurface.withValues(alpha: 0.85)
                              : cs.onSurface,
                          height: 1.45,
                          fontSize: 15,
                        ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Typing indicator
// ─────────────────────────────────────────────────────────────────────────────

class _TypingDots extends StatelessWidget {
  final AnimationController controller;
  const _TypingDots({required this.controller});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(top: 16, left: 14),
      child: SizedBox(
        width: 36,
        height: 12,
        child: AnimatedBuilder(
          animation: controller,
          builder: (_, __) {
            return Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: List.generate(3, (i) {
                final t = ((controller.value + i * 0.18) % 1.0);
                final opacity = 0.25 + 0.65 * (1 - (2 * t - 1).abs());
                return Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                    color: cs.onSurfaceVariant.withValues(alpha: opacity),
                    shape: BoxShape.circle,
                  ),
                );
              }),
            );
          },
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state — the brand-new-user hero
//
// Shows Jade's three modes (Write / Adjust / Ask) as discoverable, tappable
// suggestion cards. "Adjust" is the killer feature — most chat surfaces don't
// have hands. Surfacing it as its own column with a bolt icon tells the user
// at a glance: this isn't a chat box, it's a tool.
// ─────────────────────────────────────────────────────────────────────────────

class _JadeEmptyState extends StatelessWidget {
  final ValueChanged<String> onPickStarter;
  const _JadeEmptyState({required this.onPickStarter});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
      children: [
        // Header: subtle pulse avatar + greeting.
        Center(
          child: Column(
            children: [
              _PulseRing(
                color: cs.primary,
                child: const AnimatedJadeAvatar(
                  size: 56,
                  enableRotation: false,
                  showBorder: false,
                ),
              ),
              const SizedBox(height: 18),
              Text(
                "Hi, I'm Jade.",
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.4,
                    ),
              ),
              const SizedBox(height: 4),
              Text(
                'Your in-app writing assistant.',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: cs.onSurfaceVariant,
                    ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 28),
        _StarterGroup(
          title: 'Write',
          subtitle: 'Paste text, get a rewrite.',
          icon: CupertinoIcons.pencil,
          items: const [
            _Starter('Improve this', 'Improve this: '),
            _Starter('Paraphrase formally', 'Paraphrase this formally: '),
            _Starter('Make it concise', 'Rewrite this more concisely: '),
          ],
          onPick: onPickStarter,
        ),
        const SizedBox(height: 14),
        _StarterGroup(
          title: 'Adjust',
          subtitle: 'Tell me to change something in the app.',
          icon: CupertinoIcons.bolt_fill,
          accent: true,
          items: const [
            _Starter('Switch to dark mode', 'Switch to dark mode'),
            _Starter('Mute haptics', 'Turn off haptics'),
            _Starter('Bigger text', 'Make the text bigger'),
          ],
          onPick: onPickStarter,
        ),
        const SizedBox(height: 14),
        _StarterGroup(
          title: 'Ask',
          subtitle: 'I can explain what I do.',
          icon: CupertinoIcons.question_circle,
          items: const [
            _Starter('What can you change?',
                'List every app setting you can change for me'),
            _Starter('How do you work?', 'How do you work behind the scenes?'),
          ],
          onPick: onPickStarter,
        ),
        const SizedBox(height: 8),
      ],
    );
  }
}

class _Starter {
  final String label;
  final String prompt;
  const _Starter(this.label, this.prompt);
}

class _StarterGroup extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final List<_Starter> items;
  final ValueChanged<String> onPick;
  final bool accent;

  const _StarterGroup({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.items,
    required this.onPick,
    this.accent = false,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final iconColor = accent ? cs.primary : cs.onSurfaceVariant;
    final borderColor = accent
        ? cs.primary.withValues(alpha: 0.22)
        : cs.outlineVariant.withValues(alpha: 0.55);

    return Container(
      decoration: BoxDecoration(
        color: cs.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: borderColor, width: 0.5),
      ),
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 15, color: iconColor),
              const SizedBox(width: 7),
              Text(
                title,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: iconColor,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.4,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 2),
          Padding(
            padding: const EdgeInsets.only(left: 22),
            child: Text(
              subtitle,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: cs.onSurfaceVariant,
                  ),
            ),
          ),
          const SizedBox(height: 6),
          ...items.map(
            (s) => _StarterRow(
              label: s.label,
              accent: accent,
              onTap: () => onPick(s.prompt),
            ),
          ),
        ],
      ),
    );
  }
}

class _StarterRow extends StatefulWidget {
  final String label;
  final bool accent;
  final VoidCallback onTap;

  const _StarterRow({
    required this.label,
    required this.accent,
    required this.onTap,
  });

  @override
  State<_StarterRow> createState() => _StarterRowState();
}

class _StarterRowState extends State<_StarterRow> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      onTap: () {
        HapticFeedback.selectionClick();
        widget.onTap();
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 120),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
        margin: const EdgeInsets.only(top: 4),
        decoration: BoxDecoration(
          color: _pressed
              ? cs.onSurface.withValues(alpha: 0.04)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                widget.label,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: cs.onSurface,
                      fontWeight: FontWeight.w500,
                    ),
              ),
            ),
            Icon(
              CupertinoIcons.arrow_up_right,
              size: 14,
              color: widget.accent ? cs.primary : cs.onSurfaceVariant,
            ),
          ],
        ),
      ),
    );
  }
}

/// Subtle pulse ring behind the empty-state avatar — gives a tiny sign of
/// life without animating the avatar itself (avatar rotation reads as AI
/// slop; ring breathing reads as ambient).
class _PulseRing extends StatefulWidget {
  final Widget child;
  final Color color;
  const _PulseRing({required this.child, required this.color});

  @override
  State<_PulseRing> createState() => _PulseRingState();
}

class _PulseRingState extends State<_PulseRing>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ac;

  @override
  void initState() {
    super.initState();
    _ac = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2400),
    )..repeat();
  }

  @override
  void dispose() {
    _ac.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 88,
      height: 88,
      child: Stack(
        alignment: Alignment.center,
        children: [
          AnimatedBuilder(
            animation: _ac,
            builder: (_, __) {
              final t = _ac.value;
              final scale = 0.85 + 0.25 * t;
              final opacity = (1.0 - t).clamp(0.0, 1.0) * 0.5;
              return Transform.scale(
                scale: scale,
                child: Container(
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: widget.color.withValues(alpha: opacity),
                      width: 1.5,
                    ),
                  ),
                ),
              );
            },
          ),
          widget.child,
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composer
// ─────────────────────────────────────────────────────────────────────────────

class _MessageInput extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode focusNode;
  final bool isTyping;
  final VoidCallback onSend;

  const _MessageInput({
    required this.controller,
    required this.focusNode,
    required this.isTyping,
    required this.onSend,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      child: SafeArea(
        top: false,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 140),
                child: Container(
                  decoration: BoxDecoration(
                    color: cs.surfaceContainerHigh,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: cs.outlineVariant.withValues(alpha: 0.6),
                      width: 0.5,
                    ),
                  ),
                  child: TextField(
                    controller: controller,
                    focusNode: focusNode,
                    decoration: InputDecoration(
                      hintText: 'Message Jade',
                      hintStyle:
                          Theme.of(context).textTheme.bodyMedium?.copyWith(
                                color: cs.onSurfaceVariant
                                    .withValues(alpha: 0.7),
                              ),
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 10,
                      ),
                    ),
                    style: Theme.of(context).textTheme.bodyMedium,
                    maxLines: null,
                    minLines: 1,
                    textCapitalization: TextCapitalization.sentences,
                    onSubmitted: (_) => onSend(),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            _SendButton(isTyping: isTyping, onTap: isTyping ? null : onSend),
          ],
        ),
      ),
    );
  }
}

class _SendButton extends StatelessWidget {
  final bool isTyping;
  final VoidCallback? onTap;
  const _SendButton({required this.isTyping, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final enabled = onTap != null;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        width: 38,
        height: 38,
        decoration: BoxDecoration(
          color: enabled ? cs.primary : cs.surfaceContainerHighest,
          shape: BoxShape.circle,
        ),
        child: Center(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 160),
            child: isTyping
                ? SizedBox(
                    key: const ValueKey('loading'),
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation(cs.onPrimary),
                    ),
                  )
                : Icon(
                    key: const ValueKey('send'),
                    CupertinoIcons.arrow_up,
                    size: 18,
                    color: enabled ? cs.onPrimary : cs.onSurfaceVariant,
                  ),
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Back button (matches CustomAppBar's chevron treatment)
// ─────────────────────────────────────────────────────────────────────────────

class _AppBarBack extends StatefulWidget {
  final VoidCallback onTap;
  const _AppBarBack({required this.onTap});

  @override
  State<_AppBarBack> createState() => _AppBarBackState();
}

class _AppBarBackState extends State<_AppBarBack> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(left: 8),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (_) => setState(() => _pressed = true),
        onTapCancel: () => setState(() => _pressed = false),
        onTapUp: (_) => setState(() => _pressed = false),
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: _pressed
                ? cs.onSurface.withValues(alpha: 0.10)
                : cs.surfaceContainerHighest.withValues(alpha: 0.55),
            shape: BoxShape.circle,
          ),
          child: Icon(
            CupertinoIcons.chevron_back,
            size: 18,
            color: cs.onSurface,
          ),
        ),
      ),
    );
  }
}

class ChatMessage {
  final String content;
  final bool isUser;
  final DateTime timestamp;
  final bool isError;
  /// True when the reply came from JadeSettingsController — i.e. Jade actually
  /// performed an in-app action (theme toggle, pref write, navigation).
  /// Rendered as a compact action card instead of an open-ended chat reply.
  final bool isActionReceipt;

  ChatMessage({
    required this.content,
    required this.isUser,
    required this.timestamp,
    this.isError = false,
    this.isActionReceipt = false,
  });
}
