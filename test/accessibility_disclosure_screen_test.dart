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
      find.text('Enable Accessibility Service for Rewordium'),
      findsOneWidget,
    );
    expect(
      find.text(
        'Rewordium uses Android Accessibility Service to read on-screen text ONLY when you actively use features like rewrite, grammar correction, or translation. It helps provide suggestions and allows inserting improved text into input fields.',
      ),
      findsOneWidget,
    );
    expect(
      find.text('• Read visible text only when you trigger a feature'),
      findsOneWidget,
    );
    expect(
      find.text('• Provide AI-powered rewriting and suggestions'),
      findsOneWidget,
    );
    expect(
      find.text('• Insert improved text into active input fields'),
      findsOneWidget,
    );
    expect(
      find.text(
        'Rewordium does NOT collect, store, or share personal or sensitive data without your consent.\nAccessibility access is used only to provide app features.\nBy tapping Continue, you explicitly agree to enable this permission.',
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
