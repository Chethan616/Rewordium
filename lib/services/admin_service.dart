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
  static const List<String> adminEmails = [
    'chethankrishna2022@gmail.com',
    'rupakbabu1994@gmail.com',
  ];
  static const String adminPassword = 'sendpushnotis';

  // FCM Server Key - REPLACE THIS WITH YOUR ACTUAL SERVER KEY
  static const String fcmServerKey =
      'AAAA8gB9keI:APA91bH8vXxTdJoYiRz4QNXGkSuAEhJVQvBaQFzqKl_5w6X3YuNpM8DhF2RcE4vL9sT1nP7kG6wJ0oI5uE8rY3qA2sZ9xC7nB1mV4fH6gK8lP0qW9eR3tY5uI7oA2sD4fG6h'; // Replace with your actual key

  // Check if current user is admin
  static bool isAdmin() {
    final user = _auth.currentUser;
    return user?.email != null && adminEmails.contains(user!.email);
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
        return {'total': 0, 'pro': 0, 'free': 0, 'activeNow': 0, 'dau': 0};
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

      final now = DateTime.now();
      final activeSince = Timestamp.fromDate(
        now.subtract(const Duration(minutes: 15)),
      );
      final startOfDay = Timestamp.fromDate(
        DateTime(now.year, now.month, now.day),
      );

      final activeNowSnapshot = await _firestore
          .collection('users')
          .where('lastActiveAt', isGreaterThanOrEqualTo: activeSince)
          .get();

      final dauSnapshot = await _firestore
          .collection('users')
          .where('lastActiveAt', isGreaterThanOrEqualTo: startOfDay)
          .get();

      final activeNow = activeNowSnapshot.size;
      final dau = dauSnapshot.size;

      debugPrint(
          'User statistics: Total: $totalUsers, Pro: $proUsers, Free: $freeUsers, ActiveNow: $activeNow, DAU: $dau');

      return {
        'total': totalUsers,
        'pro': proUsers,
        'free': freeUsers,
        'activeNow': activeNow,
        'dau': dau,
      };
    } catch (e) {
      debugPrint('Error getting user stats: $e');
      return {'total': 0, 'pro': 0, 'free': 0, 'activeNow': 0, 'dau': 0};
    }
  }

  static Future<Map<String, dynamic>> getApiUsageStats() async {
    try {
      if (!isAdmin()) {
        return {
          'totalApiCalls': 0,
          'thisMonthApiCalls': 0,
          'thisMonthCreditsUsed': 0,
          'dailyCreditUsage': <Map<String, dynamic>>[],
          'dailyApiUsage': <Map<String, dynamic>>[],
          'leaderboard': <Map<String, dynamic>>[],
          'trackingStartedAt': null,
          'activeNow': 0,
          'dau': 0,
          'hasHistoricalData': false,
        };
      }

      final now = DateTime.now();
      final monthKey =
          '${now.year}-${now.month.toString().padLeft(2, '0')}';

      final globalDoc = await _firestore
          .collection('analytics_global')
          .doc('usage_counters')
          .get();
      final globalData = globalDoc.data() ?? <String, dynamic>{};

      final monthlyApiCalls =
          _toIntMap(globalData['monthlyApiCalls'] as Map<String, dynamic>?);
      final monthlyCreditsUsed = _toIntMap(
          globalData['monthlyCreditsUsed'] as Map<String, dynamic>?);
      final dailyCreditsUsed =
          _toIntMap(globalData['dailyCreditsUsed'] as Map<String, dynamic>?);
      final dailyApiCalls =
          _toIntMap(globalData['dailyApiCalls'] as Map<String, dynamic>?);

      final thisMonthApiCalls = monthlyApiCalls[monthKey] ?? 0;
      final thisMonthCreditsUsed = monthlyCreditsUsed[monthKey] ?? 0;

      final dailyCreditUsageList = dailyCreditsUsed.entries
          .where((entry) => entry.key.startsWith(monthKey))
          .map((entry) => {
                'date': entry.key,
                'credits': entry.value,
              })
          .toList()
        ..sort((a, b) =>
            (a['date'] as String).compareTo(b['date'] as String));

      final dailyApiUsageList = dailyApiCalls.entries
          .where((entry) => entry.key.startsWith(monthKey))
          .map((entry) => {
                'date': entry.key,
                'calls': entry.value,
              })
          .toList()
        ..sort((a, b) =>
            (a['date'] as String).compareTo(b['date'] as String));

      final leaderboardSnapshot = await _firestore
          .collection('user_usage_stats')
          .orderBy('totalApiCalls', descending: true)
          .limit(20)
          .get();

      final leaderboard = leaderboardSnapshot.docs.map((doc) {
        final data = doc.data();
        return {
          'uid': doc.id,
          'name': data['displayName'] ?? data['email'] ?? 'Unknown User',
          'email': data['email'] ?? '',
          'totalApiCalls': (data['totalApiCalls'] as num?)?.toInt() ?? 0,
          'totalCreditsUsed': (data['totalCreditsUsed'] as num?)?.toInt() ?? 0,
          'successRate': _calculateSuccessRate(
            successful: (data['successfulApiCalls'] as num?)?.toInt() ?? 0,
            failed: (data['failedApiCalls'] as num?)?.toInt() ?? 0,
          ),
        };
      }).toList();

      Timestamp? trackingStartedAt;
      bool hasHistoricalData = false;
      final firstEventSnapshot = await _firestore
          .collection('usage_events')
          .orderBy('createdAt')
          .limit(1)
          .get();
      if (firstEventSnapshot.docs.isNotEmpty) {
        trackingStartedAt =
            firstEventSnapshot.docs.first.data()['createdAt'] as Timestamp?;
        hasHistoricalData = trackingStartedAt != null;
      }

      final activeSince = Timestamp.fromDate(
        now.subtract(const Duration(minutes: 15)),
      );
      final startOfDay = Timestamp.fromDate(
        DateTime(now.year, now.month, now.day),
      );

      final activeNowSnapshot = await _firestore
          .collection('users')
          .where('lastActiveAt', isGreaterThanOrEqualTo: activeSince)
          .get();
      final dauSnapshot = await _firestore
          .collection('users')
          .where('lastActiveAt', isGreaterThanOrEqualTo: startOfDay)
          .get();

      return {
        'totalApiCalls': (globalData['totalApiCalls'] as num?)?.toInt() ?? 0,
        'thisMonthApiCalls': thisMonthApiCalls,
        'thisMonthCreditsUsed': thisMonthCreditsUsed,
        'dailyCreditUsage': dailyCreditUsageList,
        'dailyApiUsage': dailyApiUsageList,
        'leaderboard': leaderboard,
        'trackingStartedAt': trackingStartedAt,
        'activeNow': activeNowSnapshot.size,
        'dau': dauSnapshot.size,
        'hasHistoricalData': hasHistoricalData,
      };
    } catch (e) {
      debugPrint('Error getting API usage stats: $e');
      return {
        'totalApiCalls': 0,
        'thisMonthApiCalls': 0,
        'thisMonthCreditsUsed': 0,
        'dailyCreditUsage': <Map<String, dynamic>>[],
        'dailyApiUsage': <Map<String, dynamic>>[],
        'leaderboard': <Map<String, dynamic>>[],
        'trackingStartedAt': null,
        'activeNow': 0,
        'dau': 0,
        'hasHistoricalData': false,
      };
    }
  }

  static Map<String, int> _toIntMap(Map<String, dynamic>? raw) {
    if (raw == null) return <String, int>{};
    return raw.map((key, value) => MapEntry(key, (value as num).toInt()));
  }

  static double _calculateSuccessRate({
    required int successful,
    required int failed,
  }) {
    final total = successful + failed;
    if (total == 0) return 0;
    return (successful / total) * 100;
  }

  // Get revenue statistics (from subscription data)
  // Optional 'from' baseline will only count subscriptions upgraded after this time
  static Future<Map<String, dynamic>> getRevenueStats({DateTime? from}) async {
    try {
      debugPrint('Fetching revenue statistics from Firestore...');

      if (!isAdmin()) {
        debugPrint('Access denied: User is not admin');
        return _emptyRevenueStats();
      }

      // Get all pro users with subscription data
      final proUsersSnapshot = await _firestore
          .collection('users')
          .where('isPro', isEqualTo: true)
          .get();

      double totalRevenue = 0;
      double monthlyRevenue = 0;
      double yearlyRevenue = 0;
      int monthlySubscribers = 0;
      int yearlySubscribers = 0;
      int onetimeSubscribers = 0;
      int activeSubscriptions = 0;
      int expiredSubscriptions = 0;
      int filteredProCount = 0;
      
      final now = DateTime.now();
      final startOfMonth = DateTime(now.year, now.month, 1);
      final startOfLastMonth = DateTime(now.year, now.month - 1, 1);
      double thisMonthRevenue = 0;
      double lastMonthRevenue = 0;
      int newSubscribersThisMonth = 0;

      // Pricing (should match Google Play Console prices)
      const double monthlyPrice = 2.98;
      const double yearlyPrice = 19.00;
      const double onetimePrice = 49.99;

      for (final doc in proUsersSnapshot.docs) {
        final data = doc.data();
        final subscription = data['subscription'] as Map<String, dynamic>?;
        final planType = data['planType'] as String? ?? subscription?['planType'] as String?;
        final upgradedAt = data['upgradedAt'] as Timestamp?;
        final expiryDate = subscription?['expiryDate'] as Timestamp?;

        // If a baseline 'from' is provided, only count subscriptions after this time
        if (from != null) {
          if (upgradedAt == null || upgradedAt.toDate().isBefore(from)) {
            // Skip counting this user towards revenue and subscriber totals
            // Still allow other non-revenue aggregates if needed in future
            continue;
          }
        }

        filteredProCount++;
        
        // Count by plan type
        if (planType == 'monthly') {
          monthlySubscribers++;
          monthlyRevenue += monthlyPrice;
        } else if (planType == 'yearly') {
          yearlySubscribers++;
          yearlyRevenue += yearlyPrice;
        } else if (planType == 'onetime' || planType == 'lifetime') {
          onetimeSubscribers++;
          totalRevenue += onetimePrice;
        }

        // Check if subscription is active
        if (expiryDate != null) {
          if (expiryDate.toDate().isAfter(now)) {
            activeSubscriptions++;
          } else {
            expiredSubscriptions++;
          }
        } else {
          // No expiry means lifetime or active
          activeSubscriptions++;
        }

        // Calculate this month's new revenue
        if (upgradedAt != null) {
          final upgradeDate = upgradedAt.toDate();
          if (upgradeDate.isAfter(startOfMonth)) {
            newSubscribersThisMonth++;
            if (planType == 'monthly') {
              thisMonthRevenue += monthlyPrice;
            } else if (planType == 'yearly') {
              thisMonthRevenue += yearlyPrice;
            } else if (planType == 'onetime' || planType == 'lifetime') {
              thisMonthRevenue += onetimePrice;
            }
          } else if (upgradeDate.isAfter(startOfLastMonth) && upgradeDate.isBefore(startOfMonth)) {
            if (planType == 'monthly') {
              lastMonthRevenue += monthlyPrice;
            } else if (planType == 'yearly') {
              lastMonthRevenue += yearlyPrice;
            } else if (planType == 'onetime' || planType == 'lifetime') {
              lastMonthRevenue += onetimePrice;
            }
          }
        }
      }

      // Calculate total revenue (recurring). If 'from' is provided, totals represent revenue since 'from'
      totalRevenue += monthlyRevenue + yearlyRevenue;

      // Estimate MRR (Monthly Recurring Revenue)
      final mrr = (monthlySubscribers * monthlyPrice) + (yearlySubscribers * (yearlyPrice / 12));

      // Calculate revenue growth
      final revenueGrowth = lastMonthRevenue > 0 
          ? ((thisMonthRevenue - lastMonthRevenue) / lastMonthRevenue * 100)
          : (thisMonthRevenue > 0 ? 100.0 : 0.0);

      debugPrint('Revenue stats: Total: \$$totalRevenue, MRR: \$$mrr, This Month: \$$thisMonthRevenue');

      return {
        'totalRevenue': totalRevenue,
        'monthlyRevenue': monthlyRevenue,
        'yearlyRevenue': yearlyRevenue,
        'mrr': mrr,
        'thisMonthRevenue': thisMonthRevenue,
        'lastMonthRevenue': lastMonthRevenue,
        'revenueGrowth': revenueGrowth,
        'monthlySubscribers': monthlySubscribers,
        'yearlySubscribers': yearlySubscribers,
        'onetimeSubscribers': onetimeSubscribers,
        'activeSubscriptions': activeSubscriptions,
        'expiredSubscriptions': expiredSubscriptions,
        'newSubscribersThisMonth': newSubscribersThisMonth,
        // Conversion rate based on filtered pro count when baseline is provided
        'conversionRate': filteredProCount > 0 
        ? (filteredProCount / (await _firestore.collection('users').get()).size * 100)
        : 0.0,
      };
    } catch (e) {
      debugPrint('Error getting revenue stats: $e');
      return _emptyRevenueStats();
    }
  }

  static Map<String, dynamic> _emptyRevenueStats() {
    return {
      'totalRevenue': 0.0,
      'monthlyRevenue': 0.0,
      'yearlyRevenue': 0.0,
      'mrr': 0.0,
      'thisMonthRevenue': 0.0,
      'lastMonthRevenue': 0.0,
      'revenueGrowth': 0.0,
      'monthlySubscribers': 0,
      'yearlySubscribers': 0,
      'onetimeSubscribers': 0,
      'activeSubscriptions': 0,
      'expiredSubscriptions': 0,
      'newSubscribersThisMonth': 0,
      'conversionRate': 0.0,
    };
  }

  // Get recent subscription transactions
  // Optional 'from' baseline will only include transactions after this time
  static Future<List<Map<String, dynamic>>> getRecentTransactions({int limit = 20, DateTime? from}) async {
    try {
      if (!isAdmin()) return [];

      final snapshot = await _firestore
          .collection('users')
          .where('isPro', isEqualTo: true)
          .orderBy('upgradedAt', descending: true)
          .limit(limit)
          .get();

      final docs = snapshot.docs.where((doc) {
        if (from == null) return true;
        final ts = doc.data()['upgradedAt'] as Timestamp?;
        return ts != null && ts.toDate().isAfter(from);
      }).toList();

      return docs.map((doc) {
        final data = doc.data();
        return {
          'userId': doc.id,
          'userName': data['name'] ?? 'Unknown',
          'email': data['email'] ?? '',
          'planType': data['planType'] ?? data['subscription']?['planType'] ?? 'unknown',
          'upgradedAt': data['upgradedAt'],
          'status': data['subscription']?['status'] ?? 'active',
        };
      }).toList();
    } catch (e) {
      debugPrint('Error getting recent transactions: $e');
      return [];
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

  // Get users who subscribed to news/promotions
  static Future<List<UserModel>> getNewsSubscribers() async {
    try {
      debugPrint('Fetching news subscribers from Firestore...');
      final snapshot = await _firestore
          .collection('users')
          .where('subscribedToNews', isEqualTo: true)
          .get();

      debugPrint('Found ${snapshot.docs.length} news subscribers');
      return snapshot.docs
          .map((doc) => UserModel.fromDocumentSnapshot(doc.id, doc.data()))
          .toList();
    } catch (e) {
      debugPrint('Error getting news subscribers: $e');
      return [];
    }
  }

  // Get filtered users with sorting
  static Future<List<UserModel>> getFilteredUsers({
    String? filterBy, // 'all', 'pro', 'free', 'news_subscribers'
    String? sortBy, // 'name', 'email', 'createdAt', 'credits'
    bool ascending = true,
  }) async {
    try {
      debugPrint('Fetching filtered users: filter=$filterBy, sort=$sortBy');

      Query query = _firestore.collection('users');

      // Apply filter
      if (filterBy == 'pro') {
        query = query.where('isPro', isEqualTo: true);
      } else if (filterBy == 'free') {
        query = query.where('isPro', isEqualTo: false);
      } else if (filterBy == 'news_subscribers') {
        query = query.where('subscribedToNews', isEqualTo: true);
      }

      // Apply sorting (Firestore requires orderBy for filtered queries)
      if (sortBy != null && sortBy.isNotEmpty) {
        query = query.orderBy(sortBy, descending: !ascending);
      }

      final snapshot = await query.get();
      debugPrint('Found ${snapshot.docs.length} users matching filter');

      return snapshot.docs
          .map((doc) => UserModel.fromDocumentSnapshot(doc.id, doc.data()))
          .toList();
    } catch (e) {
      debugPrint('Error getting filtered users: $e');
      // Fallback: get all users and filter/sort in memory
      return await _getFilteredUsersInMemory(
        filterBy: filterBy,
        sortBy: sortBy,
        ascending: ascending,
      );
    }
  }

  // Fallback method to filter/sort in memory
  static Future<List<UserModel>> _getFilteredUsersInMemory({
    String? filterBy,
    String? sortBy,
    bool ascending = true,
  }) async {
    try {
      final allUsers = await getAllUsers(limit: 1000);
      var filtered = allUsers;

      // Apply filter
      if (filterBy == 'pro') {
        filtered = filtered.where((u) => u.isPro).toList();
      } else if (filterBy == 'free') {
        filtered = filtered.where((u) => !u.isPro).toList();
      } else if (filterBy == 'news_subscribers') {
        filtered = filtered.where((u) => u.subscribedToNews).toList();
      }

      // Apply sorting
      if (sortBy != null) {
        filtered.sort((a, b) {
          int compare;
          switch (sortBy) {
            case 'name':
              compare = a.name.toLowerCase().compareTo(b.name.toLowerCase());
              break;
            case 'email':
              compare = a.email.toLowerCase().compareTo(b.email.toLowerCase());
              break;
            case 'createdAt':
              compare = a.createdAt.compareTo(b.createdAt);
              break;
            case 'credits':
              compare = a.credits.compareTo(b.credits);
              break;
            default:
              compare = 0;
          }
          return ascending ? compare : -compare;
        });
      }

      return filtered;
    } catch (e) {
      debugPrint('Error in memory filtering: $e');
      return [];
    }
  }

  // Generate CSV data for users
  static String generateUsersCsv(List<UserModel> users) {
    final buffer = StringBuffer();
    
    // CSV Header
    buffer.writeln('UID,Name,Email,User Type,Plan Type,Credits,Sign-in Method,Created At,Subscribed to News,Status');
    
    // CSV Rows
    for (final user in users) {
      final createdAtStr = '${user.createdAt.year}-${user.createdAt.month.toString().padLeft(2, '0')}-${user.createdAt.day.toString().padLeft(2, '0')}';
      buffer.writeln(
        '"${user.uid}","${_escapeCsv(user.name)}","${_escapeCsv(user.email)}","${user.userType}","${user.planType ?? 'N/A'}",${user.credits},"${user.signInMethod}","$createdAtStr",${user.subscribedToNews},"${user.status}"'
      );
    }
    
    return buffer.toString();
  }

  // Helper to escape CSV values
  static String _escapeCsv(String value) {
    return value.replaceAll('"', '""');
  }
}
