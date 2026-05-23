import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

/// A quieter alternative to Material's `SnackBar` for status messages.
///
/// SnackBars push layout, animate in from the bottom on top of the IME, and
/// stack — fine for forms, jarring for the small notices Rewordium uses
/// (sign-in result, settings synced, save success). This toast:
///   * floats via Overlay so it never reflows the page beneath it,
///   * uses theme-tinted surfaces instead of saturated red/green,
///   * pins to the top safe area (out of the way of the keyboard + bottom CTAs),
///   * auto-dismisses, with a tap-to-dismiss affordance,
///   * collapses successive calls into one — the latest message wins.
class RewordiumToast {
  RewordiumToast._();

  static OverlayEntry? _entry;
  static Timer? _timer;

  /// Show [message] at the top of the nearest Overlay. Variant controls the
  /// leading icon + tint. The previous toast (if any) is dismissed first so
  /// rapid-fire calls don't stack.
  static void show(
    BuildContext context,
    String message, {
    RewordiumToastVariant variant = RewordiumToastVariant.info,
    Duration duration = const Duration(seconds: 3),
  }) {
    final overlay = Overlay.maybeOf(context, rootOverlay: true);
    if (overlay == null) return;

    _entry?.remove();
    _timer?.cancel();

    final entry = OverlayEntry(
      builder: (_) => _ToastView(
        message: message,
        variant: variant,
        onDismiss: _dismiss,
      ),
    );
    _entry = entry;
    overlay.insert(entry);

    _timer = Timer(duration, _dismiss);
  }

  static void _dismiss() {
    _timer?.cancel();
    _timer = null;
    _entry?.remove();
    _entry = null;
  }
}

enum RewordiumToastVariant { info, success, error }

extension RewordiumToastContext on BuildContext {
  /// Shorthand: `context.showToast('Saved')` / `.showToast('...', variant: .error)`.
  void showToast(
    String message, {
    RewordiumToastVariant variant = RewordiumToastVariant.info,
    Duration duration = const Duration(seconds: 3),
  }) =>
      RewordiumToast.show(this, message, variant: variant, duration: duration);
}

class _ToastView extends StatefulWidget {
  final String message;
  final RewordiumToastVariant variant;
  final VoidCallback onDismiss;

  const _ToastView({
    required this.message,
    required this.variant,
    required this.onDismiss,
  });

  @override
  State<_ToastView> createState() => _ToastViewState();
}

class _ToastViewState extends State<_ToastView>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ac;
  late final Animation<double> _opacity;
  late final Animation<Offset> _offset;

  @override
  void initState() {
    super.initState();
    _ac = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 260),
      reverseDuration: const Duration(milliseconds: 180),
    );
    _opacity =
        CurvedAnimation(parent: _ac, curve: Curves.easeOut, reverseCurve: Curves.easeIn);
    _offset = Tween<Offset>(
      begin: const Offset(0, -0.4),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _ac,
      curve: Curves.easeOutCubic,
    ));
    _ac.forward();
  }

  @override
  void dispose() {
    _ac.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final (icon, tint) = switch (widget.variant) {
      RewordiumToastVariant.success => (CupertinoIcons.checkmark_circle_fill, cs.primary),
      RewordiumToastVariant.error => (CupertinoIcons.exclamationmark_circle_fill, cs.error),
      RewordiumToastVariant.info => (CupertinoIcons.info_circle_fill, cs.onSurfaceVariant),
    };

    return Positioned(
      top: MediaQuery.of(context).padding.top + 8,
      left: 12,
      right: 12,
      child: SafeArea(
        bottom: false,
        child: FadeTransition(
          opacity: _opacity,
          child: SlideTransition(
            position: _offset,
            child: Material(
              color: Colors.transparent,
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () async {
                  await _ac.reverse();
                  widget.onDismiss();
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(
                    color: isDark
                        ? cs.surfaceContainerHigh
                        : cs.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: cs.outlineVariant.withValues(alpha: 0.6),
                      width: 0.5,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.08),
                        blurRadius: 18,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: Row(
                    children: [
                      Icon(icon, size: 18, color: tint),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          widget.message,
                          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: cs.onSurface,
                            height: 1.25,
                          ),
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
