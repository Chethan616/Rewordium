import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:dart_jsonwebtoken/dart_jsonwebtoken.dart';
import 'package:flutter/services.dart' show rootBundle;

class FCMOAuth2Service {
  static const String fcmScope =
      'https://www.googleapis.com/auth/firebase.messaging';
  static const String tokenUri = 'https://oauth2.googleapis.com/token';
  static const String fcmBaseUrl =
      'https://fcm.googleapis.com/v1/projects/rewordium/messages:send';

  static Map<String, dynamic>? _serviceAccount;
  static String? _accessToken;
  static DateTime? _tokenExpiry;

  // Load service account from assets
  static Future<void> _loadServiceAccount() async {
    if (_serviceAccount != null) return;

    try {
      // Try to load from assets first
      try {
        final serviceAccountJson = await rootBundle
            .loadString('assets/config/service-account-key.json');
        _serviceAccount = jsonDecode(serviceAccountJson);
        print('✅ Service account loaded from assets');
        return;
      } catch (e) {
        print('⚠️ Could not load from assets: $e');
      }

      // Fallback to file system
      final file = File('rewordium-4a89181f09b0.json');
      if (await file.exists()) {
        final serviceAccountJson = await file.readAsString();
        _serviceAccount = jsonDecode(serviceAccountJson);
        print('✅ Service account loaded from file system');
      } else {
        throw Exception('Service account file not found');
      }
    } catch (e) {
      print('❌ Failed to load service account: $e');
      rethrow;
    }
  }

  // Get access token using OAuth2 JWT
  static Future<String> _getAccessToken() async {
    // Return cached token if valid
    if (_accessToken != null &&
        _tokenExpiry != null &&
        DateTime.now().isBefore(_tokenExpiry!)) {
      return _accessToken!;
    }

    await _loadServiceAccount();

    if (_serviceAccount == null) {
      throw Exception('Service account not loaded');
    }

    try {
      // Create JWT claims
      final now = DateTime.now();
      final expiry = now.add(const Duration(hours: 1));

      final jwt = JWT({
        'iss': _serviceAccount!['client_email'],
        'scope': fcmScope,
        'aud': tokenUri,
        'exp': expiry.millisecondsSinceEpoch ~/ 1000,
        'iat': now.millisecondsSinceEpoch ~/ 1000,
      });

      // Sign JWT with private key
      final privateKeyPem = _serviceAccount!['private_key'] as String;
      final token =
          jwt.sign(RSAPrivateKey(privateKeyPem), algorithm: JWTAlgorithm.RS256);

      // Exchange JWT for access token
      final response = await http.post(
        Uri.parse(tokenUri),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: {
          'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer',
          'assertion': token,
        },
      );

      if (response.statusCode != 200) {
        print(
            '❌ OAuth2 token request failed: ${response.statusCode} - ${response.body}');
        throw Exception('Failed to get OAuth2 token: ${response.statusCode}');
      }

      final tokenData = jsonDecode(response.body);
      _accessToken = tokenData['access_token'];
      _tokenExpiry = now.add(
          Duration(seconds: tokenData['expires_in'] - 60)); // 60 seconds buffer

      print('✅ OAuth2 access token obtained successfully');
      return _accessToken!;
    } catch (e) {
      print('❌ Error getting access token: $e');
      rethrow;
    }
  }

  // Send FCM notification using HTTP v1 API with OAuth2
  static Future<bool> sendNotification({
    String? token,
    String? topic,
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    try {
      print('🚀 Sending FCM notification via HTTP v1 API with OAuth2...');

      final accessToken = await _getAccessToken();

      // Prepare FCM v1 payload
      final Map<String, dynamic> message = {
        'notification': {
          'title': title,
          'body': body,
        },
      };

      // Add data if provided
      if (data != null && data.isNotEmpty) {
        message['data'] =
            data.map((key, value) => MapEntry(key, value.toString()));
      }

      // Add target (token or topic)
      if (topic != null) {
        message['topic'] = topic;
      } else if (token != null) {
        message['token'] = token;
      } else {
        throw Exception('Either token or topic must be provided');
      }

      final payload = {'message': message};

      print('📤 FCM Payload: ${jsonEncode(payload)}');

      // Send to FCM
      final response = await http.post(
        Uri.parse(fcmBaseUrl),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $accessToken',
        },
        body: jsonEncode(payload),
      );

      print('📥 FCM Response: ${response.statusCode} - ${response.body}');

      if (response.statusCode == 200) {
        final responseData = jsonDecode(response.body);
        print('✅ FCM notification sent successfully: ${responseData['name']}');
        return true;
      } else {
        print('❌ FCM failed: ${response.statusCode} - ${response.body}');
        return false;
      }
    } catch (e) {
      print('❌ Error sending FCM notification: $e');
      return false;
    }
  }

  // Send to topic
  static Future<bool> sendToTopic({
    required String topic,
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) {
    return sendNotification(
      topic: topic,
      title: title,
      body: body,
      data: data,
    );
  }

  // Send to token
  static Future<bool> sendToToken({
    required String token,
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) {
    return sendNotification(
      token: token,
      title: title,
      body: body,
      data: data,
    );
  }
}
