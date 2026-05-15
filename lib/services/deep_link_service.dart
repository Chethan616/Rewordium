import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../main.dart';
import '../screens/advanced_ai_settings_screen.dart';
import '../utils/app_logger.dart';

class DeepLinkService {
  static const _channel =
      MethodChannel('com.noxquill.rewordium/deep_link');

  static bool _isInitialized = false;
  static bool _isNavigationReady = false;
  static bool _flushRetryScheduled = false;
  static bool _pendingAiSettingsOpen = false;

  static final List<String> _queuedRoutes = <String>[];

  static String? _lastHandledRoute;
  static DateTime? _lastHandledAt;

  static int _retryCount = 0;
  static const int _maxRetries = 25;

  static bool get _isAndroid =>
      defaultTargetPlatform == TargetPlatform.android;

  /// Initialize service
  static void initialize() {
    if (!_isAndroid) return;
    if (_isInitialized) return;

    _isInitialized = true;

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

        AppLogger.init(
          'DeepLink: Received navigation request: $normalizedRoute',
        );

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

    _checkPendingDeepLink();
  }

  /// Check pending deep links on startup
  static Future<void> _checkPendingDeepLink() async {
    try {
      final pendingLinks =
          await _channel.invokeListMethod<dynamic>(
        'getPendingDeepLinks',
      );

      if (pendingLinks != null && pendingLinks.isNotEmpty) {
        for (final pending in pendingLinks) {
          final normalizedRoute =
              _normalizeRoute(pending?.toString());

          if (normalizedRoute != null) {
            AppLogger.init(
              'DeepLink: Found queued deep link: $normalizedRoute',
            );

            _enqueueRoute(normalizedRoute);
          }
        }

        return;
      }

      final pendingLink =
          await _channel.invokeMethod<String>(
        'getPendingDeepLink',
      );

      if (pendingLink != null) {
        final normalizedRoute =
            _normalizeRoute(pendingLink);

        if (normalizedRoute != null) {
          AppLogger.init(
            'DeepLink: Found pending deep link: $normalizedRoute',
          );

          _enqueueRoute(normalizedRoute);
        }
      }
    } catch (e) {
      AppLogger.warning(
        'DeepLink: Error checking pending deep link: $e',
      );
    }
  }

  static void _enqueueRoute(String route) {
    if (_queuedRoutes.contains(route)) {
      return;
    }

    _queuedRoutes.add(route);

    _flushQueuedRoutes();
  }

  static void _flushQueuedRoutes() {
    if (!_isNavigationReady) return;

    final navigator = navigatorKey.currentState;

    if (navigator == null) {
      _scheduleFlushRetry();
      return;
    }

    _retryCount = 0;

    while (_queuedRoutes.isNotEmpty) {
      final route = _queuedRoutes.removeAt(0);

      _handleNavigation(route);
    }
  }

  static void _scheduleFlushRetry() {
    if (_flushRetryScheduled) return;

    if (_retryCount >= _maxRetries) {
      AppLogger.warning(
        'DeepLink: Max retry count reached',
      );

      return;
    }

    _retryCount++;

    _flushRetryScheduled = true;

    Future<void>.delayed(
      const Duration(milliseconds: 250),
      () {
        _flushRetryScheduled = false;
        _flushQueuedRoutes();
      },
    );
  }

  static String? _normalizeRoute(String? route) {
    final raw = route?.trim().toLowerCase();

    if (raw == null || raw.isEmpty) {
      return null;
    }

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
      AppLogger.warning(
        'DeepLink: Navigator state is null',
      );

      return;
    }

    navigator.popUntil(
      (route) => route.settings.name == '/home' || route.isFirst,
    );

    final context = navigatorKey.currentContext;

    final currentRouteName =
        context != null
            ? ModalRoute.of(context)?.settings.name
            : null;

    if (currentRouteName != '/home') {
      navigator.pushNamedAndRemoveUntil(
        '/home',
        (route) => false,
      );
    }
  }

  static void _openAdvancedAiSettings() {
    if (_pendingAiSettingsOpen) return;

    _pendingAiSettingsOpen = true;

    Future<void>.delayed(
      const Duration(milliseconds: 650),
      () {
        _pendingAiSettingsOpen = false;

        final navigator = navigatorKey.currentState;
        final context = navigatorKey.currentContext;

        if (navigator == null || context == null) {
          _enqueueRoute('ai_settings');
          return;
        }

        navigator.push(
          MaterialPageRoute(
            builder: (_) =>
                const AdvancedAISettingsScreen(),
          ),
        );
      },
    );
  }

  /// Handle navigation
  static void _handleNavigation(String? route) {
    if (route == null) return;

    final now = DateTime.now();

    if (_lastHandledRoute == route &&
        _lastHandledAt != null &&
        now
                .difference(_lastHandledAt!)
                .inMilliseconds <
            1200) {
      AppLogger.warning(
        'DeepLink: Ignoring duplicate route: $route',
      );

      return;
    }

    _lastHandledRoute = route;
    _lastHandledAt = now;

    final navigator = navigatorKey.currentState;

    if (navigator == null) {
      AppLogger.warning(
        'DeepLink: Navigator not ready, queueing: $route',
      );

      _enqueueRoute(route);

      return;
    }

    switch (route) {
      case 'ai_settings':
        _navigateToHomeTab(3);

        Future<void>.delayed(
          const Duration(milliseconds: 600),
          () {
            _openAdvancedAiSettings();
          },
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
        AppLogger.warning(
          'DeepLink: Unknown route: $route',
        );
    }
  }
}
