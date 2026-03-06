import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:m3e_collection/m3e_collection.dart';

import '../screens/paraphraser_page.dart';
import '../screens/grammar_page.dart';
import '../screens/ai_detector_page.dart';
import '../screens/translator_page.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/document_viewer_screen.dart';
import '../services/document_service.dart';

class ToolPopup extends StatelessWidget {
  const ToolPopup({super.key});
  
  void _navigateToTool(BuildContext context, String toolName) {
    switch (toolName.toLowerCase()) {
      case 'ai detector':
        Navigator.push(context,
            MaterialPageRoute(builder: (_) => const AIDetectorPage()));
        break;
      case 'translator':
        Navigator.push(context,
            MaterialPageRoute(builder: (_) => const TranslatorPage()));
        break;
      case 'paraphraser':
        Navigator.push(context,
            MaterialPageRoute(builder: (_) => const ParaphraserPage()));
        break;
      case 'grammar':
        Navigator.push(context,
            MaterialPageRoute(builder: (_) => const GrammarPage()));
        break;
      case 'summarizer':
        Navigator.push(context,
            MaterialPageRoute(builder: (_) => const SummarizerPage()));
        break;
      case 'tone editor':
        Navigator.push(context,
            MaterialPageRoute(builder: (_) => const ToneEditorPage()));
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
    final tools = _toolList;

    return SlideInUp(
      duration: const Duration(milliseconds: 400),
      child: Container(
        height: popupHeight,
        decoration: BoxDecoration(
          color: colorScheme.surface,
          borderRadius: const BorderRadius.only(
            topLeft: Radius.circular(28),
            topRight: Radius.circular(28),
          ),
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
                    Text("Tools", style: textTheme.headlineMedium),
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(12),
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
                    crossAxisSpacing: 14,
                    mainAxisSpacing: 14,
                    childAspectRatio: 1,
                  ),
                  itemBuilder: (context, index) {
                    final tool = tools[index];
                    return FadeInUp(
                      duration: const Duration(milliseconds: 400),
                      delay: Duration(milliseconds: 60 * index),
                      child: _ToolCard(
                        title: tool.title,
                        icon: tool.icon,
                        color: tool.color,
                        onTap: () {
                          Navigator.pop(context);
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
                child: SizedBox(
                  width: double.infinity,
                  child: ButtonM3E(
                    onPressed: () => Navigator.pop(context),
                    label: const Text('Close'),
                    style: ButtonM3EStyle.tonal,
                    size: ButtonM3ESize.md,
                    shape: ButtonM3EShape.round,
                  ),
                ),
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
    final colorScheme = Theme.of(context).colorScheme;
    return Center(
      child: FadeIn(
        duration: const Duration(milliseconds: 600),
        child: Container(
          width: 36,
          height: 5,
          margin: const EdgeInsets.only(top: 12, bottom: 12),
          decoration: BoxDecoration(
            color: colorScheme.outlineVariant,
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

    return Material(
      color: colorScheme.surfaceContainer,
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            border: Border(
              top: BorderSide(color: color, width: 3),
            ),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                padding: const EdgeInsets.all(14),
                child: Icon(icon, size: 28, color: color),
              ),
              const SizedBox(height: 14),
              Text(
                title,
                textAlign: TextAlign.center,
                style: textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
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

const List<ToolItem> _toolList = [
  ToolItem(
      title: "AI Detector",
      icon: CupertinoIcons.sparkles,
      color: Color(0xFF2E7BFF)),
  ToolItem(
      title: "Translator",
      icon: CupertinoIcons.globe,
      color: Color(0xFF4CAF50)),
  ToolItem(
      title: "Paraphraser",
      icon: CupertinoIcons.text_badge_checkmark,
      color: Color(0xFFFF9800)),
  ToolItem(
      title: "Grammar",
      icon: CupertinoIcons.checkmark_seal_fill,
      color: Color(0xFFE91E63)),
  ToolItem(
      title: "Summarizer",
      icon: CupertinoIcons.doc_text_search,
      color: Color(0xFF9C27B0)),
  ToolItem(
      title: "Tone Editor",
      icon: CupertinoIcons.waveform_path,
      color: Color(0xFF00BCD4)),
  ToolItem(
      title: "Scan Document",
      icon: CupertinoIcons.camera_viewfinder,
      color: Color(0xFF7C3AED)),
  ToolItem(
      title: "Import File",
      icon: CupertinoIcons.doc_on_doc,
      color: Color(0xFF1E3A8A)),
];
