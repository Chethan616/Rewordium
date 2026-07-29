import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rewordium/screens/accessibility_disclosure_screen.dart';

void main() {
  testWidgets('renders exact compliance disclosure content', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: AccessibilityDisclosureScreen(),
      ),
    );

    expect(
      find.text('Accessibility Permission'),
      findsOneWidget,
    );
    expect(
      find.text('Accessibility Required'),
      findsOneWidget,
    );
    expect(
      find.text(
        'Needed to read and update text across apps in real time.',
      ),
      findsOneWidget,
    );
    expect(
      find.text('Why this is required'),
      findsOneWidget,
    );
    expect(
      find.text(
        'Android does not provide any other API for cross-app text reading and writing. This permission is essential for AI rewriting, grammar correction, and inserting improved text into input fields across apps.',
      ),
      findsOneWidget,
    );
    expect(
      find.text('Data access'),
      findsOneWidget,
    );
    expect(
      find.text('Reads on-screen text only when you actively use features.'),
      findsOneWidget,
    );
    expect(
      find.text(
          'Does not access passwords, payment data, or sensitive fields.'),
      findsOneWidget,
    );
    expect(
      find.text('Does not collect, store, or share personal data.'),
      findsOneWidget,
    );
    expect(
      find.text(
        'This app does not collect or transmit personal or sensitive data.',
      ),
      findsOneWidget,
    );
    expect(
      find.text(
        'This is a core feature and will not work without this permission.',
      ),
      findsOneWidget,
    );
    expect(
      find.text(
        'By tapping "Continue", you explicitly consent to enable this service.\nYou can disable it anytime in device settings.',
      ),
      findsOneWidget,
    );
    expect(
      find.text(
        'This permission is required for core functionality and cannot be replaced by any other Android API.',
      ),
      findsOneWidget,
    );
    expect(find.text('Not Now'), findsOneWidget);
    expect(find.text('Continue'), findsOneWidget);
  });

  testWidgets('returns false when Not Now is tapped', (
    WidgetTester tester,
  ) async {
    bool? result;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: ElevatedButton(
                onPressed: () async {
                  result = await AccessibilityDisclosureScreen.show(context);
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Not Now'));
    await tester.pumpAndSettle();

    expect(result, false);
  });

  testWidgets('returns true when Continue is tapped', (
    WidgetTester tester,
  ) async {
    bool? result;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: ElevatedButton(
                onPressed: () async {
                  result = await AccessibilityDisclosureScreen.show(context);
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();

    expect(result, true);
  });
}
