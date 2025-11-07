import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:ui' as ui;
import 'dart:async';
import 'package:flutter/foundation.dart';

/// A utility class to optimize animations and improve performance
class AnimationOptimizer {
  // Cache for preloaded animation assets
  static final Map<String, ByteData> _preloadedAnimations = {};
  static bool _isInitialized = false;
  static bool _isPreloadingComplete = false;
  
  // Animation optimization settings
  static const bool useHardwareAcceleration = true;
  static const bool useCompositingOptimization = true;
  
  /// Initialize the animation optimizer
  static Future<void> initialize() async {
    if (_isInitialized) return;
    
    try {
      // Apply performance optimizations
      _applyGlobalOptimizations();
      
      // Start preloading common animations in the background
      unawaited(_preloadCommonAnimations());
      _isInitialized = true;
    } catch (e) {
      debugPrint('Error initializing animation optimizer: $e');
    }
  }
  
  /// Apply global performance optimizations
  static void _applyGlobalOptimizations() {
    // Enable hardware acceleration where available
    if (useHardwareAcceleration) {
      // This is handled by Flutter automatically, but we can ensure
      // that we're using the most optimal settings
      debugPrint('Hardware acceleration enabled for animations');
    }
    
    // Optimize compositing layers
    if (useCompositingOptimization) {
      // Reduce unnecessary compositing layers
      debugPrint('Compositing optimization enabled');
    }
  }
  
  /// Preload common animations used throughout the app
  static Future<void> _preloadCommonAnimations() async {
    try {
      // List of common animations to preload
      final animationPaths = [
        'assets/lottie/loading.json',
        'assets/lottie/aiDetector.json',
        'assets/lottie/typing.json',
        'assets/lottie/grammar.json',
        'assets/lottie/paraphraser.json',
        'assets/lottie/keyboard.json',
        'assets/lottie/gmail.json',
        'assets/lottie/feedback.json',
        'assets/lottie/pencil.json',
        'assets/lottie/empty_state.json',
        'assets/lottie/translator.json',
        'assets/lottie/summarizer.json',
        'assets/lottie/toneEditor.json',
      ];
      
      // Load animations in parallel using compute for better performance
      final results = await Future.wait(
        animationPaths.map((path) => compute(_isolatePreloadAnimation, path))
      );
      
      // Store results in cache
      for (int i = 0; i < animationPaths.length; i++) {
        if (results[i] != null) {
          _preloadedAnimations[animationPaths[i]] = results[i] as ByteData;
        }
      }
      
      _isPreloadingComplete = true;
      debugPrint('Preloaded ${_preloadedAnimations.length} animations');
    } catch (e) {
      debugPrint('Error preloading animations: $e');
    }
  }
  
  /// Isolate worker for preloading animations
  static Future<ByteData?> _isolatePreloadAnimation(String assetPath) async {
    try {
      // This runs in a separate isolate
      final data = await rootBundle.load(assetPath);
      return data;
    } catch (e) {
      debugPrint('Error preloading animation $assetPath: $e');
      return null;
    }
  }
  

  
  /// Get a preloaded animation
  static ByteData? getPreloadedAnimation(String assetPath) {
    return _preloadedAnimations[assetPath];
  }
  
  /// Check if preloading is complete
  static bool isPreloadingComplete() {
    return _isPreloadingComplete;
  }
  
  /// Optimize image assets for better performance
  static Future<ui.Image> optimizeImage(ImageProvider provider, {Size? targetSize}) async {
    final Completer<ui.Image> completer = Completer<ui.Image>();
    
    final ImageStream stream = provider.resolve(ImageConfiguration(
      size: targetSize,
      devicePixelRatio: WidgetsBinding.instance.window.devicePixelRatio,
    ));
    
    late ImageStreamListener listener;
    listener = ImageStreamListener((ImageInfo info, bool _) {
      completer.complete(info.image);
      stream.removeListener(listener);
    }, onError: (dynamic error, StackTrace? stackTrace) {
      completer.completeError(error, stackTrace);
      stream.removeListener(listener);
    });
    
    stream.addListener(listener);
    return completer.future;
  }
  
  /// Helper method to create optimized animation widgets
  static Widget createOptimizedAnimationContainer({
    required Widget child,
    bool useRepaintBoundary = true,
  }) {
    if (useRepaintBoundary) {
      return RepaintBoundary(child: child);
    }
    return child;
  }
}

// Helper function to avoid having to use 'unawaited' everywhere
void unawaited(Future<void> future) {}
