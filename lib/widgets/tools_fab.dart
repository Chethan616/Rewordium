import 'package:flutter/material.dart';

import '../screens/ai_detector_page.dart';
import '../screens/jade_chat_screen.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/translator_page.dart';

class ToolsFab extends StatefulWidget {
  final ValueChanged<int>? onSelectHomeTab;
  final String tooltip;

  const ToolsFab({
    super.key,
    this.onSelectHomeTab,
    this.tooltip = 'Tools',
  });

  @override
  State<ToolsFab> createState() => _ToolsFabState();
}

class _ToolsFabState extends State<ToolsFab> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  bool _isOpen = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 250),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggleMenu() {
    setState(() {
      _isOpen = !_isOpen;
      if (_isOpen) {
        _controller.forward();
      } else {
        _controller.reverse();
      }
    });
  }

  Future<void> _handleAction(BuildContext context, _ToolAction action) async {
    final navigator = Navigator.of(context, rootNavigator: true);

    switch (action) {
      case _ToolAction.jadeAi:
        navigator.push(MaterialPageRoute(builder: (_) => const JadeChatScreen()));
        return;
      case _ToolAction.aiDetector:
        navigator.push(MaterialPageRoute(builder: (_) => const AIDetectorPage()));
        return;
      case _ToolAction.translator:
        navigator.push(MaterialPageRoute(builder: (_) => const TranslatorPage()));
        return;
      case _ToolAction.summarizer:
        navigator.push(MaterialPageRoute(builder: (_) => const SummarizerPage()));
        return;
      case _ToolAction.toneEditor:
        navigator.push(MaterialPageRoute(builder: (_) => const ToneEditorPage()));
        return;
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        // 1. Cascading Floating Menu Items
        Flexible(
          child: IgnorePointer(
            ignoring: !_isOpen,
            child: FadeTransition(
              opacity: _controller,
              child: ScaleTransition(
                scale: CurvedAnimation(
                  parent: _controller,
                  curve: Curves.easeOutBack,
                ),
                alignment: Alignment.bottomRight,
                child: SingleChildScrollView(
                  reverse: true,
                  physics: const BouncingScrollPhysics(),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: _toolActions()
                        .map((item) => _buildToolItem(context, item))
                        .toList(),
                  ),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        // 2. Main Action Toggle Button
        FloatingActionButton(
          tooltip: widget.tooltip,
          // Adapts exactly to your app's ButtonType.primary theme
          backgroundColor: _isOpen ? colorScheme.primary : colorScheme.primaryContainer,
          foregroundColor: _isOpen ? colorScheme.onPrimary : colorScheme.onPrimaryContainer,
          onPressed: _toggleMenu,
          elevation: 6,
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            transitionBuilder: (child, anim) => RotationTransition(
              turns: child.key == const ValueKey('close')
                  ? Tween<double>(begin: -0.25, end: 0).animate(anim)
                  : Tween<double>(begin: 0.25, end: 0).animate(anim),
              child: ScaleTransition(scale: anim, child: child),
            ),
            child: _isOpen
                ? const Icon(Icons.close, key: ValueKey('close'))
                : const Icon(Icons.grid_view_rounded, key: ValueKey('open')),
          ),
        ),
      ],
    );
  }

  Widget _buildToolItem(BuildContext context, _ToolItem item) {
    final colorScheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.only(bottom: 10.0),
      child: Material(
        color: colorScheme.primaryContainer, // Light themed pill shape background
        borderRadius: BorderRadius.circular(28),
        elevation: 4,
        shadowColor: Colors.black26,
        child: InkWell(
          borderRadius: BorderRadius.circular(28),
          onTap: () {
            _toggleMenu();
            _handleAction(context, item.action);
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(item.icon, size: 20, color: colorScheme.onPrimaryContainer),
                const SizedBox(width: 12),
                Text(
                  item.label,
                  style: TextStyle(
                    color: colorScheme.onPrimaryContainer,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  List<_ToolItem> _toolActions() {
    return const [
      _ToolItem(label: 'Jade AI', icon: Icons.auto_awesome, action: _ToolAction.jadeAi),
      _ToolItem(label: 'AI Detector', icon: Icons.shield_rounded, action: _ToolAction.aiDetector),
      _ToolItem(label: 'Translator', icon: Icons.translate_rounded, action: _ToolAction.translator),
      _ToolItem(label: 'Summarizer', icon: Icons.summarize_rounded, action: _ToolAction.summarizer),
      _ToolItem(label: 'Tone Editor', icon: Icons.graphic_eq_rounded, action: _ToolAction.toneEditor),
    ];
  }
}

enum _ToolAction {
  jadeAi,
  aiDetector,
  translator,
  summarizer,
  toneEditor,
}

class _ToolItem {
  final String label;
  final IconData icon;
  final _ToolAction action;

  const _ToolItem({
    required this.label,
    required this.icon,
    required this.action,
  });
}