import 'package:flutter/material.dart';
import 'package:animate_do/animate_do.dart';
import '../../theme/app_theme.dart';

class ToolCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final Widget icon;
  final Color color;
  final bool isSmallScreen;

  const ToolCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.color,
    required this.isSmallScreen,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: isSmallScreen ? 140 : 160,
      margin: EdgeInsets.all(isSmallScreen ? 6 : 8),
      padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [color.withOpacity(0.8), color.withOpacity(0.6)],
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: color.withOpacity(0.3),
            blurRadius: 8,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        mainAxisSize: MainAxisSize.min,
        children: [
          Flexible(child: icon),
          const SizedBox(height: 4),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                title,
                style: AppTheme.headingSmall.copyWith(
                  color: Colors.white,
                  fontSize: isSmallScreen ? 14 : 16,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              Text(
                subtitle,
                style: AppTheme.bodySmall.copyWith(
                  color: Colors.white.withOpacity(0.8),
                  fontSize: isSmallScreen ? 9 : 10,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
