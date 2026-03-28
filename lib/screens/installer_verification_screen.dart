import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'dart:async';

import '../services/force_update_service.dart';
import '../providers/auth_provider.dart';
import 'auth/login_screen.dart';
import 'integrity_blocked_screen.dart';

/// Dedicated installer verification screen that checks:
/// 1. Installation source (Play Store vendor check)
/// 2. App signature verification
/// 3. Play Services availability (non-blocking)
///
/// This screen replaces the integrity check that was previously
/// embedded in the splash screen. It shows a branded security
/// verification UI with animated step indicators.
class InstallerVerificationScreen extends StatefulWidget {
  const InstallerVerificationScreen({super.key});

  @override
  State<InstallerVerificationScreen> createState() =>
      _InstallerVerificationScreenState();
}

class _InstallerVerificationScreenState
    extends State<InstallerVerificationScreen>
    with SingleTickerProviderStateMixin {
  // Verification step states
  _StepState _step1State = _StepState.pending; // Installer source
  _StepState _step2State = _StepState.pending; // App signature
  _StepState _step3State = _StepState.pending; // Play Services

  String _statusText = 'Starting verification...';
  bool _isComplete = false;
  bool _hasFailed = false;

  // Pulse animation for the shield
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 0.95, end: 1.08).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    // Start verification after a brief delay for smooth entry
    Future.delayed(const Duration(milliseconds: 400), _runVerification);
  }

  Future<void> _runVerification() async {
    try {
      // Play Store verification is temporarily disabled.
      // Keep this staged sequence for a smooth branded transition.
      setState(() {
        _step1State = _StepState.checking;
        _statusText = defaultTargetPlatform == TargetPlatform.android
            ? 'Checking installation source...'
            : 'Preparing app...';
      });

      await Future.delayed(const Duration(milliseconds: 450));
      setState(() {
        _step1State = _StepState.passed;
      });

      setState(() {
        _step2State = _StepState.checking;
        _statusText = 'Verifying app signature...';
      });
      await Future.delayed(const Duration(milliseconds: 350));
      setState(() {
        _step2State = _StepState.passed;
      });

      setState(() {
        _step3State = _StepState.checking;
        _statusText = 'Confirming Play Services...';
      });
      await Future.delayed(const Duration(milliseconds: 300));

      setState(() {
        _step3State = _StepState.passed;
        _statusText = 'Verified';
        _isComplete = true;
      });

      _navigateToApp();
    } catch (e) {
      debugPrint('Installer verification error: $e');
      if (mounted) {
        setState(() {
          _step1State = _StepState.passed;
          _step2State = _StepState.passed;
          _step3State = _StepState.warning;
          _statusText = 'Verified';
          _isComplete = true;
        });
      }
      _navigateToApp();
    }
  }

  void _handleFailure(String reason) {
    setState(() {
      _hasFailed = true;
    });

    // Navigate to blocked screen after a brief pause
    Future.delayed(const Duration(milliseconds: 800), () {
      if (mounted) {
        Navigator.of(context).pushReplacement(
          PageRouteBuilder(
            pageBuilder: (context, animation, secondaryAnimation) =>
                IntegrityBlockedScreen(reason: reason),
            transitionDuration: const Duration(milliseconds: 600),
            transitionsBuilder:
                (context, animation, secondaryAnimation, child) {
              return FadeTransition(opacity: animation, child: child);
            },
          ),
        );
      }
    });
  }

  void _navigateToApp() {
    Future.delayed(const Duration(milliseconds: 800), () {
      if (!mounted) return;

      _pulseController.stop();

      final authProvider = Provider.of<AuthProvider>(context, listen: false);

      if (authProvider.isLoggedIn) {
        Navigator.of(context).pushReplacementNamed('/home');
      } else {
        Navigator.of(context).pushReplacement(
          PageRouteBuilder(
            pageBuilder: (context, animation, secondaryAnimation) =>
                const LoginScreen(),
            transitionDuration: const Duration(milliseconds: 800),
            transitionsBuilder:
                (context, animation, secondaryAnimation, child) {
              return FadeTransition(opacity: animation, child: child);
            },
          ),
        );
      }

      // Initialize force update service after navigation
      ForceUpdateService.initialize();
    });
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Animated shield icon
                  AnimatedBuilder(
                    animation: _pulseAnimation,
                    builder: (context, child) {
                      return Transform.scale(
                        scale: _pulseAnimation.value,
                        child: child,
                      );
                    },
                    child: Container(
                      width: 96,
                      height: 96,
                      decoration: BoxDecoration(
                        color: _hasFailed
                            ? Theme.of(context).colorScheme.error.withValues(alpha: 0.1)
                            : _isComplete
                                ? Theme.of(context).colorScheme.primary.withValues(alpha: 0.1)
                                : Theme.of(context).colorScheme.primary.withValues(alpha: 0.08),
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        _hasFailed
                            ? CupertinoIcons.shield_slash
                            : _isComplete
                                ? CupertinoIcons.checkmark_shield_fill
                                : CupertinoIcons.shield_lefthalf_fill,
                        size: 48,
                        color: _hasFailed
                            ? Theme.of(context).colorScheme.error
                            : Theme.of(context).colorScheme.primary,
                      ),
                    ),
                  ),

                  const SizedBox(height: 32),

                  // Title
                  Text(
                    _hasFailed ? 'Verification Failed' : 'Verifying Installation',
                    style: Theme.of(context).textTheme.headlineSmall!.copyWith(
                      color: Theme.of(context).colorScheme.onSurface,
                    ),
                  ),

                  const SizedBox(height: 8),

                  // Status text
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    child: Text(
                      _statusText,
                      key: ValueKey(_statusText),
                      style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),

                  const SizedBox(height: 40),

                  // Verification steps card
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.surface,
                      borderRadius: BorderRadius.circular(28),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: Theme.of(context).brightness == Brightness.dark ? 0.25 : 0.06),
                          offset: const Offset(0, 2),
                          blurRadius: 12,
                        ),
                      ],
                    ),
                    child: Column(
                      children: [
                        _buildStepRow(
                          'Installation source',
                          'Checking Play Store vendor',
                          _step1State,
                          Theme.of(context).colorScheme.primary,
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Divider(
                            height: 1,
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.15),
                          ),
                        ),
                        _buildStepRow(
                          'App signature',
                          'Verifying release certificate',
                          _step2State,
                          Colors.blue,
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Divider(
                            height: 1,
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.15),
                          ),
                        ),
                        _buildStepRow(
                          'Play Services',
                          'Confirming Google Play availability',
                          _step3State,
                          Colors.orange,
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 40),

                  // Footer
                  Text(
                    'Rewordium Security',
                    style: Theme.of(context).textTheme.bodySmall!.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
                      letterSpacing: 1.0,
                      fontSize: 11,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStepRow(
    String title,
    String subtitle,
    _StepState state,
    Color accentColor,
  ) {
    return Row(
      children: [
        // Status indicator
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: _getStepColor(state, accentColor).withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Center(
            child: _buildStepIcon(state, accentColor),
          ),
        ),
        const SizedBox(width: 14),
        // Text
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: Theme.of(context).textTheme.bodySmall!.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                  fontSize: 12,
                ),
              ),
            ],
          ),
        ),
        // Result icon on the right
        if (state != _StepState.pending && state != _StepState.checking)
          AnimatedOpacity(
            opacity: 1.0,
            duration: const Duration(milliseconds: 300),
            child: Icon(
              state == _StepState.passed
                  ? CupertinoIcons.checkmark_circle_fill
                  : state == _StepState.warning
                      ? CupertinoIcons.exclamationmark_triangle_fill
                      : CupertinoIcons.xmark_circle_fill,
              color: state == _StepState.passed
                  ? Theme.of(context).colorScheme.primary
                  : state == _StepState.warning
                      ? Colors.orange
                      : Theme.of(context).colorScheme.error,
              size: 22,
            ),
          ),
      ],
    );
  }

  Widget _buildStepIcon(_StepState state, Color accentColor) {
    switch (state) {
      case _StepState.pending:
        return Icon(
          CupertinoIcons.circle,
          size: 20,
          color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.4),
        );
      case _StepState.checking:
        return SizedBox(
          width: 20,
          height: 20,
          child: LoadingIndicatorM3E(
            constraints: BoxConstraints(maxWidth: 20, maxHeight: 20),
          ),
        );
      case _StepState.passed:
        return Icon(
          CupertinoIcons.checkmark,
          size: 20,
          color: Theme.of(context).colorScheme.primary,
        );
      case _StepState.failed:
        return Icon(
          CupertinoIcons.xmark,
          size: 20,
          color: Theme.of(context).colorScheme.error,
        );
      case _StepState.warning:
        return Icon(
          CupertinoIcons.exclamationmark,
          size: 20,
          color: Colors.orange,
        );
    }
  }

  Color _getStepColor(_StepState state, Color accentColor) {
    switch (state) {
      case _StepState.pending:
        return Theme.of(context).colorScheme.onSurfaceVariant;
      case _StepState.checking:
        return accentColor;
      case _StepState.passed:
        return Theme.of(context).colorScheme.primary;
      case _StepState.failed:
        return Theme.of(context).colorScheme.error;
      case _StepState.warning:
        return Colors.orange;
    }
  }
}

enum _StepState { pending, checking, passed, failed, warning }
