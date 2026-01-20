import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import '../main.dart';
import '../screens/advanced_ai_settings_screen.dart';
import '../utils/app_logger.dart';

/// Service to handle deep links from Android app shortcuts
class DeepLinkService {
  static const _channel = MethodChannel('com.noxquill.rewordium/deep_link');
  static bool _isInitialized = false;

  /// Initialize the deep link service
  static void initialize() {
    if (_isInitialized) return;
    _isInitialized = true;

    // Listen for navigation requests from Android
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'navigateTo') {
        final route = call.arguments['route'] as String?;
        AppLogger.init('DeepLink: Received navigation request to $route');
        _handleNavigation(route);
      }
      return null;
    });

    // Check for pending deep links on startup
    _checkPendingDeepLink();
  }

  /// Check if there's a pending deep link from app launch
  static Future<void> _checkPendingDeepLink() async {
    try {
      final pendingLink = await _channel.invokeMethod<String>('getPendingDeepLink');
      if (pendingLink != null) {
        AppLogger.init('DeepLink: Found pending deep link: $pendingLink');
        // Delay navigation to ensure the app is fully loaded
        await Future.delayed(const Duration(milliseconds: 1000));
        _handleNavigation(pendingLink);
      }
    } catch (e) {
      AppLogger.warning('DeepLink: Error checking pending deep link: $e');
    }
  }

  /// Handle navigation to the appropriate screen
  static void _handleNavigation(String? route) {
    if (route == null) return;

    final context = navigatorKey.currentContext;
    if (context == null) {
      AppLogger.warning('DeepLink: Navigator context is null, cannot navigate');
      return;
    }

    switch (route) {
      case 'ai_settings':
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (context) => const AdvancedAISettingsScreen(),
          ),
        );
        break;
      default:
        AppLogger.warning('DeepLink: Unknown route: $route');
    }
  }
}
