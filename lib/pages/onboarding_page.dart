import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'dart:math'
    as math; // Kept for potential future use, but not used in this version.

import '../main.dart';
import '../theme/app_theme.dart';

class OnboardingPage extends StatefulWidget {
  const OnboardingPage({super.key});

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<OnboardingPage> {
  late PageController _pageController;
  int _currentPage = 0;
  final int _totalPages = 4;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _nextPage() {
    if (_currentPage < _totalPages - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOutCubic,
      );
    } else {
      _goToHome();
    }
  }

  void _previousPage() {
    if (_currentPage > 0) {
      _pageController.previousPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOutCubic,
      );
    }
  }

  void _goToHome() {
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (context, animation, secondaryAnimation) =>
            const HomePage(),
        transitionDuration: const Duration(milliseconds: 800),
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          return FadeTransition(
            opacity: animation,
            child: ScaleTransition(
              scale: Tween<double>(begin: 0.95, end: 1.0).animate(
                CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
              ),
              child: child,
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // THEME UPDATE: Use the scaffold background color from the theme.
      backgroundColor: AppTheme.scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            // Header with skip button
            Padding(
              padding: const EdgeInsets.all(24.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  // Back button (show only if not first page)
                  _currentPage > 0
                      ? IconButton(
                          onPressed: _previousPage,
                          icon: Icon(
                            CupertinoIcons.chevron_left,
                            // THEME UPDATE: Use primary text color.
                            color: AppTheme.textPrimaryColor,
                            size: 28,
                          ),
                        )
                      : const SizedBox(width: 48),

                  // Page indicator
                  Row(
                    children: List.generate(_totalPages, (index) {
                      return AnimatedContainer(
                        duration: const Duration(milliseconds: 300),
                        margin: const EdgeInsets.symmetric(horizontal: 4),
                        height: 8,
                        width: _currentPage == index ? 24 : 8,
                        decoration: BoxDecoration(
                          // THEME UPDATE: Use theme colors for the indicator.
                          color: _currentPage == index
                              ? AppTheme.primaryColor
                              : AppTheme.disabledColor,
                          borderRadius: BorderRadius.circular(4),
                        ),
                      );
                    }),
                  ),

                  // Skip button
                  TextButton(
                    onPressed: _goToHome,
                    child: Text(
                      'Skip',
                      // THEME UPDATE: Use a theme style for the button text.
                      style: AppTheme.bodyMedium.copyWith(
                        color: AppTheme.primaryColor,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // PageView content
            Expanded(
              child: PageView(
                controller: _pageController,
                onPageChanged: (index) {
                  setState(() {
                    _currentPage = index;
                  });
                },
                children: [
                  _buildWelcomePage(),
                  _buildIMEKeyboardPage(),
                  _buildAccessibilityServicePage(),
                  _buildGetStartedPage(),
                ],
              ),
            ),

            // Navigation buttons
            Padding(
              padding: const EdgeInsets.all(24.0),
              child: SizedBox(
                width: double.infinity,
                height: 56,
                // THEME UPDATE: Use the themed ElevatedButton.
                child: ElevatedButton(
                  onPressed: _nextPage,
                  // The style is now automatically applied from the theme's ElevatedButtonThemeData.
                  child: Text(
                    _currentPage == _totalPages - 1
                        ? 'Get Started'
                        : 'Continue',
                    // The button text style is also applied from the theme.
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Helper widget for consistent page icons
  Widget _buildPageIcon(IconData icon) {
    return Container(
      width: 120,
      height: 120,
      decoration: BoxDecoration(
        color: AppTheme.primaryColor.withOpacity(0.1),
        shape: BoxShape.circle,
      ),
      child: Icon(
        icon,
        size: 60,
        color: AppTheme.primaryColor,
      ),
    );
  }

  Widget _buildWelcomePage() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _buildPageIcon(CupertinoIcons.sparkles),
          const SizedBox(height: 48),
          Text(
            'Welcome to Rewordium AI',
            textAlign: TextAlign.center,
            // THEME UPDATE: Use heading style from AppTheme.
            style: AppTheme.headingLarge,
          ),
          const SizedBox(height: 24),
          Text(
            'Your ultimate AI-powered writing assistant that works seamlessly across all your apps.',
            textAlign: TextAlign.center,
            // THEME UPDATE: Use body style with secondary text color.
            style: AppTheme.bodyLarge.copyWith(
              color: AppTheme.textSecondaryColor,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildIMEKeyboardPage() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _buildPageIcon(CupertinoIcons.keyboard),
          const SizedBox(height: 48),
          Text(
            'Smart AI Keyboard',
            textAlign: TextAlign.center,
            style: AppTheme.headingLarge,
          ),
          const SizedBox(height: 24),
          Text(
            'Experience intelligent typing with real-time AI suggestions, smart autocorrect, and context-aware predictions.',
            textAlign: TextAlign.center,
            style: AppTheme.bodyLarge.copyWith(
              color: AppTheme.textSecondaryColor,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 32),
          Column(
            children: [
              _buildFeatureRow(
                  CupertinoIcons.wand_rays, 'AI-Powered Suggestions'),
              _buildFeatureRow(CupertinoIcons.textformat, 'Smart Autocorrect'),
              _buildFeatureRow(CupertinoIcons.eye, 'Context Awareness'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAccessibilityServicePage() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _buildPageIcon(CupertinoIcons.bubble_left_bubble_right),
          const SizedBox(height: 48),
          Text(
            'Accessibility Service',
            textAlign: TextAlign.center,
            style: AppTheme.headingLarge,
          ),
          const SizedBox(height: 24),
          Text(
            'Enable our service to get AI assistance in any app via a floating bubble. We respect your privacy.',
            textAlign: TextAlign.center,
            style: AppTheme.bodyLarge.copyWith(
              color: AppTheme.textSecondaryColor,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 32),
          Column(
            children: [
              _buildFeatureRow(CupertinoIcons.app_badge, 'Works in Any App'),
              _buildFeatureRow(
                  CupertinoIcons.hand_point_left, 'One-Tap Assistance'),
              _buildFeatureRow(
                  CupertinoIcons.shield_lefthalf_fill, 'Privacy Protected'),
            ],
          ),
          const SizedBox(height: 32),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppTheme.primaryColor.withOpacity(0.05),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppTheme.primaryColor.withOpacity(0.1)),
            ),
            child: Row(
              children: [
                Icon(
                  CupertinoIcons.info_circle,
                  color: AppTheme.primaryColor,
                  size: 20,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'We\'ll guide you through enabling this service later.',
                    style: AppTheme.bodyMedium
                        .copyWith(color: AppTheme.textSecondaryColor),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGetStartedPage() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _buildPageIcon(CupertinoIcons.checkmark_alt),
          const SizedBox(height: 48),
          Text(
            'You\'re All Set!',
            textAlign: TextAlign.center,
            style: AppTheme.headingLarge,
          ),
          const SizedBox(height: 24),
          Text(
            'Start creating amazing content with intelligent assistance at your fingertips. Ready to transform your writing?',
            textAlign: TextAlign.center,
            style: AppTheme.bodyLarge.copyWith(
              color: AppTheme.textSecondaryColor,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFeatureRow(IconData icon, String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            size: 20,
            color: AppTheme.primaryColor,
          ),
          const SizedBox(width: 16),
          Text(
            title,
            style: AppTheme.bodyMedium.copyWith(
              fontWeight: FontWeight.w500,
              color: AppTheme.textPrimaryColor,
            ),
          ),
        ],
      ),
    );
  }
}
