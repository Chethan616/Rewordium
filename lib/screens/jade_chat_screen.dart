import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:m3e_collection/m3e_collection.dart';

import '../services/groq_service.dart';
import '../services/unified_ai_service.dart';
import '../services/jade_settings_controller.dart';
import '../widgets/animated_jade_avatar.dart';

class JadeChatScreen extends StatefulWidget {
  const JadeChatScreen({super.key});

  @override
  State<JadeChatScreen> createState() => _JadeChatScreenState();
}

class _JadeChatScreenState extends State<JadeChatScreen>
    with TickerProviderStateMixin {
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final FocusNode _messageFocusNode = FocusNode();

  List<ChatMessage> _messages = [];
  bool _isTyping = false;
  late AnimationController _typingAnimationController;
  late AnimationController _sendButtonController;

  @override
  void initState() {
    super.initState();
    _typingAnimationController = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );
    _sendButtonController = AnimationController(
      duration: const Duration(milliseconds: 200),
      vsync: this,
    );

    _addWelcomeMessage();
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    _messageFocusNode.dispose();
    _typingAnimationController.dispose();
    _sendButtonController.dispose();
    super.dispose();
  }

  void _addWelcomeMessage() {
    final welcomeMessage = ChatMessage(
      content:
          "Hey there! 👋 I'm Jade, your AI writing assistant.\n\nI can help you with:\n• **Improve your writing** — grammar, clarity, tone\n• **Paraphrase text** — rewrite in different styles\n• **Creative ideas** — brainstorm, outline, draft\n• **Translations** & language help\n• **App settings** — just tell me what to change\n\nTry pasting some text or ask me anything!",
      isUser: false,
      timestamp: DateTime.now(),
    );

    setState(() {
      _messages.add(welcomeMessage);
    });
  }

  Future<void> _sendMessage() async {
    final message = _messageController.text.trim();
    if (message.isEmpty) return;

    // Haptic feedback
    HapticFeedback.lightImpact();

    // Add user message
    final userMessage = ChatMessage(
      content: message,
      isUser: true,
      timestamp: DateTime.now(),
    );

    setState(() {
      _messages.add(userMessage);
      _isTyping = true;
    });

    _messageController.clear();
    _scrollToBottom();

    // Start typing animation only once
    if (!_typingAnimationController.isAnimating) {
      _typingAnimationController.repeat();
    }

    try {
      // First check if it's a settings command with advanced NLP
      final settingsResponse =
          await JadeSettingsController.processCommand(message, context);

      String response;
      if (settingsResponse.isNotEmpty) {
        // It was a settings command
        response = settingsResponse;
      } else {
        // Regular AI chat response using unified service
        await GroqService.initialize();

        // Build conversation context from recent messages (last 6)
        final recentMessages = _messages
            .skip(_messages.length > 7 ? _messages.length - 7 : 0)
            .map((m) => '${m.isUser ? "User" : "Jade"}: ${m.content}')
            .join('\n');

        final contextualMessage =
            'Conversation so far:\n$recentMessages\n\nUser: $message';

        try {
          final result = await UnifiedAIService.chatWithCustomPrompt(
            contextualMessage,
            "You are Jade, Rewordium's advanced AI writing assistant. You are warm, knowledgeable, concise, and encouraging.\n\nCore capabilities:\n- Writing improvement: grammar, clarity, tone, style\n- Paraphrasing & rewriting text in different styles\n- Grammar explanations with examples\n- Translation guidance\n- Creative writing help & brainstorming\n- Content summarization\n- Tone analysis & adjustment\n\nGuidelines:\n- Be concise — aim for 2-4 sentences unless the user asks for detail\n- Use emojis sparingly (1-2 per response, not every line)\n- Give actionable advice with specific examples\n- Remember conversation context — reference earlier messages when relevant\n- If the user shares text, provide specific improvements rather than generic tips\n- For writing feedback, use a friendly but professional tone\n- When asked about app features, be helpful and specific\n- Never fabricate capabilities the app doesn't have",
          );

          // Extract content from the unified service response
          if (result.containsKey('content') && result['content'] != null) {
            response = result['content'].toString().trim();
          } else if (result.containsKey('error')) {
            // API returned an error
            response =
                "I'm experiencing some technical difficulties. Let me try to help you anyway! 💪\n\nCould you please rephrase your question or try asking something else?";
          } else {
            response =
                "Hi there! I'm here to help you with writing, editing, and app settings. What would you like to do today? ✨";
          }

          // Ensure we have a valid response
          if (response.isEmpty) {
            response =
                "Hi there! I'm here to help you with writing, editing, and app settings. What would you like to do today? ✨";
          }
        } catch (e) {
          // Handle any exceptions from the API call
          response =
              "I'm experiencing some technical difficulties right now. Let me try to help you anyway! 💪\n\nWhat would you like assistance with?";
        }
      }

      // Add AI response
      final aiMessage = ChatMessage(
        content: response,
        isUser: false,
        timestamp: DateTime.now(),
      );

      setState(() {
        _messages.add(aiMessage);
        _isTyping = false;
      });

      _typingAnimationController.stop();
      _typingAnimationController.reset();
      _scrollToBottom();
    } catch (e) {
      // Debug: Chat Error: $e
      // Handle error with more helpful message
      final errorMessage = ChatMessage(
        content:
            "I'm sorry, I'm having trouble connecting right now. Please check your internet connection and try again! 🌐\n\nIn the meantime, I'm here to help with:\n• Writing assistance ✍️\n• Grammar corrections 📝\n• Content improvement 🎯\n• Creative ideas �",
        isUser: false,
        timestamp: DateTime.now(),
        isError: true,
      );

      setState(() {
        _messages.add(errorMessage);
        _isTyping = false;
      });

      _typingAnimationController.stop();
      _typingAnimationController.reset();
      _scrollToBottom();
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: colorScheme.surfaceContainerLowest,
      appBar: AppBar(
        backgroundColor: colorScheme.surface,
        elevation: 0,
        scrolledUnderElevation: 1,
        leading: IconButtonM3E(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => Navigator.pop(context),
          variant: IconButtonM3EVariant.standard,
        ),
        title: Row(
          children: [
            AnimatedJadeAvatar(
              size: 32,
              enableRotation: true,
              rotationInterval: const Duration(seconds: 12),
              showBorder: false,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Jade',
                    style: Theme.of(context).textTheme.titleMedium!.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Row(
                    children: [
                      Container(
                        width: 6, height: 6,
                        decoration: BoxDecoration(
                          color: colorScheme.tertiary,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        'Online',
                        style: Theme.of(context).textTheme.bodySmall!.copyWith(
                          color: colorScheme.onSurfaceVariant,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButtonM3E(
            icon: const Icon(Icons.help_outline_rounded, size: 20),
            onPressed: () => _sendQuickMessage("What settings can you control?"),
            variant: IconButtonM3EVariant.standard,
          ),
          IconButtonM3E(
            icon: const Icon(Icons.refresh_rounded, size: 20),
            onPressed: () {
              setState(() => _messages.clear());
              _addWelcomeMessage();
            },
            variant: IconButtonM3EVariant.standard,
          ),
        ],
      ),
      body: Column(
        children: [
          // Quick Actions Bar
          if (_messages.length <= 1 || !_messages.last.isUser)
            _buildQuickActionsBar(),

          // Messages list
          Expanded(
            child: ListView.builder(
              controller: _scrollController,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              itemCount: _messages.length + (_isTyping ? 1 : 0),
              itemBuilder: (context, index) {
                if (index == _messages.length && _isTyping) {
                  return _buildTypingIndicator();
                }
                return _buildMessageBubble(_messages[index], index);
              },
            ),
          ),

          // Message input
          _buildMessageInput(),
        ],
      ),
    );
  }

  Widget _buildMessageBubble(ChatMessage message, int index) {
    final colorScheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: message.isUser
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (!message.isUser) ...[
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: message.isError
                    ? colorScheme.errorContainer
                    : colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(16),
              ),
              child: message.isError
                  ? Center(child: Icon(Icons.warning_rounded, size: 16, color: colorScheme.error))
                  : const AnimatedJadeAvatar(
                      size: 32,
                      enableRotation: true,
                      rotationInterval: Duration(seconds: 8),
                      showBorder: false,
                    ),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: message.isUser
                    ? colorScheme.primary
                    : message.isError
                        ? colorScheme.errorContainer
                        : colorScheme.surfaceContainer,
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(18),
                  topRight: const Radius.circular(18),
                  bottomLeft: message.isUser
                      ? const Radius.circular(18)
                      : const Radius.circular(4),
                  bottomRight: message.isUser
                      ? const Radius.circular(4)
                      : const Radius.circular(18),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    message.content,
                    style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                      color: message.isUser
                          ? colorScheme.onPrimary
                          : message.isError
                              ? colorScheme.onErrorContainer
                              : colorScheme.onSurface,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _formatTime(message.timestamp),
                    style: Theme.of(context).textTheme.bodySmall!.copyWith(
                      color: message.isUser
                          ? colorScheme.onPrimary.withValues(alpha: 0.6)
                          : colorScheme.onSurfaceVariant,
                      fontSize: 10,
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (message.isUser) ...[
            const SizedBox(width: 8),
            Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                color: colorScheme.secondaryContainer,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(Icons.person_rounded, size: 16, color: colorScheme.onSecondaryContainer),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildTypingIndicator() {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: colorScheme.primaryContainer,
              borderRadius: BorderRadius.circular(16),
            ),
            child: const AnimatedJadeAvatar(
              size: 32,
              enableRotation: true,
              rotationInterval: Duration(seconds: 6),
              showBorder: false,
            ),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: BoxDecoration(
              color: colorScheme.surfaceContainer,
              borderRadius: BorderRadius.circular(18),
            ),
            child: LoadingIndicatorM3E(
              color: colorScheme.primary,
              constraints: const BoxConstraints(maxWidth: 32, maxHeight: 24),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageInput() {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainer,
                  borderRadius: BorderRadius.circular(24),
                ),
                child: TextField(
                  controller: _messageController,
                  focusNode: _messageFocusNode,
                  decoration: InputDecoration(
                    hintText: "Ask Jade anything...",
                    hintStyle: Theme.of(context).textTheme.bodyMedium!.copyWith(
                      color: colorScheme.onSurfaceVariant,
                    ),
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 18,
                      vertical: 12,
                    ),
                  ),
                  style: Theme.of(context).textTheme.bodyMedium!,
                  maxLines: null,
                  textCapitalization: TextCapitalization.sentences,
                  onSubmitted: (_) => _sendMessage(),
                ),
              ),
            ),
            const SizedBox(width: 8),
            IconButtonM3E(
              icon: AnimatedSwitcher(
                duration: const Duration(milliseconds: 200),
                child: _isTyping
                    ? LoadingIndicatorM3E(
                        key: const ValueKey('loading'),
                        constraints: const BoxConstraints(maxWidth: 20, maxHeight: 20),
                        color: colorScheme.onPrimary,
                      )
                    : Icon(
                        key: const ValueKey('send'),
                        Icons.send_rounded,
                        color: colorScheme.onPrimary,
                        size: 20,
                      ),
              ),
              onPressed: _isTyping ? null : () {
                _sendButtonController.forward().then((_) {
                  _sendButtonController.reverse();
                });
                _sendMessage();
              },
              variant: IconButtonM3EVariant.filled,
            ),
          ],
        ),
      ),
    );
  }

  String _formatTime(DateTime timestamp) {
    final now = DateTime.now();
    final difference = now.difference(timestamp);

    if (difference.inMinutes < 1) {
      return "Just now";
    } else if (difference.inHours < 1) {
      return "${difference.inMinutes}m ago";
    } else if (difference.inDays < 1) {
      return "${difference.inHours}h ago";
    } else {
      return "${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}";
    }
  }

  // Quick message sender for buttons
  void _sendQuickMessage(String message) {
    // If the message ends with a space/colon, it's a prompt — just populate the input
    if (message.endsWith(': ')) {
      _messageController.text = message;
      _messageController.selection = TextSelection.collapsed(offset: message.length);
      _messageFocusNode.requestFocus();
      return;
    }
    _messageController.text = message;
    _sendMessage();
  }

  Widget _buildQuickActionsBar() {
    final colorScheme = Theme.of(context).colorScheme;
    final quickActions = [
      {'icon': Icons.edit_note_rounded, 'label': 'Improve Text', 'message': 'Help me improve this text: '},
      {'icon': Icons.auto_awesome_rounded, 'label': 'Paraphrase', 'message': 'Paraphrase this for me: '},
      {'icon': Icons.dark_mode_rounded, 'label': 'Dark Mode', 'message': 'Switch to dark mode'},
      {'icon': Icons.help_rounded, 'label': 'What Can You Do?', 'message': 'What can you help me with?'},
    ];

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: quickActions.map((action) {
            return Padding(
              padding: const EdgeInsets.only(right: 8),
              child: GestureDetector(
                onTap: () => _sendQuickMessage(action['message'] as String),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: colorScheme.surfaceContainer,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: colorScheme.outlineVariant.withValues(alpha: 0.5)),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(action['icon'] as IconData, size: 16, color: colorScheme.primary),
                      const SizedBox(width: 6),
                      Text(
                        action['label'] as String,
                        style: Theme.of(context).textTheme.labelSmall!.copyWith(
                          color: colorScheme.onSurface,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }).toList(),
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
