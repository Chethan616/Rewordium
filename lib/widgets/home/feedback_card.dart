import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../theme/app_theme.dart';
import '../../utils/lottie_assets.dart';
import '../animated_card.dart';
import '../custom_button.dart';

class FeedbackCard extends StatelessWidget {
  const FeedbackCard({super.key});

  Future<void> _launchPlayStore() async {
    const String packageName = 'com.noxquill.rewordium';
    final Uri playStoreUri = Uri.parse('market://details?id=$packageName');
    final Uri webUri =
        Uri.parse('https://play.google.com/store/apps/details?id=$packageName');

    try {
      // Try to launch Play Store app first
      if (await canLaunchUrl(playStoreUri)) {
        await launchUrl(playStoreUri);
      } else {
        // Fallback to web browser
        await launchUrl(webUri, mode: LaunchMode.externalApplication);
      }
    } catch (e) {
      // If both fail, try web browser as final fallback
      await launchUrl(webUri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedCard(
      animationDelay: 500,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
            "Rate Us",
            style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.w600),
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
        ],
      ),
    );
  }
}
