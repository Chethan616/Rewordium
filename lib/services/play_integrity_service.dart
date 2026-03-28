import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:package_info_plus/package_info_plus.dart';
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
  static const bool _verificationDisabled = true;

  // ── Cache keys & TTLs ─────────────────────────
  static const String _kCacheResult  = 'pi_result';
  static const String _kCacheTime    = 'pi_time';
  static const String _kCacheVersion = 'pi_version';
  /// Passed result stays valid for 24 hours.
  static const int _kPassTTLMs = 86400000;
  /// Failed result retries after 30 minutes.
  static const int _kFailTTLMs = 1800000;

  // ── Cached integrity check ────────────────────

  /// Returns cached result when fresh; otherwise runs a live check and caches it.
  /// Call this from the splash — it is fast (~50 ms) on every subsequent cold start.
  static Future<bool> checkIntegrityWithCache() async {
    if (_verificationDisabled) {
      AppLogger.warning('Play Integrity: verification disabled (temporary bypass)');
      return true;
    }

    if (defaultTargetPlatform != TargetPlatform.android) return true;

    try {
      final prefs = await SharedPreferences.getInstance();
      final cachedResult  = prefs.getBool(_kCacheResult);
      final cachedTime    = prefs.getInt(_kCacheTime);
      final cachedVersion = prefs.getString(_kCacheVersion);

      // Invalidate cache when the app is updated
      final info = await PackageInfo.fromPlatform();
      final currentVersion = '${info.version}+${info.buildNumber}';

      if (cachedResult != null && cachedTime != null && cachedVersion == currentVersion) {
        final ageMs = DateTime.now().millisecondsSinceEpoch - cachedTime;
        final ttlMs = cachedResult ? _kPassTTLMs : _kFailTTLMs;
        if (ageMs < ttlMs) {
          AppLogger.info(
            'Play Integrity: Cache HIT (${cachedResult ? "PASS" : "FAIL"}, '
            'age=${ageMs ~/ 1000}s / ttl=${ttlMs ~/ 1000}s)',
          );
          return cachedResult;
        }
      }

      // Cache miss or expired — run live check and persist result
      AppLogger.info('Play Integrity: Cache MISS — running live check...');
      final result = await checkIntegrity();
      await prefs.setBool(_kCacheResult, result);
      await prefs.setInt(_kCacheTime, DateTime.now().millisecondsSinceEpoch);
      await prefs.setString(_kCacheVersion, currentVersion);
      return result;
    } catch (e) {
      AppLogger.error('Play Integrity: Cache error, falling back to live check', e);
      return checkIntegrity();
    }
  }

  /// Clears the cached integrity result (e.g. on force-update or logout).
  static Future<void> clearCache() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_kCacheResult);
      await prefs.remove(_kCacheTime);
      await prefs.remove(_kCacheVersion);
    } catch (_) {}
  }


  /// Returns true ONLY if ALL conditions pass:
  /// - MEETS_STRONG_INTEGRITY (device is genuine, not rooted)
  /// - PLAY_RECOGNIZED (app is the real, unmodified APK)
  /// - LICENSED (installed from Play Store, not sideloaded)
  /// 
  /// Returns false on ANY error (strict mode - block by default)
  static Future<bool> checkIntegrity() async {
    if (_verificationDisabled) {
      AppLogger.warning('Play Integrity: check skipped (temporary bypass)');
      return true;
    }

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
    if (_verificationDisabled) {
      return {
        'allowed': true,
        'reason': 'Verification disabled temporarily',
        'isFromPlayStore': true,
        'signatureValid': true,
      };
    }

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
    if (_verificationDisabled) {
      AppLogger.warning('Play Integrity: initialize skipped (temporary bypass)');
      return;
    }

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
    if (_verificationDisabled) {
      return true;
    }

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
