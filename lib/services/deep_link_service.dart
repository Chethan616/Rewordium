import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import '../main.dart';
import '../screens/advanced_ai_settings_screen.dart';
import '../utils/app_logger.dart';

/// Service to handle deep links from Android app shortcuts
class DeepLinkService {
  static const _channel = MethodChannel('com.noxquill.rewordium/deep_link');
  static bool _isInitialized = false;
  static bool _isNavigationReady = false;
  static final List<String> _queuedRoutes = <String>[];

  static bool get _isAndroid => defaultTargetPlatform == TargetPlatform.android;

  /// Initialize the deep link service
  static void initialize() {
    if (!_isAndroid) return;
    if (_isInitialized) return;
    _isInitialized = true;

    // Listen for navigation requests from Android
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'navigateTo') {
        String? route;
        final args = call.arguments;
        if (args is Map) {
          route = args['route']?.toString();
        } else {
          route = args?.toString();
        }

        final normalizedRoute = _normalizeRoute(route);
        AppLogger.init('DeepLink: Received navigation request to $normalizedRoute');
        if (normalizedRoute != null) {
          _enqueueRoute(normalizedRoute);
        }
      }
      return null;
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _isNavigationReady = true;
      _flushQueuedRoutes();
    });

    // Check for pending deep links on startup
    _checkPendingDeepLink();
  }

  /// Check if there's a pending deep link from app launch
  static Future<void> _checkPendingDeepLink() async {
    try {
      final pendingLinks = await _channel.invokeListMethod<dynamic>('getPendingDeepLinks');
      if (pendingLinks != null && pendingLinks.isNotEmpty) {
        for (final pending in pendingLinks) {
          final normalizedRoute = _normalizeRoute(pending?.toString());
          if (normalizedRoute != null) {
            AppLogger.init('DeepLink: Found queued deep link: $normalizedRoute');
            _enqueueRoute(normalizedRoute);
          }
        }
        return;
      }

      final pendingLink = await _channel.invokeMethod<String>('getPendingDeepLink');
      if (pendingLink != null) {
        final normalizedRoute = _normalizeRoute(pendingLink);
        if (normalizedRoute != null) {
          AppLogger.init('DeepLink: Found pending deep link: $normalizedRoute');
          _enqueueRoute(normalizedRoute);
        }
      }
    } catch (e) {
      AppLogger.warning('DeepLink: Error checking pending deep link: $e');
    }
  }

  static void _enqueueRoute(String route) {
    _queuedRoutes.add(route);
    _flushQueuedRoutes();
  }

  static void _flushQueuedRoutes() {
    if (!_isNavigationReady) return;
    if (navigatorKey.currentContext == null || navigatorKey.currentState == null) {
      return;
    }

    while (_queuedRoutes.isNotEmpty) {
      final route = _queuedRoutes.removeAt(0);
      _handleNavigation(route);
    }
  }

  static String? _normalizeRoute(String? route) {
    final raw = route?.trim().toLowerCase();
    if (raw == null || raw.isEmpty) return null;

    switch (raw) {
      case 'ai_settings':
      case 'ai-settings':
      case 'aisettings':
      case 'ai':
      case 'jade_ai':
      case 'jade-ai':
      case 'jadeai':
        return 'ai_settings';
      case 'home':
        return 'home';
      case 'settings':
      case 'app_settings':
      case 'app-settings':
        return 'settings';
      case 'paraphraser':
      case 'paraphrase':
      case 'rewrite':
        return 'paraphraser';
      case 'grammar':
      case 'grammar_check':
      case 'grammar-check':
        return 'grammar';
      case 'tools':
      case 'tool':
        return 'tools';
      default:
        return null;
    }
  }

  static void _navigateToHomeTab(int tabIndex) {
    homeTabNavigationRequest.value = tabIndex;
    final navigator = navigatorKey.currentState;
    if (navigator == null) {
      AppLogger.warning('DeepLink: Navigator state is null for home tab route');
      return;
    }

    navigator.popUntil((route) => route.settings.name == '/home' || route.isFirst);

    final context = navigatorKey.currentContext;
    final currentRouteName = context != null ? ModalRoute.of(context)?.settings.name : null;
    if (currentRouteName != '/home') {
      navigator.pushNamedAndRemoveUntil('/home', (route) => false);
    }
  }

  /// Handle navigation to the appropriate screen
  static void _handleNavigation(String? route) {
    if (route == null) return;

    final context = navigatorKey.currentContext;
    if (context == null) {
      AppLogger.warning('DeepLink: Navigator context is null, queueing route: $route');
      _enqueueRoute(route);
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
      case 'home':
        _navigateToHomeTab(0);
        break;
      case 'paraphraser':
        _navigateToHomeTab(1);
        break;
      case 'grammar':
        _navigateToHomeTab(2);
        break;
      case 'settings':
        _navigateToHomeTab(3);
        break;
      case 'tools':
        _navigateToHomeTab(0);
        break;
      default:
        AppLogger.warning('DeepLink: Unknown route: $route');
    }
  }
}
