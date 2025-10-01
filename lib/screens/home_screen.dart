import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:provider/provider.dart';

import '../providers/auth_provider.dart';
import '../theme/app_theme.dart';
import '../utils/lottie_assets.dart';
import '../widgets/animated_card.dart';
import '../widgets/custom_button.dart';
import '../widgets/home/keyboard_status_card.dart';
import '../widgets/home/assistant_status_card.dart';
import '../widgets/home/feedback_card.dart';
import '../screens/auth/login_screen.dart';
import '../widgets/profile_dropdown.dart';
import '../screens/paraphraser_page.dart';
import '../screens/grammar_page.dart';
import '../screens/translator_page.dart';
import '../screens/ai_detector_page.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/jade_chat_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  void _showProfileDropdown(BuildContext context) {
    final RenderBox overlay =
        Navigator.of(context).overlay!.context.findRenderObject() as RenderBox;
    final Offset topRight = overlay.size.topRight(Offset.zero);
    showMenu(
      context: context,
      position: RelativeRect.fromLTRB(
        topRight.dx - 240, // width of dropdown
        topRight.dy + 40, // below the app bar
        16,
        0,
      ),
      color: Colors.transparent,
      elevation: 0,
      items: [
        PopupMenuItem(
          height: 0,
          padding: EdgeInsets.zero,
          child: const ProfileDropdown(),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);
    final userName = authProvider.userName ?? 'Rewordium';
    final isLoggedIn = authProvider.isLoggedIn;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AnimatedCard(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              isLoggedIn ? "Welcome back" : "Welcome to",
                              style: AppTheme.bodyLarge.copyWith(
                                color: AppTheme.textSecondaryColor,
                              ),
                            ),
                            Text(
                              userName,
                              style: AppTheme.headingLarge.copyWith(
                                color: AppTheme.primaryColor,
                                fontWeight: FontWeight.w900,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      if (!isLoggedIn)
                        CustomButton(
                          text: "Log in",
                          onPressed: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                  builder: (context) => const LoginScreen()),
                            );
                          },
                          width: 90,
                          type: ButtonType.primary,
                        )
                      else
                        IconButton(
                          icon: const Icon(CupertinoIcons.person_fill),
                          onPressed: () => _showProfileDropdown(context),
                          color: AppTheme.primaryColor,
                        ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Text(
                    "Improve your writing with AI",
                    style: AppTheme.bodyMedium,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            _buildSectionTitle("Your Tools"),
            const _ToolsRow(),
            const SizedBox(height: 8),
            _buildSectionTitle("Setup Status"),
            const KeyboardStatusCard(),
            const AssistantStatusCard(),
            const FeedbackCard(),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Text(title, style: AppTheme.headingSmall),
    );
  }
}

class _ToolsRow extends StatelessWidget {
  const _ToolsRow();

  @override
  Widget build(BuildContext context) {
    // Get screen size for responsive design
    final Size screenSize = MediaQuery.of(context).size;
    final bool isSmallScreen = screenSize.width < 360;

    return SizedBox(
      height: isSmallScreen ? 130 : 150,
      child: ListView(
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.symmetric(horizontal: 8),
        children: [
          _cleanToolCard(
            context,
            title: "Paraphraser",
            subtitle: "Rewrite text in your style",
            icon: LottieAssets.getParaphrasingAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            color: Colors.green,
            isSmallScreen: isSmallScreen,
          ),
          _cleanToolCard(
            context,
            title: "Grammar Check",
            subtitle: "Fix errors in your writing",
            icon: LottieAssets.getGrammarCheckAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            color: Colors.red,
            isSmallScreen: isSmallScreen,
          ),
          _cleanToolCard(
            context,
            title: "Translator",
            subtitle: "Translate to any language",
            icon: LottieAssets.getTranslatorAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            color: Colors.blue,
            isSmallScreen: isSmallScreen,
          ),
          _cleanToolCard(
            context,
            title: "AI Detector",
            subtitle: "Check if text is AI-written",
            icon: LottieAssets.getAIDetectorAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            color: Colors.purple,
            isSmallScreen: isSmallScreen,
          ),
          _cleanToolCard(
            context,
            title: "Summarizer",
            subtitle: "Create concise summaries",
            icon: LottieAssets.getSummarizerAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            color: Colors.orange,
            isSmallScreen: isSmallScreen,
          ),
          _cleanToolCard(
            context,
            title: "Tone Editor",
            subtitle: "Adjust the tone of your text",
            icon: LottieAssets.getToneEditorAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            color: Colors.teal,
            isSmallScreen: isSmallScreen,
          ),
          _cleanToolCard(
            context,
            title: "Jade AI",
            subtitle: "Chat with AI assistant",
            icon: LottieAssets.getAssistantAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            color: Colors.deepPurple,
            isSmallScreen: isSmallScreen,
          ),
        ],
      ),
    );
  }

  Widget _cleanToolCard(
    BuildContext context, {
    required String title,
    required String subtitle,
    required Widget icon,
    required Color color,
    required bool isSmallScreen,
  }) {
    return FadeInRight(
      delay: Duration(milliseconds: 200),
      child: GestureDetector(
        onTap: () {
          final normalizedTitle = title.trim().toLowerCase();
          if (normalizedTitle == "paraphraser") {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const ParaphraserPage()),
            );
          } else if (normalizedTitle == "grammar check" ||
              normalizedTitle == "grammar") {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const GrammarPage()),
            );
          } else if (normalizedTitle == "translator") {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const TranslatorPage()),
            );
          } else if (normalizedTitle == "ai detector") {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const AIDetectorPage()),
            );
          } else if (normalizedTitle == "summarizer") {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const SummarizerPage()),
            );
          } else if (normalizedTitle == "tone editor") {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const ToneEditorPage()),
            );
          } else if (normalizedTitle == "jade ai") {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const JadeChatScreen()),
            );
          }
        },
        child: Container(
          width: isSmallScreen ? 140 : 160,
          margin: EdgeInsets.all(isSmallScreen ? 6 : 8),
          padding: EdgeInsets.all(isSmallScreen ? 8 : 12),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [color.withOpacity(0.8), color.withOpacity(0.6)],
            ),
            borderRadius: BorderRadius.circular(18),
            boxShadow: [
              BoxShadow(
                color: color.withOpacity(0.3),
                blurRadius: 12,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Flexible(child: icon),
              const SizedBox(height: 4),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: AppTheme.headingSmall.copyWith(
                      color: Colors.white,
                      fontSize: isSmallScreen ? 14 : 16,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    subtitle,
                    style: AppTheme.bodySmall.copyWith(
                      color: Colors.white.withOpacity(0.8),
                      fontSize: isSmallScreen ? 9 : 10,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
