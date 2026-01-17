import 'package:flutter/material.dart';
import 'dart:math';

/// Easter egg "Broken Code Mode" screen
/// Displays a chaotic falling UI effect with bouncing widgets
/// Safe, reversible, and Play Store compliant
class BrokenScreen extends StatefulWidget {
  const BrokenScreen({super.key});

  @override
  State<BrokenScreen> createState() => _BrokenScreenState();
}

class _BrokenScreenState extends State<BrokenScreen>
    with TickerProviderStateMixin {
  late List<_FallingWidget> _fallingWidgets;
  final Random _random = Random();

  @override
  void initState() {
    super.initState();
    _initFallingWidgets();
  }

  void _initFallingWidgets() {
    _fallingWidgets = [
      _FallingWidget(
        widget: _buildFakeCard('Settings', Icons.settings),
        startX: 0.1,
        delay: 0.0,
        fallDuration: 2.5,
        rotation: 0.3,
        bounciness: 0.4,
      ),
      _FallingWidget(
        widget: _buildFakeCard('Profile', Icons.person),
        startX: 0.6,
        delay: 0.2,
        fallDuration: 2.0,
        rotation: -0.2,
        bounciness: 0.5,
      ),
      _FallingWidget(
        widget: _buildFakeButton('Save'),
        startX: 0.3,
        delay: 0.4,
        fallDuration: 1.8,
        rotation: 0.5,
        bounciness: 0.6,
      ),
      _FallingWidget(
        widget: _buildFakeToggle(),
        startX: 0.7,
        delay: 0.1,
        fallDuration: 2.2,
        rotation: -0.4,
        bounciness: 0.35,
      ),
      _FallingWidget(
        widget: _buildFakeCard('Theme', Icons.palette),
        startX: 0.2,
        delay: 0.5,
        fallDuration: 2.8,
        rotation: 0.15,
        bounciness: 0.45,
      ),
      _FallingWidget(
        widget: _buildFakeButton('Cancel'),
        startX: 0.5,
        delay: 0.3,
        fallDuration: 1.6,
        rotation: -0.35,
        bounciness: 0.55,
      ),
      _FallingWidget(
        widget: _buildFakeIcon(Icons.star),
        startX: 0.15,
        delay: 0.6,
        fallDuration: 1.4,
        rotation: 0.8,
        bounciness: 0.7,
      ),
      _FallingWidget(
        widget: _buildFakeIcon(Icons.bolt),
        startX: 0.8,
        delay: 0.15,
        fallDuration: 1.9,
        rotation: -0.6,
        bounciness: 0.65,
      ),
      _FallingWidget(
        widget: _buildFakeSlider(),
        startX: 0.4,
        delay: 0.35,
        fallDuration: 2.4,
        rotation: 0.25,
        bounciness: 0.3,
      ),
    ];
  }

  Widget _buildFakeCard(String title, IconData icon) {
    return Container(
      width: 140,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 32, color: Colors.grey[600]),
          const SizedBox(height: 8),
          Text(
            title,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: Colors.grey[800],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFakeButton(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFF009B6E),
        borderRadius: BorderRadius.circular(8),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF009B6E).withOpacity(0.3),
            blurRadius: 8,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w600,
          fontSize: 14,
        ),
      ),
    );
  }

  Widget _buildFakeToggle() {
    return Container(
      width: 60,
      height: 32,
      decoration: BoxDecoration(
        color: const Color(0xFF009B6E),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Align(
        alignment: Alignment.centerRight,
        child: Container(
          width: 26,
          height: 26,
          margin: const EdgeInsets.only(right: 3),
          decoration: const BoxDecoration(
            color: Colors.white,
            shape: BoxShape.circle,
          ),
        ),
      ),
    );
  }

  Widget _buildFakeIcon(IconData icon) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.orange.withOpacity(0.2),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Icon(icon, size: 28, color: Colors.orange),
    );
  }

  Widget _buildFakeSlider() {
    return Container(
      width: 120,
      height: 8,
      decoration: BoxDecoration(
        color: Colors.grey[300],
        borderRadius: BorderRadius.circular(4),
      ),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Container(
          width: 70,
          height: 8,
          decoration: BoxDecoration(
            color: const Color(0xFF009B6E),
            borderRadius: BorderRadius.circular(4),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[100],
      body: Stack(
        children: [
          // Falling widgets
          ..._fallingWidgets.map((fw) => _FallingWidgetAnimator(
                key: ValueKey(fw.hashCode),
                config: fw,
              )),

          // Center message
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.warning_amber_rounded,
                  size: 64,
                  color: Colors.orange,
                ),
                const SizedBox(height: 24),
                Text(
                  'Rewordium',
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    color: Colors.grey[800],
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'swipe back.',
                  style: TextStyle(
                    fontSize: 18,
                    color: Colors.grey[600],
                  ),
                ),
                const SizedBox(height: 32),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  decoration: BoxDecoration(
                    color: Colors.orange.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.orange.withOpacity(0.3)),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.bolt, size: 16, color: Colors.orange[700]),
                      const SizedBox(width: 6),
                      Text(
                        'version: Jellyfish 1.0.28',
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.orange[700],
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    super.dispose();
  }
}

class _FallingWidget {
  final Widget widget;
  final double startX; // 0.0 to 1.0 (percentage of screen width)
  final double delay; // seconds before starting to fall
  final double fallDuration; // seconds to complete fall
  final double rotation; // radians to rotate during fall
  final double bounciness; // 0.0 to 1.0 (how much to bounce)

  _FallingWidget({
    required this.widget,
    required this.startX,
    required this.delay,
    required this.fallDuration,
    required this.rotation,
    required this.bounciness,
  });
}

class _FallingWidgetAnimator extends StatefulWidget {
  final _FallingWidget config;

  const _FallingWidgetAnimator({
    super.key,
    required this.config,
  });

  @override
  State<_FallingWidgetAnimator> createState() => _FallingWidgetAnimatorState();
}

class _FallingWidgetAnimatorState extends State<_FallingWidgetAnimator>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _fallAnimation;
  late Animation<double> _rotationAnimation;

  @override
  void initState() {
    super.initState();

    _controller = AnimationController(
      vsync: this,
      duration: Duration(milliseconds: (widget.config.fallDuration * 1000).toInt()),
    );

    // Bouncy fall animation using custom curve – lands at bottom and bounces once
    _fallAnimation = Tween<double>(begin: -0.25, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: _BouncyCurve(bounciness: widget.config.bounciness),
      ),
    );

    // Rotation animation
    _rotationAnimation = Tween<double>(
      begin: 0,
      end: widget.config.rotation,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOut,
    ));

    // Start animation after delay
    Future.delayed(Duration(milliseconds: (widget.config.delay * 1000).toInt()), () {
      if (mounted) {
        _controller.forward();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        final yPos = _fallAnimation.value * screenHeight;
        final xPos = widget.config.startX * screenWidth;

        // Keep items visible near the floor (avoid drifting off-screen)
        final safeTop = (yPos - 50).clamp(0.0, screenHeight - 140);

        return Positioned(
          left: xPos - 70, // Center widget roughly
          top: safeTop,
          child: Transform.rotate(
            angle: _rotationAnimation.value,
            child: widget.config.widget,
          ),
        );
      },
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

/// Custom smooth bouncy curve for falling animation
class _BouncyCurve extends Curve {
  final double bounciness;

  _BouncyCurve({required this.bounciness});

  @override
  double transformInternal(double t) {
    // One main bounce at the bottom; no mid-air bounces
    const double contact = 0.72; // when it touches bottom
    const double settleStart = 0.88; // start settling after bounce
    const double bottom = 0.90; // bottom position (stay inside screen)

    if (t < contact) {
      // Smooth accelerating fall using ease-in-out cubic
      final fallT = t / contact;
      return bottom * (fallT * fallT * (3.0 - 2.0 * fallT));
    } else if (t < settleStart) {
      // Single bounce after contact
      final bounceT = (t - contact) / (settleStart - contact);
      final bounceHeight = 0.08 * bounciness; // amplitude
      final smoothBounce = sin(bounceT * pi) * (1.0 - bounceT * 0.35);
      return bottom - bounceHeight * smoothBounce;
    } else {
      // Settle to bottom with gentle damping
      final settleT = (t - settleStart) / (1.0 - settleStart);
      final damping = 0.02 * bounciness;
      return bottom - damping * (1.0 - settleT) * (1.0 - settleT);
    }
  }
}
