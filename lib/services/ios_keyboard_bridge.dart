import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Writes Rewordium settings into the iOS App Group container so the
/// RewordiumKeyboard extension can read them.
///
/// Mirrors the Android equivalent (`rewordium_keyboard_service.dart`) but
/// runs over a different MethodChannel because iOS extensions cannot share
/// a Flutter engine with the host app — they read settings out-of-process
/// via App Group `UserDefaults`.
///
/// Calls are no-ops on non-iOS platforms.
class IosKeyboardBridge {
  static const _channel =
      MethodChannel('com.noxquill.rewordium/keyboard_settings');

  static bool get _isIOS => defaultTargetPlatform == TargetPlatform.iOS;

  /// Push the current Rewordium settings into the App Group. Pass only the
  /// fields you want to update — omitted keys are left untouched on the
  /// native side. Passing an empty string for [groqAPIKey] explicitly clears
  /// the stored key.
  static Future<bool> writeSettings({
    String? groqAPIKey,
    String? groqModel,
    bool? hapticsEnabled,
    bool? aiEnabled,
    String? defaultTone,
  }) async {
    if (!_isIOS) return false;
    final args = <String, dynamic>{};
    if (groqAPIKey != null)     args['groq_api_key']     = groqAPIKey;
    if (groqModel != null)      args['groq_model']       = groqModel;
    if (hapticsEnabled != null) args['haptics_enabled']  = hapticsEnabled;
    if (aiEnabled != null)      args['ai_enabled']       = aiEnabled;
    if (defaultTone != null)    args['default_tone']     = defaultTone;
    if (args.isEmpty) return true;

    try {
      final ok = await _channel.invokeMethod<bool>('write', args);
      return ok ?? false;
    } on MissingPluginException {
      // Build without the bridge registered yet (e.g. during initial CI run).
      return false;
    } on PlatformException {
      return false;
    }
  }

  /// Read back what the extension would see. Useful for diagnostics.
  static Future<Map<String, dynamic>?> readSettings() async {
    if (!_isIOS) return null;
    try {
      final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>('read');
      return raw?.cast<String, dynamic>();
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }

  /// Wipe the App Group store (e.g. on sign-out).
  static Future<bool> clear() async {
    if (!_isIOS) return false;
    try {
      final ok = await _channel.invokeMethod<bool>('clear');
      return ok ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }
}
