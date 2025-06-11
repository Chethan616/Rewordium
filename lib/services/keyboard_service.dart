import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import '../widgets/keyboard/system_keyboard_overlay.dart';

enum KeyboardLayout {
  onePlus,
  samsung,
  apple
}

class KeyboardService {
  static const MethodChannel _channel = MethodChannel('com.example.yc_startup/rewordium_keyboard');
  
  // Singleton instance
  static final KeyboardService _instance = KeyboardService._internal();
  
  factory KeyboardService() {
    return _instance;
  }
  
  KeyboardService._internal();
  
  /// Check if the Rewordium Keyboard is enabled as an input method
  Future<bool> isKeyboardEnabled() async {
    try {
      final bool result = await _channel.invokeMethod('isKeyboardEnabled');
      return result;
    } on PlatformException catch (e) {
      print('Error checking keyboard status: ${e.message}');
      return false;
    }
  }
  
  /// Open the system keyboard settings to enable the Rewordium Keyboard
  Future<void> openKeyboardSettings() async {
    try {
      await _channel.invokeMethod('openKeyboardSettings');
    } on PlatformException catch (e) {
      print('Error opening keyboard settings: ${e.message}');
    }
  }
  
  /// Set the keyboard layout for the system-wide keyboard
  Future<bool> setKeyboardLayout(KeyboardLayout layout) async {
    try {
      final String layoutString = layout.toString().split('.').last;
      final bool result = await _channel.invokeMethod('setKeyboardLayout', {
        'layout': layoutString,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting keyboard layout: ${e.message}');
      return false;
    }
  }
  
  /// Get the current keyboard layout
  Future<KeyboardLayout> getCurrentLayout() async {
    try {
      final String result = await _channel.invokeMethod('getCurrentLayout');
      switch (result) {
        case 'samsung':
          return KeyboardLayout.samsung;
        case 'apple':
          return KeyboardLayout.apple;
        case 'oneplus':
        default:
          return KeyboardLayout.onePlus;
      }
    } on PlatformException catch (e) {
      print('Error getting current layout: ${e.message}');
      return KeyboardLayout.onePlus;
    }
  }
  
  /// Enable or disable haptic feedback
  Future<bool> setHapticFeedback(bool enabled) async {
    try {
      final bool result = await _channel.invokeMethod('setHapticFeedback', {
        'enabled': enabled,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting haptic feedback: ${e.message}');
      return false;
    }
  }
  
  /// Enable or disable AI suggestions
  Future<bool> setAiSuggestions(bool enabled) async {
    try {
      final bool result = await _channel.invokeMethod('setAiSuggestions', {
        'enabled': enabled,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting AI suggestions: ${e.message}');
      return false;
    }
  }

  /// Notify the native keyboard about theme changes (dark/light mode)
  Future<void> updateKeyboardTheme(bool isDarkMode) async {
    try {
      await _channel.invokeMethod('updateTheme', {'isDarkMode': isDarkMode});
      print('Keyboard theme update notification sent. isDarkMode: $isDarkMode');
    } on PlatformException catch (e) {
      print('Error updating keyboard theme: ${e.message}');
    }
  }
  
  /// Enable or disable the paraphraser button
  Future<bool> setParaphraserButton(bool enabled) async {
    try {
      final bool result = await _channel.invokeMethod('setParaphraserButton', {
        'enabled': enabled,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting paraphraser button: ${e.message}');
      return false;
    }
  }
  
  // Show the system keyboard overlay
  OverlayEntry? _overlayEntry;
  bool _isOverlayVisible = false;
  
  void showSystemKeyboardOverlay(BuildContext context) {
    if (_overlayEntry != null) {
      return;
    }
    
    _overlayEntry = OverlayEntry(
      builder: (context) => SystemKeyboardOverlay(
        onVisibilityChanged: (isVisible) {
          _isOverlayVisible = isVisible;
        },
      ),
    );
    
    Overlay.of(context).insert(_overlayEntry!);
  }
  
  void hideSystemKeyboardOverlay() {
    if (_overlayEntry != null) {
      _overlayEntry!.remove();
      _overlayEntry = null;
      _isOverlayVisible = false;
    }
  }
  
  bool get isOverlayVisible => _isOverlayVisible;
  
  /// Set the active persona for the keyboard
  Future<bool> setPersona(String personaName) async {
    try {
      final bool result = await _channel.invokeMethod('setPersona', {
        'personaName': personaName,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting persona: ${e.message}');
      return false;
    }
  }
  
  /// Set the keyboard personas (max 3) that will appear on the keyboard
  Future<bool> setKeyboardPersonas(List<String> personaNames) async {
    try {
      // Ensure we have at most 3 personas
      if (personaNames.length > 3) {
        personaNames = personaNames.sublist(0, 3);
      }
      
      // Log the personas being sent
      print('[Flutter KeyboardService] Sending personas to native: $personaNames');
      
      final dynamic result = await _channel.invokeMethod('updateKeyboardPersonas', {
        'personas': personaNames,
      });
      
      // Ensure we got a boolean response
      if (result is bool) {
        return result;
      }
      
      // Log warning if response is not a boolean
      print('[Flutter KeyboardService] Warning: Unexpected response type from updateKeyboardPersonas');
      return false;
      
    } on PlatformException catch (e) {
      print('Error setting keyboard personas: ${e.message}');
      return false;
    } catch (e) {
      print('Unexpected error in setKeyboardPersonas: $e');
      return false;
    }
  }
}
