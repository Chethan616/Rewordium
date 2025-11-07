import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'dart:async';
import 'package:provider/provider.dart';

import '../theme/app_theme.dart';
import '../main.dart';
import '../services/groq_service.dart';
import '../services/firebase_service.dart';
import '../services/force_update_service.dart';
import '../providers/auth_provider.dart';
import '../screens/auth/login_screen.dart';
import 'package:google_fonts/google_fonts.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _animationController;
  bool _animationCompleted = false;
  bool _servicesInitialized = false;
  bool _hasError = false;
  String _errorMessage = '';
  Timer? _serviceCheckTimer;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 3),
    );

    _animationController.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        setState(() {
          _animationCompleted = true;
        });
        // Check if we can navigate to home page
        _checkAndNavigate();
      }
    });

    // Start the initialization process
    _initializeApp();

    // Auto-play the animation
    _animationController.forward();
  }

  Future<void> _initializeApp() async {
    try {
      setState(() => _isLoading = true);

      // Initialize Firebase if not already done
      if (!isFirebaseInitialized) {
        try {
          await FirebaseService.initializeFirebase();
          isFirebaseInitialized = true;
          debugPrint('Firebase initialized successfully');
        } catch (e) {
          debugPrint('Error initializing Firebase: $e');
          _showError(
              'Failed to initialize Firebase. Some features may not work correctly.');
        }
      }

      // Initialize Groq service
      if (!isGroqInitialized) {
        try {
          await GroqService.initialize();
          isGroqInitialized = true;
          debugPrint('Groq service initialized successfully');
        } catch (e) {
          debugPrint('Error initializing Groq service: $e');
          _showError(
              'Failed to initialize AI services. Some features may be limited.');
        }
      }

      // Mark services as initialized
      if (mounted) {
        setState(() {
          _servicesInitialized = isFirebaseInitialized && isGroqInitialized;
          _isLoading = false;
        });
        _checkAndNavigate();
      }
    } catch (e) {
      debugPrint('Error during app initialization: $e');
      _showError(
          'An error occurred during initialization. Please restart the app.');
    }
  }

  void _showError(String message) {
    if (mounted) {
      setState(() {
        _hasError = true;
        _errorMessage = message;
        _isLoading = false;
      });
    }
  }

  // Check if both animation is complete and services are initialized before navigating
  void _checkAndNavigate() {
    if (_animationCompleted && (_servicesInitialized || _hasError)) {
      // Add a small delay before navigation for a smoother transition
      Timer(const Duration(milliseconds: 500), () {
        if (mounted) {
          // Show error if there were initialization errors
          if (_hasError) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(_errorMessage),
                  duration: const Duration(seconds: 5),
                ),
              );
            });
          }

          // Check authentication status
          final authProvider =
              Provider.of<AuthProvider>(context, listen: false);

          if (authProvider.isLoggedIn) {
            // User is already logged in, go to home
            Navigator.of(context).pushReplacementNamed('/home');
          } else {
            // User is not logged in, show sign in page
            Navigator.of(context).pushReplacement(
              PageRouteBuilder(
                pageBuilder: (context, animation, secondaryAnimation) =>
                    const LoginScreen(),
                transitionDuration: const Duration(milliseconds: 800),
                transitionsBuilder:
                    (context, animation, secondaryAnimation, child) {
                  return FadeTransition(
                    opacity: animation,
                    child: child,
                  );
                },
              ),
            );
          }

          // Initialize force update service after navigation
          ForceUpdateService.initialize();
        }
      });
    }
  }

  Widget _buildLottieAnimation() {
    return Lottie.asset(
      'assets/lottie/loading.json',
      controller: _animationController,
      fit: BoxFit.contain,
      frameRate: FrameRate(60),
      onLoaded: (composition) {
        _animationController.duration = composition.duration;
        _animationController.forward();
      },
    );
  }

  @override
  void dispose() {
    _animationController.dispose();
    _serviceCheckTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // App logo or name with animation
            AnimatedOpacity(
              opacity: _animationCompleted ? 0.0 : 1.0,
              duration: const Duration(milliseconds: 500),
              child: Text(
                'Rewordium',
                style: GoogleFonts.pacifico(
                  textStyle: TextStyle(
                    fontSize: 42,
                    fontWeight: FontWeight.w400,
                    color: AppTheme.primaryColor,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
            ),

            const SizedBox(height: 40),

            // Loading animation
            SizedBox(
              height: 200,
              width: 200,
              child: _buildLottieAnimation(),
            ),

            // Error message (shown below the animation if there's an error)
            if (_hasError)
              Padding(
                padding: const EdgeInsets.only(top: 20.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.error_outline,
                      color: Colors.orange,
                      size: 32,
                    ),
                    const SizedBox(height: 12),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 24.0),
                      child: Text(
                        _errorMessage,
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                          fontSize: 14,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: _initializeApp,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryColor,
                        foregroundColor: Colors.white,
                      ),
                      child: const Text('Retry'),
                    ),
                  ],
                ),
              ),

            const SizedBox(height: 40),

            // Status or tagline
            AnimatedOpacity(
              opacity: _animationCompleted ? 0.0 : 1.0,
              duration: const Duration(milliseconds: 500),
              child: Column(
                children: [
                  const SizedBox(height: 20),
                  Text(
                    _isLoading
                        ? 'Initializing services...'
                        : 'Your AI Writing Assistant',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: _isLoading
                          ? Colors.grey
                          : Theme.of(context).textTheme.bodyLarge?.color,
                    ),
                  ),
                  const SizedBox(height: 8),
                  if (!_servicesInitialized && !_hasError)
                    Text(
                      'Loading services...',
                      style: TextStyle(
                        fontSize: 14,
                        color: Theme.of(context)
                            .textTheme
                            .bodySmall
                            ?.color
                            ?.withOpacity(0.7),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
