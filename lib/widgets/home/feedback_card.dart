import 'dart:io';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../utils/lottie_assets.dart';
import '../animated_card.dart';
import '../custom_button.dart';

class FeedbackCard extends StatefulWidget {
  const FeedbackCard({super.key});

  static const String _hasRatedKey = 'has_rated_app';
  static const String _rateRemindLaterKey = 'rate_remind_later_timestamp';
  static const Duration _remindLaterDelay = Duration(days: 5);

  /// Check if the card should be shown
  static Future<bool> shouldShow() async {
    final prefs = await SharedPreferences.getInstance();
    final hasRated = prefs.getBool(_hasRatedKey) ?? false;
    if (hasRated) return false;

    final remindTimestamp = prefs.getInt(_rateRemindLaterKey);
    if (remindTimestamp != null) {
      final remindDate = DateTime.fromMillisecondsSinceEpoch(remindTimestamp);
      if (DateTime.now().isBefore(remindDate)) return false;
    }
    return true;
  }

  @override
  State<FeedbackCard> createState() => _FeedbackCardState();
}

class _FeedbackCardState extends State<FeedbackCard> {
  bool _visible = true;

  @override
  void initState() {
    super.initState();
    _checkVisibility();
  }

  Future<void> _checkVisibility() async {
    final show = await FeedbackCard.shouldShow();
    if (mounted && !show) {
      setState(() => _visible = false);
    }
  }

  Future<void> _launchPlayStore() async {
    const String packageName = 'com.noxquill.rewordium';

    // Mark as rated
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(FeedbackCard._hasRatedKey, true);

    if (Platform.isIOS) {
      // TODO: Replace with actual App Store ID when available
      final Uri appStoreUri = Uri.parse('https://apps.apple.com/app/rewordium/id0000000000');
      try {
        await launchUrl(appStoreUri, mode: LaunchMode.externalApplication);
      } catch (_) {}
    } else {
      final Uri playStoreUri = Uri.parse('market://details?id=$packageName');
      final Uri webUri =
          Uri.parse('https://play.google.com/store/apps/details?id=$packageName');

      try {
        if (await canLaunchUrl(playStoreUri)) {
          await launchUrl(playStoreUri);
        } else {
          await launchUrl(webUri, mode: LaunchMode.externalApplication);
        }
      } catch (e) {
        await launchUrl(webUri, mode: LaunchMode.externalApplication);
      }
    }

    if (mounted) setState(() => _visible = false);
  }

  Future<void> _remindLater() async {
    final prefs = await SharedPreferences.getInstance();
    final remindDate = DateTime.now().add(FeedbackCard._remindLaterDelay);
    await prefs.setInt(
        FeedbackCard._rateRemindLaterKey, remindDate.millisecondsSinceEpoch);
    if (mounted) setState(() => _visible = false);
  }

  @override
  Widget build(BuildContext context) {
    if (!_visible) return const SizedBox.shrink();

    return AnimatedCard(
      animationDelay: 500,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
            "Enjoying Rewordium?",
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 4),
          Text(
            "Your rating helps us grow!",
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 90,
            child: LottieAssets.getFeedbackAnimation(),
          ),
          const SizedBox(height: 12),
          CustomButton(
            text: "Rate Now",
            onPressed: _launchPlayStore,
            type: ButtonType.secondary,
            width: 200,
          ),
          const SizedBox(height: 4),
          TextButton(
            onPressed: _remindLater,
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              minimumSize: const Size(0, 28),
            ),
            child: Text(
              'Maybe Later',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
