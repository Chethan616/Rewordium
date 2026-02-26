import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../utils/app_logger.dart';

/// Service for Google Play Integrity API
/// Verifies app integrity and device authenticity
class PlayIntegrityService {
  static const MethodChannel _channel = MethodChannel('com.noxquill.rewordium/integrity');

  /// Check if the device and app meet integrity requirements
  /// Returns true if integrity check passes, false otherwise
  static Future<bool> checkIntegrity() async {
    try {
      // Only run on Android
      if (!defaultTargetPlatform.name.contains('android')) {
        AppLogger.info('Play Integrity: Not Android platform, skipping');
        return true;
      }

      final result = await _channel.invokeMethod<bool>('checkIntegrity');
      
      if (result == true) {
        AppLogger.info('Play Integrity: Check passed ✓');
        return true;
      } else {
        AppLogger.warning('Play Integrity: Check failed ✗');
        return false;
      }
    } catch (e) {
      AppLogger.error('Play Integrity: Error during check', e);
      // Return true in case of errors to not block app functionality
      // You can change this to false for stricter security
      return true;
    }
  }

  /// Get detailed integrity verdict
  /// Returns a map with integrity details or null on error
  static Future<Map<String, dynamic>?> getIntegrityVerdict() async {
    try {
      if (!defaultTargetPlatform.name.contains('android')) {
        return null;
      }

      final result = await _channel.invokeMethod<Map>('getIntegrityVerdict');
      
      if (result != null) {
        AppLogger.info('Play Integrity: Verdict received');
        return Map<String, dynamic>.from(result);
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
      if (!defaultTargetPlatform.name.contains('android')) {
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
      if (!defaultTargetPlatform.name.contains('android')) {
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
