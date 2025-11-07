import 'package:flutter/material.dart';
import 'package:animate_do/animate_do.dart';

import '../theme/app_theme.dart';
import '../utils/lottie_assets.dart';
import '../widgets/animated_card.dart';
import '../widgets/animated_jade_avatar.dart';
import '../widgets/custom_button.dart';
import '../screens/jade_chat_screen.dart';
import 'home/assistant_status_card.dart';

class HomeContent extends StatelessWidget {
  const HomeContent({super.key});

  @override
  Widget build(BuildContext context) {
    // Get screen size for responsive design
    final Size screenSize = MediaQuery.of(context).size;
    final bool isSmallScreen = screenSize.width < 360;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHomeHeader(context),
            const SizedBox(height: 8),
            FadeInUp(
              duration: const Duration(milliseconds: 600),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text("Your Tools", style: AppTheme.headingSmall),
              ),
            ),
            _buildQuickTools(isSmallScreen, context),
            const SizedBox(height: 8),
            FadeInUp(
              duration: const Duration(milliseconds: 800),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text("Setup Status", style: AppTheme.headingSmall),
              ),
            ),
            _buildKeyboardStatusCard(isSmallScreen),
            const AssistantStatusCard(),
            _buildFeedbackCard(),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildHomeHeader(BuildContext context) {
    return AnimatedCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              FadeInLeft(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "Welcome to",
                      style: AppTheme.bodyLarge.copyWith(
                        color: AppTheme.textSecondaryColor,
                      ),
                    ),
                    Text(
                      "YC Startup",
                      style: AppTheme.headingLarge.copyWith(
                        color: AppTheme.primaryColor,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ],
                ),
              ),
              FadeInRight(
                child: CustomButton(
                  text: "Log in",
                  onPressed: () {},
                  width: 100,
                  type: ButtonType.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          FadeIn(
            delay: const Duration(milliseconds: 300),
            child: Text(
              "Improve your writing with AI",
              style: AppTheme.bodyMedium,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuickTools(bool isSmallScreen, BuildContext context) {
    print(
        "🔧 Building quick tools with ${isSmallScreen ? 'small' : 'normal'} screen size");
    return SizedBox(
      height: isSmallScreen ? 130 : 150,
      child: ListView(
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.symmetric(horizontal: 8),
        children: [
          _buildToolCard(
            "Paraphraser",
            "Rewrite text in your style",
            LottieAssets.getParaphrasingAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            Colors.green,
            100,
            isSmallScreen,
          ),
          _buildToolCard(
            "Grammar Check",
            "Fix errors in your writing",
            LottieAssets.getGrammarCheckAnimation(
              height: isSmallScreen ? 50 : 60,
            ),
            Colors.red,
            300,
            isSmallScreen,
          ),
          _buildToolCard(
            "Keyboard",
            "Write better everywhere",
            LottieAssets.getKeyboardAnimation(height: isSmallScreen ? 50 : 60),
            Colors.blue,
            500,
            isSmallScreen,
          ),
          GestureDetector(
            onTap: () {
              print("🤖 Jade AI card tapped!");
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const JadeChatScreen(),
                ),
              );
            },
            child: _buildJadeAICard(isSmallScreen),
          ),
        ],
      ),
    );
  }

  Widget _buildToolCard(
    String title,
    String subtitle,
    Widget icon,
    Color color,
    int delay,
    bool isSmallScreen,
  ) {
    return FadeInRight(
      delay: Duration(milliseconds: delay),
      child: Container(
        width: isSmallScreen ? 140 : 160,
        margin: EdgeInsets.all(isSmallScreen ? 6 : 8),
        padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [color.withOpacity(0.8), color.withOpacity(0.6)],
          ),
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: color.withOpacity(0.3),
              blurRadius: 8,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(child: icon),
            const SizedBox(height: 4),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  title,
                  style: AppTheme.headingSmall.copyWith(
                    color: Colors.white,
                    fontSize: isSmallScreen ? 14 : 16,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  subtitle,
                  style: AppTheme.bodySmall.copyWith(
                    color: Colors.white.withOpacity(0.8),
                    fontSize: isSmallScreen ? 9 : 10,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildKeyboardStatusCard(bool isSmallScreen) {
    return AnimatedCard(
      animationDelay: 300,
      padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: const BoxDecoration(
                  color: Colors.amber,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                "Keyboard Status",
                style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "Switch to YC Startup Keyboard",
                      style: AppTheme.bodyMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      "Write better in all your apps",
                      style: AppTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 4),
              CustomButton(
                text: "Switch",
                onPressed: () {},
                width: isSmallScreen ? 85 : 110,
                height: isSmallScreen ? 40 : 48,
                type: ButtonType.primary,
              ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 80,
            child: LottieAssets.getKeyboardAnimation(),
          ),
        ],
      ),
    );
  }

  Widget _buildFeedbackCard() {
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

  Widget _buildJadeAICard(bool isSmallScreen) {
    return FadeInRight(
      delay: const Duration(milliseconds: 700),
      child: Container(
        width: isSmallScreen ? 140 : 160,
        margin: EdgeInsets.all(isSmallScreen ? 6 : 8),
        padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Colors.deepPurple.withOpacity(0.8),
              Colors.deepPurple.withOpacity(0.6),
              const Color(0xFF4ECDC4).withOpacity(0.5),
            ],
          ),
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.deepPurple.withOpacity(0.3),
              blurRadius: 8,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Center(
                child: AnimatedJadeAvatar(
                  size: isSmallScreen ? 50 : 60,
                  enableRotation: true,
                  rotationInterval: const Duration(seconds: 10),
                  showBorder: false, // No border for the home card
                ),
              ),
            ),
            const SizedBox(height: 4),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  "Jade AI",
                  style: AppTheme.headingSmall.copyWith(
                    color: Colors.white,
                    fontSize: isSmallScreen ? 14 : 16,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  "Chat with AI assistant",
                  style: AppTheme.bodySmall.copyWith(
                    color: Colors.white.withOpacity(0.8),
                    fontSize: isSmallScreen ? 9 : 10,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
