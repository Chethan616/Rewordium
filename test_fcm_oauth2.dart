import 'dart:io';
import 'lib/services/fcm_oauth2_service.dart';

void main() async {
  print('🧪 Testing FCM OAuth2 Service...\n');

  try {
    // Test topic notification
    print('📤 Sending test notification to topic "all_users"...');
    final topicSuccess = await FCMOAuth2Service.sendToTopic(
      topic: 'all_users',
      title: 'OAuth2 Test',
      body: 'This is a test notification using FCM HTTP v1 API with OAuth2',
      data: {
        'test': 'true',
        'timestamp': DateTime.now().toIso8601String(),
        'method': 'oauth2',
      },
    );

    if (topicSuccess) {
      print('✅ Topic notification sent successfully!');
    } else {
      print('❌ Topic notification failed');
    }

    print('\n' + '=' * 50 + '\n');

    // Test token notification (using a sample token)
    print('📤 Testing token-based notification...');
    final tokenSuccess = await FCMOAuth2Service.sendToToken(
      token:
          'erppXmJ1Q8KOB0kkTIj3ON:APA91bFyqgJEo7eV300cVQuGxhcWGtSVSKtpvmwxHwX39JdFwLq-EGHK1zLIqSdrJnGQ_R4Mf_OoV4oWpajqKjmxqrdI_PyT0fFX0df8NM3R25vW-UNfros',
      title: 'OAuth2 Token Test',
      body: 'Direct token notification test using OAuth2',
      data: {
        'type': 'token_test',
        'oauth2': 'true',
      },
    );

    if (tokenSuccess) {
      print('✅ Token notification sent successfully!');
    } else {
      print('❌ Token notification failed');
    }

    print('\n🎉 FCM OAuth2 Test completed!');
    print('Check your device/admin panel for notifications.');
  } catch (e) {
    print('❌ Test failed with error: $e');
    exit(1);
  }
}
