import 'package:flutter/material.dart';
import 'package:rewordium/services/play_integrity_service.dart';
import 'package:rewordium/utils/app_logger.dart';

/// Example: How to use Play Integrity Service in your app
/// 
/// This file demonstrates common use cases for the Play Integrity API.
/// Copy these examples to where you need integrity checks in your app.

class PlayIntegrityExamples {
  
  /// Example 1: Check integrity on app startup (already done in main.dart)
  /// This is a passive check that runs in the background
  static Future<void> checkOnStartup() async {
    try {
      await PlayIntegrityService.initialize();
      AppLogger.info('Play Integrity initialized on startup');
    } catch (e) {
      AppLogger.error('Play Integrity initialization failed', e);
    }
  }

  /// Example 2: Verify integrity before critical operations
  /// Use this before purchases, sensitive API calls, etc.
  static Future<bool> verifyBeforeCriticalOperation() async {
    final isValid = await PlayIntegrityService.checkIntegrity();
    
    if (!isValid) {
      AppLogger.warning('Integrity check failed before critical operation');
      // Handle integrity failure (show warning, log event, etc.)
      return false;
    }
    
    AppLogger.info('Integrity verified for critical operation');
    return true;
  }

  /// Example 3: Check integrity before in-app purchase
  static Future<bool> verifyBeforePurchase() async {
    final isSecure = await PlayIntegrityService.isDeviceSecure();
    
    if (!isSecure) {
      // Show warning to user
      AppLogger.warning('Device security check failed before purchase');
      return false;
    }
    
    // Proceed with purchase
    return true;
  }

  /// Example 4: Get detailed integrity verdict
  /// Use this for debugging or logging purposes
  static Future<void> getDetailedVerdict() async {
    final verdict = await PlayIntegrityService.getIntegrityVerdict();
    
    if (verdict != null) {
      AppLogger.info('Integrity verdict: $verdict');
      
      // You can log specific fields
      final hasToken = verdict['hasToken'] ?? false;
      final timestamp = verdict['timestamp'] ?? 0;
      
      print('Has token: $hasToken');
      print('Timestamp: $timestamp');
    } else {
      AppLogger.warning('Could not get integrity verdict');
    }
  }

  /// Example 5: Show integrity status to user (for debugging)
  static Future<void> showIntegrityDialog(BuildContext context) async {
    final isValid = await PlayIntegrityService.checkIntegrity();
    final isSecure = await PlayIntegrityService.isDeviceSecure();
    
    if (!context.mounted) return;
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Integrity Status'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Integrity Check: ${isValid ? '✅ Passed' : '❌ Failed'}'),
            const SizedBox(height: 8),
            Text('Device Secure: ${isSecure ? '✅ Yes' : '❌ No'}'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  /// Example 6: Periodic integrity check (e.g., every 24 hours)
  static Future<void> scheduledIntegrityCheck() async {
    final lastCheck = DateTime.now().subtract(const Duration(hours: 24));
    
    // Check if 24 hours have passed since last check
    if (DateTime.now().difference(lastCheck).inHours >= 24) {
      final isValid = await PlayIntegrityService.checkIntegrity();
      
      if (!isValid) {
        AppLogger.warning('Scheduled integrity check failed');
        // Handle failure (send analytics, show warning, etc.)
      } else {
        AppLogger.info('Scheduled integrity check passed');
      }
    }
  }

  /// Example 7: Integrity check with user feedback
  static Future<bool> checkWithUserFeedback(BuildContext context) async {
    // Show loading indicator
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(
        child: CircularProgressIndicator(),
      ),
    );

    final isValid = await PlayIntegrityService.checkIntegrity();
    
    if (!context.mounted) return false;
    
    // Close loading indicator
    Navigator.pop(context);

    if (!isValid) {
      // Show error message
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Security verification failed. Some features may be limited.'),
          backgroundColor: Colors.red,
        ),
      );
      return false;
    }

    return true;
  }

  /// Example 8: Integrity check before premium feature access
  static Future<bool> verifyPremiumAccess() async {
    // First check if device is secure
    final isSecure = await PlayIntegrityService.isDeviceSecure();
    if (!isSecure) {
      AppLogger.warning('Device not secure for premium features');
      return false;
    }

    // Then verify overall integrity
    final isValid = await PlayIntegrityService.checkIntegrity();
    if (!isValid) {
      AppLogger.warning('Integrity check failed for premium access');
      return false;
    }

    return true;
  }
}

/// Usage examples in your app:

/*

// 1. In your settings screen - show integrity status
class SettingsScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: const Text('Check App Integrity'),
      trailing: const Icon(Icons.security),
      onTap: () => PlayIntegrityExamples.showIntegrityDialog(context),
    );
  }
}

// 2. Before making a purchase
Future<void> makePurchase() async {
  final isValid = await PlayIntegrityExamples.verifyBeforePurchase();
  if (!isValid) {
    // Show error and abort purchase
    return;
  }
  
  // Proceed with purchase
  // ...
}

// 3. In your API service - before sensitive calls
class ApiService {
  Future<void> sensitiveApiCall() async {
    final isValid = await PlayIntegrityExamples.verifyBeforeCriticalOperation();
    if (!isValid) {
      throw Exception('Integrity check failed');
    }
    
    // Make API call
    // ...
  }
}

// 4. In a splash screen or home screen - periodic check
class HomeScreen extends StatefulWidget {
  @override
  _HomeScreenState createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  void initState() {
    super.initState();
    PlayIntegrityExamples.scheduledIntegrityCheck();
  }
  
  @override
  Widget build(BuildContext context) {
    // Your UI
    return Container();
  }
}

*/
