import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';
import 'usage_analytics_service.dart';

class CreditService {
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  static final CollectionReference _usersCollection =
      _firestore.collection('users');

  static Future<bool> consumeCredit(String uid) async {
    try {
      await _usersCollection.doc(uid).update({
        'credits': FieldValue.increment(-1),
      });
      await UsageAnalyticsService.recordCreditUsage(
        uid: uid,
        creditsUsed: 1,
        source: 'credit_service',
      );
      debugPrint('Successfully consumed one credit for user $uid.');
      return true;
    } catch (e) {
      debugPrint('Error consuming credit for user $uid: $e');
      return false;
    }
  }

  static Future<int> getCredits(String uid) async {
    try {
      final doc = await _usersCollection.doc(uid).get(const GetOptions(source: Source.server));
      if (doc.exists && doc.data() != null) {
        final data = doc.data() as Map<String, dynamic>;
        return data['credits'] as int? ?? 0;
      }
      return 0;
    } catch (e) {
      debugPrint('Error getting credits for user $uid: $e');
      return 0;
    }
  }
}