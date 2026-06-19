import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../screens/ai_detector_page.dart';
import '../screens/jade_chat_screen.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/translator_page.dart';

/// Tools FAB — the "+" button that fans out the writing tools.
///
/// Plain Material 3 behaviour: tapping the FAB toggles a column of menu
/// items above it. The reveal is a standard curve-driven fade + slide (no
/// spring physics, no overshoot). The "+" icon rotates 45° into a close
/// affordance. A light scrim behind the open menu catches outside taps.
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

class _ToolsFabState extends State<ToolsFab>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _anim;
  bool _isOpen = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 220),
      reverseDuration: const Duration(milliseconds: 180),
    );
    _anim = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeOutCubic,
      reverseCurve: Curves.easeInCubic,
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggleMenu() {
    HapticFeedback.selectionClick();
    setState(() => _isOpen = !_isOpen);
    if (_isOpen) {
      _controller.forward();
    } else {
      _controller.reverse();
    }
  }

  Future<void> _handleAction(BuildContext context, _ToolAction action) async {
    final navigator = Navigator.of(context, rootNavigator: true);
    HapticFeedback.lightImpact();
    _toggleMenu();
    if (!context.mounted) return;
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
    final items = _toolActions();

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        // Menu items — fade + slide up together, driven by a plain curve.
        AnimatedBuilder(
          animation: _anim,
          builder: (context, child) {
            final t = _anim.value;
            if (t == 0) return const SizedBox.shrink();
            return IgnorePointer(
              ignoring: !_isOpen,
              child: Opacity(
                opacity: t,
                child: Transform.translate(
                  offset: Offset(0, 12 * (1 - t)),
                  child: child,
                ),
              ),
            );
          },
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              for (final item in items)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12.0),
                  child: _ToolPill(
                    item: item,
                    onTap: () => _handleAction(context, item.action),
                  ),
                ),
            ],
          ),
        ),
        // Main FAB — standard Material 3, only the icon rotates.
        FloatingActionButton(
          tooltip: widget.tooltip,
          onPressed: _toggleMenu,
          child: AnimatedBuilder(
            animation: _anim,
            builder: (context, child) => Transform.rotate(
              angle: _anim.value * 0.785398, // 45°
              child: child,
            ),
            child: const Icon(CupertinoIcons.add, size: 26),
          ),
        ),
      ],
    );
  }

  List<_ToolItem> _toolActions() {
    return const [
      _ToolItem(label: 'Jade AI', icon: CupertinoIcons.sparkles, action: _ToolAction.jadeAi),
      _ToolItem(label: 'AI Detector', icon: CupertinoIcons.shield_lefthalf_fill, action: _ToolAction.aiDetector),
      _ToolItem(label: 'Translator', icon: CupertinoIcons.globe, action: _ToolAction.translator),
      _ToolItem(label: 'Summarizer', icon: CupertinoIcons.doc_text, action: _ToolAction.summarizer),
      _ToolItem(label: 'Tone Editor', icon: CupertinoIcons.waveform, action: _ToolAction.toneEditor),
    ];
  }
}

class _ToolPill extends StatelessWidget {
  final _ToolItem item;
  final VoidCallback onTap;
  const _ToolPill({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: cs.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(16),
      elevation: 3,
      shadowColor: Colors.black.withValues(alpha: 0.2),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: cs.primary.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(item.icon, size: 15, color: cs.primary),
              ),
              const SizedBox(width: 10),
              Text(
                item.label,
                style: TextStyle(
                  color: cs.onSurface,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.1,
                ),
              ),
            ],
          ),
        ),
      ),
    );
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
