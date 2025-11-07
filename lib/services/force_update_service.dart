import 'package:flutter/material.dart';
import 'package:rewordium/services/version_service.dart';
import 'package:rewordium/services/connectivity_service.dart';
import 'package:rewordium/main.dart';

class ForceUpdateService {
  static bool _isCheckingUpdate = false;
  static bool _hasInitialized = false;
  static DateTime? _lastCheckTime;
  static const Duration _checkInterval = Duration(hours: 1); // Check every hour

  /// Initialize force update checking - this should be called from a widget after build
  static Future<void> initialize() async {
    if (_hasInitialized) return;
    _hasInitialized = true;

    // Schedule update check for next frame to ensure app is fully loaded
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      // Wait a bit more to ensure everything is settled
      await Future.delayed(const Duration(seconds: 1));
      await checkForUpdate();
    });
  }

  /// Check for app updates and show dialog if needed
  static Future<void> checkForUpdate({bool forceCheck = false}) async {
    if (_isCheckingUpdate && !forceCheck) return;

    // Don't check too frequently unless forced
    if (!forceCheck && _lastCheckTime != null) {
      final timeSinceLastCheck = DateTime.now().difference(_lastCheckTime!);
      if (timeSinceLastCheck < _checkInterval) {
        debugPrint('Skipping update check - too soon since last check');
        return;
      }
    }

    _isCheckingUpdate = true;
    _lastCheckTime = DateTime.now();

    try {
      debugPrint('Checking for app updates...');
      final updateStatus = await VersionService.checkForUpdate();

      switch (updateStatus) {
        case UpdateStatus.forceUpdate:
          debugPrint('Force update required');
          await _showUpdateDialog(UpdateStatus.forceUpdate);
          break;
        case UpdateStatus.optionalUpdate:
          debugPrint('Optional update available');
          await _showUpdateDialog(UpdateStatus.optionalUpdate);
          break;
        case UpdateStatus.noUpdateNeeded:
          debugPrint('No update needed');
          break;
        case UpdateStatus.error:
          debugPrint('Error checking for updates');
          break;
      }
    } catch (e) {
      debugPrint('Error in force update check: $e');
    } finally {
      _isCheckingUpdate = false;
    }
  }

  /// Show update dialog using the current context
  static Future<void> _showUpdateDialog(UpdateStatus status) async {
    final context = _getCurrentContext();
    if (context == null) {
      debugPrint('No context available for showing update dialog');
      return;
    }

    if (!context.mounted) {
      debugPrint('Context is not mounted, skipping update dialog');
      return;
    }

    // Additional safety: Wait for any potential navigation to complete
    await Future.delayed(const Duration(milliseconds: 100));

    if (!context.mounted) {
      debugPrint('Context became unmounted, skipping update dialog');
      return;
    }

    try {
      debugPrint('Showing update dialog with status: $status');
      await VersionService.showUpdateDialog(context, status);
    } catch (e) {
      debugPrint('Error showing update dialog: $e');
    }
  }

  /// Get current context from the navigator
  static BuildContext? _getCurrentContext() {
    return navigatorKey.currentContext;
  }

  /// Check for updates when app resumes from background
  static Future<void> onAppResume() async {
    await checkForUpdate();
  }

  /// Manual update check (for settings page)
  static Future<void> manualUpdateCheck(BuildContext context) async {
    // First check connectivity
    final connectivityInfo =
        await ConnectivityService.getDetailedConnectivityInfo();

    if (!connectivityInfo.hasInternet) {
      _showErrorDialog(context, 'no_internet', connectivityInfo.description);
      return;
    }

    // Show loading indicator
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const AlertDialog(
        content: Row(
          children: [
            CircularProgressIndicator(),
            SizedBox(width: 16),
            Text('Checking for updates...'),
          ],
        ),
      ),
    );

    String? errorType;
    try {
      final updateStatus = await VersionService.checkForUpdate();

      // Hide loading indicator
      if (context.mounted) {
        Navigator.of(context).pop();
      }

      switch (updateStatus) {
        case UpdateStatus.forceUpdate:
        case UpdateStatus.optionalUpdate:
          if (context.mounted) {
            await VersionService.showUpdateDialog(context, updateStatus);
          }
          break;
        case UpdateStatus.noUpdateNeeded:
          if (context.mounted) {
            _showNoUpdateDialog(context);
          }
          break;
        case UpdateStatus.error:
          if (context.mounted) {
            _showErrorDialog(context, 'service_error');
          }
          break;
      }
    } catch (e) {
      debugPrint('Manual update check error: $e');
      // Determine error type for better user messaging
      if (e.toString().contains('network') ||
          e.toString().contains('connection') ||
          e.toString().contains('timeout') ||
          e.toString().contains('socket')) {
        errorType = 'network_error';
      } else if (e.toString().contains('firebase') ||
          e.toString().contains('firestore') ||
          e.toString().contains('permission')) {
        errorType = 'firebase_error';
      } else {
        errorType = 'unknown_error';
      }

      // Hide loading indicator
      if (context.mounted) {
        Navigator.of(context).pop();
        _showErrorDialog(context, errorType);
      }
    }
  }

  /// Show dialog when no update is available
  static void _showNoUpdateDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('You\'re up to date!'),
        content: const Text('You have the latest version of the app.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  /// Show error dialog with specific error messages
  static void _showErrorDialog(BuildContext context,
      [String? errorType, String? customMessage]) {
    String title = 'Update Check Failed';
    String message =
        customMessage ?? 'Unable to check for updates. Please try again later.';
    List<Widget> actions = [];

    switch (errorType) {
      case 'no_internet':
        title = 'No Internet Connection';
        message = customMessage ??
            'Please check your internet connection and try again.';
        actions = [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              manualUpdateCheck(context); // Retry
            },
            child: const Text('Retry'),
          ),
        ];
        break;
      case 'network_error':
        title = 'Connection Error';
        message = 'Please check your internet connection and try again.';
        actions = [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              manualUpdateCheck(context); // Retry
            },
            child: const Text('Retry'),
          ),
        ];
        break;
      case 'firebase_error':
        title = 'Service Unavailable';
        message =
            'Update service is temporarily unavailable. Please try again in a few minutes.';
        actions = [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              manualUpdateCheck(context); // Retry
            },
            child: const Text('Retry'),
          ),
        ];
        break;
      case 'service_error':
        title = 'Service Error';
        message =
            'The update service encountered an error. Please try again later or contact support if the problem persists.';
        actions = [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              manualUpdateCheck(context); // Retry
            },
            child: const Text('Retry'),
          ),
        ];
        break;
      default:
        title = 'Update Check Failed';
        message =
            'Unable to check for updates. Please ensure you have an active internet connection and try again.';
        actions = [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              manualUpdateCheck(context); // Retry
            },
            child: const Text('Retry'),
          ),
        ];
        break;
    }

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: actions,
      ),
    );
  }
}
