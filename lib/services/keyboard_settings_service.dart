import 'package:flutter/services.dart';
import 'dart:async';

class KeyboardSettingsService {
  static const MethodChannel _channel =
      MethodChannel('keyboard_settings_service');
  static StreamController<bool>? _settingsController;

  static Stream<bool> get settingsStream {
    _settingsController ??= StreamController<bool>.broadcast();
    return _settingsController!.stream;
  }

  static Future<void> initialize() async {
    try {
      _channel.setMethodCallHandler(_handleMethodCall);
      print('🔧 KeyboardSettingsService initialized successfully');
    } catch (e) {
      print('❌ Error initializing KeyboardSettingsService: $e');
    }
  }

  static Future<dynamic> _handleMethodCall(MethodCall call) async {
    print('🔔 KeyboardSettingsService received method call: ${call.method}');
    print('📊 Method call arguments: ${call.arguments}');
    
    switch (call.method) {
      case 'showKeyboardSettings':
        final bool isDarkMode = call.arguments['isDarkMode'] ?? false;
        print('🎨 Showing keyboard settings with isDarkMode: $isDarkMode');
        _settingsController?.add(isDarkMode);
        print('✅ Settings stream event sent');
        break;
      default:
        print('⚠️ Unknown method called: ${call.method}');
        throw PlatformException(
          code: 'Unimplemented',
          details: 'Method ${call.method} not implemented',
        );
    }
  }

  static void dispose() {
    _settingsController?.close();
    _settingsController = null;
  }

  // Test method to manually trigger the settings popup
  static void testShowSettings({bool isDarkMode = false}) {
    print('🧪 Testing keyboard settings popup with isDarkMode: $isDarkMode');
    _settingsController?.add(isDarkMode);
  }
}
