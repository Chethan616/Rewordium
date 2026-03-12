import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Flutter bridge for Google Play In-App Update API.
/// Handles both flexible (background) and immediate (blocking) updates.
class InAppUpdateService {
  static const _channel = MethodChannel('com.noxquill.rewordium/update');
  static bool _initialized = false;
  static VoidCallback? _onUpdateDownloaded;

  /// Initialize the update service and listen for flexible update completion.
  /// Call once from the app's main widget.
  static void initialize({VoidCallback? onUpdateDownloaded}) {
    if (_initialized) return;
    _initialized = true;
    _onUpdateDownloaded = onUpdateDownloaded;

    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onUpdateDownloaded') {
        _onUpdateDownloaded?.call();
      }
    });
  }

  /// Check for a Play Store update.
  /// [preferImmediate] forces a blocking full-screen update if available.
  static Future<void> checkForUpdate({bool preferImmediate = false}) async {
    try {
      await _channel.invokeMethod('checkForUpdate', {
        'preferImmediate': preferImmediate,
      });
    } catch (e) {
      debugPrint('InAppUpdateService.checkForUpdate error: $e');
    }
  }

  /// Complete a flexible update that was downloaded in the background.
  /// This restarts the app with the new version.
  static Future<void> completeFlexibleUpdate() async {
    try {
      await _channel.invokeMethod('completeFlexibleUpdate');
    } catch (e) {
      debugPrint('InAppUpdateService.completeFlexibleUpdate error: $e');
    }
  }
}
