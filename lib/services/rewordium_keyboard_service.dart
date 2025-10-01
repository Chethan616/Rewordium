import 'package:flutter/services.dart';

/// Service to interact with the RewordiumAIKeyboardService on Android
class RewordiumKeyboardService {
  static const MethodChannel _channel =
      MethodChannel('com.noxquill.rewordium/rewordium_keyboard');

  /// Check if the Rewordium AI Keyboard service is enabled
  static Future<bool> isKeyboardEnabled() async {
    try {
      final bool result =
          await _channel.invokeMethod('isRewordiumAIKeyboardEnabled');
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

  /// Toggle haptic feedback with immediate application
  static Future<void> setHapticFeedback(bool enabled) async {
    try {
      print('🔊 Setting haptic feedback: $enabled');
      await _channel.invokeMethod('setHapticFeedback', {'enabled': enabled});

      // ULTRA-AGGRESSIVE IMMEDIATE REFRESH
      await forceKeyboardRecreation();

      print('✅ Haptic feedback setting applied successfully');
    } on PlatformException catch (e) {
      print('❌ Error setting haptic feedback: ${e.message}');
    }
  }

  /// Toggle dark mode with immediate application
  static Future<void> setDarkMode(bool enabled) async {
    try {
      print('🌙 Setting dark mode: $enabled');
      await _channel.invokeMethod('setDarkMode', {'enabled': enabled});

      // ULTRA-AGGRESSIVE IMMEDIATE REFRESH
      await forceKeyboardRecreation();

      print('✅ Dark mode setting applied successfully');
    } on PlatformException catch (e) {
      print('❌ Error setting dark mode: ${e.message}');
    }
  }

  /// Update the keyboard theme color with immediate application
  static Future<void> updateThemeColor(String colorHex) async {
    try {
      print('🎨 Setting theme color: $colorHex');
      await _channel.invokeMethod('updateThemeColor', {'colorHex': colorHex});

      // ULTRA-AGGRESSIVE IMMEDIATE REFRESH
      await forceKeyboardRecreation();

      print('✅ Theme color setting applied successfully');
    } on PlatformException catch (e) {
      print('❌ Error updating theme color: ${e.message}');
    }
  }

  /// Refresh keyboard settings to apply changes immediately
  static Future<bool> refreshKeyboard() async {
    try {
      print('🔄 Refreshing keyboard settings...');
      final result = await _channel.invokeMethod<bool>('refreshKeyboard');
      print('✅ Keyboard refresh completed');
      return result ?? false;
    } on PlatformException catch (e) {
      print('❌ Error refreshing keyboard: ${e.message}');
      return false;
    }
  }

  /// Get current keyboard settings
  static Future<Map<String, dynamic>> getKeyboardSettings() async {
    try {
      final Map<dynamic, dynamic> result =
          await _channel.invokeMethod('getKeyboardSettings');
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

  /// Toggle auto capitalize with immediate application
  static Future<void> setAutoCapitalize(bool enabled) async {
    try {
      print('🔤 Setting auto capitalize: $enabled');
      await _channel.invokeMethod('setAutoCapitalize', {'enabled': enabled});

      // ULTRA-AGGRESSIVE IMMEDIATE REFRESH
      await forceKeyboardRecreation();

      print('✅ Auto capitalize setting applied successfully');
    } on PlatformException catch (e) {
      print('❌ Error setting auto capitalize: ${e.message}');
    }
  }

  /// Toggle double space period with immediate application
  static Future<void> setDoubleSpacePeriod(bool enabled) async {
    try {
      print('⏸️ Setting double space period: $enabled');
      await _channel.invokeMethod('setDoubleSpacePeriod', {'enabled': enabled});

      // ULTRA-AGGRESSIVE IMMEDIATE REFRESH
      await forceKeyboardRecreation();

      print('✅ Double space period setting applied successfully');
    } on PlatformException catch (e) {
      print('❌ Error setting double space period: ${e.message}');
    }
  }

  /// Force ultra-aggressive keyboard recreation for immediate settings application
  static Future<bool> forceKeyboardRecreation() async {
    try {
      print('🚨 Forcing ultra-aggressive keyboard recreation...');
      final result =
          await _channel.invokeMethod<bool>('forceKeyboardRecreation');
      print('✅ Ultra-aggressive keyboard recreation completed');
      return result ?? false;
    } on PlatformException catch (e) {
      print('❌ Error forcing keyboard recreation: ${e.message}');
      return false;
    }
  }
}
