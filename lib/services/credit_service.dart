import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';

class CreditService {
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  static final CollectionReference _usersCollection =
      _firestore.collection('users');

  static Future<bool> consumeCredit(String uid) {
    try {
      _usersCollection.doc(uid).update({
        'credits': FieldValue.increment(-1),
      });
      debugPrint('Successfully consumed one credit for user $uid.');
      return Future.value(true);
    } catch (e) {
      debugPrint('Error consuming credit for user $uid: $e');
      return Future.value(false);
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