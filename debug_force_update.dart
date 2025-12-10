import 'package:firebase_core/firebase_core.dart';
import 'package:rewordium/services/version_service.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:package_info_plus/package_info_plus.dart';

void main() async {
  // Initialize Firebase (adjust this based on your Firebase setup)
  try {
    await Firebase.initializeApp();
  } catch (e) {
    print('Firebase initialization failed: $e');
    print('Please ensure Firebase is properly configured');
    return;
  }

  print('🔍 Force Update Debug Tool');
  print('=' * 50);

  await debugForceUpdate();
}

Future<void> debugForceUpdate() async {
  try {
    // Get current app version info
    final packageInfo = await PackageInfo.fromPlatform();
    final currentVersion = packageInfo.version;
    final currentBuildNumber = packageInfo.buildNumber;

    print('📱 Current App Info:');
    print('   Version: $currentVersion');
    print('   Build: $currentBuildNumber');
    print('');

    // Get Firestore configuration
    final doc = await FirebaseFirestore.instance
        .collection('app_config')
        .doc('version_control')
        .get();

    if (!doc.exists) {
      print('❌ ERROR: Version control document not found in Firestore');
      print('   Path: app_config/version_control');
      return;
    }

    final data = doc.data()!;
    print('☁️ Firestore Configuration:');
    print('   minimum_version: ${data['minimum_version']}');
    print('   latest_version: ${data['latest_version']}');
    print('   minimum_build_number: ${data['minimum_build_number']}');
    print('   latest_build_number: ${data['latest_build_number']}');
    print('   force_update: ${data['force_update']}');
    print('   update_message: ${data['update_message']}');
    print('   play_store_url: ${data['play_store_url']}');
    print('');

    // Perform update check
    print('⚡ Running Update Check...');
    final updateStatus = await VersionService.checkForUpdate();

    print('📋 Update Check Result:');
    print('   Status: $updateStatus');
    print('');

    // Analyze the logic
    print('🔬 Logic Analysis:');

    final minimumBuildNumber = data['minimum_build_number'] as String?;
    final latestBuildNumber = data['latest_build_number'] as String?;
    final forceUpdate = data['force_update'] as bool? ?? false;

    if (minimumBuildNumber != null) {
      final currentBuild = int.tryParse(currentBuildNumber) ?? 0;
      final minBuild = int.tryParse(minimumBuildNumber) ?? 0;

      print('   Current build ($currentBuild) vs Minimum build ($minBuild)');
      if (currentBuild < minBuild) {
        print('   → FORCE UPDATE: Current build is below minimum');
      } else {
        print('   → OK: Current build meets minimum requirement');
      }
    }

    if (latestBuildNumber != null) {
      final currentBuild = int.tryParse(currentBuildNumber) ?? 0;
      final latestBuild = int.tryParse(latestBuildNumber) ?? 0;

      print('   Current build ($currentBuild) vs Latest build ($latestBuild)');
      if (currentBuild < latestBuild) {
        print('   → UPDATE AVAILABLE: Newer version exists');
        if (forceUpdate) {
          print('   → FORCE UPDATE: Force update is enabled');
        } else {
          print('   → OPTIONAL UPDATE: Force update is disabled');
        }
      } else {
        print('   → UP TO DATE: Current build is latest');
        if (forceUpdate) {
          print('   → NO UPDATE: Force update enabled but no newer version');
        }
      }
    }

    print('');
    print('🏁 Summary:');
    switch (updateStatus) {
      case UpdateStatus.noUpdateNeeded:
        print('   ✅ No update needed - app is up to date');
        break;
      case UpdateStatus.optionalUpdate:
        print('   🔵 Optional update available');
        break;
      case UpdateStatus.forceUpdate:
        print('   🔴 Force update required');
        break;
      case UpdateStatus.error:
        print('   ❌ Error occurred during update check');
        break;
    }
  } catch (e, stackTrace) {
    print('❌ ERROR: $e');
    print('Stack trace: $stackTrace');
  }
}
