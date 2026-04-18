import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'dart:async';

import '../providers/auth_provider.dart';
import '../services/force_update_service.dart';
import '../services/in_app_update_service.dart';
import 'auth/login_screen.dart';
import '../pages/onboarding_page.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with TickerProviderStateMixin {
  late AnimationController _animationController;
  late AnimationController _entranceController;
  late Animation<double> _titleFade;
  late Animation<Offset> _titleSlide;
  late Animation<double> _lottieFade;
  late Animation<double> _lottieScale;
  String _statusText = '';

  @override
  void initState() {
    super.initState();

    // Start the Lottie animation immediately.
    _animationController = AnimationController(vsync: this);

    // Staggered entrance animations
    _entranceController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _titleFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
          parent: _entranceController,
          curve: const Interval(0.0, 0.5, curve: Curves.easeOut)),
    );
    _titleSlide =
        Tween<Offset>(begin: const Offset(0, -0.3), end: Offset.zero).animate(
      CurvedAnimation(
          parent: _entranceController,
          curve: const Interval(0.0, 0.5, curve: Curves.easeOutCubic)),
    );
    _lottieFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
          parent: _entranceController,
          curve: const Interval(0.3, 0.8, curve: Curves.easeOut)),
    );
    _lottieScale = Tween<double>(begin: 0.7, end: 1.0).animate(
      CurvedAnimation(
          parent: _entranceController,
          curve: const Interval(0.3, 0.8, curve: Curves.easeOutBack)),
    );
    _entranceController.forward();

    // Kick off startup logic: gate on max(1.5 s, _doStartup()).
    // This means we show the splash for at least 1.5 s (so it doesn't flash),
    // and no longer than however long the async work takes.
    Future.wait([
      Future.delayed(const Duration(milliseconds: 1500)),
      _doStartup(),
    ]).then((_) => _navigate());
  }

  // ── Startup work ──────────────────────────────

  Future<void> _doStartup() async {
    _setStatus('Preparing app...');
    await Future<void>.delayed(const Duration(milliseconds: 200));

    _setStatus('');
  }

  void _setStatus(String text) {
    if (mounted) setState(() => _statusText = text);
  }

  // ── Navigation ────────────────────────────────

  Future<void> _navigate() async {
    if (!mounted) return;

    // Integrity passed — route based on auth state.
    final authProvider = Provider.of<AuthProvider>(context, listen: false);
    if (authProvider.isLoggedIn) {
      final seenOnboarding = await OnboardingPage.hasCompleted();
      if (!mounted) return;
      if (seenOnboarding) {
        Navigator.of(context).pushReplacementNamed('/home');
      } else {
        Navigator.of(context).pushReplacement(
          PageRouteBuilder(
            pageBuilder: (_, __, ___) => const OnboardingPage(),
            transitionDuration: const Duration(milliseconds: 500),
            transitionsBuilder: (_, anim, __, child) =>
                FadeTransition(opacity: anim, child: child),
          ),
        );
      }
    } else {
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          pageBuilder: (_, __, ___) => const LoginScreen(),
          transitionDuration: const Duration(milliseconds: 500),
          transitionsBuilder: (_, anim, __, child) =>
              FadeTransition(opacity: anim, child: child),
        ),
      );
    }

    // Initialize force-update service after navigation.
    ForceUpdateService.initialize();

    // Initialize Google Play In-App Update (flexible background download).
    InAppUpdateService.initialize();
    InAppUpdateService.checkForUpdate();
  }

  // ── Dispose ───────────────────────────────────

  @override
  void dispose() {
    _animationController.dispose();
    _entranceController.dispose();
    super.dispose();
  }

  // ── Build ─────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      backgroundColor: colorScheme.surfaceContainerLow,
      body: SafeArea(
        child: Column(
          children: [
            // ── Branding (centre) ──────────────
            Expanded(
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SlideTransition(
                      position: _titleSlide,
                      child: FadeTransition(
                        opacity: _titleFade,
                        child: Text(
                          'Rewordium',
                          style: TextStyle(
                            fontFamily: 'Pacifico',
                            fontSize: 42,
                            fontWeight: FontWeight.bold,
                            color: colorScheme.primary,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                    FadeTransition(
                      opacity: _lottieFade,
                      child: ScaleTransition(
                        scale: _lottieScale,
                        child: SizedBox(
                          height: 180,
                          width: 180,
                          child: Lottie.asset(
                            'assets/lottie/loading.json',
                            controller: _animationController,
                            fit: BoxFit.contain,
                            frameRate: FrameRate(60),
                            delegates: LottieDelegates(
                              values: [
                                ValueDelegate.color(
                                  const ['**'],
                                  value: colorScheme.primary,
                                ),
                              ],
                            ),
                            onLoaded: (composition) {
                              _animationController.duration =
                                  composition.duration;
                              _animationController.repeat();
                            },
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // ── Status text (bottom) ───────────
            Padding(
              padding: const EdgeInsets.only(bottom: 40),
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 300),
                child: _statusText.isEmpty
                    ? const SizedBox(key: ValueKey('empty'), height: 20)
                    : Row(
                        key: const ValueKey('status'),
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          LoadingIndicatorM3E(
                            color: colorScheme.primary,
                            constraints: const BoxConstraints(
                                maxWidth: 16, maxHeight: 16),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            _statusText,
                            style: textTheme.bodySmall?.copyWith(
                              color: colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
