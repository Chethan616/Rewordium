import 'package:flutter/services.dart';

/// Service to interact with the RewordiumAIKeyboardService on Android
class RewordiumKeyboardService {
  static const MethodChannel _channel = MethodChannel('com.example.yc_startup/rewordium_keyboard');

  /// Check if the Rewordium AI Keyboard service is enabled
  static Future<bool> isKeyboardEnabled() async {
    try {
      final bool result = await _channel.invokeMethod('isRewordiumAIKeyboardEnabled');
      return result;
    } on PlatformException catch (e) {
      print('Error checking keyboard status: ${e.message}');
      return false;
    }
  }

  /// Open the system keyboard settings
  static Future<bool> openKeyboardSettings() async {
    try {
      final result = await _channel.invokeMethod<bool>('openKeyboardSettings');
      return result ?? false;
    } on PlatformException catch (e) {
      print('Error opening keyboard settings: ${e.message}');
      return false;
    }
  }

  /// Open system keyboard settings
  static Future<void> openKeyboardSettingsOld() async {
    try {
      await _channel.invokeMethod('openKeyboardSettings');
    } on PlatformException catch (e) {
      print('Error opening keyboard settings: ${e.message}');
    }
  }

  /// Update the keyboard theme color
  static Future<void> updateThemeColor(String colorHex) async {
    try {
      await _channel.invokeMethod('updateThemeColor', {'colorHex': colorHex});
    } on PlatformException catch (e) {
      print('Error updating theme color: ${e.message}');
    }
  }

  /// Toggle dark mode
  static Future<void> setDarkMode(bool enabled) async {
    try {
      await _channel.invokeMethod('setDarkMode', {'enabled': enabled});
    } on PlatformException catch (e) {
      print('Error setting dark mode: ${e.message}');
    }
  }

  /// Toggle haptic feedback
  static Future<void> setHapticFeedback(bool enabled) async {
    try {
      await _channel.invokeMethod('setHapticFeedback', {'enabled': enabled});
    } on PlatformException catch (e) {
      print('Error setting haptic feedback: ${e.message}');
    }
  }

  /// Toggle auto-capitalization
  static Future<void> setAutoCapitalize(bool enabled) async {
    try {
      await _channel.invokeMethod('setAutoCapitalize', {'enabled': enabled});
    } on PlatformException catch (e) {
      print('Error setting auto-capitalize: ${e.message}');
    }
  }

  /// Toggle double-space for period
  static Future<void> setDoubleSpacePeriod(bool enabled) async {
    try {
      await _channel.invokeMethod('setDoubleSpacePeriod', {'enabled': enabled});
    } on PlatformException catch (e) {
      print('Error setting double-space period: ${e.message}');
    }
  }

  /// Get current keyboard settings
  static Future<Map<String, dynamic>> getKeyboardSettings() async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod('getKeyboardSettings');
      // Ensure all values are present with defaults
      return {
        'themeColor': result['themeColor'] ?? '#007AFF',
        'darkMode': result['darkMode'] ?? false,
        'hapticFeedback': result['hapticFeedback'] ?? true,
        'autoCapitalize': result['autoCapitalize'] ?? true,
        'doubleSpacePeriod': result['doubleSpacePeriod'] ?? true,
      };
    } on PlatformException catch (e) {
      print('Error getting keyboard settings: ${e.message}');
      // Return defaults if there's an error
      return {
        'themeColor': '#007AFF',
        'darkMode': false,
        'hapticFeedback': true,
        'autoCapitalize': true,
        'doubleSpacePeriod': true,
      };
    }
  }

  /// Force refresh the keyboard UI
  static Future<void> refreshKeyboard() async {
    try {
      await _channel.invokeMethod('refreshKeyboard');
    } on PlatformException catch (e) {
      print('Error refreshing keyboard: ${e.message}');
    }
  }

  /// Force a complete restart of the keyboard service
  /// This is a more aggressive approach when regular refresh doesn't work
  static Future<void> forceRestartKeyboard() async {
    try {
      await _channel.invokeMethod('forceRestartKeyboard');
    } on PlatformException catch (e) {
      print('Error force restarting keyboard: ${e.message}');
    }
  }

  /// Toggle autocorrect
  static Future<void> setAutocorrect(bool enabled) async {
    try {
      await _channel.invokeMethod('setAutocorrect', {'enabled': enabled});
    } on PlatformException catch (e) {
      print('Error setting autocorrect: ${e.message}');
    }
  }
}
