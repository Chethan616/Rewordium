import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'dart:async';

import '../providers/auth_provider.dart';
import '../services/play_integrity_service.dart';
import '../services/force_update_service.dart';
import 'auth/login_screen.dart';
import 'integrity_blocked_screen.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _animationController;
  String _statusText = '';
  bool _integrityPassed = true;
  String _failReason = '';

  @override
  void initState() {
    super.initState();

    // Start the Lottie animation immediately.
    _animationController = AnimationController(vsync: this);

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
    _setStatus('Verifying installation...');

    // Play Integrity check — uses a 24-hour cache so it is fast on subsequent
    // cold starts (typically < 50 ms).  Only hits the network on first open
    // after install or app update.
    try {
      _integrityPassed = await PlayIntegrityService.checkIntegrityWithCache();
      if (!_integrityPassed) {
        final verdict = await PlayIntegrityService.getIntegrityVerdict();
        _failReason =
            verdict?['reason'] as String? ?? 'Installation verification failed.';
      }
    } catch (_) {
      // On unexpected error, allow the app through (network issue ≠ tampered).
      _integrityPassed = true;
    }

    _setStatus('');
  }

  void _setStatus(String text) {
    if (mounted) setState(() => _statusText = text);
  }

  // ── Navigation ────────────────────────────────

  void _navigate() {
    if (!mounted) return;

    if (!_integrityPassed) {
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          pageBuilder: (_, __, ___) =>
              IntegrityBlockedScreen(reason: _failReason),
          transitionDuration: const Duration(milliseconds: 400),
          transitionsBuilder: (_, anim, __, child) =>
              FadeTransition(opacity: anim, child: child),
        ),
      );
      return;
    }

    // Integrity passed — route based on auth state.
    final authProvider = Provider.of<AuthProvider>(context, listen: false);
    if (authProvider.isLoggedIn) {
      Navigator.of(context).pushReplacementNamed('/home');
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
  }

  // ── Dispose ───────────────────────────────────

  @override
  void dispose() {
    _animationController.dispose();
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
                    Text(
                      'Rewordium',
                      style: TextStyle(
                        fontFamily: 'Pacifico',
                        fontSize: 42,
                        fontWeight: FontWeight.bold,
                        color: colorScheme.primary,
                        letterSpacing: 0.5,
                      ),
                    ),
                    const SizedBox(height: 32),
                    SizedBox(
                      height: 180,
                      width: 180,
                      child: Lottie.asset(
                        'assets/lottie/loading.json',
                        controller: _animationController,
                        fit: BoxFit.contain,
                        frameRate: FrameRate(60),
                        onLoaded: (composition) {
                          _animationController.duration = composition.duration;
                          _animationController.repeat();
                        },
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
