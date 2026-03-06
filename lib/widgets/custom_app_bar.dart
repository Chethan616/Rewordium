import 'package:flutter/material.dart';
import 'package:animate_do/animate_do.dart';

class CustomAppBar extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final List<Widget>? actions;
  final bool showBackButton;
  final Widget? leadingIcon;
  final VoidCallback? onLeadingTap;

  const CustomAppBar({
    super.key,
    required this.title,
    this.actions,
    this.showBackButton = false,
    this.leadingIcon,
    this.onLeadingTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    Widget? leading;
    if (showBackButton) {
      leading = IconButton(
        icon: const Icon(Icons.arrow_back_ios_new),
        onPressed: onLeadingTap ?? () => Navigator.of(context).pop(),
      );
    } else if (leadingIcon != null) {
      leading = leadingIcon;
    }

    return AppBar(
      backgroundColor: colorScheme.surface,
      elevation: 0,
      scrolledUnderElevation: 0,
      leading: leading,
      automaticallyImplyLeading: false,
      title: FadeIn(
        duration: const Duration(milliseconds: 300),
        child: Text(
          title,
          style: textTheme.titleLarge,
        ),
      ),
      actions: actions,
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
