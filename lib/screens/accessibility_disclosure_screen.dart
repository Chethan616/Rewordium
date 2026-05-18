import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
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
    final baseTheme = Theme.of(context);
    final themedData = baseTheme.copyWith(
      textTheme: GoogleFonts.ibmPlexSansTextTheme(baseTheme.textTheme).apply(
        bodyColor: baseTheme.colorScheme.onSurface,
        displayColor: baseTheme.colorScheme.onSurface,
      ),
    );

    return Theme(
      data: themedData,
      child: Builder(
        builder: (context) {
          final theme = Theme.of(context);
          final cs = theme.colorScheme;

          return Scaffold(
            extendBodyBehindAppBar: true,
            appBar: AppBar(
              title: Text(
                'Accessibility Permission',
                style: _headlineStyle(context),
              ),
              backgroundColor: Colors.transparent,
              elevation: 0,
              surfaceTintColor: Colors.transparent,
            ),
            body: Stack(
              children: [
                _buildBackground(cs),
                SafeArea(
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
                                  width: double.infinity,
                                  padding: const EdgeInsets.all(18),
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(20),
                                    color: cs.surfaceContainerHigh,
                                    border: Border.all(color: cs.outlineVariant),
                                    boxShadow: [
                                      BoxShadow(
                                        color: cs.shadow.withValues(alpha: 0.08),
                                        blurRadius: 18,
                                        offset: const Offset(0, 10),
                                      ),
                                    ],
                                  ),
                                  child: Row(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Container(
                                        width: 4,
                                        height: 48,
                                        decoration: BoxDecoration(
                                          borderRadius: BorderRadius.circular(6),
                                          color: cs.primary,
                                        ),
                                      ),
                                      const SizedBox(width: 12),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: [
                                            Text(
                                              'Accessibility Required',
                                              style: _sectionTitleStyle(context),
                                            ),
                                            const SizedBox(height: 4),
                                            Text(
                                              'Needed to read and update text across apps in real time.',
                                              style: _bodySubtleStyle(context),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 18),
                                Text(
                                  'Why this is required',
                                  style: _sectionTitleStyle(context),
                                ),
                                const SizedBox(height: 8),
                                Container(
                                  width: double.infinity,
                                  padding: const EdgeInsets.all(16),
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(16),
                                    color: cs.surfaceContainerLow,
                                    border: Border.all(color: cs.outlineVariant),
                                  ),
                                  child: Text(
                                    'Android does not provide any other API for cross-app text reading and writing. This permission is essential for AI rewriting, grammar correction, and inserting improved text into input fields across apps.',
                                    style: _bodySubtleStyle(context),
                                  ),
                                ),
                                const SizedBox(height: 18),
                                Text(
                                  'Data access',
                                  style: _sectionTitleStyle(context),
                                ),
                                const SizedBox(height: 8),
                                _bulletLine(
                                  context,
                                  'Reads on-screen text only when you actively use features.',
                                ),
                                _bulletLine(
                                  context,
                                  'Does not access passwords, payment data, or sensitive fields.',
                                ),
                                _bulletLine(
                                  context,
                                  'Does not collect, store, or share personal data.',
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  'This app does not collect or transmit personal or sensitive data.',
                                  style: _bodyStyle(context).copyWith(
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(height: 18),
                                Container(
                                  width: double.infinity,
                                  padding: const EdgeInsets.all(16),
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(16),
                                    color: cs.tertiaryContainer,
                                    border: Border.all(color: cs.tertiary),
                                  ),
                                  child: Row(
                                    children: [
                                      Icon(
                                        Icons.check_circle,
                                        color: cs.onTertiaryContainer,
                                      ),
                                      const SizedBox(width: 10),
                                      Expanded(
                                        child: Text(
                                          'This is a core feature and will not work without this permission.',
                                          style: _bodySubtleStyle(context)
                                              .copyWith(
                                            color: cs.onTertiaryContainer,
                                            fontWeight: FontWeight.w600,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 16),
                                Text(
                                  'By tapping "Continue", you explicitly consent to enable this service.\nYou can disable it anytime in device settings.',
                                  style: _bodySubtleStyle(context),
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  'This permission is required for core functionality and cannot be replaced by any other Android API.',
                                  style: _bodyStyle(context).copyWith(
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
              ],
            ),
          );
        },
      ),
    );
  }

  TextStyle _headlineStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.spaceGrotesk(
      textStyle: theme.textTheme.titleMedium?.copyWith(
        color: cs.onSurface,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.2,
      ),
    );
  }

  TextStyle _sectionTitleStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.spaceGrotesk(
      textStyle: theme.textTheme.titleMedium?.copyWith(
        color: cs.onSurface,
        fontWeight: FontWeight.w600,
      ),
    );
  }

  TextStyle _bodyStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.ibmPlexSans(
      textStyle: theme.textTheme.bodyMedium?.copyWith(
        color: cs.onSurface,
        height: 1.45,
      ),
    );
  }

  TextStyle _bodySubtleStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.ibmPlexSans(
      textStyle: theme.textTheme.bodyMedium?.copyWith(
        color: cs.onSurfaceVariant,
        height: 1.45,
      ),
    );
  }

  Widget _bulletLine(BuildContext context, String text) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Icon(
              Icons.circle,
              size: 8,
              color: cs.primary,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: _bodySubtleStyle(context),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBackground(ColorScheme cs) {
    return Stack(
      children: [
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  cs.surface,
                  cs.surfaceContainerLow,
                  cs.surface,
                ],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: Transform.rotate(
            angle: -0.12,
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    Colors.transparent,
                    cs.primaryContainer.withValues(alpha: 0.12),
                    Colors.transparent,
                  ],
                  stops: const [0.2, 0.5, 0.8],
                ),
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(0.85, -0.85),
                radius: 1.05,
                colors: [
                  cs.secondaryContainer.withValues(alpha: 0.3),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Positioned(
          bottom: -120,
          left: -60,
          child: Container(
            width: 240,
            height: 240,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: cs.tertiaryContainer.withValues(alpha: 0.22),
            ),
          ),
        ),
      ],
    );
  }
}
