import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../utils/app_logger.dart';

/// Service for Google Play Integrity API
/// Verifies app integrity and device authenticity via server-side verification
/// 
/// Flow:
/// 1. Flutter calls native Android code via MethodChannel
/// 2. Android requests integrity token from Google Play
/// 3. Android sends token to Firebase Cloud Function (verifyIntegrity)
/// 4. Cloud Function decodes token and checks verdicts
/// 5. Returns allow/block decision
class PlayIntegrityService {
  static const MethodChannel _channel = MethodChannel('com.noxquill.rewordium/integrity');

  /// Check if the device and app meet integrity requirements
  /// Returns true ONLY if ALL conditions pass:
  /// - MEETS_STRONG_INTEGRITY (device is genuine, not rooted)
  /// - PLAY_RECOGNIZED (app is the real, unmodified APK)
  /// - LICENSED (installed from Play Store, not sideloaded)
  /// 
  /// Returns false on ANY error (strict mode - block by default)
  static Future<bool> checkIntegrity() async {
    try {
      // Only run on Android
      if (defaultTargetPlatform != TargetPlatform.android) {
        AppLogger.info('Play Integrity: Not Android platform, skipping');
        return true;
      }

      AppLogger.info('Play Integrity: Starting verification...');
      
      final result = await _channel.invokeMethod<bool>('checkIntegrity');
      
      if (result == true) {
        AppLogger.info('Play Integrity: ✅ All checks passed (Strong + PlayRecognized + Licensed)');
        return true;
      } else {
        AppLogger.warning('Play Integrity: ⛔ Verification FAILED');
        return false;
      }
    } on PlatformException catch (e) {
      AppLogger.error('Play Integrity: Platform error - ${e.code}: ${e.message}', e);
      // STRICT: Return false on errors - block the app
      // A legitimate Play Store install should never hit this
      return false;
    } catch (e) {
      AppLogger.error('Play Integrity: Error during check', e);
      // STRICT: Return false on ANY error
      return false;
    }
  }

  /// Get detailed integrity verdict from backend
  /// Returns full verdict data including which checks passed/failed
  static Future<Map<String, dynamic>?> getIntegrityVerdict() async {
    try {
      if (defaultTargetPlatform != TargetPlatform.android) {
        return null;
      }

      final result = await _channel.invokeMethod<Map>('getIntegrityVerdict');
      
      if (result != null) {
        final verdict = Map<String, dynamic>.from(result);
        AppLogger.info('Play Integrity: Detailed verdict received');
        AppLogger.info('  allowed: ${verdict['allowed']}');
        AppLogger.info('  reason: ${verdict['reason']}');
        return verdict;
      }
      
      return null;
    } catch (e) {
      AppLogger.error('Play Integrity: Error getting verdict', e);
      return null;
    }
  }

  /// Initialize the Play Integrity API
  /// Should be called during app startup
  static Future<void> initialize() async {
    try {
      if (defaultTargetPlatform != TargetPlatform.android) {
        return;
      }

      await _channel.invokeMethod('initialize');
      AppLogger.init('Play Integrity API');
    } catch (e) {
      AppLogger.error('Play Integrity: Initialization error', e);
    }
  }

  /// Check if device meets basic play protect requirements
  static Future<bool> isDeviceSecure() async {
    try {
      if (defaultTargetPlatform != TargetPlatform.android) {
        return true;
      }

      final result = await _channel.invokeMethod<bool>('isDeviceSecure');
      return result ?? false;
    } catch (e) {
      AppLogger.error('Play Integrity: Error checking device security', e);
      return false;
    }
  }
}
