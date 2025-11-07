import 'package:flutter/material.dart';
import 'package:animate_do/animate_do.dart';
import '../theme/app_theme.dart';

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
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: AppTheme.cardColor,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.03),
            offset: const Offset(0, 2),
            blurRadius: 5,
          ),
        ],
      ),
      child: SafeArea(
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                if (showBackButton)
                  IconButton(
                    icon: const Icon(Icons.arrow_back_ios_new),
                    onPressed:
                        onLeadingTap ?? () => Navigator.of(context).pop(),
                  )
                else if (leadingIcon != null)
                  leadingIcon!,
                const SizedBox(width: 8),
                FadeIn(
                  duration: const Duration(milliseconds: 300),
                  child: Text(
                    title,
                    style: AppTheme.headingSmall,
                  ),
                ),
              ],
            ),
            Row(
              children: actions ?? [],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
