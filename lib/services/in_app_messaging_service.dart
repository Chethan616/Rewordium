import 'dart:async';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_in_app_messaging/firebase_in_app_messaging.dart';
import 'package:flutter/material.dart';
import '../widgets/in_app_message_dialog.dart';

/// Comprehensive In-App Messaging Service
/// Integrates both Firebase In-App Messaging (FIAM) SDK and real-time Admin Panel in-app campaigns
class InAppMessagingService {
  static final InAppMessagingService _instance = InAppMessagingService._internal();
  final FirebaseInAppMessaging _fiam = FirebaseInAppMessaging.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  bool _isInitialized = false;
  GlobalKey<NavigatorState>? _navigatorKey;
  StreamSubscription? _firestoreSubscription;

  factory InAppMessagingService() {
    return _instance;
  }

  InAppMessagingService._internal();

  /// Initialize FIAM SDK and real-time Firestore in-app messaging listener
  Future<void> initialize(GlobalKey<NavigatorState> navigatorKey) async {
    if (_isInitialized) return;
    _navigatorKey = navigatorKey;

    try {
      // Enable Firebase In-App Messaging
      await _fiam.setMessagesSuppressed(false);
      await _fiam.setAutomaticDataCollectionEnabled(true);

      // Listen for real-time in-app campaign messages published from Admin Panel
      _listenToAdminInAppMessages();

      _isInitialized = true;
      debugPrint('[InAppMessagingService] Initialized successfully');
    } catch (e) {
      debugPrint('[InAppMessagingService] Initialization error: $e');
    }
  }

  /// Trigger a custom FIAM event (e.g. 'rewrite_completed', 'pdf_uploaded', 'pro_promo_triggered')
  Future<void> triggerEvent(String eventName) async {
    try {
      await _fiam.triggerEvent(eventName);
      debugPrint('[InAppMessagingService] Triggered FIAM event: $eventName');
    } catch (e) {
      debugPrint('[InAppMessagingService] Error triggering FIAM event: $e');
    }
  }

  /// Suppress or allow in-app messages (useful during critical flows)
  Future<void> setMessagesSuppressed(bool suppress) async {
    await _fiam.setMessagesSuppressed(suppress);
  }

  /// Listen to real-time Admin Panel in-app message broadcasts
  void _listenToAdminInAppMessages() {
    final DateTime sessionStart = DateTime.now();

    _firestoreSubscription = _firestore
        .collection('in_app_messages')
        .where('publishedAt', isGreaterThan: Timestamp.fromDate(sessionStart))
        .snapshots()
        .listen((snapshot) {
      for (var change in snapshot.docChanges) {
        if (change.type == DocumentChangeType.added) {
          final data = change.doc.data();
          if (data != null) {
            _displayInAppMessageFromDoc(data);
          }
        }
      }
    });
  }

  void _displayInAppMessageFromDoc(Map<String, dynamic> data) {
    final context = _navigatorKey?.currentContext;
    if (context == null) return;

    final title = data['title'] as String? ?? 'Announcement';
    final body = data['body'] as String? ?? '';
    final imageUrl = data['imageUrl'] as String?;
    final buttonText = data['buttonText'] as String?;
    final actionUrl = data['actionUrl'] as String?;
    final typeStr = data['type'] as String? ?? 'card';

    if (typeStr == 'banner') {
      InAppMessageDialog.showTopBanner(
        context,
        title: title,
        body: body,
        buttonText: buttonText,
        actionUrl: actionUrl,
      );
    } else {
      InAppMessageDialog.showModal(
        context,
        title: title,
        body: body,
        imageUrl: imageUrl,
        buttonText: buttonText,
        actionUrl: actionUrl,
        type: typeStr == 'modal' ? InAppMessageType.modal : InAppMessageType.card,
      );
    }
  }

  void dispose() {
    _firestoreSubscription?.cancel();
  }
}
