import 'dart:io';
import 'dart:math';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Counts devices, not accounts.
///
/// The admin dashboard used to count only documents in `users`, which exist
/// once someone finishes sign-in. Google Play counts every device that
/// installs the app, including people who never sign in, so the two numbers
/// could never match. Each installation now keeps an anonymous random ID and
/// upserts `app_installs/{installId}` on launch; the dashboard counts those.
///
/// The Firestore rules for `app_installs` allow unauthenticated writes but
/// only with exactly these fields (see firestore.rules).
class InstallTrackingService {
  static const String _installIdKey = 'rewordium_install_id';
  static const String _registeredKey = 'rewordium_install_registered';
  static const String _collection = 'app_installs';

  static Future<String>? _installId;
  static String? _lastRecordedUid;
  static bool _recordedThisLaunch = false;

  static Future<String> _getInstallId() {
    return _installId ??= () async {
      final prefs = await SharedPreferences.getInstance();
      final existing = prefs.getString(_installIdKey);
      if (existing != null && existing.length >= 16) return existing;
      final random = Random.secure();
      final id = List.generate(24, (_) => random.nextInt(36).toRadixString(36)).join();
      await prefs.setString(_installIdKey, id);
      return id;
    }();
  }

  /// Records that this installation was opened. Cheap and safe to call more
  /// than once per launch: it writes at most once per launch, plus once more
  /// when a user signs in so the install is linked to the account.
  static Future<void> recordAppOpen() async {
    try {
      final uid = FirebaseAuth.instance.currentUser?.uid;
      if (_recordedThisLaunch && uid == _lastRecordedUid) return;
      final installId = await _getInstallId();
      final info = await PackageInfo.fromPlatform();
      final platform = kIsWeb
          ? 'web'
          : Platform.isAndroid
              ? 'android'
              : Platform.isIOS
                  ? 'ios'
                  : 'other';
      final ref = FirebaseFirestore.instance.collection(_collection).doc(installId);
      final data = <String, dynamic>{
        'platform': platform,
        'appVersion': info.version,
        'buildNumber': info.buildNumber,
        'lastSeenAt': FieldValue.serverTimestamp(),
      };
      if (uid != null) data['uid'] = uid;
      // Clients may not read app_installs, so remember locally whether this
      // installation's document has been created (to stamp firstSeenAt once).
      final prefs = await SharedPreferences.getInstance();
      final registered = prefs.getBool(_registeredKey) ?? false;
      if (!registered) {
        data['firstSeenAt'] = FieldValue.serverTimestamp();
      }
      await ref.set(data, SetOptions(merge: true));
      if (!registered) await prefs.setBool(_registeredKey, true);
      _recordedThisLaunch = true;
      _lastRecordedUid = uid;
    } catch (e) {
      debugPrint('InstallTrackingService.recordAppOpen error: $e');
    }
  }
}
