import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../main.dart';

class OnboardingPage extends StatefulWidget {
  const OnboardingPage({super.key});

  /// Returns true if onboarding has been completed before.
  static Future<bool> hasCompleted() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool('onboarding_completed') ?? false;
  }

  /// Marks onboarding as completed.
  static Future<void> markCompleted() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarding_completed', true);
  }

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<OnboardingPage>
    with TickerProviderStateMixin {
  late PageController _pageController;
  int _currentPage = 0;

  late AnimationController _fadeController;
  late AnimationController _slideController;
  late Animation<double> _fadeAnimation;
  late Animation<Offset> _slideAnimation;

  static const _pages = [
    _OnboardingData(
      icon: CupertinoIcons.sparkles,
      title: 'Welcome to Rewordium AI',
      subtitle:
          'Your all-in-one AI writing assistant — scan, translate, rewrite, and chat across every app.',
      features: [],
    ),
    _OnboardingData(
      icon: CupertinoIcons.doc_text_viewfinder,
      title: 'Scan & Extract',
      subtitle:
          'Point your camera at any document — receipts, notes, articles — and instantly extract editable text.',
      features: [
        _FeatureItem(CupertinoIcons.camera, 'Camera & gallery import'),
        _FeatureItem(CupertinoIcons.doc_richtext, 'PDF & image support'),
        _FeatureItem(CupertinoIcons.wand_rays, 'AI-enhanced OCR'),
      ],
    ),
    _OnboardingData(
      icon: CupertinoIcons.globe,
      title: 'Translate Anything',
      subtitle:
          'Translate text between 50+ languages with AI-powered accuracy and natural phrasing.',
      features: [
        _FeatureItem(CupertinoIcons.arrow_right_arrow_left, 'Instant translation'),
        _FeatureItem(CupertinoIcons.text_badge_checkmark, 'Context-aware results'),
        _FeatureItem(CupertinoIcons.keyboard, 'Works from the keyboard'),
      ],
    ),
    _OnboardingData(
      icon: CupertinoIcons.chat_bubble_2,
      title: 'Chat with Jade AI',
      subtitle:
          'Ask questions, brainstorm ideas, or get writing help — your personal AI assistant is always ready.',
      features: [
        _FeatureItem(CupertinoIcons.lightbulb, 'Smart suggestions'),
        _FeatureItem(CupertinoIcons.pencil_ellipsis_rectangle, 'Rewrite & improve text'),
        _FeatureItem(CupertinoIcons.bubble_left_bubble_right, 'Conversational AI'),
      ],
    ),
    _OnboardingData(
      icon: CupertinoIcons.keyboard,
      title: 'AI Keyboard',
      subtitle:
          'Type smarter everywhere — get AI suggestions, autocorrect, and quick actions right from your keyboard.',
      features: [
        _FeatureItem(CupertinoIcons.wand_rays, 'AI-powered suggestions'),
        _FeatureItem(CupertinoIcons.app_badge, 'Works in any app'),
        _FeatureItem(CupertinoIcons.bolt, 'One-tap actions'),
      ],
    ),
  ];

  @override
  void initState() {
    super.initState();
    _pageController = PageController();

    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    _slideController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    );

    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeOut,
    );
    _slideAnimation = Tween<Offset>(
      begin: const Offset(0, 0.15),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _slideController,
      curve: Curves.easeOutCubic,
    ));

    _fadeController.forward();
    _slideController.forward();
  }

  @override
  void dispose() {
    _pageController.dispose();
    _fadeController.dispose();
    _slideController.dispose();
    super.dispose();
  }

  void _nextPage() {
    if (_currentPage < _pages.length - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOutCubic,
      );
    } else {
      _complete();
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

  Future<void> _complete() async {
    await OnboardingPage.markCompleted();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (context, animation, secondaryAnimation) =>
            const HomePage(),
        transitionDuration: const Duration(milliseconds: 600),
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

  void _onPageChanged(int index) {
    setState(() => _currentPage = index);
    _fadeController.reset();
    _slideController.reset();
    _fadeController.forward();
    _slideController.forward();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _currentPage > 0
                      ? IconButton(
                          onPressed: _previousPage,
                          icon: Icon(
                            CupertinoIcons.chevron_left,
                            color: cs.onSurface,
                            size: 24,
                          ),
                        )
                      : const SizedBox(width: 48),

                  // Dot indicators
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: List.generate(_pages.length, (i) {
                      final isActive = i == _currentPage;
                      return AnimatedContainer(
                        duration: const Duration(milliseconds: 300),
                        curve: Curves.easeInOut,
                        margin: const EdgeInsets.symmetric(horizontal: 4),
                        height: 8,
                        width: isActive ? 28 : 8,
                        decoration: BoxDecoration(
                          color: isActive
                              ? cs.primary
                              : cs.onSurface.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                      );
                    }),
                  ),

                  TextButton(
                    onPressed: _complete,
                    child: Text(
                      'Skip',
                      style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                            color: cs.primary,
                            fontWeight: FontWeight.w600,
                          ),
                    ),
                  ),
                ],
              ),
            ),

            // Pages
            Expanded(
              child: PageView.builder(
                controller: _pageController,
                onPageChanged: _onPageChanged,
                itemCount: _pages.length,
                itemBuilder: (context, index) =>
                    _buildPage(_pages[index]),
              ),
            ),

            // Bottom button
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
              child: SizedBox(
                width: double.infinity,
                height: 56,
                child: ElevatedButton(
                  onPressed: _nextPage,
                  child: Text(
                    _currentPage == _pages.length - 1
                        ? 'Get Started'
                        : 'Continue',
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPage(_OnboardingData data) {
    final cs = Theme.of(context).colorScheme;
    return FadeTransition(
      opacity: _fadeAnimation,
      child: SlideTransition(
        position: _slideAnimation,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Icon circle
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  color: cs.primary.withOpacity(0.1),
                  shape: BoxShape.circle,
                ),
                child: Icon(data.icon, size: 56, color: cs.primary),
              ),
              const SizedBox(height: 40),

              // Title
              Text(
                data.title,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 16),

              // Subtitle
              Text(
                data.subtitle,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                      color: cs.onSurfaceVariant,
                      height: 1.5,
                    ),
              ),

              // Feature rows (if any)
              if (data.features.isNotEmpty) ...[
                const SizedBox(height: 32),
                ...data.features.map((f) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Container(
                            width: 36,
                            height: 36,
                            decoration: BoxDecoration(
                              color: cs.primaryContainer,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Icon(f.icon, size: 18,
                                color: cs.onPrimaryContainer),
                          ),
                          const SizedBox(width: 14),
                          Text(
                            f.label,
                            style: Theme.of(context)
                                .textTheme
                                .bodyMedium!
                                .copyWith(
                                  fontWeight: FontWeight.w500,
                                  color: cs.onSurface,
                                ),
                          ),
                        ],
                      ),
                    )),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _OnboardingData {
  final IconData icon;
  final String title;
  final String subtitle;
  final List<_FeatureItem> features;

  const _OnboardingData({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.features,
  });
}

class _FeatureItem {
  final IconData icon;
  final String label;
  const _FeatureItem(this.icon, this.label);
}
