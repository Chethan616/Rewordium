import 'dart:io';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';

class FirebaseMessagingService {
  static final FirebaseMessagingService _instance =
      FirebaseMessagingService._internal();
  final FirebaseMessaging _firebaseMessaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _flutterLocalNotificationsPlugin =
      FlutterLocalNotificationsPlugin();

  static const AndroidNotificationChannel channel = AndroidNotificationChannel(
    'rewordium_channel',
    'Rewordium Notifications',
    description: 'Channel for Rewordium notifications',
    importance: Importance.high,
    playSound: true,
  );

  factory FirebaseMessagingService() {
    return _instance;
  }

  FirebaseMessagingService._internal();

  Future<void> initialize() async {
    try {
      // Request notification permissions
      await _requestPermissions();

      // Initialize local notifications
      const AndroidInitializationSettings initializationSettingsAndroid =
          AndroidInitializationSettings('@mipmap/ic_launcher');

      final InitializationSettings initializationSettings =
          InitializationSettings(
        android: initializationSettingsAndroid,
      );

      await _flutterLocalNotificationsPlugin.initialize(
        initializationSettings,
      );

      // Subscribe to topic for receiving notifications
      await _firebaseMessaging.subscribeToTopic('all_users');
      print('Subscribed to topic: all_users');

      // Create notification channel for Android 8.0+
      if (Platform.isAndroid) {
        await _flutterLocalNotificationsPlugin
            .resolvePlatformSpecificImplementation<
                AndroidFlutterLocalNotificationsPlugin>()
            ?.createNotificationChannel(channel);
      }

      // Configure Firebase messaging
      await _configureFirebaseMessaging();
    } catch (e) {
      debugPrint('Error initializing Firebase Messaging: $e');
    }
  }

  Future<bool> _requestPermissions() async {
    if (Platform.isIOS || Platform.isMacOS) {
      await _firebaseMessaging.requestPermission(
        alert: true,
        announcement: false,
        badge: true,
        carPlay: false,
        criticalAlert: false,
        provisional: false,
        sound: true,
      );
    } else if (Platform.isAndroid) {
      // Request notification permission on Android 13+
      final status = await Permission.notification.status;
      if (status.isDenied) {
        await Permission.notification.request();
      }

      // Additional permissions for Android 12L and below
      if (await Permission.storage.isDenied) {
        await Permission.storage.request();
      }

      // Get and store FCM token
      final token = await _firebaseMessaging.getToken();
      if (token != null) {
        print('FCM Token: $token');
        await _storeFCMToken(token);
      }
    }
    return true;
  }

  Future<void> _configureFirebaseMessaging() async {
    // Request permission for iOS
    NotificationSettings settings = await _firebaseMessaging.requestPermission(
      alert: true,
      announcement: false,
      badge: true,
      carPlay: false,
      criticalAlert: false,
      provisional: false,
      sound: true,
    );

    // Handle token refresh
    _firebaseMessaging.onTokenRefresh.listen((token) {
      // Send this token to your server and store in Firestore
      print('FCM Token refreshed: $token');
      _storeFCMToken(token);
    });

    // Handle messages when the app is in the foreground
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      _showNotification(message);
    });

    // Handle when the app is opened from a terminated state
    FirebaseMessaging.instance
        .getInitialMessage()
        .then((RemoteMessage? message) {
      if (message != null) {
        _handleMessage(message);
      }
    });

    // Handle when the app is in the background but opened from a notification
    FirebaseMessaging.onMessageOpenedApp.listen(_handleMessage);
  }

  Future<void> _showNotification(RemoteMessage message) async {
    try {
      final androidDetails = AndroidNotificationDetails(
        channel.id,
        channel.name,
        channelDescription: channel.description,
        importance: channel.importance,
        playSound: channel.playSound,
        icon: '@mipmap/ic_launcher',
      );

      final notificationDetails = NotificationDetails(
        android: androidDetails,
      );

      await _flutterLocalNotificationsPlugin.show(
        message.hashCode,
        message.notification?.title,
        message.notification?.body,
        notificationDetails,
        payload: message.data.toString(),
      );
    } catch (e) {
      debugPrint('Error showing notification: $e');
    }
  }

  void _handleMessage(RemoteMessage message) {
    // Handle the message when the app is opened from a notification
    print('Handling message: ${message.messageId}');
    // You can navigate to a specific screen based on the message data
  }

  Future<String?> getToken() async {
    return await _firebaseMessaging.getToken();
  }

  // Send notification to a specific topic (e.g., 'all_users')
  Future<void> sendNotificationToTopic({
    required String title,
    required String body,
    required String topic,
  }) async {
    try {
      // This is a client-side implementation that calls a Cloud Function
      // You'll need to deploy a Cloud Function to handle the actual sending
      // of notifications to topics
      await _firebaseMessaging.subscribeToTopic(topic);

      // For testing, we'll just log the notification
      debugPrint('Would send notification to topic: $topic');
      debugPrint('Title: $title');
      debugPrint('Body: $body');

      // In a real implementation, you would call a Cloud Function here
      // Example:
      // final response = await http.post(
      //   Uri.parse('YOUR_CLOUD_FUNCTION_URL/sendNotification'),
      //   headers: {'Content-Type': 'application/json'},
      //   body: jsonEncode({
      //     'topic': topic,
      //     'title': title,
      //     'body': body,
      //   }),
      // );
      //
      // if (response.statusCode != 200) {
      //   throw Exception('Failed to send notification');
      // }

      debugPrint('Notification would be sent to topic: $topic');
    } catch (e) {
      debugPrint('Error sending notification: $e');
      rethrow;
    }
  }

  // Store FCM token in Firestore user document
  Future<void> _storeFCMToken(String token) async {
    try {
      final user = FirebaseAuth.instance.currentUser;
      if (user != null) {
        await FirebaseFirestore.instance
            .collection('users')
            .doc(user.uid)
            .update({'fcmToken': token});
        print('FCM token stored for user ${user.uid}');
      }
    } catch (e) {
      print('Error storing FCM token: $e');
    }
  }

  // Get FCM token for a specific user
  static Future<String?> getFCMTokenForUser(String userId) async {
    try {
      final userDoc = await FirebaseFirestore.instance
          .collection('users')
          .doc(userId)
          .get();

      if (userDoc.exists) {
        final data = userDoc.data();
        return data?['fcmToken'] as String?;
      }
    } catch (e) {
      print('Error getting FCM token for user $userId: $e');
    }
    return null;
  }
}
