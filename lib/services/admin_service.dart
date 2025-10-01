import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import '../models/user_model.dart';
import 'fcm_oauth2_service.dart';

class AdminService {
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  static final FirebaseAuth _auth = FirebaseAuth.instance;

  // Initialize service (production ready)
  static void init() {
    debugPrint('AdminService initialized for production use');
  }

  // Admin credentials - this would be in a secure config in production
  static const String adminEmail = 'chethankrishna2022@gmail.com';
  static const String adminPassword = 'sendpushnotis';

  // FCM Server Key - REPLACE THIS WITH YOUR ACTUAL SERVER KEY
  static const String fcmServerKey =
      'AAAA8gB9keI:APA91bH8vXxTdJoYiRz4QNXGkSuAEhJVQvBaQFzqKl_5w6X3YuNpM8DhF2RcE4vL9sT1nP7kG6wJ0oI5uE8rY3qA2sZ9xC7nB1mV4fH6gK8lP0qW9eR3tY5uI7oA2sD4fG6h'; // Replace with your actual key

  // Check if current user is admin
  static bool isAdmin() {
    final user = _auth.currentUser;
    return user?.email == adminEmail;
  }

  // Verify admin password
  static bool verifyAdminPassword(String password) {
    return password == adminPassword;
  }

  // Get all users with pagination
  static Future<List<UserModel>> getAllUsers({
    int limit = 50,
    DocumentSnapshot? lastDoc,
  }) async {
    try {
      debugPrint('Fetching all users from Firestore...');

      Query query = _firestore.collection('users').limit(limit);

      if (lastDoc != null) {
        query = query.startAfterDocument(lastDoc);
      }

      final snapshot = await query.get();
      debugPrint('Found ${snapshot.docs.length} users in Firestore');

      final users = snapshot.docs
          .map((doc) {
            try {
              return UserModel.fromDocumentSnapshot(doc.id, doc.data());
            } catch (e) {
              debugPrint('Error parsing user ${doc.id}: $e');
              return null;
            }
          })
          .where((user) => user != null)
          .cast<UserModel>()
          .toList();

      debugPrint('Successfully parsed ${users.length} users');
      return users;
    } catch (e) {
      debugPrint('Error getting all users: $e');
      return [];
    }
  }

  // Get pro users only
  static Future<List<UserModel>> getProUsers() async {
    try {
      debugPrint('Fetching pro users from Firestore...');
      final snapshot = await _firestore
          .collection('users')
          .where('isPro', isEqualTo: true)
          .get();

      debugPrint('Found ${snapshot.docs.length} pro users');
      return snapshot.docs
          .map((doc) => UserModel.fromDocumentSnapshot(doc.id, doc.data()))
          .toList();
    } catch (e) {
      debugPrint('Error getting pro users: $e');
      return [];
    }
  }

  // Get free users only
  static Future<List<UserModel>> getFreeUsers() async {
    try {
      debugPrint('Fetching free users from Firestore...');
      final snapshot = await _firestore
          .collection('users')
          .where('isPro', isEqualTo: false)
          .get();

      debugPrint('Found ${snapshot.docs.length} free users');
      return snapshot.docs
          .map((doc) => UserModel.fromDocumentSnapshot(doc.id, doc.data()))
          .toList();
    } catch (e) {
      debugPrint('Error getting free users: $e');
      return [];
    }
  }

  // Search users by name or email
  static Future<List<UserModel>> searchUsers(String query) async {
    try {
      debugPrint('Searching users with query: $query');
      final normalizedQuery = query.toLowerCase().trim();

      // Since Firestore doesn't support full-text search natively,
      // we'll need to implement this differently for better performance
      // For now, we'll use a compound query approach

      final List<UserModel> results = [];

      // Search by email prefix
      final emailQuery = await _firestore
          .collection('users')
          .where('email', isGreaterThanOrEqualTo: normalizedQuery)
          .where('email', isLessThan: '${normalizedQuery}z')
          .limit(20)
          .get();

      results.addAll(emailQuery.docs
          .map((doc) => UserModel.fromDocumentSnapshot(doc.id, doc.data()))
          .toList());

      // Search by name prefix
      final nameQuery = await _firestore
          .collection('users')
          .where('name', isGreaterThanOrEqualTo: normalizedQuery)
          .where('name', isLessThan: '${normalizedQuery}z')
          .limit(20)
          .get();

      results.addAll(nameQuery.docs
          .map((doc) => UserModel.fromDocumentSnapshot(doc.id, doc.data()))
          .toList());

      // Remove duplicates and filter further
      final Map<String, UserModel> uniqueUsers = {};
      for (final user in results) {
        if (user.name.toLowerCase().contains(normalizedQuery) ||
            user.email.toLowerCase().contains(normalizedQuery)) {
          uniqueUsers[user.uid] = user;
        }
      }

      final finalResults = uniqueUsers.values.toList();
      debugPrint('Search found ${finalResults.length} matching users');
      return finalResults;
    } catch (e) {
      debugPrint('Error searching users: $e');
      return [];
    }
  }

  // Get user statistics (production ready - direct Firestore)
  static Future<Map<String, int>> getUserStats() async {
    try {
      debugPrint('Fetching user statistics from Firestore...');

      if (!isAdmin()) {
        debugPrint('Access denied: User is not admin');
        return {'total': 0, 'pro': 0, 'free': 0};
      }

      // Get all users count
      final allUsersSnapshot = await _firestore.collection('users').get();
      final totalUsers = allUsersSnapshot.size;

      // Get pro users count
      final proUsersSnapshot = await _firestore
          .collection('users')
          .where('isPro', isEqualTo: true)
          .get();
      final proUsers = proUsersSnapshot.size;

      // Calculate free users
      final freeUsers = totalUsers - proUsers;

      debugPrint(
          'User statistics: Total: $totalUsers, Pro: $proUsers, Free: $freeUsers');

      return {
        'total': totalUsers,
        'pro': proUsers,
        'free': freeUsers,
      };
    } catch (e) {
      debugPrint('Error getting user stats: $e');
      return {'total': 0, 'pro': 0, 'free': 0};
    }
  }

  // Send FCM notification using OAuth2 HTTP v1 API (production ready)
  static Future<bool> _sendFCMNotification({
    String? token,
    String? topic,
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    try {
      debugPrint('Sending FCM notification via OAuth2 HTTP v1 API...');

      if (!isAdmin()) {
        debugPrint('Access denied: User is not admin');
        return false;
      }

      // Use the new OAuth2 FCM service
      final success = await FCMOAuth2Service.sendNotification(
        token: token,
        topic: topic,
        title: title,
        body: body,
        data: data,
      );

      if (success) {
        debugPrint('✅ FCM notification sent successfully via OAuth2');
        return true;
      } else {
        debugPrint('❌ OAuth2 FCM failed, trying legacy fallback...');
        // Try legacy FCM API as fallback
        return await _sendFCMLegacy(
          token: token,
          topic: topic,
          title: title,
          body: body,
          data: data,
        );
      }
    } catch (e) {
      debugPrint('Error sending FCM notification: $e');

      // Try legacy FCM API as fallback
      return await _sendFCMLegacy(
        token: token,
        topic: topic,
        title: title,
        body: body,
        data: data,
      );
    }
  }

  // Fallback to legacy FCM API
  static Future<bool> _sendFCMLegacy({
    String? token,
    String? topic,
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    try {
      debugPrint('Using legacy FCM API...');

      // Prepare legacy FCM payload
      final Map<String, dynamic> payload = {
        'notification': {
          'title': title,
          'body': body,
        },
        'data': data ?? {},
      };

      // Add topic or token
      if (topic != null) {
        payload['to'] = '/topics/$topic';
      } else if (token != null) {
        payload['to'] = token;
      }

      const String legacyFcmUrl = 'https://fcm.googleapis.com/fcm/send';

      final response = await http.post(
        Uri.parse(legacyFcmUrl),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'key=$fcmServerKey',
        },
        body: json.encode(payload),
      );

      if (response.statusCode == 200) {
        final responseData = json.decode(response.body);
        if (responseData['success'] != null && responseData['success'] > 0) {
          debugPrint('Legacy FCM notification sent successfully');
          return true;
        }
      }

      debugPrint(
          'Legacy FCM failed: ${response.statusCode} - ${response.body}');
      return false;
    } catch (e) {
      debugPrint('Legacy FCM error: $e');
      return false;
    }
  }

  // Send notification to all users
  static Future<bool> sendNotificationToAllUsers({
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    try {
      debugPrint('Sending notification to all users:');
      debugPrint('Title: $title');
      debugPrint('Body: $body');

      // Send via FCM topic
      final fcmSuccess = await _sendFCMNotification(
        topic: 'all_users',
        title: title,
        body: body,
        data: data,
      );

      // Store notification in Firestore for logging
      await _firestore.collection('notifications').add({
        'title': title,
        'body': body,
        'data': data ?? {},
        'sentAt': FieldValue.serverTimestamp(),
        'sentBy': _auth.currentUser?.email,
        'target': 'all_users',
        'type': 'broadcast',
        'fcmSuccess': fcmSuccess,
      });

      return fcmSuccess;
    } catch (e) {
      debugPrint('Error sending notification to all users: $e');
      return false;
    }
  }

  // Send notification to pro users only
  static Future<bool> sendNotificationToProUsers({
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    try {
      debugPrint('Sending notification to pro users:');
      debugPrint('Title: $title');
      debugPrint('Body: $body');

      // Send via FCM topic
      final fcmSuccess = await _sendFCMNotification(
        topic: 'pro_users',
        title: title,
        body: body,
        data: data,
      );

      await _firestore.collection('notifications').add({
        'title': title,
        'body': body,
        'data': data ?? {},
        'sentAt': FieldValue.serverTimestamp(),
        'sentBy': _auth.currentUser?.email,
        'target': 'pro_users',
        'type': 'segment',
        'fcmSuccess': fcmSuccess,
      });

      return fcmSuccess;
    } catch (e) {
      debugPrint('Error sending notification to pro users: $e');
      return false;
    }
  }

  // Send notification to free users only
  static Future<bool> sendNotificationToFreeUsers({
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    try {
      debugPrint('Sending notification to free users:');
      debugPrint('Title: $title');
      debugPrint('Body: $body');

      // Send via FCM topic
      final fcmSuccess = await _sendFCMNotification(
        topic: 'free_users',
        title: title,
        body: body,
        data: data,
      );

      await _firestore.collection('notifications').add({
        'title': title,
        'body': body,
        'data': data ?? {},
        'sentAt': FieldValue.serverTimestamp(),
        'sentBy': _auth.currentUser?.email,
        'target': 'free_users',
        'type': 'segment',
        'fcmSuccess': fcmSuccess,
      });

      return fcmSuccess;
    } catch (e) {
      debugPrint('Error sending notification to free users: $e');
      return false;
    }
  }

  // Send notification to specific user
  static Future<bool> sendNotificationToUser({
    required String userId,
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    try {
      debugPrint('Sending notification to user: $userId');
      debugPrint('Title: $title');
      debugPrint('Body: $body');

      // Get user's FCM token
      final userDoc = await _firestore.collection('users').doc(userId).get();
      final userData = userDoc.data();
      final fcmToken = userData?['fcmToken'] as String?;

      bool fcmSuccess = false;
      if (fcmToken != null && fcmToken.isNotEmpty) {
        fcmSuccess = await _sendFCMNotification(
          token: fcmToken,
          title: title,
          body: body,
          data: data,
        );
      } else {
        debugPrint('No FCM token found for user $userId');
      }

      await _firestore.collection('notifications').add({
        'title': title,
        'body': body,
        'data': data ?? {},
        'sentAt': FieldValue.serverTimestamp(),
        'sentBy': _auth.currentUser?.email,
        'target': userId,
        'type': 'individual',
        'fcmSuccess': fcmSuccess,
        'fcmToken': fcmToken != null ? 'present' : 'missing',
      });

      return fcmSuccess;
    } catch (e) {
      debugPrint('Error sending notification to user: $e');
      return false;
    }
  }

  // Get notification history
  static Future<List<Map<String, dynamic>>> getNotificationHistory({
    int limit = 50,
  }) async {
    try {
      debugPrint('Fetching notification history...');
      final snapshot = await _firestore
          .collection('notifications')
          .orderBy('sentAt', descending: true)
          .limit(limit)
          .get();

      final history = snapshot.docs.map((doc) {
        final data = doc.data();
        data['id'] = doc.id;
        return data;
      }).toList();

      debugPrint('Found ${history.length} notifications in history');
      return history;
    } catch (e) {
      debugPrint('Error getting notification history: $e');
      return [];
    }
  }

  // Update user pro status
  static Future<bool> updateUserProStatus(String userId, bool isPro) async {
    try {
      debugPrint('Updating user $userId pro status to: $isPro');
      await _firestore.collection('users').doc(userId).update({
        'isPro': isPro,
        'updatedAt': FieldValue.serverTimestamp(),
        'updatedBy': _auth.currentUser?.email,
      });
      debugPrint('Successfully updated user pro status');
      return true;
    } catch (e) {
      debugPrint('Error updating user pro status: $e');
      return false;
    }
  }

  // Update user credits
  static Future<bool> updateUserCredits(String userId, int credits) async {
    try {
      debugPrint('Updating user $userId credits to: $credits');
      await _firestore.collection('users').doc(userId).update({
        'credits': credits,
        'updatedAt': FieldValue.serverTimestamp(),
        'updatedBy': _auth.currentUser?.email,
      });
      debugPrint('Successfully updated user credits');
      return true;
    } catch (e) {
      debugPrint('Error updating user credits: $e');
      return false;
    }
  }

  // Ban/unban user
  static Future<bool> updateUserStatus(String userId, bool isActive) async {
    try {
      debugPrint(
          'Updating user $userId status to: ${isActive ? 'active' : 'inactive'}');
      await _firestore.collection('users').doc(userId).update({
        'isActive': isActive,
        'updatedAt': FieldValue.serverTimestamp(),
        'updatedBy': _auth.currentUser?.email,
      });
      debugPrint('Successfully updated user status');
      return true;
    } catch (e) {
      debugPrint('Error updating user status: $e');
      return false;
    }
  }
}
