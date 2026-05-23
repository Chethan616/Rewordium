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
    _addWelcomeMessage();
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    _messageFocusNode.dispose();
    _typingDots.dispose();
    super.dispose();
  }

  void _addWelcomeMessage() {
    _messages.add(ChatMessage(
      content:
          'Paste text to rewrite or ask anything. I can change app settings too.',
      isUser: false,
      timestamp: DateTime.now(),
    ));
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
      if (settingsResponse.isNotEmpty) {
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
          IconButton(
            tooltip: 'New chat',
            icon: const Icon(CupertinoIcons.refresh, size: 20),
            onPressed: () {
              HapticFeedback.selectionClick();
              setState(() => _messages.clear());
              _addWelcomeMessage();
            },
            color: cs.onSurfaceVariant,
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              controller: _scrollController,
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
              itemCount: _messages.length + (_isTyping ? 1 : 0),
              itemBuilder: (context, index) {
                if (index == _messages.length && _isTyping) {
                  return _TypingDots(controller: _typingDots);
                }
                final message = _messages[index];
                final prev = index > 0 ? _messages[index - 1] : null;
                final next =
                    index + 1 < _messages.length ? _messages[index + 1] : null;
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
          if (_messages.length <= 1) _Starters(onPick: (text) {
            _messageController.text = text;
            if (text.endsWith(': ')) {
              _messageController.selection =
                  TextSelection.collapsed(offset: text.length);
              _messageFocusNode.requestFocus();
            } else {
              _sendMessage();
            }
          }),
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Message rendering
// ─────────────────────────────────────────────────────────────────────────────

class _MessageBlock extends StatelessWidget {
  final ChatMessage message;
  final bool isStartOfGroup;
  final bool isEndOfGroup;

  const _MessageBlock({
    required this.message,
    required this.isStartOfGroup,
    required this.isEndOfGroup,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        top: isStartOfGroup ? 18 : 4,
        bottom: isEndOfGroup ? 2 : 0,
      ),
      child: message.isUser ? _userBubble(context) : _assistantBlock(context),
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
              topRight: Radius.circular(isStartOfGroup ? 16 : 6),
              bottomLeft: const Radius.circular(16),
              bottomRight: Radius.circular(isEndOfGroup ? 16 : 6),
            ),
          ),
          child: SelectableText(
            message.content,
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
    final isError = message.isError;
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
                color: accent.withValues(alpha: isStartOfGroup ? 1.0 : 0.4),
                borderRadius: BorderRadius.circular(1),
              ),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (isStartOfGroup)
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
                    message.content,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: isError
                              ? cs.onSurface.withValues(alpha: 0.85)
                              : cs.onSurface,
                          height: 1.45,
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
// Starters (only on empty state)
// ─────────────────────────────────────────────────────────────────────────────

class _Starters extends StatelessWidget {
  final ValueChanged<String> onPick;
  const _Starters({required this.onPick});

  static const _items = <(String, String)>[
    ('Improve this', 'Improve this: '),
    ('Paraphrase', 'Paraphrase: '),
    ('Make formal', 'Rewrite this formally: '),
    ('What can you do?', 'What can you help me with?'),
  ];

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 4),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: _items
              .map((item) => Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: GestureDetector(
                      onTap: () => onPick(item.$2),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 7),
                        decoration: BoxDecoration(
                          color: cs.surfaceContainerHigh,
                          borderRadius: BorderRadius.circular(999),
                          border: Border.all(
                            color: cs.outlineVariant.withValues(alpha: 0.6),
                            width: 0.5,
                          ),
                        ),
                        child: Text(
                          item.$1,
                          style:
                              Theme.of(context).textTheme.labelMedium?.copyWith(
                                    color: cs.onSurface,
                                    fontWeight: FontWeight.w500,
                                  ),
                        ),
                      ),
                    ),
                  ))
              .toList(),
        ),
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

  ChatMessage({
    required this.content,
    required this.isUser,
    required this.timestamp,
    this.isError = false,
  });
}
