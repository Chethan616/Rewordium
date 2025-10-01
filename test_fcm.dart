import 'dart:convert';
import 'package:http/http.dart' as http;

// Test FCM notification with your server key
Future<void> testFCMNotification() async {
  // REPLACE THIS WITH YOUR ACTUAL FCM SERVER KEY FROM FIREBASE CONSOLE
  const String fcmServerKey =
      'AAAA8gB9keI:APA91bH8vXxTdJoYiRz4QNXGkSuAEhJVQvBaQFzqKl_5w6X3YuNpM8DhF2RcE4vL9sT1nP7kG6wJ0oI5uE8rY3qA2sZ9xC7nB1mV4fH6gK8lP0qW9eR3tY5uI7oA2sD4fG6h';
  const String testTopic = 'all_users';

  try {
    print('🧪 Testing FCM notification to topic: $testTopic');
    print('📋 Server Key: ${fcmServerKey.substring(0, 20)}...');

    final Map<String, dynamic> payload = {
      'to': '/topics/$testTopic',
      'notification': {
        'title': '🎉 Admin Panel Test',
        'body': 'FCM is working perfectly! Time: ${DateTime.now()}',
      },
      'data': {
        'test': 'true',
        'source': 'admin_panel',
        'timestamp': DateTime.now().millisecondsSinceEpoch.toString(),
      },
    };

    print('📤 Sending payload: ${json.encode(payload)}');

    const String legacyFcmUrl = 'https://fcm.googleapis.com/fcm/send';

    final response = await http.post(
      Uri.parse(legacyFcmUrl),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'key=$fcmServerKey',
      },
      body: json.encode(payload),
    );

    print('📨 Response Status: ${response.statusCode}');
    print('📨 Response Body: ${response.body}');

    if (response.statusCode == 200) {
      final responseData = json.decode(response.body);
      if (responseData['success'] != null && responseData['success'] > 0) {
        print('✅ FCM Test SUCCESS!');
        print('   📊 Messages sent: ${responseData['success']}');
        print(
            '   📊 Message ID: ${responseData['results']?[0]?['message_id']}');
      } else if (responseData['failure'] != null &&
          responseData['failure'] > 0) {
        print('⚠️  FCM Test PARTIAL FAILURE!');
        print('   📊 Failures: ${responseData['failure']}');
        print('   📊 Results: ${responseData['results']}');
      } else {
        print('❓ FCM Test UNKNOWN RESPONSE!');
        print('   📊 Response: $responseData');
      }
    } else {
      print('❌ FCM Test FAILED!');
      print('   📊 Status Code: ${response.statusCode}');
      print('   📊 Error: ${response.body}');

      if (response.statusCode == 401) {
        print('🔑 AUTHENTICATION ERROR: Check your FCM server key!');
        print(
            '   📋 Get it from: https://console.firebase.google.com/project/rewordium/settings/cloudmessaging');
      } else if (response.statusCode == 400) {
        print('📝 BAD REQUEST: Check your payload format!');
      }
    }
  } catch (e) {
    print('💥 FCM Test ERROR: $e');
  }
}

void main() async {
  print('🚀 Starting FCM Test...\n');
  await testFCMNotification();
  print('\n✅ FCM Test Complete!');
}
