import 'dart:developer';
import 'package:flutter/material.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_core/firebase_core.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await Firebase.initializeApp();

    // Configure for local emulator
    FirebaseFunctions.instance.useFunctionsEmulator('localhost', 5001);

    print('🚀 Testing Cloud Functions locally...\n');

    // Test 1: Get FCM Token (no admin required)
    await testGetFCMToken();

    // Test 2: Test admin functions (these should fail without proper admin email)
    await testAdminFunctions();

    print('\n✅ All tests completed!');
  } catch (e) {
    print('❌ Error initializing Firebase: $e');
  }
}

Future<void> testGetFCMToken() async {
  try {
    print('📱 Testing getFCMToken...');

    final callable = FirebaseFunctions.instance.httpsCallable('getFCMToken');
    final result = await callable.call();

    if (result.data != null && result.data['success'] == true) {
      print('✅ getFCMToken successful');
      print('   Token: ${result.data['token']?.substring(0, 20)}...');
    } else {
      print('❌ getFCMToken failed: ${result.data}');
    }
  } catch (e) {
    print('❌ getFCMToken error: $e');
  }
  print('');
}

Future<void> testAdminFunctions() async {
  // Test with invalid admin email (should fail)
  final testEmail = 'test@example.com';

  // Test getUserStats
  try {
    print('📊 Testing getUserStats with non-admin email...');

    final callable = FirebaseFunctions.instance.httpsCallable('getUserStats');
    final result = await callable.call({
      'adminEmail': testEmail,
    });

    print('❌ getUserStats should have failed but returned: ${result.data}');
  } catch (e) {
    if (e.toString().contains('admin') || e.toString().contains('authorized')) {
      print('✅ getUserStats correctly rejected non-admin user');
    } else {
      print('⚠️ getUserStats failed with unexpected error: $e');
    }
  }
  print('');

  // Test sendNotification
  try {
    print('📤 Testing sendNotification with non-admin email...');

    final callable =
        FirebaseFunctions.instance.httpsCallable('sendNotification');
    final result = await callable.call({
      'adminEmail': testEmail,
      'topic': 'all_users',
      'title': 'Test Notification',
      'body': 'This is a test notification',
    });

    print('❌ sendNotification should have failed but returned: ${result.data}');
  } catch (e) {
    if (e.toString().contains('admin') || e.toString().contains('authorized')) {
      print('✅ sendNotification correctly rejected non-admin user');
    } else {
      print('⚠️ sendNotification failed with unexpected error: $e');
    }
  }
  print('');

  // Test createSampleUsers
  try {
    print('👥 Testing createSampleUsers with non-admin email...');

    final callable =
        FirebaseFunctions.instance.httpsCallable('createSampleUsers');
    final result = await callable.call({
      'adminEmail': testEmail,
    });

    print(
        '❌ createSampleUsers should have failed but returned: ${result.data}');
  } catch (e) {
    if (e.toString().contains('admin') || e.toString().contains('authorized')) {
      print('✅ createSampleUsers correctly rejected non-admin user');
    } else {
      print('⚠️ createSampleUsers failed with unexpected error: $e');
    }
  }
}
