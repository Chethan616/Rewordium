import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/services.dart';

import '../screens/ai_detector_page.dart';
import '../screens/jade_chat_screen.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/translator_page.dart';

/// Tools FAB — the "+" button that fans out the writing tools.
///
/// Motion brief (matches Google Drive's FAB feel):
///   * Spring-driven open/close (overshoots slightly then settles). Plain
///     `Curves.easeOutBack` snaps; a real spring oscillates and reads as
///     mechanical the way GDrive's does.
///   * Items reveal in stagger — bottom item first (nearest to FAB), each
///     subsequent one delayed by ~40ms. Closing reverses the stagger.
///   * Icon morphs with a 90° quarter-turn while also pulsing scale,
///     so the FAB feels alive on tap, not just swapped.
///   * Haptic tick on toggle + on each item tap.
///   * A soft scrim fades in behind the menu so taps outside dismiss; the
///     scrim is intentionally NOT pitch-black — it's just enough to focus
///     attention on the menu without blanking the underlying screen.
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
  bool _isOpen = false;

  // Springs tuned for GDrive-style bounce. Two of them: a softer one for
  // opening (lets items "land" with a tiny overshoot), a tighter one for
  // closing (no oscillation, just clean retreat — open is fun, close is
  // efficient).
  static const _openSpring = SpringDescription(
    mass: 1.0,
    stiffness: 480,
    damping: 22, // damping ratio ~0.50 → noticeable overshoot
  );
  static const _closeSpring = SpringDescription(
    mass: 1.0,
    stiffness: 600,
    damping: 38, // damping ratio ~0.78 → critical-ish, no bounce
  );

  @override
  void initState() {
    super.initState();
    _controller = AnimationController.unbounded(vsync: this, value: 0.0);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggleMenu() {
    HapticFeedback.selectionClick();
    setState(() => _isOpen = !_isOpen);
    final target = _isOpen ? 1.0 : 0.0;
    _controller.animateWith(
      SpringSimulation(
        _isOpen ? _openSpring : _closeSpring,
        _controller.value,
        target,
        // Velocity kick — gives the spring something to overshoot off of so
        // the bounce is felt even on a static start.
        _isOpen ? 6.0 : -4.0,
      ),
    );
  }

  Future<void> _handleAction(BuildContext context, _ToolAction action) async {
    final navigator = Navigator.of(context, rootNavigator: true);
    HapticFeedback.lightImpact();
    _toggleMenu();
    // Wait a frame so the menu has started closing before pushing — the page
    // transition feels less stacked when the FAB has visibly retreated first.
    await Future.delayed(const Duration(milliseconds: 80));
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
    final cs = Theme.of(context).colorScheme;
    final items = _toolActions();

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        final t = _controller.value.clamp(0.0, 1.2); // allow overshoot
        return Stack(
          clipBehavior: Clip.none,
          alignment: Alignment.bottomRight,
          children: [
            // Scrim was removed because it was rendering as a localized black box 
            // behind the FAB column instead of a full-screen overlay.
            Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                // Cascading items (bottom-most reveals first).
                IgnorePointer(
                  ignoring: !_isOpen,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: List.generate(items.length, (i) {
                      // Stagger: bottom item (i = items.length-1) starts at
                      // t=0; top item starts later. Each item gets ~ 0.18 of
                      // the timeline; the rest overlaps.
                      final reverseIndex = items.length - 1 - i;
                      final stagger = reverseIndex * 0.08;
                      final local = ((t - stagger) / (1.0 - stagger))
                          .clamp(0.0, 1.0);
                      // Easing within each item's slice: a small overshoot
                      // by reading the spring value past 1.0.
                      final scale = _controller.value > 1.0
                          ? (1.0 + 0.04 * (_controller.value - 1.0))
                          : local;
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 10.0),
                        child: Opacity(
                          opacity: local,
                          child: Transform.translate(
                            offset: Offset(0, 18 * (1 - local)),
                            child: Transform.scale(
                              scale: scale.clamp(0.0, 1.1),
                              alignment: Alignment.bottomRight,
                              child: _ToolPill(
                                item: items[i],
                                onTap: () => _handleAction(context, items[i].action),
                              ),
                            ),
                          ),
                        ),
                      );
                    }),
                  ),
                ),
                const SizedBox(height: 4),
                // Main FAB
                Transform.scale(
                  scale: 1.0 + 0.06 * (1.0 - (1.0 - t.clamp(0.0, 1.0)).abs()),
                  child: FloatingActionButton(
                    tooltip: widget.tooltip,
                    backgroundColor: Color.lerp(
                      cs.primaryContainer,
                      cs.primary,
                      t.clamp(0.0, 1.0),
                    ),
                    foregroundColor: Color.lerp(
                      cs.onPrimaryContainer,
                      cs.onPrimary,
                      t.clamp(0.0, 1.0),
                    ),
                    onPressed: _toggleMenu,
                    elevation: 6 + 2 * t.clamp(0.0, 1.0),
                    child: Transform.rotate(
                      angle: t.clamp(0.0, 1.0) * 0.5 * 3.14159, // 90°
                      child: const Icon(CupertinoIcons.add, size: 26),
                    ),
                  ),
                ),
              ],
            ),
          ],
        );
      },
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

class _ToolPill extends StatefulWidget {
  final _ToolItem item;
  final VoidCallback onTap;
  const _ToolPill({required this.item, required this.onTap});

  @override
  State<_ToolPill> createState() => _ToolPillState();
}

class _ToolPillState extends State<_ToolPill> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      onTap: widget.onTap,
      child: AnimatedScale(
        scale: _pressed ? 0.96 : 1.0,
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOut,
        child: Material(
          color: cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(28),
          elevation: 3,
          shadowColor: Colors.black.withValues(alpha: 0.25),
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
                  child: Icon(widget.item.icon, size: 15, color: cs.primary),
                ),
                const SizedBox(width: 10),
                Text(
                  widget.item.label,
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
