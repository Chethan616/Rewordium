import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'package:expressive_refresh/expressive_refresh.dart';

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
import '../utils/responsive.dart';
import '../utils/doc_gate.dart';
import '../theme/theme_provider.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  void _showProfileSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => const ProfileDropdown(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);
    final userName = authProvider.userName ?? 'Rewordium';
    final isLoggedIn = authProvider.isLoggedIn;
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    final themeProvider = Provider.of<ThemeProvider>(context);
    final useDynamicColors = themeProvider.useDynamicColors;
    final r = Responsive.of(context);
    final heroBackgroundColor = useDynamicColors
      ? colorScheme.secondaryContainer
      : (isDarkMode ? const Color(0xFF1E1E1E) : Colors.white);
    final heroAccentColor = useDynamicColors
      ? colorScheme.onSecondaryContainer
      : (isDarkMode ? const Color(0xFFAFFF99) : colorScheme.primary);

    return ExpressiveRefreshIndicator(
      onRefresh: () async {
        await authProvider.refreshUserData();
      },
      color: colorScheme.primary,
      backgroundColor: colorScheme.surface,
      child: SingleChildScrollView(
      physics: const AlwaysScrollableScrollPhysics(
        parent: BouncingScrollPhysics(),
      ),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Hero header card
            Container(
              margin: r.all(16),
              padding: r.all(20),
              decoration: BoxDecoration(
                color: heroBackgroundColor,
                borderRadius: BorderRadius.circular(r.r(28)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: isDarkMode ? 0.25 : 0.08),
                    blurRadius: 20,
                    offset: const Offset(0, 8),
                  ),
                ],
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
                                color: heroAccentColor.withValues(alpha: 0.8),
                              ),
                            ),
                            Text(
                              userName,
                              style: textTheme.headlineMedium?.copyWith(
                                color: heroAccentColor,
                                fontWeight: FontWeight.w800,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      SizedBox(width: r.w(8)),
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
                        GestureDetector(
                          onTap: () => _showProfileSheet(context),
                          child: Container(
                            padding: r.all(3),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(color: heroAccentColor, width: 2),
                            ),
                            child: CircleAvatar(
                              radius: r.r(20),
                              backgroundColor: heroAccentColor.withValues(alpha: 0.15),
                              backgroundImage: authProvider.user?.photoURL != null
                                  ? NetworkImage(authProvider.user!.photoURL!)
                                  : null,
                              child: authProvider.user?.photoURL == null
                                  ? Icon(CupertinoIcons.person_fill, size: r.r(20), color: heroAccentColor)
                                  : null,
                            ),
                          ),
                        ),
                    ],
                  ),
                  SizedBox(height: r.h(12)),
                  Text(
                    "Improve your writing with AI",
                    style: textTheme.bodyMedium?.copyWith(
                      color: heroAccentColor.withValues(alpha: 0.6),
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(height: r.h(4)),
            _buildSectionTitle(context, "Your Tools"),
            _ToolsRow(useDynamicColors: useDynamicColors),
            SizedBox(height: r.h(4)),
            _buildSectionTitle(context, "Setup Status"),
            const KeyboardStatusCard(),
            const AssistantStatusCard(),
            const FeedbackCard(),
            SizedBox(height: r.h(20)),
          ],
        ),
      ),
    ),
    );
  }

  Widget _buildSectionTitle(BuildContext context, String title) {
    final r = Responsive.of(context);
    return Padding(
      padding: r.symmetric(horizontal: 16, vertical: 4),
      child: Text(title, style: Theme.of(context).textTheme.titleLarge),
    );
  }
}

class _ToolsRow extends StatelessWidget {
  const _ToolsRow({required this.useDynamicColors});

  final bool useDynamicColors;

  @override
  Widget build(BuildContext context) {
    final r = Responsive.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final toolColors = useDynamicColors
        ? <Color>[
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary,
            colorScheme.error,
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer,
            colorScheme.tertiaryContainer,
            colorScheme.inversePrimary,
          ]
        : <Color>[
            Colors.green,
            Colors.red,
            Colors.blue,
            Colors.purple,
            Colors.orange,
            Colors.teal,
            Colors.deepPurple,
            const Color(0xFF7C3AED),
          ];

    return SizedBox(
      height: r.h(150),
      child: ListView(
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        padding: r.symmetric(horizontal: 8),
        children: [
          _cleanToolCard(
            context,
            title: "Paraphraser",
            subtitle: "Rewrite text in your style",
            icon: LottieAssets.getParaphrasingAnimation(
              height: r.r(55),
            ),
            color: toolColors[0],
            r: r,
          ),
          _cleanToolCard(
            context,
            title: "Grammar Check",
            subtitle: "Fix errors in your writing",
            icon: LottieAssets.getGrammarCheckAnimation(
              height: r.r(55),
            ),
            color: toolColors[1],
            r: r,
          ),
          _cleanToolCard(
            context,
            title: "Translator",
            subtitle: "Translate to any language",
            icon: LottieAssets.getTranslatorAnimation(
              height: r.r(55),
            ),
            color: toolColors[2],
            r: r,
          ),
          _cleanToolCard(
            context,
            title: "AI Detector",
            subtitle: "Check if text is AI-written",
            icon: LottieAssets.getAIDetectorAnimation(
              height: r.r(55),
            ),
            color: toolColors[3],
            r: r,
          ),
          _cleanToolCard(
            context,
            title: "Summarizer",
            subtitle: "Create concise summaries",
            icon: LottieAssets.getSummarizerAnimation(
              height: r.r(55),
            ),
            color: toolColors[4],
            r: r,
          ),
          _cleanToolCard(
            context,
            title: "Tone Editor",
            subtitle: "Adjust the tone of your text",
            icon: LottieAssets.getToneEditorAnimation(
              height: r.r(55),
            ),
            color: toolColors[5],
            r: r,
          ),
          _cleanToolCard(
            context,
            title: "Jade AI",
            subtitle: "Chat with AI assistant",
            icon: LottieAssets.getAssistantAnimation(
              height: r.r(55),
            ),
            color: toolColors[6],
            r: r,
          ),
          _buildDocActionCard(
            context,
            title: "Scan Doc",
            subtitle: "Camera scan to text",
            icon: CupertinoIcons.camera_viewfinder,
            color: toolColors[7],
            r: r,
            onTap: () async {
              try {
                if (!await DocGate.check(context)) return;
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
            color: useDynamicColors ? colorScheme.primary : const Color(0xFF1E3A8A),
            r: r,
            onTap: () async {
              try {
                if (!await DocGate.check(context)) return;
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
    required ResponsiveData r,
  }) {
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
          width: r.w(155),
          margin: r.all(7),
          padding: r.all(10),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.9),
            borderRadius: BorderRadius.circular(r.r(20)),
            boxShadow: [
              BoxShadow(
                color: color.withValues(alpha: 0.3),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Flexible(child: icon),
              SizedBox(height: r.h(4)),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: textTheme.titleSmall?.copyWith(
                      fontSize: r.sp(15),
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    subtitle,
                    style: textTheme.bodySmall?.copyWith(
                      fontSize: r.sp(9.5),
                      color: Colors.white.withValues(alpha: 0.8),
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
    required ResponsiveData r,
    required VoidCallback onTap,
  }) {
    final textTheme = Theme.of(context).textTheme;

    return FadeInRight(
      delay: const Duration(milliseconds: 200),
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: r.w(155),
          margin: r.all(7),
          padding: r.all(10),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.9),
            borderRadius: BorderRadius.circular(r.r(20)),
            boxShadow: [
              BoxShadow(
                color: color.withValues(alpha: 0.3),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: Colors.white, size: r.r(44)),
              SizedBox(height: r.h(4)),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: textTheme.titleSmall?.copyWith(
                      fontSize: r.sp(15),
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    subtitle,
                    style: textTheme.bodySmall?.copyWith(
                      fontSize: r.sp(9.5),
                      color: Colors.white.withValues(alpha: 0.8),
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
