import 'package:flutter/material.dart';
import 'package:animate_do/animate_do.dart';
import '../theme/app_theme.dart';

class AnimatedCard extends StatelessWidget {
  final Widget child;
  final double? width;
  final double? height;
  final EdgeInsetsGeometry padding;
  final Duration animationDuration;
  final int animationDelay;

  const AnimatedCard({
    super.key,
    required this.child,
    this.width,
    this.height,
    this.padding = const EdgeInsets.all(16),
    this.animationDuration = const Duration(milliseconds: 500),
    this.animationDelay = 0,
  });

  @override
  Widget build(BuildContext context) {
    return FadeInUp(
      duration: animationDuration,
      delay: Duration(milliseconds: animationDelay),
      child: Container(
        width: width,
        height: height,
        margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
        padding: padding,
        decoration: AppTheme.cardDecoration,
        child: child,
      ),
    );
  }
}
