import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';

import '../providers/auth_provider.dart';
import '../utils/lottie_assets.dart';
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
import '../services/document_service.dart';
import '../screens/document_viewer_screen.dart';

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
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Hero header card
            Container(
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    colorScheme.primaryContainer,
                    colorScheme.secondaryContainer,
                  ],
                ),
                borderRadius: BorderRadius.circular(28),
              ),
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
                              style: textTheme.bodyLarge?.copyWith(
                                color: colorScheme.onPrimaryContainer
                                    .withValues(alpha: 0.7),
                              ),
                            ),
                            Text(
                              userName,
                              style: textTheme.headlineMedium?.copyWith(
                                color: colorScheme.onPrimaryContainer,
                                fontWeight: FontWeight.w800,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      if (!isLoggedIn)
                        ButtonM3E(
                          onPressed: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                  builder: (_) => const LoginScreen()),
                            );
                          },
                          label: const Text("Log in"),
                          style: ButtonM3EStyle.filled,
                          size: ButtonM3ESize.sm,
                          shape: ButtonM3EShape.round,
                        )
                      else
                        IconButtonM3E(
                          icon: const Icon(CupertinoIcons.person_fill),
                          onPressed: () => _showProfileDropdown(context),
                          variant: IconButtonM3EVariant.tonal,
                          size: IconButtonM3ESize.md,
                          shape: IconButtonM3EShapeVariant.round,
                        ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    "Improve your writing with AI",
                    style: textTheme.bodyMedium?.copyWith(
                      color: colorScheme.onPrimaryContainer
                          .withValues(alpha: 0.6),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 4),
            _buildSectionTitle(context, "Your Tools"),
            const _ToolsRow(),
            const SizedBox(height: 4),
            _buildSectionTitle(context, "Setup Status"),
            const KeyboardStatusCard(),
            const AssistantStatusCard(),
            const FeedbackCard(),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(BuildContext context, String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Text(title, style: Theme.of(context).textTheme.titleLarge),
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
          _buildDocActionCard(
            context,
            title: "Scan Doc",
            subtitle: "Camera scan to text",
            icon: CupertinoIcons.camera_viewfinder,
            color: const Color(0xFF7C3AED),
            isSmallScreen: isSmallScreen,
            onTap: () async {
              try {
                final result = await DocumentService.scanDocument();
                if (result != null && result.text.isNotEmpty && context.mounted) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => DocumentViewerScreen(
                        document: result,
                        onUseText: (text) {
                          // User can choose a tool from the viewer
                        },
                      ),
                    ),
                  );
                }
              } catch (e) {
                debugPrint('Scan error: $e');
              }
            },
          ),
          _buildDocActionCard(
            context,
            title: "Import File",
            subtitle: "PDF, DOCX, TXT",
            icon: CupertinoIcons.doc_on_doc,
            color: const Color(0xFF1E3A8A),
            isSmallScreen: isSmallScreen,
            onTap: () async {
              try {
                final result = await DocumentService.pickFile();
                if (result != null && result.text.isNotEmpty && context.mounted) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => DocumentViewerScreen(
                        document: result,
                        onUseText: (text) {},
                      ),
                    ),
                  );
                }
              } catch (e) {
                debugPrint('Import error: $e');
              }
            },
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
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return FadeInRight(
      delay: const Duration(milliseconds: 200),
      child: GestureDetector(
        onTap: () {
          final normalizedTitle = title.trim().toLowerCase();
          if (normalizedTitle == "paraphraser") {
            Navigator.push(context,
                MaterialPageRoute(builder: (_) => const ParaphraserPage()));
          } else if (normalizedTitle == "grammar check" ||
              normalizedTitle == "grammar") {
            Navigator.push(context,
                MaterialPageRoute(builder: (_) => const GrammarPage()));
          } else if (normalizedTitle == "translator") {
            Navigator.push(context,
                MaterialPageRoute(builder: (_) => const TranslatorPage()));
          } else if (normalizedTitle == "ai detector") {
            Navigator.push(context,
                MaterialPageRoute(builder: (_) => const AIDetectorPage()));
          } else if (normalizedTitle == "summarizer") {
            Navigator.push(context,
                MaterialPageRoute(builder: (_) => const SummarizerPage()));
          } else if (normalizedTitle == "tone editor") {
            Navigator.push(context,
                MaterialPageRoute(builder: (_) => const ToneEditorPage()));
          } else if (normalizedTitle == "jade ai") {
            Navigator.push(context,
                MaterialPageRoute(builder: (_) => const JadeChatScreen()));
          }
        },
        child: Container(
          width: isSmallScreen ? 140 : 160,
          margin: EdgeInsets.all(isSmallScreen ? 6 : 8),
          padding: EdgeInsets.all(isSmallScreen ? 8 : 12),
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainer,
            borderRadius: BorderRadius.circular(20),
            border: Border(
              left: BorderSide(color: color, width: 3),
            ),
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
                    style: textTheme.titleSmall?.copyWith(
                      fontSize: isSmallScreen ? 14 : 16,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    subtitle,
                    style: textTheme.bodySmall?.copyWith(
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

  Widget _buildDocActionCard(
    BuildContext context, {
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required bool isSmallScreen,
    required VoidCallback onTap,
  }) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return FadeInRight(
      delay: const Duration(milliseconds: 200),
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: isSmallScreen ? 140 : 160,
          margin: EdgeInsets.all(isSmallScreen ? 6 : 8),
          padding: EdgeInsets.all(isSmallScreen ? 8 : 12),
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainer,
            borderRadius: BorderRadius.circular(20),
            border: Border(
              left: BorderSide(color: color, width: 3),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: color, size: isSmallScreen ? 40 : 48),
              const SizedBox(height: 4),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: textTheme.titleSmall?.copyWith(
                      fontSize: isSmallScreen ? 14 : 16,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    subtitle,
                    style: textTheme.bodySmall?.copyWith(
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
