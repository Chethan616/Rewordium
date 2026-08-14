import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';

import '../screens/ai_detector_page.dart';
import '../screens/jade_chat_screen.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/translator_page.dart';

/// Tools FAB — Material 3 Expressive FAB Menu.
///
/// Features animated fan-out menu items, expressive morphing icons,
/// and Material 3 Expressive styling.
class ToolsFab extends StatelessWidget {
  final ValueChanged<int>? onSelectHomeTab;
  final String tooltip;

  const ToolsFab({
    super.key,
    this.onSelectHomeTab,
    this.tooltip = 'Tools',
  });

  void _navigateTo(BuildContext context, Widget page) {
    HapticFeedback.lightImpact();
    Navigator.of(context, rootNavigator: true).push(
      MaterialPageRoute(builder: (_) => page),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Transform.scale(
      scale: 0.85,
      alignment: Alignment.bottomRight,
      child: M3EFabMenu(
        position: M3EFabMenuPosition.right,
        expandIcon: const Icon(CupertinoIcons.add),
        collapseIcon: const Icon(CupertinoIcons.xmark),
        items: <M3EFabMenuItem>[
          M3EFabMenuItem(
            icon: const Icon(CupertinoIcons.sparkles),
            label: 'Jade AI',
            onPressed: () => _navigateTo(context, const JadeChatScreen()),
          ),
          M3EFabMenuItem(
            icon: const Icon(CupertinoIcons.shield_lefthalf_fill),
            label: 'AI Detector',
            onPressed: () => _navigateTo(context, const AIDetectorPage()),
          ),
          M3EFabMenuItem(
            icon: const Icon(CupertinoIcons.globe),
            label: 'Translator',
            onPressed: () => _navigateTo(context, const TranslatorPage()),
          ),
          M3EFabMenuItem(
            icon: const Icon(CupertinoIcons.doc_text),
            label: 'Summarizer',
            onPressed: () => _navigateTo(context, const SummarizerPage()),
          ),
          M3EFabMenuItem(
            icon: const Icon(CupertinoIcons.waveform),
            label: 'Tone Editor',
            onPressed: () => _navigateTo(context, const ToneEditorPage()),
          ),
        ],
      ),
    );
  }
}
