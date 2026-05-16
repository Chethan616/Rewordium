import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/physics.dart';

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
  bool _open = false;

  // Bouncier open, snappier close
  static const _openSpring  = SpringDescription(mass: 1, stiffness: 180, damping: 14);
  static const _closeSpring = SpringDescription(mass: 1, stiffness: 380, damping: 26);

  static const double _itemSpacing = 56.0;
  static const double _baseOffset  = 68.0;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 600));
    _rotateAnim = Tween<double>(begin: 0, end: math.pi / 4).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOutCubic),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggle() {
    if (_open) {
      _controller.animateWith(SpringSimulation(_closeSpring, _controller.value, 0, -4));
    } else {
      _controller.animateWith(SpringSimulation(_openSpring, _controller.value, 1, 4));
    }
    setState(() => _open = !_open);
  }

  void _close() { if (_open) _toggle(); }

  void _closeAndRun(VoidCallback action) {
    if (_open) {
      setState(() => _open = false);
      _controller.animateWith(SpringSimulation(_closeSpring, _controller.value, 0, -4));
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) action();
    });
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final n = widget.actions.length;

    return Stack(
      alignment: Alignment.bottomRight,
      clipBehavior: Clip.none,
      children: [
        if (_open)
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: _close,
            ),
          ),

        ...List.generate(n, (i) {
          final slot  = n - 1 - i;
          // Longer stagger window → items pop one by one, not all at once
          final start = (slot * 0.13).clamp(0.0, 0.65);
          final anim  = CurvedAnimation(
            parent: _controller,
            curve: Interval(start, 1.0, curve: Curves.elasticOut),
          );
          return Positioned(
            bottom: _baseOffset + (n - i) * _itemSpacing,
            right: 0,
            child: AnimatedBuilder(
              animation: anim,
              builder: (context, child) {
                final t = anim.value.clamp(0.0, 1.0);
                return IgnorePointer(
                  ignoring: t < 0.9,
                  child: Opacity(
                    opacity: t.clamp(0.0, 1.0),
                    child: Transform.translate(
                      offset: Offset(0, 28 * (1 - t)),
                      child: Transform.scale(scale: 0.72 + 0.28 * t, child: child),
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

        // Main FAB — no custom scale animation, just the rotate
        FloatingActionButton(
          heroTag: 'physics_fab_main',
          tooltip: _open ? 'Close' : widget.tooltip,
          backgroundColor: cs.primaryContainer,
          foregroundColor: cs.onPrimaryContainer,
          elevation: 3,         // toned down from 6
          highlightElevation: 4,
          onPressed: _toggle,
          shape: const CircleBorder(),
          child: AnimatedBuilder(
            animation: _rotateAnim,
            builder: (_, __) => Transform.rotate(
              angle: _rotateAnim.value,
              child: _open
                  ? const Icon(CupertinoIcons.xmark, size: 20)
                  : widget.closedIcon,
            ),
          ),
        ),
      ],
    );
  }
}

class _ActionButton extends StatefulWidget {
  final FabAction action;
  final VoidCallback onTap;
  const _ActionButton({required this.action, required this.onTap});

  @override
  State<_ActionButton> createState() => _ActionButtonState();
}

class _ActionButtonState extends State<_ActionButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _press;

  @override
  void initState() {
    super.initState();
    _press = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 80),
      lowerBound: 0.92,
      upperBound: 1.0,
      value: 1.0,
    );
  }

  @override
  void dispose() { _press.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;
    final color   = widget.action.color   ?? cs.secondaryContainer;
    final onColor = widget.action.onColor ?? cs.onSecondaryContainer;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown:  (_) => _press.reverse(),
      onTap:       ()  { _press.forward(); widget.onTap(); },
      onTapCancel: ()  => _press.forward(),
      child: ScaleTransition(
        scale: _press,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Label chip — clean, no shadow
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: cs.surface,
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: cs.outlineVariant, width: 0.75),
                ),
                child: Text(
                  widget.action.label,
                  style: tt.labelLarge?.copyWith(
                    color: cs.onSurface,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              // Mini FAB — flat Material, no extra container/border/shadow
              Material(
                color: color,
                shape: const CircleBorder(),
                elevation: 2,
                child: SizedBox(
                  width: 46,
                  height: 46,
                  child: Icon(widget.action.icon, color: onColor, size: 22),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

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