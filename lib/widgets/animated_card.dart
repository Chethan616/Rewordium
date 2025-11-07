import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class AnimatedCard extends StatefulWidget {
  final Widget child;
  final double? width;
  final double? height;
  final EdgeInsetsGeometry padding;
  final Duration animationDuration;
  final int animationDelay;
  final VoidCallback? onTap;
  final bool isVisible;
  final VoidCallback? onExitComplete;

  const AnimatedCard({
    super.key,
    required this.child,
    this.width,
    this.height,
    this.padding = const EdgeInsets.all(16),
    this.animationDuration = const Duration(milliseconds: 500),
    this.animationDelay = 0,
    this.onTap,
    this.isVisible = true,
    this.onExitComplete,
  });

  @override
  State<AnimatedCard> createState() => _AnimatedCardState();
}

class _AnimatedCardState extends State<AnimatedCard>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _fadeAnimation;
  late Animation<Offset> _slideAnimation;
  late Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: widget.animationDuration,
      vsync: this,
    );

    _fadeAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOut,
    ));

    _slideAnimation = Tween<Offset>(
      begin: const Offset(0, 0.3),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: Curves.easeOutCubic,
    ));

    _scaleAnimation = Tween<double>(
      begin: 0.8,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: Curves.elasticOut,
    ));

    // Start animation with delay
    Future.delayed(Duration(milliseconds: widget.animationDelay), () {
      if (mounted && widget.isVisible) {
        _controller.forward();
      }
    });
  }

  @override
  void didUpdateWidget(AnimatedCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isVisible != oldWidget.isVisible) {
      if (widget.isVisible) {
        _controller.forward();
      } else {
        _controller.reverse().then((_) {
          if (widget.onExitComplete != null) {
            widget.onExitComplete!();
          }
        });
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cardContent = Container(
      width: widget.width,
      height: widget.height,
      margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      padding: widget.padding,
      decoration: AppTheme.cardDecoration,
      child: widget.child,
    );

    Widget finalWidget;
    if (widget.onTap != null) {
      finalWidget = InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(16),
        child: cardContent,
      );
    } else {
      finalWidget = cardContent;
    }

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return FadeTransition(
          opacity: _fadeAnimation,
          child: SlideTransition(
            position: _slideAnimation,
            child: ScaleTransition(
              scale: _scaleAnimation,
              child: finalWidget,
            ),
          ),
        );
      },
    );
  }
}
