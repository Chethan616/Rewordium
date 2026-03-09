import 'package:flutter/material.dart';
import 'animated_card.dart';

class SmoothDismissibleCard extends StatefulWidget {
  final Widget child;
  final double? width;
  final double? height;
  final EdgeInsetsGeometry padding;
  final Duration animationDuration;
  final int animationDelay;
  final VoidCallback? onTap;
  final VoidCallback? onDismissed;
  final String? dismissibleKey;
  final bool enableDismiss;
  final DismissDirection dismissDirection;

  const SmoothDismissibleCard({
    super.key,
    required this.child,
    this.width,
    this.height,
    this.padding = const EdgeInsets.all(16),
    this.animationDuration = const Duration(milliseconds: 500),
    this.animationDelay = 0,
    this.onTap,
    this.onDismissed,
    this.dismissibleKey,
    this.enableDismiss = true,
    this.dismissDirection = DismissDirection.horizontal,
  });

  @override
  State<SmoothDismissibleCard> createState() => _SmoothDismissibleCardState();
}

class _SmoothDismissibleCardState extends State<SmoothDismissibleCard>
    with TickerProviderStateMixin {
  late AnimationController _dismissController;
  late Animation<double> _fadeOutAnimation;
  late Animation<Offset> _slideOutAnimation;
  late Animation<double> _scaleOutAnimation;
  bool _isVisible = true;

  @override
  void initState() {
    super.initState();
    _dismissController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );

    _fadeOutAnimation = Tween<double>(
      begin: 1.0,
      end: 0.0,
    ).animate(CurvedAnimation(
      parent: _dismissController,
      curve: Curves.easeInCubic,
    ));

    _slideOutAnimation = Tween<Offset>(
      begin: Offset.zero,
      end: const Offset(1.0, 0),
    ).animate(CurvedAnimation(
      parent: _dismissController,
      curve: Curves.easeInCubic,
    ));

    _scaleOutAnimation = Tween<double>(
      begin: 1.0,
      end: 0.8,
    ).animate(CurvedAnimation(
      parent: _dismissController,
      curve: Curves.easeInCubic,
    ));
  }

  @override
  void dispose() {
    _dismissController.dispose();
    super.dispose();
  }

  void _handleDismiss() {
    setState(() {
      _isVisible = false;
    });

    _dismissController.forward().then((_) {
      if (widget.onDismissed != null) {
        widget.onDismissed!();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    Widget cardWidget = AnimatedCard(
      width: widget.width,
      height: widget.height,
      padding: widget.padding,
      animationDuration: widget.animationDuration,
      animationDelay: widget.animationDelay,
      onTap: widget.onTap,
      isVisible: _isVisible,
      child: widget.child,
    );

    if (!widget.enableDismiss) {
      return cardWidget;
    }

    return AnimatedBuilder(
      animation: _dismissController,
      builder: (context, child) {
        return FadeTransition(
          opacity: _fadeOutAnimation,
          child: SlideTransition(
            position: _slideOutAnimation,
            child: ScaleTransition(
              scale: _scaleOutAnimation,
              child: Dismissible(
                key: Key(widget.dismissibleKey ?? UniqueKey().toString()),
                direction: widget.dismissDirection,
                onDismissed: (_) => _handleDismiss(),
                background: Container(
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  alignment: Alignment.centerRight,
                  padding: const EdgeInsets.only(right: 20),
                  child: const Icon(
                    Icons.delete_outline,
                    color: Colors.red,
                    size: 28,
                  ),
                ),
                secondaryBackground: Container(
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  alignment: Alignment.centerLeft,
                  padding: const EdgeInsets.only(left: 20),
                  child: const Icon(
                    Icons.delete_outline,
                    color: Colors.red,
                    size: 28,
                  ),
                ),
                child: cardWidget,
              ),
            ),
          ),
        );
      },
    );
  }
}
