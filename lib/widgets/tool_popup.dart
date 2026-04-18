import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';

import '../screens/paraphraser_page.dart';
import '../screens/grammar_page.dart';
import '../screens/ai_detector_page.dart';
import '../screens/translator_page.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/document_viewer_screen.dart';
import '../services/document_service.dart';
import '../utils/doc_gate.dart';

class ToolPopup extends StatelessWidget {
  final ValueChanged<int>? onSelectHomeTab;

  const ToolPopup({
    super.key,
    this.onSelectHomeTab,
  });

  // Navigate to the selected tool
  void _navigateToTool(BuildContext context, String toolName) {
    switch (toolName.toLowerCase()) {
      case 'ai detector':
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const AIDetectorPage()),
        );
        break;
      case 'translator':
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const TranslatorPage()),
        );
        break;
      case 'paraphraser':
        if (onSelectHomeTab != null) {
          onSelectHomeTab!(1);
        } else {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (context) => const ParaphraserPage()),
          );
        }
        break;
      case 'grammar':
        if (onSelectHomeTab != null) {
          onSelectHomeTab!(2);
        } else {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (context) => const GrammarPage()),
          );
        }
        break;
      case 'summarizer':
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const SummarizerPage()),
        );
        break;
      case 'tone editor':
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const ToneEditorPage()),
        );
        break;
      case 'scan document':
        _scanDocument(context);
        break;
      case 'import file':
        _importFile(context);
        break;
    }
  }

  void _scanDocument(BuildContext context) async {
    if (!await DocGate.check(context)) return;
    try {
      final result = await DocumentService.scanDocument();
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
      debugPrint('Scan error: $e');
    }
  }

  void _importFile(BuildContext context) async {
    if (!await DocGate.check(context)) return;
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
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final popupHeight = size.height * 0.6;
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    final tools = _toolList(colorScheme, isDarkMode: isDarkMode);

    return SlideInUp(
      duration: const Duration(milliseconds: 400),
      child: Container(
        height: popupHeight,
        decoration: BoxDecoration(
          color: colorScheme.surfaceContainerLow,
          borderRadius: const BorderRadius.only(
            topLeft: Radius.circular(30),
            topRight: Radius.circular(30),
          ),
          boxShadow: [
            BoxShadow(
              color: colorScheme.scrim.withValues(alpha: 0.15),
              blurRadius: 20,
              offset: const Offset(0, -8),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _Handle(),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 0),
              child: FadeIn(
                duration: const Duration(milliseconds: 600),
                delay: const Duration(milliseconds: 200),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      "Tools",
                      style: textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.5,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        CupertinoIcons.square_grid_2x2,
                        color: colorScheme.onPrimaryContainer,
                        size: 22,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: GridView.builder(
                  physics: const BouncingScrollPhysics(),
                  itemCount: tools.length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 16,
                    mainAxisSpacing: 16,
                    childAspectRatio: 1,
                  ),
                  itemBuilder: (context, index) {
                    final tool = tools[index];
                    return FadeInUp(
                      duration: const Duration(milliseconds: 400),
                      delay: Duration(milliseconds: 100 * index),
                      child: _ToolCard(
                        title: tool.title,
                        icon: tool.icon,
                        color: tool.color,
                        onTap: () {
                          Navigator.pop(context);
                          // Navigate to the selected tool
                          _navigateToTool(context, tool.title);
                        },
                      ),
                    );
                  },
                ),
              ),
            ),
            const SizedBox(height: 16),
            FadeInUp(
              duration: const Duration(milliseconds: 400),
              delay: const Duration(milliseconds: 350),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 26),
                child: _BottomButton(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Handle extends StatelessWidget {
  const _Handle();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: FadeIn(
        duration: const Duration(milliseconds: 600),
        child: Container(
          width: 36,
          height: 5,
          margin: const EdgeInsets.only(top: 12, bottom: 12),
          decoration: BoxDecoration(
            color: Theme.of(context)
                .colorScheme
                .onSurfaceVariant
                .withValues(alpha: 0.35),
            borderRadius: BorderRadius.circular(2.5),
          ),
        ),
      ),
    );
  }
}

class _ToolCard extends StatelessWidget {
  final String title;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  const _ToolCard({
    required this.title,
    required this.icon,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    final backgroundAlpha = isDarkMode ? 0.16 : 0.14;
    final borderAlpha = isDarkMode ? 0.34 : 0.28;
    final iconBackgroundAlpha = isDarkMode ? 0.25 : 0.22;
    final shadowAlpha = isDarkMode ? 0.14 : 0.10;
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: color.withValues(alpha: backgroundAlpha),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: color.withValues(alpha: borderAlpha),
              width: 1.0,
            ),
            boxShadow: [
              BoxShadow(
                color: color.withValues(alpha: shadowAlpha),
                blurRadius: 12,
                offset: const Offset(0, 5),
              ),
            ],
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                decoration: BoxDecoration(
                  color: color.withValues(alpha: iconBackgroundAlpha),
                  shape: BoxShape.circle,
                ),
                padding: const EdgeInsets.all(14),
                child: Icon(icon, size: 28, color: color),
              ),
              const SizedBox(height: 14),
              Text(
                title,
                textAlign: TextAlign.center,
                style: textTheme.bodyLarge?.copyWith(
                  fontWeight: FontWeight.w600,
                  color: colorScheme.onSurface,
                  letterSpacing: -0.2,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BottomButton extends StatelessWidget {
  const _BottomButton();

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    return CupertinoButton(
      padding: EdgeInsets.zero,
      onPressed: () => Navigator.pop(context),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: colorScheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: Colors.red.shade600,
            width: 1.4,
          ),
        ),
        child: Center(
          child: Text(
            "Close",
            style: textTheme.bodyLarge?.copyWith(
              fontWeight: FontWeight.w500,
              color: Colors.red.shade600,
            ),
          ),
        ),
      ),
    );
  }
}

class ToolItem {
  final String title;
  final IconData icon;
  final Color color;

  const ToolItem(
      {required this.title, required this.icon, required this.color});
}

List<ToolItem> _toolList(ColorScheme colorScheme, {required bool isDarkMode}) => [
      ToolItem(
          title: "AI Detector",
          icon: CupertinoIcons.sparkles,
          color: colorScheme.primary),
      ToolItem(
          title: "Translator",
          icon: CupertinoIcons.globe,
          color: colorScheme.secondary),
      ToolItem(
          title: "Paraphraser",
          icon: CupertinoIcons.text_badge_checkmark,
          color: colorScheme.tertiary),
      ToolItem(
          title: "Grammar",
          icon: CupertinoIcons.checkmark_seal_fill,
          color: colorScheme.error),
      ToolItem(
          title: "Summarizer",
          icon: CupertinoIcons.doc_text_search,
          color: isDarkMode
            ? colorScheme.primaryContainer
            : const Color(0xFFB45309)),
      ToolItem(
          title: "Tone Editor",
          icon: CupertinoIcons.waveform_path,
          color: isDarkMode
            ? colorScheme.secondaryContainer
            : const Color(0xFF0F766E)),
      ToolItem(
          title: "Scan Document",
          icon: CupertinoIcons.camera_viewfinder,
          color: isDarkMode
            ? colorScheme.tertiaryContainer
            : const Color(0xFF4C1D95)),
      ToolItem(
          title: "Import File",
          icon: CupertinoIcons.doc_on_doc,
          color: isDarkMode
            ? colorScheme.inversePrimary
            : const Color(0xFF1E3A8A)),
    ];
