import 'dart:ui';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:animate_do/animate_do.dart';

/// App bar with two affordances callers can mix freely:
///   * a leading **back button** — shown automatically whenever the route can
///     pop (so pushed tool screens always have a way out), unless explicitly
///     suppressed via `showBackButton: false`.
///   * a **leadingIcon** — a small decorative icon (sparkle, pencil, etc.).
///     If a back button is also showing, the icon is demoted to a tinted chip
///     immediately to the left of the title instead of fighting for the
///     leading slot. This keeps the back chevron at the standard iOS position
///     while still letting the screen brand itself.
class CustomAppBar extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final List<Widget>? actions;

  /// `true` → always show back button.
  /// `false` → never show back button.
  /// `null` → show iff `Navigator.canPop()` (the sensible default).
  final bool? showBackButton;
  final Widget? leadingIcon;
  final VoidCallback? onLeadingTap;

  const CustomAppBar({
    super.key,
    required this.title,
    this.actions,
    this.showBackButton,
    this.leadingIcon,
    this.onLeadingTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    final shouldShowBack = showBackButton ?? false;

    Widget? leading;
    if (shouldShowBack) {
      leading = _BackChevron(
        onPressed: onLeadingTap ?? () => Navigator.of(context).maybePop(),
      );
    } else if (leadingIcon != null) {
      leading = leadingIcon;
    }

    // When the back chevron occupies the leading slot, surface the
    // decorative icon as a soft-tinted chip next to the title so the screen
    // still reads as "Grammar" / "Paraphraser" at a glance.
    Widget titleWidget = Text(title, style: textTheme.titleLarge);
    if (shouldShowBack && leadingIcon != null) {
      titleWidget = Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(10),
            ),
            child: IconTheme(
              data: IconTheme.of(context).copyWith(size: 16),
              child: leadingIcon!,
            ),
          ),
          const SizedBox(width: 10),
          Flexible(child: titleWidget),
        ],
      );
    }

    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: kToolbarHeight + MediaQuery.of(context).padding.top,
      ),
      child: AppBar(
        backgroundColor: colorScheme.surface.withValues(alpha: 0.42),
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        flexibleSpace: ClipRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: colorScheme.surface.withValues(alpha: 0.20),
                border: Border(
                  bottom: BorderSide(
                    color: colorScheme.outlineVariant.withValues(alpha: 0.22),
                    width: 0.6,
                  ),
                ),
              ),
              child: const SizedBox.expand(),
            ),
          ),
        ),
        leading: leading,
        automaticallyImplyLeading: false,
        title: FadeIn(
          duration: const Duration(milliseconds: 300),
          child: titleWidget,
        ),
        actions: actions,
      ),
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}

/// iOS-style back affordance: chevron + tappable circle with a subtle
/// pressed-state. Sits in the standard leading slot so it doesn't crowd the
/// title or shove actions off-screen.
class _BackChevron extends StatefulWidget {
  final VoidCallback onPressed;
  const _BackChevron({required this.onPressed});

  @override
  State<_BackChevron> createState() => _BackChevronState();
}

class _BackChevronState extends State<_BackChevron> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(left: 8),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (_) => setState(() => _pressed = true),
        onTapCancel: () => setState(() => _pressed = false),
        onTapUp: (_) => setState(() => _pressed = false),
        onTap: widget.onPressed,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          curve: Curves.easeOut,
          width: 36,
          height: 36,
          child: ClipOval(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 120),
                curve: Curves.easeOut,
                decoration: BoxDecoration(
                  color: _pressed
                      ? cs.onSurface.withValues(alpha: 0.11)
                      : Colors.white.withValues(alpha: 0.38),
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.48),
                    width: 0.7,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.06),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Icon(
                  CupertinoIcons.chevron_back,
                  size: 18,
                  color: cs.onSurface,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
