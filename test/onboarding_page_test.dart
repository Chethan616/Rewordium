import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rewordium/pages/onboarding_page.dart';
import 'package:rewordium/theme/theme_provider.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const androidOnly = TargetPlatformVariant(<TargetPlatform>{
    TargetPlatform.android,
  });

  const accessibilityChannel =
      MethodChannel('com.noxquill.rewordium/accessibility');
  const keyboardChannel =
      MethodChannel('com.noxquill.rewordium/rewordium_keyboard');

  late bool accessibilityEnabled;
  int accessibilitySettingsOpenCount = 0;

  Future<void> pumpOnboarding(WidgetTester tester) async {
    await tester.pumpWidget(
      ChangeNotifierProvider<ThemeProvider>(
        create: (_) => ThemeProvider(),
        child: const MaterialApp(home: OnboardingPage()),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> goToAssistantModeStep(WidgetTester tester) async {
    await tester.tap(find.widgetWithText(FilledButton, 'Continue'));
    await tester.pumpAndSettle();
    expect(find.text('Assistant mode'), findsOneWidget);
  }

  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{});

    accessibilityEnabled = false;
    accessibilitySettingsOpenCount = 0;

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(accessibilityChannel, (
      MethodCall call,
    ) async {
      switch (call.method) {
        case 'isAccessibilityServiceEnabled':
          return accessibilityEnabled;
        case 'requestAccessibilitySettings':
          accessibilitySettingsOpenCount += 1;
          return true;
        default:
          return null;
      }
    });

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(keyboardChannel, (
      MethodCall call,
    ) async {
      switch (call.method) {
        case 'isKeyboardEnabled':
          return true;
        case 'isKeyboardSelectedAsDefault':
          return true;
        case 'showInputMethodPicker':
          return true;
        case 'setAiSuggestions':
          return true;
        case 'setHapticFeedback':
          return true;
        case 'openKeyboardSettings':
          return true;
        default:
          return null;
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(accessibilityChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(keyboardChannel, null);
  });

  testWidgets('welcome step uses updated headline copy', (
    WidgetTester tester,
  ) async {
    await pumpOnboarding(tester);

    expect(find.text('A short setup. About a minute.'),
        findsOneWidget);
  }, variant: androidOnly);

  testWidgets(
      'overlay selection shows disclosure prompt and opens settings on continue',
      (
    WidgetTester tester,
  ) async {
    await pumpOnboarding(tester);
    await goToAssistantModeStep(tester);

    await tester.tap(find.text('Accessibility overlay'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Continue'));
    await tester.pumpAndSettle();

    expect(
      find.text('Accessibility Permission'),
      findsOneWidget,
    );

    await tester.tap(find.widgetWithText(ButtonM3E, 'Continue'));
    await tester.pumpAndSettle();

    expect(accessibilitySettingsOpenCount, 1);
    expect(find.text('Assistant mode'), findsOneWidget);

    // Drain toast timer
    await tester.pump(const Duration(seconds: 4));
  }, variant: androidOnly);

  testWidgets('declining disclosure falls back to keyboard mode and advances', (
    WidgetTester tester,
  ) async {
    await pumpOnboarding(tester);
    await goToAssistantModeStep(tester);

    await tester.tap(find.text('Accessibility overlay'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Continue'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ButtonM3E, 'Not Now'));
    await tester.pumpAndSettle();

    expect(accessibilitySettingsOpenCount, 0);
    expect(find.text('Theme'), findsOneWidget);

    // Drain toast timer
    await tester.pump(const Duration(seconds: 4));
  }, variant: androidOnly);

  testWidgets(
      'accepted disclosure advances directly when accessibility is already enabled',
      (
    WidgetTester tester,
  ) async {
    accessibilityEnabled = true;

    await pumpOnboarding(tester);
    await goToAssistantModeStep(tester);

    await tester.tap(find.text('Accessibility overlay'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Continue'));
    await tester.pumpAndSettle();

    expect(accessibilitySettingsOpenCount, 0);
    expect(find.text('Theme'), findsOneWidget);
  }, variant: androidOnly);
}
