import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

/// Service for managing swipe gesture features in the keyboard
class SwipeGestureService {
  static const MethodChannel _channel =
      MethodChannel('com.noxquill.rewordium/swipe_gestures');

  static bool get _isAndroid => defaultTargetPlatform == TargetPlatform.android;

  // Singleton pattern
  static final SwipeGestureService _instance = SwipeGestureService._internal();
  factory SwipeGestureService() => _instance;
  SwipeGestureService._internal();

  /// Initialize the swipe gesture service
  static Future<bool> initialize() async {
    if (!_isAndroid) return false;
    try {
      final bool? result = await _channel.invokeMethod('initialize');
      final initialized = result ?? false;
      debugPrint('🚀 SwipeGestureService initialized: $initialized');

      // If not initialized, wait and try once more
      if (!initialized) {
        await Future.delayed(Duration(milliseconds: 200));
        final bool? retryResult = await _channel.invokeMethod('initialize');
        final retryInitialized = retryResult ?? false;
        debugPrint(
            '🔄 SwipeGestureService retry initialized: $retryInitialized');
        return retryInitialized;
      }

      return initialized;
    } catch (e) {
      debugPrint('❌ Error initializing SwipeGestureService: $e');
      return false;
    }
  }

  /// Enable or disable swipe gestures
  static Future<bool> setSwipeGesturesEnabled(bool enabled) async {
    if (!_isAndroid) return false;
    try {
      final bool? result =
          await _channel.invokeMethod('setSwipeGesturesEnabled', {
        'enabled': enabled,
      });
      debugPrint(
          '🎯 Swipe gestures ${enabled ? 'enabled' : 'disabled'}: $result');
      return result ?? false;
    } catch (e) {
      debugPrint('❌ Error setting swipe gestures: $e');
      return false;
    }
  }

  /// Configure swipe gesture sensitivity
  static Future<bool> setSwipeSensitivity(double sensitivity) async {
    if (!_isAndroid) return false;
    try {
      final bool? result = await _channel.invokeMethod('setSwipeSensitivity', {
        'sensitivity': sensitivity,
      });
      debugPrint('🎚️ Swipe sensitivity set to $sensitivity: $result');
      return result ?? false;
    } catch (e) {
      debugPrint('❌ Error setting swipe sensitivity: $e');
      return false;
    }
  }

  /// Configure gesture preview settings
  static Future<bool> setGesturePreview(bool showPreview) async {
    if (!_isAndroid) return false;
    try {
      final bool? result = await _channel.invokeMethod('setGesturePreview', {
        'showPreview': showPreview,
      });
      debugPrint(
          '👀 Gesture preview ${showPreview ? 'enabled' : 'disabled'}: $result');
      return result ?? false;
    } catch (e) {
      debugPrint('❌ Error setting gesture preview: $e');
      return false;
    }
  }

  /// Get current swipe gesture statistics
  static Future<Map<String, dynamic>?> getGestureStats() async {
    if (!_isAndroid) return null;
    try {
      final Map<dynamic, dynamic>? result =
          await _channel.invokeMethod('getGestureStats');
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return null;
    } catch (e) {
      debugPrint('❌ Error getting gesture stats: $e');
      return null;
    }
  }

  /// Reset gesture learning data
  static Future<bool> resetGestureLearning() async {
    if (!_isAndroid) return false;
    try {
      final bool? result = await _channel.invokeMethod('resetGestureLearning');
      debugPrint('🔄 Gesture learning reset: $result');
      return result ?? false;
    } catch (e) {
      debugPrint('❌ Error resetting gesture learning: $e');
      return false;
    }
  }

  /// Configure special gesture shortcuts
  static Future<bool> configureSpecialGestures({
    bool spaceDeleteEnabled = true,
    bool cursorMovementEnabled = true,
    bool capsToggleEnabled = true,
    bool symbolModeEnabled = true,
  }) async {
    if (!_isAndroid) return false;
    try {
      final bool? result =
          await _channel.invokeMethod('configureSpecialGestures', {
        'spaceDeleteEnabled': spaceDeleteEnabled,
        'cursorMovementEnabled': cursorMovementEnabled,
        'capsToggleEnabled': capsToggleEnabled,
        'symbolModeEnabled': symbolModeEnabled,
      });
      debugPrint('⚡ Special gestures configured: $result');
      return result ?? false;
    } catch (e) {
      debugPrint('❌ Error configuring special gestures: $e');
      return false;
    }
  }

  /// Test the swipe gesture system
  static Future<bool> testGestureSystem() async {
    if (!_isAndroid) return false;
    try {
      final bool? result = await _channel.invokeMethod('testGestureSystem');
      debugPrint('🧪 Gesture system test result: $result');
      return result ?? false;
    } catch (e) {
      debugPrint('❌ Error testing gesture system: $e');
      return false;
    }
  }

  /// Get gesture performance metrics
  static Future<Map<String, dynamic>?> getPerformanceMetrics() async {
    if (!_isAndroid) return null;
    try {
      final Map<dynamic, dynamic>? result =
          await _channel.invokeMethod('getPerformanceMetrics');
      if (result != null) {
        final metrics = Map<String, dynamic>.from(result);
        debugPrint('📊 Gesture performance metrics: $metrics');
        return metrics;
      }
      return null;
    } catch (e) {
      debugPrint('❌ Error getting performance metrics: $e');
      return null;
    }
  }

  /// Set gesture learning mode
  static Future<bool> setLearningMode(bool adaptiveLearning) async {
    if (!_isAndroid) return false;
    try {
      final bool? result = await _channel.invokeMethod('setLearningMode', {
        'adaptiveLearning': adaptiveLearning,
      });
      debugPrint(
          '🧠 Adaptive learning ${adaptiveLearning ? 'enabled' : 'disabled'}: $result');
      return result ?? false;
    } catch (e) {
      debugPrint('❌ Error setting learning mode: $e');
      return false;
    }
  }
}
