import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import '../models/advanced_ai_settings.dart';
import '../screens/advanced_ai_settings_screen.dart';
import '../theme/app_theme.dart';

/// Gates document features (file, scan, URL) behind a custom AI endpoint.
/// Returns `true` if the user has a custom endpoint configured, `false` otherwise.
class DocGate {
  static Future<bool> check(BuildContext context) async {
    final hasCustom = await AdvancedAISettingsService.shouldUseCustomLLM();
    if (hasCustom) return true;

    if (!context.mounted) return false;

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.cardColor,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'AI Endpoint Required',
          style: TextStyle(
            color: AppTheme.textPrimaryColor,
            fontWeight: FontWeight.bold,
          ),
        ),
        content: Text(
          'Document features use heavy AI processing. '
          'To avoid rate limits, please connect your own AI endpoint '
          '(Gemini, GPT, Anthropic, or any custom API) in Advanced AI Settings.',
          style: TextStyle(
            color: AppTheme.textSecondaryColor,
            height: 1.5,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: Text(
              'Cancel',
              style: TextStyle(color: AppTheme.textSecondaryColor),
            ),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              Navigator.push(
                ctx,
                MaterialPageRoute(
                  builder: (_) => const AdvancedAISettingsScreen(),
                ),
              );
            },
            style: FilledButton.styleFrom(
              backgroundColor: AppTheme.primaryColor,
            ),
            child: const Text('Open Settings'),
          ),
        ],
      ),
    );

    return false;
  }
}
