import 'package:flutter/services.dart';

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
  static Future<void> showReview() async {
    try {
      await _channel.invokeMethod('showReview');
    } catch (_) {
      // Non-critical — silently ignore
    }
  }
}
