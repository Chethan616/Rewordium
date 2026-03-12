import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Flutter-side bridge for Google Play In-App Review.
/// Communicates with InAppReviewHelper.kt via MethodChannel.
class InAppReviewService {
  static const _channel = MethodChannel('com.noxquill.rewordium/review');

  /// Call after each successful AI generation.
  /// The native side tracks the count and prompts when ready.
  static Future<void> onSuccessfulGeneration() async {
    try {
      await _channel.invokeMethod('onSuccessfulGeneration');
    } catch (_) {
      // Non-critical — silently ignore
    }
  }

  /// Directly show the review dialog (e.g. from "Rate Us" button).
  /// Also marks the user as having rated in SharedPreferences so the
  /// FeedbackCard stops appearing.
  static Future<void> showReview() async {
    try {
      await _channel.invokeMethod('showReview');
      // Mark as rated in Flutter SharedPreferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('has_rated_app', true);
    } catch (_) {
      // Non-critical — silently ignore
    }
  }
}
