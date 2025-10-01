import 'package:flutter/material.dart';
import '../utils/lottie_assets.dart';

class AnimatedJadeAvatar extends StatefulWidget {
  final double size;
  final bool enableRotation;
  final Duration rotationInterval;
  final bool showBorder;
  final List<Color>? borderGradientColors;

  const AnimatedJadeAvatar({
    super.key,
    this.size = 56.0,
    this.enableRotation = true,
    this.rotationInterval = const Duration(seconds: 8),
    this.showBorder = true,
    this.borderGradientColors,
  });

  @override
  State<AnimatedJadeAvatar> createState() => _AnimatedJadeAvatarState();
}

class _AnimatedJadeAvatarState extends State<AnimatedJadeAvatar>
    with TickerProviderStateMixin {
  late AnimationController _rotationController;
  late AnimationController _lottieController;
  late Animation<double> _rotationAnimation;

  @override
  void initState() {
    super.initState();

    // Rotation controller for periodic tilting
    _rotationController = AnimationController(
      duration: widget.rotationInterval,
      vsync: this,
    );

    // Lottie animation controller
    _lottieController = AnimationController(
      duration:
          const Duration(seconds: 4), // Match the Lottie animation duration
      vsync: this,
    );

    // Create rotation animation (tilt effect)
    _rotationAnimation = Tween<double>(
      begin: 0.0,
      end: 0.2, // Slight tilt (about 11.5 degrees)
    ).animate(CurvedAnimation(
      parent: _rotationController,
      curve: Curves.easeInOut,
    ));

    // Start animations
    if (widget.enableRotation) {
      _startRotationCycle();
    }
    _lottieController.repeat(); // Loop the Lottie animation
  }

  void _startRotationCycle() {
    // Create a periodic rotation cycle
    _rotationController.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        // Wait a bit, then reverse
        Future.delayed(const Duration(milliseconds: 500), () {
          if (mounted) {
            _rotationController.reverse();
          }
        });
      } else if (status == AnimationStatus.dismissed) {
        // Wait a bit, then start the next cycle
        Future.delayed(widget.rotationInterval, () {
          if (mounted) {
            _rotationController.forward();
          }
        });
      }
    });

    // Start the first cycle
    Future.delayed(const Duration(seconds: 2), () {
      if (mounted) {
        _rotationController.forward();
      }
    });
  }

  @override
  void dispose() {
    _rotationController.dispose();
    _lottieController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final borderColors = widget.borderGradientColors ??
        [
          const Color(0xFF6B73FF).withOpacity(0.9),
          const Color(0xFF9B59B6).withOpacity(0.9),
          const Color(0xFFE91E63).withOpacity(0.9),
          const Color(0xFFFF6B35).withOpacity(0.9),
          const Color(0xFF4ECDC4).withOpacity(0.9),
        ];

    return AnimatedBuilder(
      animation: _rotationAnimation,
      builder: (context, child) {
        return Transform.rotate(
          angle: _rotationAnimation.value,
          child: Container(
            width: widget.size,
            height: widget.size,
            decoration: widget.showBorder
                ? BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: borderColors,
                      stops: const [0.0, 0.25, 0.5, 0.75, 1.0],
                    ),
                    borderRadius: BorderRadius.circular(widget.size / 2),
                    boxShadow: [
                      BoxShadow(
                        color: borderColors.first.withOpacity(0.4),
                        blurRadius: 20,
                        offset: const Offset(0, 8),
                      ),
                      BoxShadow(
                        color: Colors.purple.withOpacity(0.2),
                        blurRadius: 40,
                        offset: const Offset(0, 16),
                      ),
                    ],
                  )
                : null,
            child: Container(
              margin: widget.showBorder
                  ? const EdgeInsets.all(2.5)
                  : EdgeInsets.zero,
              decoration: BoxDecoration(
                color: widget.showBorder ? Colors.white : Colors.transparent,
                borderRadius: BorderRadius.circular(widget.showBorder
                    ? (widget.size - 5) / 2
                    : widget.size / 2),
                boxShadow: widget.showBorder
                    ? [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.1),
                          blurRadius: 8,
                          offset: const Offset(0, 2),
                        ),
                      ]
                    : null,
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(widget.showBorder
                    ? (widget.size - 5) / 2
                    : widget.size / 2),
                child: LottieAssets.getJadeAiAnimation(
                  width: widget.showBorder ? widget.size - 5 : widget.size,
                  height: widget.showBorder ? widget.size - 5 : widget.size,
                  controller: _lottieController,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
