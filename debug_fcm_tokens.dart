import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';

void main() async {
  print('🔍 Checking FCM tokens in user documents...\n');

  try {
    // Initialize Firebase
    await Firebase.initializeApp();

    // Query all users and check for FCM tokens
    final usersSnapshot =
        await FirebaseFirestore.instance.collection('users').get();

    int totalUsers = usersSnapshot.docs.length;
    int usersWithTokens = 0;
    int usersWithoutTokens = 0;

    print('📊 Analyzing $totalUsers users:\n');

    for (final doc in usersSnapshot.docs) {
      final data = doc.data();
      final userId = doc.id;
      final email = data['email'] ?? 'No email';
      final fcmToken = data['fcmToken'];

      if (fcmToken != null && fcmToken.toString().isNotEmpty) {
        usersWithTokens++;
        print('✅ $email (${userId.substring(0, 8)}...) - HAS FCM TOKEN');
      } else {
        usersWithoutTokens++;
        print('❌ $email (${userId.substring(0, 8)}...) - NO FCM TOKEN');
      }
    }

    print('\n📈 SUMMARY:');
    print('Total users: $totalUsers');
    print('Users WITH FCM tokens: $usersWithTokens');
    print('Users WITHOUT FCM tokens: $usersWithoutTokens');
    print(
        '\n🎯 SUCCESS RATE: ${((usersWithTokens / totalUsers) * 100).toStringAsFixed(1)}%');

    if (usersWithoutTokens > 0) {
      print('\n⚠️  ISSUE FOUND:');
      print('$usersWithoutTokens users don\'t have FCM tokens stored.');
      print('This is why notifications to them are failing.');
      print('\n💡 SOLUTIONS:');
      print(
          '1. Users need to open the app and log in to store their FCM tokens');
      print(
          '2. Use topic notifications (all_users, pro_users, free_users) instead');
      print('3. Implement token storage during user registration');
    } else {
      print('\n🎉 All users have FCM tokens! Issue must be elsewhere.');
    }
  } catch (e) {
    print('❌ Error checking FCM tokens: $e');
  }
}
