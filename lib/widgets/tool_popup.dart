import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';
import 'package:animate_do/animate_do.dart';

import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../screens/paraphraser_page.dart';
import '../screens/grammar_page.dart';
import '../screens/ai_detector_page.dart';
import '../screens/translator_page.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';

class ToolPopup extends StatelessWidget {
  const ToolPopup({super.key});
  
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
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const ParaphraserPage()),
        );
        break;
      case 'grammar':
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const GrammarPage()),
        );
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
    }
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final popupHeight = size.height * 0.6;

    final tools = _toolList;

    return SlideInUp(
      duration: const Duration(milliseconds: 400),
      child: Container(
        height: popupHeight,
        decoration: BoxDecoration(
          color: AppTheme.cardColor,
          borderRadius: const BorderRadius.only(
            topLeft: Radius.circular(30),
            topRight: Radius.circular(30),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.15),
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
                      style: AppTheme.headingMedium.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.5,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryColor.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        CupertinoIcons.square_grid_2x2,
                        color: AppTheme.primaryColor,
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
            color: Colors.grey.withOpacity(0.25),
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
            color: color.withOpacity(0.05),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: color.withOpacity(0.18),
              width: 1.0,
            ),
            boxShadow: [
              BoxShadow(
                color: color.withOpacity(0.06),
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
                  color: color.withOpacity(0.12),
                  shape: BoxShape.circle,
                ),
                padding: const EdgeInsets.all(14),
                child: Icon(icon, size: 28, color: color),
              ),
              const SizedBox(height: 14),
              Text(
                title,
                textAlign: TextAlign.center,
                style: AppTheme.bodyLarge.copyWith(
                  fontWeight: FontWeight.w600,
                  color: AppTheme.textPrimaryColor,
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
    return CupertinoButton(
      padding: EdgeInsets.zero,
      onPressed: () => Navigator.pop(context),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: Colors.grey.withOpacity(0.06),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: Colors.grey.withOpacity(0.15),
            width: 1.0,
          ),
        ),
        child: Center(
          child: Text(
            "Close",
            style: AppTheme.bodyLarge.copyWith(
              fontWeight: FontWeight.w500,
              color: AppTheme.textSecondaryColor,
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
];
