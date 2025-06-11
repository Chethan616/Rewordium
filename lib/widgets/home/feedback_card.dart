import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';
import '../../utils/lottie_assets.dart';
import '../animated_card.dart';
import '../custom_button.dart';

class FeedbackCard extends StatelessWidget {
  const FeedbackCard({super.key});

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
            onPressed: () {},
            type: ButtonType.secondary,
            width: 200,
          ),
        ],
      ),
    );
  }
}
