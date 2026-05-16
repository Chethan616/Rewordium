import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/physics.dart';

/// A Google Drive–style expanding FAB with spring physics.
/// Tap to toggle a column of labelled action mini-FABs, each spring-animated
/// with staggered delays just like Google Drive's new file FAB.
class PhysicsFab extends StatefulWidget {
  final List<FabAction> actions;
  final Widget closedIcon;
  final String tooltip;

  const PhysicsFab({
    super.key,
    required this.actions,
    this.closedIcon = const Icon(CupertinoIcons.square_grid_2x2),
    this.tooltip = 'Tools',
  });

  @override
  State<PhysicsFab> createState() => _PhysicsFabState();
}

class _PhysicsFabState extends State<PhysicsFab>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _rotateAnim;
  late final Animation<double> _scaleAnim;
  bool _open = false;

  static const _openSpring = SpringDescription(mass: 1, stiffness: 380, damping: 26);
  static const _closeSpring = SpringDescription(mass: 1, stiffness: 500, damping: 32);

  // Spacing tuning — reduced to close the gap between FAB and items
  static const double _itemSpacing = 52.0;  // was 64
  static const double _baseOffset = 64.0;   // was 72

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    );

    _rotateAnim = Tween<double>(begin: 0, end: math.pi / 4).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOutCubic),
    );

    _scaleAnim = TweenSequence<double>([
      TweenSequenceItem(tween: Tween(begin: 1.0, end: 0.86), weight: 15),
      TweenSequenceItem(tween: Tween(begin: 0.86, end: 1.08), weight: 45),
      TweenSequenceItem(tween: Tween(begin: 1.08, end: 1.0), weight: 40),
    ]).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOut));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggle() {
    if (_open) {
      final sim = SpringSimulation(_closeSpring, _controller.value, 0, -6);
      _controller.animateWith(sim);
    } else {
      final sim = SpringSimulation(_openSpring, _controller.value, 1, 6);
      _controller.animateWith(sim);
    }
    setState(() => _open = !_open);
  }

  void _close() {
    if (!_open) return;
    _toggle();
  }

  void _closeAndRun(VoidCallback action) {
    _toggle();
    // Run the action after the animation has mostly completed,
    // using addPostFrameCallback to ensure the widget tree is stable.
    Future.delayed(const Duration(milliseconds: 180), () {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        action();
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final n = widget.actions.length;

    return Stack(
      alignment: Alignment.bottomRight,
      clipBehavior: Clip.none,
      children: [
        // Invisible barrier: when FAB is open, tapping anywhere else closes it.
        if (_open)
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: _close,
              child: const SizedBox.expand(),
            ),
          ),

        // Action buttons stacked above FAB
        ...List.generate(n, (i) {
          // Stagger: top-most item animates last (index 0 = topmost = highest slot)
          final slot = n - 1 - i; // slot 0 is closest to FAB
          // Increased stagger delay for visible one-by-one bounce effect
          final start = (slot * 0.10).clamp(0.0, 0.60);
          final delayedAnim = CurvedAnimation(
            parent: _controller,
            // elasticOut gives the bouncy springy feel
            curve: Interval(start, 1.0, curve: Curves.elasticOut),
          );

          return Positioned(
            bottom: _baseOffset + (n - i) * _itemSpacing,
            right: 0,
            child: AnimatedBuilder(
              animation: delayedAnim,
              builder: (context, child) {
                final t = delayedAnim.value.clamp(0.0, 1.0);
                return IgnorePointer(
                  ignoring: t < 0.1,
                  child: Opacity(
                    opacity: t.clamp(0.0, 1.0),
                    child: Transform.translate(
                      offset: Offset(0, 30 * (1 - t)),
                      child: Transform.scale(
                        scale: 0.5 + 0.5 * t,
                        child: child,
                      ),
                    ),
                  ),
                );
              },
              child: _ActionButton(
                action: widget.actions[i],
                onTap: () => _closeAndRun(widget.actions[i].onTap),
              ),
            ),
          );
        }),

        // Main FAB
        AnimatedBuilder(
          animation: _scaleAnim,
          builder: (context, child) => Transform.scale(
            scale: _scaleAnim.value.clamp(0.8, 1.2),
            child: child,
          ),
          child: FloatingActionButton(
            heroTag: 'physics_fab_main',
            tooltip: _open ? 'Close' : widget.tooltip,
            backgroundColor: colorScheme.primaryContainer,
            foregroundColor: colorScheme.onPrimaryContainer,
            elevation: 6,
            onPressed: _toggle,
            shape: const CircleBorder(),
            child: AnimatedBuilder(
              animation: _rotateAnim,
              builder: (_, __) => Transform.rotate(
                angle: _rotateAnim.value,
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 200),
                  switchInCurve: Curves.easeOutBack,
                  switchOutCurve: Curves.easeIn,
                  transitionBuilder: (child, anim) => ScaleTransition(
                    scale: anim,
                    child: child,
                  ),
                  child: _open
                      ? Icon(CupertinoIcons.xmark, key: const ValueKey('close'), size: 22)
                      : KeyedSubtree(
                          key: const ValueKey('open'),
                          child: widget.closedIcon,
                        ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Individual action row: label chip + mini FAB, with press-scale feedback.
/// Now includes outlines/borders on both the label chip and mini-FAB for
/// visibility against any background.
class _ActionButton extends StatefulWidget {
  final FabAction action;
  final VoidCallback onTap;

  const _ActionButton({required this.action, required this.onTap});

  @override
  State<_ActionButton> createState() => _ActionButtonState();
}

class _ActionButtonState extends State<_ActionButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pressCtrl;
  late final Animation<double> _pressAnim;

  @override
  void initState() {
    super.initState();
    _pressCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 90),
    );
    _pressAnim = Tween<double>(begin: 1.0, end: 0.90).animate(
      CurvedAnimation(parent: _pressCtrl, curve: Curves.easeIn),
    );
  }

  @override
  void dispose() {
    _pressCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final color = widget.action.color ?? colorScheme.secondaryContainer;
    final onColor = widget.action.onColor ?? colorScheme.onSecondaryContainer;

    return GestureDetector(
      onTapDown: (_) => _pressCtrl.forward(),
      onTapUp: (_) {
        _pressCtrl.reverse();
        widget.onTap();
      },
      onTapCancel: () => _pressCtrl.reverse(),
      child: AnimatedBuilder(
        animation: _pressAnim,
        builder: (_, child) =>
            Transform.scale(scale: _pressAnim.value, child: child),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 3),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Label chip — with outline border for visibility
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: colorScheme.surface,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(
                    color: colorScheme.outlineVariant.withValues(alpha: 0.5),
                    width: 1.0,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.16),
                      blurRadius: 12,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Text(
                  widget.action.label,
                  style: textTheme.labelLarge?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: colorScheme.onSurface,
                    letterSpacing: -0.1,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              // Mini FAB circle — with outline ring for visibility
              Container(
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: colorScheme.outline.withValues(alpha: 0.25),
                    width: 1.5,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: color.withValues(alpha: 0.35),
                      blurRadius: 10,
                      spreadRadius: 1,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Material(
                  color: color,
                  shape: const CircleBorder(),
                  elevation: 4,
                  child: SizedBox(
                    width: 48,
                    height: 48,
                    child: Center(
                      child: Icon(widget.action.icon, color: onColor, size: 22),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Data class describing one action in the expanding FAB menu.
class FabAction {
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final Color? color;
  final Color? onColor;

  const FabAction({
    required this.label,
    required this.icon,
    required this.onTap,
    this.color,
    this.onColor,
  });
}
