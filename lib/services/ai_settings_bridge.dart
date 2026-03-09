import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../models/advanced_ai_settings.dart';

/// Bridge to sync AI settings between Flutter and Android (Accessibility Service & Keyboard)
class AISettingsBridge {
  static const MethodChannel _channel = MethodChannel('com.noxquill.rewordium/ai_settings');

  static bool get _isAndroid => defaultTargetPlatform == TargetPlatform.android;
  
  /// Sync AI settings to Android native services
  static Future<bool> syncSettingsToAndroid() async {
    if (!_isAndroid) return false;
    try {
      final settings = await AdvancedAISettingsService.loadSettings();
      
      // If advanced settings are disabled, send default Groq config
      if (!settings.enabled) {
        await _channel.invokeMethod('updateAISettings', {
          'enabled': false,
          'provider': 'groq',
          'apiKey': '', // Android will use BuildConfig.GROQ_API_KEY
          'model': 'llama-3.1-8b-instant',
          'maxTokens': 8192,
          'customEndpoint': '',
        });
        return true;
      }
      
      // Send custom settings
      await _channel.invokeMethod('updateAISettings', {
        'enabled': true,
        'provider': settings.provider.name,
        'apiKey': settings.apiKey,
        'model': settings.modelName.isNotEmpty ? settings.modelName : settings.getDefaultModelName(),
        'maxTokens': settings.maxTokens > 0 ? settings.maxTokens : settings.getDefaultMaxTokens(),
        'customEndpoint': settings.customEndpoint,
      });
      
      print('AI settings synced to Android successfully');
      return true;
    } catch (e) {
      print('Error syncing AI settings to Android: $e');
      return false;
    }
  }
  
  /// Get the current AI config for native services
  static Future<Map<String, dynamic>> getAIConfigForNative() async {
    try {
      final config = await AdvancedAISettingsService.getAPIConfig();
      return config;
    } catch (e) {
      // If error getting config (e.g., enabled but no key), return default Groq
      return {
        'provider': 'groq',
        'apiKey': '', // Android will use BuildConfig.GROQ_API_KEY
        'model': 'llama-3.1-8b-instant',
        'maxTokens': 8192,
      };
    }
  }
  
  /// Initialize the bridge and set up handlers for Android -> Flutter calls
  static void initialize() {
    if (!_isAndroid) return;
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'getAISettings':
          return await getAIConfigForNative();
        case 'isAdvancedSettingsEnabled':
          final settings = await AdvancedAISettingsService.loadSettings();
          return settings.enabled && settings.isApiKeyValid();
        default:
          throw PlatformException(
            code: 'NOT_IMPLEMENTED',
            message: 'Method ${call.method} not implemented',
          );
      }
    });
  }
}
