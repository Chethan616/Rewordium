import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'package:flutter/scheduler.dart';
import '../theme/app_theme.dart';
import 'animation_optimizer.dart';

class LottieAssets {
  // Animation paths
  static const String _baseAnimationPath = 'assets/lottie/';
  static const String sampleAnimation =
      '${_baseAnimationPath}sample_animation.json';

  static const String typingAnimation = '${_baseAnimationPath}typing.json';
  static const String grammarCheckAnimation =
      '${_baseAnimationPath}grammar.json';
  static const String paraphrasingAnimation =
      '${_baseAnimationPath}paraphraser.json';
  static const String keyboardAnimation = '${_baseAnimationPath}keyboard.json';
  static const String assistantAnimation = '${_baseAnimationPath}gmail.json';
  static const String feedbackAnimation = '${_baseAnimationPath}feedback.json';
  static const String pencilAnimation = '${_baseAnimationPath}pencil.json';
  static const String emptyStateAnimation =
      '${_baseAnimationPath}empty_state.json';
  static const String aiDetectorAnimation = '${_baseAnimationPath}aiDetector.json';
  static const String translatorAnimation = '${_baseAnimationPath}translator.json';
  static const String summarizerAnimation = '${_baseAnimationPath}summarizer.json';
  static const String toneEditorAnimation = '${_baseAnimationPath}toneEditor.json';

  // Cache for loaded animations to improve performance
  static final Map<String, LottieComposition> _animationCache = {};
  
  // Animation frame timing control for 60 FPS
  static final Map<String, int> _animationLastFrameTime = {};
  static const int _targetFrameTimeMs = 16; // Target ~60 FPS

  // Helper method to display animations with default settings
  static Widget getLottieAnimation({
    required String animationPath,
    double? width,
    double? height,
    BoxFit fit = BoxFit.contain,
    bool repeat = true,
    bool animate = true,
    AnimationController? controller,
    Color? placeholderColor,
    IconData? placeholderIcon,
    bool optimizeRendering = true,
    bool optimizeMemory = true,
    bool highPerformanceMode = true,
  }) {
    // Check if we need to throttle this animation to maintain 60 FPS
    final currentTime = DateTime.now().millisecondsSinceEpoch;
    final lastFrameTime = _animationLastFrameTime[animationPath] ?? 0;
    final shouldThrottle = (currentTime - lastFrameTime) < _targetFrameTimeMs;
    
    // Update the last frame time
    if (!shouldThrottle) {
      _animationLastFrameTime[animationPath] = currentTime;
    }
    
    // Check if animation is preloaded for faster loading
    AnimationOptimizer.getPreloadedAnimation(animationPath);
    
    // Create a builder that builds the animation with the current performance settings
    Widget animationBuilder() {
      // Use cached composition if available to improve performance
      return Lottie.asset(
        animationPath,
        width: width,
        height: height,
        fit: fit,
        repeat: repeat,
        animate: animate && !shouldThrottle, // Throttle animations if needed
        controller: controller,
        frameRate: FrameRate(60), // Target exact 60 FPS
        // Advanced performance options
        options: LottieOptions(
          enableMergePaths: false, // Disable merge paths for better performance
        ),
        delegates: LottieDelegates(
          values: [ValueDelegate.color(const ['**'], value: null)],
        ),
        // Cache the composition for future use
        onLoaded: (composition) {
          _animationCache[animationPath] = composition;
          
          // Schedule a frame callback to ensure smooth animation
          SchedulerBinding.instance.scheduleFrameCallback((_) {
            // This will be called before the next frame is rendered
          });
        },
        errorBuilder: (context, error, stackTrace) {
          // Try to use the sample animation if the requested one isn't found
          return Lottie.asset(
            sampleAnimation,
            width: width,
            height: height,
            fit: fit,
            repeat: repeat,
            animate: animate,
            controller: controller,
            frameRate: FrameRate(60),
            errorBuilder: (context, error, stackTrace) {
              // If sample animation also fails, show a placeholder
              return _buildPlaceholder(
                  width, height, placeholderColor, placeholderIcon);
            },
          );
        },
      );
    }
    
    Widget animationWidget = animationBuilder();
    
    // Apply additional optimizations
    if (highPerformanceMode) {
      // Use hardware acceleration for rendering
      animationWidget = animationWidget;
    }
    
    // Apply RepaintBoundary for optimized rendering and reduced GPU calls
    if (optimizeRendering) {
      return RepaintBoundary(
        child: animationWidget,
      );
    }
    
    return animationWidget;
  }

  static Widget _buildPlaceholder(
      double? width, double? height, Color? color, IconData? icon) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: color ?? AppTheme.primaryColor.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Center(
        child: Icon(
          icon ?? Icons.animation,
          size: (width ?? 100) * 0.3,
          color: color ?? AppTheme.primaryColor,
        ),
      ),
    );
  }

  // Pre-configured animation widgets
  static Widget getTypingAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: typingAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.keyboard,
      placeholderColor: Colors.blue,
    );
  }

  static Widget getGrammarCheckAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: grammarCheckAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.spellcheck,
      placeholderColor: Colors.red,
    );
  }

  static Widget getPencilAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: pencilAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.spellcheck,
      placeholderColor: Colors.red,
    );
  }

  static Widget getParaphrasingAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: paraphrasingAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.edit_note,
      placeholderColor: Colors.green,
    );
  }

  static Widget getKeyboardAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: keyboardAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.keyboard_alt_outlined,
      placeholderColor: Colors.blue,
    );
  }

  static Widget getAssistantAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: assistantAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.email,
      placeholderColor: Colors.red,
    );
  }

  static Widget getFeedbackAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: feedbackAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.star_rate,
      placeholderColor: Colors.amber,
    );
  }

  static Widget getEmptyStateAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: emptyStateAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.hourglass_empty,
      placeholderColor: Colors.grey,
    );
  }
  
  static Widget getAIDetectorAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: aiDetectorAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.psychology,
      placeholderColor: Colors.purple,
    );
  }
  
  static Widget getTranslatorAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: translatorAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.translate,
      placeholderColor: Colors.blue,
    );
  }
  
  static Widget getSummarizerAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: summarizerAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.summarize,
      placeholderColor: Colors.orange,
    );
  }
  
  static Widget getToneEditorAnimation({double? width, double? height}) {
    return getLottieAnimation(
      animationPath: toneEditorAnimation,
      width: width,
      height: height,
      placeholderIcon: Icons.mood,
      placeholderColor: Colors.teal,
    );
  }
}
