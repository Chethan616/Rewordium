import 'package:flutter/material.dart';
import 'package:m3e_collection/m3e_collection.dart';

class AccessibilityDisclosureScreen extends StatefulWidget {
  const AccessibilityDisclosureScreen({super.key});

  static Future<bool> show(BuildContext context) async {
    final bool? result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => const AccessibilityDisclosureScreen(),
      ),
    );
    return result ?? false;
  }

  @override
  State<AccessibilityDisclosureScreen> createState() =>
      _AccessibilityDisclosureScreenState();
}

class _AccessibilityDisclosureScreenState
    extends State<AccessibilityDisclosureScreen> {
  static const String _title = 'Enable Accessibility Service for Rewordium';
  static const String _description =
      'Rewordium uses Android Accessibility Service to read on-screen text ONLY when you actively use features like rewrite, grammar correction, or translation. It helps provide suggestions and allows inserting improved text into input fields.';

  static const String _bulletRead =
      '• Read visible text only when you trigger a feature';
  static const String _bulletRewrite =
      '• Provide AI-powered rewriting and suggestions';
  static const String _bulletInsert =
      '• Insert improved text into active input fields';

  static const String _complianceText =
      'Rewordium does NOT collect, store, or share personal or sensitive data without your consent.\n'
      'Accessibility access is used only to provide app features.\n'
      'By tapping Continue, you explicitly agree to enable this permission.';

  void _onNotNow() {
    Navigator.of(context).pop(false);
  }

  void _onContinue() {
    Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Permission Disclosure'),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
          child: Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 52,
                        height: 52,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(14),
                          color: cs.primaryContainer,
                        ),
                        child: Icon(
                          Icons.accessibility_new_rounded,
                          color: cs.onPrimaryContainer,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        _title,
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        _description,
                        style: theme.textTheme.bodyLarge?.copyWith(
                          color: cs.onSurfaceVariant,
                          height: 1.5,
                        ),
                      ),
                      const SizedBox(height: 18),
                      _bulletLine(context, _bulletRead),
                      _bulletLine(context, _bulletRewrite),
                      _bulletLine(context, _bulletInsert),
                      const SizedBox(height: 20),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(16),
                          color: cs.tertiaryContainer,
                          border: Border.all(color: cs.tertiary),
                        ),
                        child: Text(
                          _complianceText,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: cs.onTertiaryContainer,
                            height: 1.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: ButtonM3E(
                      onPressed: _onNotNow,
                      label: const Text('Not Now'),
                      style: ButtonM3EStyle.outlined,
                      shape: ButtonM3EShape.round,
                      size: ButtonM3ESize.md,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ButtonM3E(
                      onPressed: _onContinue,
                      label: const Text('Continue'),
                      style: ButtonM3EStyle.filled,
                      shape: ButtonM3EShape.round,
                      size: ButtonM3ESize.md,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _bulletLine(BuildContext context, String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        text,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.45),
      ),
    );
  }
}
