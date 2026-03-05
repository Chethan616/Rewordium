import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:provider/provider.dart';
import 'dart:async';

import '../theme/app_theme.dart';
import '../services/play_integrity_service.dart';
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
    // Skip all checks on non-Android platforms
    if (defaultTargetPlatform != TargetPlatform.android) {
      setState(() {
        _step1State = _StepState.passed;
        _step2State = _StepState.passed;
        _step3State = _StepState.passed;
        _statusText = 'Verified';
        _isComplete = true;
      });
      _navigateToApp();
      return;
    }

    try {
      // Initialize the integrity service
      await PlayIntegrityService.initialize();

      // STEP 1: Check installation source
      setState(() {
        _step1State = _StepState.checking;
        _statusText = 'Checking installation source...';
      });

      await Future.delayed(const Duration(milliseconds: 600));

      final integrityPassed = await PlayIntegrityService.checkIntegrity();

      if (!integrityPassed) {
        // Get detailed verdict to know which check failed
        final verdict = await PlayIntegrityService.getIntegrityVerdict();
        final isFromPlayStore = verdict?['isFromPlayStore'] as bool? ?? false;
        final signatureValid = verdict?['signatureValid'] as bool? ?? false;
        final reason = verdict?['reason'] as String? ??
            'Installation verification failed';

        if (!isFromPlayStore) {
          // Step 1 failed — not from Play Store
          setState(() {
            _step1State = _StepState.failed;
            _statusText = 'Not installed from Play Store';
          });
          await Future.delayed(const Duration(milliseconds: 500));
          _handleFailure(reason);
          return;
        }

        if (!signatureValid) {
          // Step 1 passed, step 2 failed
          setState(() {
            _step1State = _StepState.passed;
            _step2State = _StepState.failed;
            _statusText = 'App signature mismatch';
          });
          await Future.delayed(const Duration(milliseconds: 500));
          _handleFailure(reason);
          return;
        }

        // Generic failure
        setState(() {
          _step1State = _StepState.failed;
          _statusText = 'Verification failed';
        });
        await Future.delayed(const Duration(milliseconds: 500));
        _handleFailure(reason);
        return;
      }

      // Step 1 passed
      setState(() {
        _step1State = _StepState.passed;
      });

      // STEP 2: Signature check (already passed within checkIntegrity)
      setState(() {
        _step2State = _StepState.checking;
        _statusText = 'Verifying app signature...';
      });
      await Future.delayed(const Duration(milliseconds: 500));
      setState(() {
        _step2State = _StepState.passed;
      });

      // STEP 3: Play Services (non-blocking)
      setState(() {
        _step3State = _StepState.checking;
        _statusText = 'Confirming Play Services...';
      });
      await Future.delayed(const Duration(milliseconds: 400));

      final isSecure = await PlayIntegrityService.isDeviceSecure();
      setState(() {
        _step3State = isSecure ? _StepState.passed : _StepState.warning;
        _statusText = 'Verified';
        _isComplete = true;
      });

      // All checks passed — navigate to app
      _navigateToApp();
    } catch (e) {
      debugPrint('Installer verification error: $e');
      _handleFailure('Security verification failed.');
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
        backgroundColor: AppTheme.backgroundColor,
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
                            ? AppTheme.errorColor.withOpacity(0.1)
                            : _isComplete
                                ? AppTheme.primaryColor.withOpacity(0.1)
                                : AppTheme.primaryColor.withOpacity(0.08),
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
                            ? AppTheme.errorColor
                            : AppTheme.primaryColor,
                      ),
                    ),
                  ),

                  const SizedBox(height: 32),

                  // Title
                  Text(
                    _hasFailed ? 'Verification Failed' : 'Verifying Installation',
                    style: AppTheme.headingMedium.copyWith(
                      color: AppTheme.textPrimaryColor,
                    ),
                  ),

                  const SizedBox(height: 8),

                  // Status text
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    child: Text(
                      _statusText,
                      key: ValueKey(_statusText),
                      style: AppTheme.bodyMedium.copyWith(
                        color: AppTheme.textSecondaryColor,
                      ),
                    ),
                  ),

                  const SizedBox(height: 40),

                  // Verification steps card
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: AppTheme.cardDecoration,
                    child: Column(
                      children: [
                        _buildStepRow(
                          'Installation source',
                          'Checking Play Store vendor',
                          _step1State,
                          AppTheme.primaryColor,
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Divider(
                            height: 1,
                            color:
                                AppTheme.textSecondaryColor.withOpacity(0.15),
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
                                AppTheme.textSecondaryColor.withOpacity(0.15),
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
                    style: AppTheme.bodySmall.copyWith(
                      color: AppTheme.textSecondaryColor.withOpacity(0.5),
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
            color: _getStepColor(state, accentColor).withOpacity(0.12),
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
                style: AppTheme.bodyMedium.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: AppTheme.bodySmall.copyWith(
                  color: AppTheme.textSecondaryColor,
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
                  ? AppTheme.primaryColor
                  : state == _StepState.warning
                      ? Colors.orange
                      : AppTheme.errorColor,
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
          color: AppTheme.textSecondaryColor.withOpacity(0.4),
        );
      case _StepState.checking:
        return SizedBox(
          width: 20,
          height: 20,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            valueColor: AlwaysStoppedAnimation<Color>(accentColor),
          ),
        );
      case _StepState.passed:
        return Icon(
          CupertinoIcons.checkmark,
          size: 20,
          color: AppTheme.primaryColor,
        );
      case _StepState.failed:
        return Icon(
          CupertinoIcons.xmark,
          size: 20,
          color: AppTheme.errorColor,
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
        return AppTheme.textSecondaryColor;
      case _StepState.checking:
        return accentColor;
      case _StepState.passed:
        return AppTheme.primaryColor;
      case _StepState.failed:
        return AppTheme.errorColor;
      case _StepState.warning:
        return Colors.orange;
    }
  }
}

enum _StepState { pending, checking, passed, failed, warning }
