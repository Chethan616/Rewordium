import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:rewordium/main.dart';

/// Service to prompt users who haven't subscribed to news
/// Shows a dialog similar to the app update dialog with options:
/// - Subscribe
/// - Remind me later
/// - Don't show again
class NewsSubscriptionService {
  static bool _isCheckingSubscription = false;
  static bool _hasInitialized = false;
  static DateTime? _lastCheckTime;
  static const Duration _checkInterval = Duration(hours: 24); // Check once per day
  
  // SharedPreferences keys
  static const String _keyDontShowAgain = 'news_subscription_dont_show_again';
  static const String _keyRemindLaterTimestamp = 'news_subscription_remind_later';
  static const Duration _remindLaterDelay = Duration(days: 3); // Remind after 3 days

  /// Initialize news subscription checking
  static Future<void> initialize() async {
    if (_hasInitialized) return;
    _hasInitialized = true;

    // Schedule check for next frame to ensure app is fully loaded
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      // Wait a bit more to ensure everything is settled and update dialog has shown
      await Future.delayed(const Duration(seconds: 3));
      await checkAndPromptSubscription();
    });
  }

  /// Check if user should be prompted for news subscription
  static Future<void> checkAndPromptSubscription({bool forceCheck = false}) async {
    if (_isCheckingSubscription && !forceCheck) return;

    // Don't check too frequently unless forced
    if (!forceCheck && _lastCheckTime != null) {
      final timeSinceLastCheck = DateTime.now().difference(_lastCheckTime!);
      if (timeSinceLastCheck < _checkInterval) {
        debugPrint('Skipping news subscription check - too soon since last check');
        return;
      }
    }

    _isCheckingSubscription = true;
    _lastCheckTime = DateTime.now();

    try {
      // Check if user is logged in
      final currentUser = FirebaseAuth.instance.currentUser;
      if (currentUser == null) {
        debugPrint('User not logged in, skipping news subscription prompt');
        return;
      }

      // Check SharedPreferences for "Don't show again"
      final prefs = await SharedPreferences.getInstance();
      final dontShowAgain = prefs.getBool(_keyDontShowAgain) ?? false;
      
      if (dontShowAgain) {
        debugPrint('User opted to not show news subscription prompt again');
        return;
      }

      // Check "Remind me later" timestamp
      final remindLaterTimestamp = prefs.getInt(_keyRemindLaterTimestamp);
      if (remindLaterTimestamp != null) {
        final remindDate = DateTime.fromMillisecondsSinceEpoch(remindLaterTimestamp);
        if (DateTime.now().isBefore(remindDate)) {
          debugPrint('Remind later period not passed yet');
          return;
        }
      }

      // Check if user is already subscribed to news in Firebase
      final isSubscribed = await _checkUserSubscriptionStatus(currentUser.uid);
      if (isSubscribed) {
        debugPrint('User is already subscribed to news');
        return;
      }

      // Show the prompt dialog
      await _showSubscriptionDialog();
    } catch (e) {
      debugPrint('Error in news subscription check: $e');
    } finally {
      _isCheckingSubscription = false;
    }
  }

  /// Check if user is already subscribed to news in Firebase
  static Future<bool> _checkUserSubscriptionStatus(String uid) async {
    try {
      final doc = await FirebaseFirestore.instance
          .collection('users')
          .doc(uid)
          .get();
      
      if (!doc.exists) return false;
      
      final data = doc.data();
      return data?['subscribedToNews'] ?? false;
    } catch (e) {
      debugPrint('Error checking subscription status: $e');
      return false;
    }
  }

  /// Update user's news subscription status in Firebase
  static Future<bool> updateSubscriptionStatus(String uid, bool subscribed) async {
    try {
      await FirebaseFirestore.instance
          .collection('users')
          .doc(uid)
          .update({'subscribedToNews': subscribed});
      return true;
    } catch (e) {
      debugPrint('Error updating subscription status: $e');
      return false;
    }
  }

  /// Show the subscription prompt dialog
  static Future<void> _showSubscriptionDialog() async {
    final context = _getCurrentContext();
    if (context == null) {
      debugPrint('No context available for showing subscription dialog');
      return;
    }

    if (!context.mounted) {
      debugPrint('Context is not mounted, skipping subscription dialog');
      return;
    }

    // Additional safety: Wait for any potential navigation to complete
    await Future.delayed(const Duration(milliseconds: 100));

    if (!context.mounted) {
      debugPrint('Context became unmounted, skipping subscription dialog');
      return;
    }

    try {
      debugPrint('Showing news subscription dialog');
      await showDialog(
        context: context,
        barrierDismissible: true,
        builder: (context) => const NewsSubscriptionDialog(),
      );
    } catch (e) {
      debugPrint('Error showing subscription dialog: $e');
    }
  }

  /// Get current context from the navigator
  static BuildContext? _getCurrentContext() {
    return navigatorKey.currentContext;
  }

  /// Handle "Remind me later" action
  static Future<void> remindLater() async {
    final prefs = await SharedPreferences.getInstance();
    final remindDate = DateTime.now().add(_remindLaterDelay);
    await prefs.setInt(_keyRemindLaterTimestamp, remindDate.millisecondsSinceEpoch);
    debugPrint('News subscription reminder set for: $remindDate');
  }

  /// Handle "Don't show again" action
  static Future<void> dontShowAgain() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyDontShowAgain, true);
    debugPrint('News subscription prompt disabled permanently');
  }

  /// Reset the service (for testing or if user wants to see the prompt again)
  static Future<void> reset() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyDontShowAgain);
    await prefs.remove(_keyRemindLaterTimestamp);
    _hasInitialized = false;
    debugPrint('News subscription service reset');
  }
}

/// The dialog widget for news subscription prompt
/// Styled similar to UpdateDialog for consistency
class NewsSubscriptionDialog extends StatefulWidget {
  const NewsSubscriptionDialog({super.key});

  @override
  State<NewsSubscriptionDialog> createState() => _NewsSubscriptionDialogState();
}

class _NewsSubscriptionDialogState extends State<NewsSubscriptionDialog> {
  bool _isLoading = false;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.blue.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(
              Icons.mail_outline,
              color: Colors.blue,
              size: 28,
            ),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Text(
              'Stay Updated!',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 20,
              ),
            ),
          ),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Subscribe to our newsletter to receive:',
            style: TextStyle(fontSize: 15),
          ),
          const SizedBox(height: 12),
          _buildBulletPoint('🚀 New feature announcements'),
          _buildBulletPoint('💡 Tips & tricks for better writing'),
          _buildBulletPoint('🎁 Exclusive offers & promotions'),
          _buildBulletPoint('📊 Product updates & improvements'),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.green.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.green.withOpacity(0.3)),
            ),
            child: Row(
              children: [
                const Icon(Icons.lock_outline, size: 18, color: Colors.green),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'We respect your privacy. No spam, ever.',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.green.shade700,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      actionsAlignment: MainAxisAlignment.spaceBetween,
      actionsPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      actions: [
        // Don't show again - subtle link style
        TextButton(
          onPressed: _isLoading ? null : _handleDontShowAgain,
          style: TextButton.styleFrom(
            foregroundColor: Colors.grey,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          ),
          child: const Text(
            "Don't show again",
            style: TextStyle(fontSize: 12),
          ),
        ),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Remind me later
            TextButton(
              onPressed: _isLoading ? null : _handleRemindLater,
              child: const Text('Later'),
            ),
            const SizedBox(width: 8),
            // Subscribe button - primary action
            ElevatedButton.icon(
              onPressed: _isLoading ? null : _handleSubscribe,
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              ),
              icon: _isLoading
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      ),
                    )
                  : const Icon(Icons.mail, size: 18),
              label: Text(_isLoading ? 'Subscribing...' : 'Subscribe'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildBulletPoint(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(text, style: const TextStyle(fontSize: 14)),
        ],
      ),
    );
  }

  Future<void> _handleSubscribe() async {
    setState(() => _isLoading = true);

    try {
      final currentUser = FirebaseAuth.instance.currentUser;
      if (currentUser == null) {
        _showSnackBar('Please log in to subscribe', isError: true);
        return;
      }

      final success = await NewsSubscriptionService.updateSubscriptionStatus(
        currentUser.uid,
        true,
      );

      if (mounted) {
        Navigator.of(context).pop();
        
        if (success) {
          _showSnackBar('🎉 Thanks for subscribing! You\'ll receive our updates.');
        } else {
          _showSnackBar('Failed to subscribe. Please try again.', isError: true);
        }
      }
    } catch (e) {
      if (mounted) {
        Navigator.of(context).pop();
        _showSnackBar('Error: $e', isError: true);
      }
    }
  }

  Future<void> _handleRemindLater() async {
    await NewsSubscriptionService.remindLater();
    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  Future<void> _handleDontShowAgain() async {
    await NewsSubscriptionService.dontShowAgain();
    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  void _showSnackBar(String message, {bool isError = false}) {
    final context = navigatorKey.currentContext;
    if (context != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: isError ? Colors.red : Colors.green,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }
}
