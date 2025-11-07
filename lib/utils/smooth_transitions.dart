import 'package:flutter/material.dart';

class SmoothPageTransitions {
  // Smooth slide transition when navigating back
  static PageRouteBuilder<T> slideFromRight<T extends Object?>(
    Widget page, {
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOutCubic,
  }) {
    return PageRouteBuilder<T>(
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionDuration: duration,
      reverseTransitionDuration: duration,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        const begin = Offset(1.0, 0.0);
        const end = Offset.zero;
        final tween = Tween(begin: begin, end: end);
        final offsetAnimation = animation.drive(tween.chain(
          CurveTween(curve: curve),
        ));

        // For back navigation, slide out to the right
        if (secondaryAnimation.status != AnimationStatus.dismissed) {
          final slideOut = Tween<Offset>(
            begin: Offset.zero,
            end: const Offset(-0.3, 0.0),
          ).animate(CurvedAnimation(
            parent: secondaryAnimation,
            curve: curve,
          ));

          return SlideTransition(
            position: slideOut,
            child: FadeTransition(
              opacity: Tween<double>(begin: 1.0, end: 0.8)
                  .animate(secondaryAnimation),
              child: child,
            ),
          );
        }

        return SlideTransition(
          position: offsetAnimation,
          child: FadeTransition(
            opacity: animation,
            child: child,
          ),
        );
      },
    );
  }

  // Smooth fade transition
  static PageRouteBuilder<T> fadeTransition<T extends Object?>(
    Widget page, {
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOut,
  }) {
    return PageRouteBuilder<T>(
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionDuration: duration,
      reverseTransitionDuration: duration,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return FadeTransition(
          opacity: CurvedAnimation(parent: animation, curve: curve),
          child: child,
        );
      },
    );
  }

  // Scale transition with fade
  static PageRouteBuilder<T> scaleTransition<T extends Object?>(
    Widget page, {
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOutCubic,
  }) {
    return PageRouteBuilder<T>(
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionDuration: duration,
      reverseTransitionDuration: duration,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        const begin = 0.8;
        const end = 1.0;
        final tween = Tween(begin: begin, end: end);
        final scaleAnimation = animation.drive(tween.chain(
          CurveTween(curve: curve),
        ));

        return FadeTransition(
          opacity: animation,
          child: ScaleTransition(
            scale: scaleAnimation,
            child: child,
          ),
        );
      },
    );
  }

  // Custom smooth back gesture handler
  static Widget smoothBackGesture({
    required Widget child,
    required VoidCallback onBack,
    double sensitivity = 0.3,
  }) {
    return GestureDetector(
      onHorizontalDragUpdate: (details) {
        // If swiping from left edge with sufficient velocity
        if (details.primaryDelta! > sensitivity) {
          onBack();
        }
      },
      child: child,
    );
  }
}

// Extension to make navigation easier
extension SmoothNavigation on BuildContext {
  Future<T?> pushSmoothly<T extends Object?>(
    Widget page, {
    SmoothTransitionType type = SmoothTransitionType.slide,
    Duration duration = const Duration(milliseconds: 300),
  }) {
    PageRouteBuilder<T> route;

    switch (type) {
      case SmoothTransitionType.slide:
        route =
            SmoothPageTransitions.slideFromRight<T>(page, duration: duration);
        break;
      case SmoothTransitionType.fade:
        route =
            SmoothPageTransitions.fadeTransition<T>(page, duration: duration);
        break;
      case SmoothTransitionType.scale:
        route =
            SmoothPageTransitions.scaleTransition<T>(page, duration: duration);
        break;
    }

    return Navigator.of(this).push(route);
  }

  void popSmoothly<T extends Object?>([T? result]) {
    Navigator.of(this).pop(result);
  }
}

enum SmoothTransitionType {
  slide,
  fade,
  scale,
}
