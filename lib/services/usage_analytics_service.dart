import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';

class UsageAnalyticsService {
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  static final FirebaseAuth _auth = FirebaseAuth.instance;

  static String _dateKey(DateTime dt) {
    final m = dt.month.toString().padLeft(2, '0');
    final d = dt.day.toString().padLeft(2, '0');
    return '${dt.year}-$m-$d';
  }

  static String _monthKey(DateTime dt) {
    final m = dt.month.toString().padLeft(2, '0');
    return '${dt.year}-$m';
  }

  static Future<void> touchUserActivity({String? uid}) async {
    final userId = uid ?? _auth.currentUser?.uid;
    if (userId == null) return;

    try {
      await _firestore.collection('users').doc(userId).set({
        'lastActiveAt': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));
    } catch (e) {
      debugPrint('UsageAnalyticsService.touchUserActivity error: $e');
    }
  }

  static Future<void> recordApiCall({
    required String feature,
    required String provider,
    required bool success,
    String? errorType,
    int creditsUsed = 0,
  }) async {
    final user = _auth.currentUser;
    if (user == null) return;

    final now = DateTime.now().toUtc();
    final dateKey = _dateKey(now);
    final monthKey = _monthKey(now);

    try {
      final batch = _firestore.batch();

      final eventRef = _firestore.collection('usage_events').doc();
      batch.set(eventRef, {
        'uid': user.uid,
        'email': user.email,
        'feature': feature,
        'provider': provider,
        'success': success,
        'errorType': errorType,
        'creditsUsed': creditsUsed,
        'createdAt': FieldValue.serverTimestamp(),
      });

      final userStatsRef = _firestore.collection('user_usage_stats').doc(user.uid);
      batch.set(
        userStatsRef,
        {
          'uid': user.uid,
          'email': user.email,
          'displayName': user.displayName,
          'totalApiCalls': FieldValue.increment(1),
          'successfulApiCalls': FieldValue.increment(success ? 1 : 0),
          'failedApiCalls': FieldValue.increment(success ? 0 : 1),
          'totalCreditsUsed': FieldValue.increment(creditsUsed),
          'lastCalledAt': FieldValue.serverTimestamp(),
          'monthlyApiCalls.$monthKey': FieldValue.increment(1),
          'monthlyCreditsUsed.$monthKey': FieldValue.increment(creditsUsed),
          'dailyApiCalls.$dateKey': FieldValue.increment(1),
          'dailyCreditsUsed.$dateKey': FieldValue.increment(creditsUsed),
        },
        SetOptions(merge: true),
      );

      final globalRef = _firestore.collection('analytics_global').doc('usage_counters');
      batch.set(
        globalRef,
        {
          'totalApiCalls': FieldValue.increment(1),
          'totalCreditsUsed': FieldValue.increment(creditsUsed),
          'monthlyApiCalls.$monthKey': FieldValue.increment(1),
          'monthlyCreditsUsed.$monthKey': FieldValue.increment(creditsUsed),
          'dailyApiCalls.$dateKey': FieldValue.increment(1),
          'dailyCreditsUsed.$dateKey': FieldValue.increment(creditsUsed),
          'updatedAt': FieldValue.serverTimestamp(),
        },
        SetOptions(merge: true),
      );

      final userDocRef = _firestore.collection('users').doc(user.uid);
      batch.set(
        userDocRef,
        {
          'lastActiveAt': FieldValue.serverTimestamp(),
        },
        SetOptions(merge: true),
      );

      await batch.commit();
    } catch (e) {
      debugPrint('UsageAnalyticsService.recordApiCall error: $e');
    }
  }

  static Future<void> recordCreditUsage({
    required String uid,
    int creditsUsed = 1,
    String source = 'unknown',
  }) async {
    if (creditsUsed <= 0) return;

    final now = DateTime.now().toUtc();
    final dateKey = _dateKey(now);
    final monthKey = _monthKey(now);

    try {
      final batch = _firestore.batch();

      final eventRef = _firestore.collection('credit_usage_events').doc();
      batch.set(eventRef, {
        'uid': uid,
        'creditsUsed': creditsUsed,
        'source': source,
        'createdAt': FieldValue.serverTimestamp(),
      });

      final userStatsRef = _firestore.collection('user_usage_stats').doc(uid);
      batch.set(
        userStatsRef,
        {
          'uid': uid,
          'totalCreditsUsed': FieldValue.increment(creditsUsed),
          'monthlyCreditsUsed.$monthKey': FieldValue.increment(creditsUsed),
          'dailyCreditsUsed.$dateKey': FieldValue.increment(creditsUsed),
          'lastCreditEventAt': FieldValue.serverTimestamp(),
        },
        SetOptions(merge: true),
      );

      final globalRef = _firestore.collection('analytics_global').doc('usage_counters');
      batch.set(
        globalRef,
        {
          'totalCreditsUsed': FieldValue.increment(creditsUsed),
          'monthlyCreditsUsed.$monthKey': FieldValue.increment(creditsUsed),
          'dailyCreditsUsed.$dateKey': FieldValue.increment(creditsUsed),
          'updatedAt': FieldValue.serverTimestamp(),
        },
        SetOptions(merge: true),
      );

      final userDocRef = _firestore.collection('users').doc(uid);
      batch.set(
        userDocRef,
        {
          'lastActiveAt': FieldValue.serverTimestamp(),
        },
        SetOptions(merge: true),
      );

      await batch.commit();
    } catch (e) {
      debugPrint('UsageAnalyticsService.recordCreditUsage error: $e');
    }
  }
}
