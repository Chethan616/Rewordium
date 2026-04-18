import 'package:flutter/material.dart';
import '../screens/advanced_ai_settings_screen.dart';

/// Helper class to handle AI service errors and display appropriate snackbars
class AIErrorHandler {
  /// Shows appropriate snackbar based on error type from UnifiedAIService
  static void showErrorSnackBar(
      BuildContext context, Map<String, dynamic> result) {
    if (!result.containsKey('error')) return;

    final errorType = result['errorType'] as String? ?? 'UNKNOWN';

    Color backgroundColor;
    IconData icon;
    String title;

    switch (errorType) {
      case 'MISSING_API_KEY':
        backgroundColor = Colors.orange;
        icon = Icons.key_off;
        title = 'API Key Required';
        break;
      case 'RATE_LIMIT':
        backgroundColor = Colors.purple;
        icon = Icons.speed;
        title = 'Rate Limit Exceeded';
        break;
      case 'INVALID_API_KEY':
        backgroundColor = Colors.red;
        icon = Icons.error;
        title = 'Invalid API Key';
        break;
      case 'MISSING_ENDPOINT':
        backgroundColor = Colors.orange;
        icon = Icons.link_off;
        title = 'Endpoint Missing';
        break;
      default:
        backgroundColor = Colors.red;
        icon = Icons.error_outline;
        title = 'AI Service Error';
    }

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            Icon(icon, color: Colors.white, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                '$title: ${_getShortMessage(errorType)}',
                style: const TextStyle(color: Colors.white),
              ),
            ),
          ],
        ),
        backgroundColor: backgroundColor,
        duration: const Duration(seconds: 3),
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.all(16),
        action: errorType == 'MISSING_API_KEY' || errorType == 'INVALID_API_KEY'
            ? SnackBarAction(
                label: 'SETTINGS',
                textColor: Colors.white,
                onPressed: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => const AdvancedAISettingsScreen(),
                    ),
                  );
                },
              )
            : null,
      ),
    );
  }

  static String _getShortMessage(String errorType) {
    switch (errorType) {
      case 'MISSING_API_KEY':
        return 'Please add your API key in Advanced AI Settings';
      case 'RATE_LIMIT':
        return 'Please wait and try again';
      case 'INVALID_API_KEY':
        return 'Check your API key in settings';
      case 'MISSING_ENDPOINT':
        return 'Add a valid endpoint URL';
      default:
        return 'Please try again';
    }
  }

  /// Checks if result has error and shows snackbar if needed
  /// Returns true if there was an error
  static bool handleResult(BuildContext context, Map<String, dynamic> result) {
    if (result.containsKey('error')) {
      showErrorSnackBar(context, result);
      return true;
    }
    return false;
  }
}
