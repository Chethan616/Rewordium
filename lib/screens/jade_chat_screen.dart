import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:animate_do/animate_do.dart';

import '../theme/app_theme.dart';
import '../services/groq_service.dart';
import '../services/jade_settings_controller.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/animated_card.dart';
import '../widgets/animated_jade_avatar.dart';
import '../utils/lottie_assets.dart';

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
  late AnimationController _waveAnimationController;

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
    _waveAnimationController = AnimationController(
      duration: const Duration(seconds: 4),
      vsync: this,
    );

    // Add focus listener for better UI responsiveness
    _messageFocusNode.addListener(() {
      setState(() {}); // Trigger rebuild for focus-based styling
    });

    // Start continuous wave animation
    _startContinuousWaveAnimation();

    // Add welcome message from Jade
    _addWelcomeMessage();
  }

  void _startContinuousWaveAnimation() {
    _waveAnimationController.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        _waveAnimationController.reverse();
      } else if (status == AnimationStatus.dismissed) {
        _waveAnimationController.forward();
      }
    });
    _waveAnimationController.forward();
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    _messageFocusNode.dispose();
    _typingAnimationController.dispose();
    _sendButtonController.dispose();
    _waveAnimationController.dispose();
    super.dispose();
  }

  void _addWelcomeMessage() {
    final welcomeMessage = ChatMessage(
      content:
          "👋 Hi! I'm Jade, your AI writing assistant from Rewordium! \n\nI can help you with:\n\n✨ **Writing & Editing**\n📝 Grammar corrections\n🎯 Content improvement\n💡 Creative ideas\n🌍 Translations\n🎨 Tone adjustments\n\n🛠️ **App Settings Control**\n• Change themes (\"switch to dark mode\")\n• Keyboard settings (\"open keyboard settings\")\n• Notifications & preferences\n• Text size adjustments\n\n💬 **Smart Assistance**\nJust ask me naturally! I understand context and can help you get things done quickly.\n\nWhat can I help you with today?",
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
        // Regular AI chat response
        await GroqService.initialize();

        try {
          final result = await GroqService.paraphraseWithCustomPrompt(
            message,
            "You are Jade, Rewordium's advanced AI writing assistant. Be helpful, encouraging, and conversational. Use emojis naturally and provide actionable advice. Respond naturally to the user's message without mentioning JSON format.",
          );

          // The paraphraseWithCustomPrompt method returns direct response, not wrapped in success/data
          if (result.containsKey('paraphrased_text') &&
              result['paraphrased_text'] != null) {
            response = result['paraphrased_text'].toString().trim();
          } else if (result.containsKey('error')) {
            // API returned an error
            response =
                "I'm experiencing some technical difficulties. Let me try to help you anyway! 💪\n\nCould you please rephrase your question or try asking something else?";
          } else {
            // Try to get any string value from the result
            final content = result.values.firstWhere(
              (value) => value is String && value.trim().isNotEmpty,
              orElse: () => null,
            );
            response = content?.toString().trim() ??
                "I'm sorry, I couldn't process that request right now. Please try again! 🔄";
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
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: CustomAppBar(
        title: "Jade AI Assistant",
        showBackButton: true,
        onLeadingTap: () => Navigator.pop(context),
        actions: [
          // Settings Help Button
          IconButton(
            icon: Icon(Icons.help_outline, color: AppTheme.primaryColor),
            onPressed: () {
              _sendQuickMessage("What settings can you control?");
            },
            tooltip: "Settings Help",
          ),
          IconButton(
            icon: Icon(Icons.refresh, color: AppTheme.primaryColor),
            onPressed: () {
              setState(() {
                _messages.clear();
              });
              _addWelcomeMessage();
            },
            tooltip: "New Chat",
          ),
        ],
      ),
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              AppTheme.backgroundColor,
              AppTheme.backgroundColor.withOpacity(0.95),
              Colors.purple.withOpacity(0.05),
            ],
          ),
        ),
        child: Stack(
          children: [
            // Wave background animation - Continuous with better performance
            Positioned.fill(
              child: AnimatedBuilder(
                animation: _waveAnimationController,
                builder: (context, child) {
                  return Transform.translate(
                    offset: Offset(
                      50 * (0.5 - _waveAnimationController.value),
                      30 * (0.5 - _waveAnimationController.value),
                    ),
                    child: Opacity(
                      opacity: 0.15 + (0.1 * _waveAnimationController.value),
                      child: LottieAssets.getWaveAnimation(
                        width: MediaQuery.of(context).size.width * 1.2,
                        height: MediaQuery.of(context).size.height * 1.2,
                      ),
                    ),
                  );
                },
              ),
            ),
            // iOS-style moving gradient overlay
            Positioned.fill(
              child: AnimatedContainer(
                duration: const Duration(seconds: 3),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Colors.purple.withOpacity(0.02),
                      Colors.blue.withOpacity(0.02),
                      Colors.teal.withOpacity(0.02),
                      Colors.purple.withOpacity(0.02),
                    ],
                    stops: const [0.0, 0.3, 0.7, 1.0],
                  ),
                ),
              ),
            ),
            // Main content
            Column(
              children: [
                // Chat Header with Jade's avatar and status
                _buildChatHeader(),

                // Quick Actions Bar (only show if no messages yet or last message is from AI)
                if (_messages.length <= 1 || !_messages.last.isUser) ...[
                  _buildQuickActionsBar(),
                ],

                // Messages list
                Expanded(
                  child: ListView.builder(
                    controller: _scrollController,
                    padding:
                        const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
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
          ],
        ),
      ),
    );
  }

  Widget _buildChatHeader() {
    return AnimatedCard(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          // Professional Jade avatar with animated Lottie
          FadeInLeft(
            child: AnimatedJadeAvatar(
              size: 56,
              enableRotation: true,
              rotationInterval: const Duration(seconds: 12),
              showBorder: true,
            ),
          ),
          const SizedBox(width: 12),

          // Jade's info
          Expanded(
            child: FadeInUp(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "Jade",
                    style: AppTheme.headingMedium.copyWith(
                      color: AppTheme.primaryColor,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Row(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: Colors.green,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        "Online • AI Writing Assistant",
                        style: AppTheme.bodySmall.copyWith(
                          color: AppTheme.textSecondaryColor,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageBubble(ChatMessage message, int index) {
    return RepaintBoundary(
      child: FadeInUp(
        delay: Duration(milliseconds: index * 50),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            mainAxisAlignment: message.isUser
                ? MainAxisAlignment.end
                : MainAxisAlignment.start,
            children: [
              if (!message.isUser) ...[
                RepaintBoundary(
                  child: Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: message.isError
                            ? [
                                Colors.red.withOpacity(0.8),
                                Colors.red.withOpacity(0.6)
                              ]
                            : [
                                const Color(0xFF6B73FF).withOpacity(0.9),
                                const Color(0xFF9B59B6).withOpacity(0.9),
                                const Color(0xFFE91E63).withOpacity(0.9),
                                const Color(0xFF4ECDC4).withOpacity(0.9),
                              ],
                      ),
                      borderRadius: BorderRadius.circular(18),
                      boxShadow: [
                        BoxShadow(
                          color: message.isError
                              ? Colors.red.withOpacity(0.3)
                              : const Color(0xFF6B73FF).withOpacity(0.3),
                          blurRadius: 8,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: message.isError
                        ? const Center(
                            child: Text(
                              "⚠️",
                              style: TextStyle(fontSize: 16),
                            ),
                          )
                        : Container(
                            margin: const EdgeInsets.all(2),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: const Center(
                              child: AnimatedJadeAvatar(
                                size: 32,
                                enableRotation: true,
                                rotationInterval: Duration(seconds: 6),
                                showBorder: false,
                              ),
                            ),
                          ),
                  ),
                ),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: message.isUser
                          ? [
                              const Color(0xFF6B73FF),
                              const Color(0xFF9B59B6),
                            ]
                          : message.isError
                              ? [
                                  Colors.red.withOpacity(0.1),
                                  Colors.red.withOpacity(0.05)
                                ]
                              : [
                                  Colors.white,
                                  const Color(0xFFF8F9FF),
                                ],
                    ),
                    borderRadius: BorderRadius.only(
                      topLeft: const Radius.circular(20),
                      topRight: const Radius.circular(20),
                      bottomLeft: message.isUser
                          ? const Radius.circular(20)
                          : const Radius.circular(4),
                      bottomRight: message.isUser
                          ? const Radius.circular(4)
                          : const Radius.circular(20),
                    ),
                    border: message.isError
                        ? Border.all(color: Colors.red.withOpacity(0.3))
                        : message.isUser
                            ? null
                            : Border.all(color: Colors.grey.withOpacity(0.1)),
                    boxShadow: [
                      BoxShadow(
                        color: message.isUser
                            ? const Color(0xFF6B73FF).withOpacity(0.2)
                            : Colors.black.withOpacity(0.05),
                        blurRadius: 8,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        message.content,
                        style: AppTheme.bodyMedium.copyWith(
                          color: message.isUser
                              ? Colors.white
                              : message.isError
                                  ? Colors.red.shade700
                                  : AppTheme.textPrimaryColor,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _formatTime(message.timestamp),
                        style: AppTheme.bodySmall.copyWith(
                          color: message.isUser
                              ? Colors.white.withOpacity(0.7)
                              : AppTheme.textSecondaryColor,
                          fontSize: 10,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (message.isUser) ...[
                const SizedBox(width: 8),
                RepaintBoundary(
                  child: Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: AppTheme.cardColor,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: AppTheme.primaryColor.withOpacity(0.2),
                      ),
                    ),
                    child: const Center(
                      child: Text(
                        "👤",
                        style: TextStyle(fontSize: 16),
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTypingIndicator() {
    return FadeInUp(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            // Animated Jade avatar for typing indicator
            AnimatedJadeAvatar(
              size: 36,
              enableRotation: true,
              rotationInterval: const Duration(seconds: 6),
              showBorder: true,
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    Colors.grey.withOpacity(0.1),
                    Colors.grey.withOpacity(0.05)
                  ],
                ),
                borderRadius: BorderRadius.circular(20),
              ),
              child: AnimatedBuilder(
                animation: _typingAnimationController,
                builder: (context, child) {
                  return Row(
                    mainAxisSize: MainAxisSize.min,
                    children: List.generate(3, (index) {
                      return AnimatedContainer(
                        duration: Duration(milliseconds: 300 + (index * 100)),
                        margin: const EdgeInsets.symmetric(horizontal: 2),
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          color: AppTheme.primaryColor.withOpacity(
                            ((_typingAnimationController.value + index * 0.3) %
                                    1.0)
                                .clamp(0.3, 1.0),
                          ),
                          shape: BoxShape.circle,
                        ),
                      );
                    }),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageInput() {
    return RepaintBoundary(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              AppTheme.cardColor.withOpacity(0.95),
              AppTheme.cardColor,
            ],
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.08),
              blurRadius: 15,
              offset: const Offset(0, -3),
            ),
          ],
        ),
        child: SafeArea(
          child: Row(
            children: [
              Expanded(
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  curve: Curves.easeInOut,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        Colors.white,
                        const Color(0xFFF8F9FF),
                      ],
                    ),
                    borderRadius: BorderRadius.circular(25),
                    border: Border.all(
                      color: _messageFocusNode.hasFocus
                          ? const Color(0xFF6B73FF).withOpacity(0.3)
                          : const Color(0xFF6B73FF).withOpacity(0.1),
                      width: _messageFocusNode.hasFocus ? 2.0 : 1.5,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: _messageFocusNode.hasFocus
                            ? const Color(0xFF6B73FF).withOpacity(0.2)
                            : const Color(0xFF6B73FF).withOpacity(0.1),
                        blurRadius: _messageFocusNode.hasFocus ? 15 : 10,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: TextField(
                    controller: _messageController,
                    focusNode: _messageFocusNode,
                    decoration: InputDecoration(
                      hintText: "Ask Jade anything... 💭",
                      hintStyle: AppTheme.bodyMedium.copyWith(
                        color: AppTheme.textSecondaryColor,
                      ),
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 12,
                      ),
                    ),
                    style: AppTheme.bodyMedium,
                    maxLines: null,
                    textCapitalization: TextCapitalization.sentences,
                    onSubmitted: (_) => _sendMessage(),
                  ),
                ),
              ),
              const SizedBox(width: 8),

              // Enhanced Send button with better animations
              RepaintBoundary(
                child: AnimatedBuilder(
                  animation: _sendButtonController,
                  builder: (context, child) {
                    return Transform.scale(
                      scale: 1.0 + (_sendButtonController.value * 0.1),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 200),
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: _isTyping
                                ? [
                                    Colors.grey.withOpacity(0.6),
                                    Colors.grey.withOpacity(0.4)
                                  ]
                                : [
                                    const Color(0xFF6B73FF),
                                    const Color(0xFF9B59B6),
                                    const Color(0xFFE91E63),
                                  ],
                          ),
                          borderRadius: BorderRadius.circular(24),
                          boxShadow: [
                            BoxShadow(
                              color: _isTyping
                                  ? Colors.grey.withOpacity(0.2)
                                  : const Color(0xFF6B73FF).withOpacity(0.4),
                              blurRadius: 12,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Material(
                          color: Colors.transparent,
                          child: InkWell(
                            onTap: _isTyping
                                ? null
                                : () {
                                    _sendButtonController.forward().then((_) {
                                      _sendButtonController.reverse();
                                    });
                                    _sendMessage();
                                  },
                            borderRadius: BorderRadius.circular(24),
                            child: Center(
                              child: AnimatedSwitcher(
                                duration: const Duration(milliseconds: 300),
                                child: _isTyping
                                    ? SizedBox(
                                        key: const ValueKey('loading'),
                                        width: 20,
                                        height: 20,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                          color: Colors.white.withOpacity(0.8),
                                        ),
                                      )
                                    : Icon(
                                        key: const ValueKey('send'),
                                        Icons.send_rounded,
                                        color: Colors.white,
                                        size: 22,
                                      ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
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
    _messageController.text = message;
    _sendMessage();
  }

  // Quick Actions Bar
  Widget _buildQuickActionsBar() {
    final quickActions = [
      {
        'icon': Icons.dark_mode,
        'label': 'Dark Mode',
        'message': 'Switch to dark mode'
      },
      {
        'icon': Icons.keyboard,
        'label': 'Keyboard',
        'message': 'Open keyboard settings'
      },
      {
        'icon': Icons.notifications,
        'label': 'Notifications',
        'message': 'Toggle notifications'
      },
      {
        'icon': Icons.help,
        'label': 'Help',
        'message': 'What settings can you control?'
      },
    ];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: quickActions.map((action) {
            return Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: () => _sendQuickMessage(action['message'] as String),
                  borderRadius: BorderRadius.circular(20),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 10),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          AppTheme.primaryColor.withOpacity(0.1),
                          AppTheme.primaryColor.withOpacity(0.05),
                        ],
                      ),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: AppTheme.primaryColor.withOpacity(0.2),
                        width: 1,
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          action['icon'] as IconData,
                          size: 16,
                          color: AppTheme.primaryColor,
                        ),
                        const SizedBox(width: 6),
                        Text(
                          action['label'] as String,
                          style: AppTheme.bodySmall.copyWith(
                            color: AppTheme.primaryColor,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
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
