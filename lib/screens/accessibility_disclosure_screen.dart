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
        title: const Text('Accessibility Permission Required'),
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
                        'Rewordium uses the Android Accessibility Service to read and modify text across apps in real time.',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 18),
                      Text(
                        'Why this is required:',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Android does not provide any other API for cross-app text reading and writing. This permission is essential for core features like AI rewriting, grammar correction, and inserting improved text into input fields across apps.',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: cs.onSurfaceVariant,
                          height: 1.5,
                        ),
                      ),
                      const SizedBox(height: 18),
                      Text(
                        'Data Access:',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 8),
                      _bulletLine(
                        context,
                        '• Reads on-screen text only when you actively use features',
                      ),
                      _bulletLine(
                        context,
                        '• Does NOT access passwords, payment data, or sensitive fields',
                      ),
                      _bulletLine(
                        context,
                        '• Does NOT collect, store, or share any personal data',
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'This app does not collect or transmit personal or sensitive data.',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: cs.onSurfaceVariant,
                          height: 1.45,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
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
                          'This is a core feature and will not work without this permission.',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: cs.onTertiaryContainer,
                            height: 1.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'By tapping "Continue", you explicitly consent to enable this service.\nYou can disable it anytime in device settings.',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: cs.onSurfaceVariant,
                          height: 1.5,
                        ),
                      ),
                      const SizedBox(height: 14),
                      Text(
                        'This permission is required for core functionality and cannot be replaced by any other Android API.',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: cs.onSurface,
                          height: 1.45,
                          fontWeight: FontWeight.w600,
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
