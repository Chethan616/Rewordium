import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rewordium/pages/onboarding_page.dart';
import 'package:rewordium/theme/theme_provider.dart';

void main() {
  const androidOnly = TargetPlatformVariant(<TargetPlatform>{
    TargetPlatform.android,
  });
  const accessibilityChannel =
      MethodChannel('com.noxquill.rewordium/accessibility');
  const keyboardChannel =
      MethodChannel('com.noxquill.rewordium/rewordium_keyboard');

  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{});

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(accessibilityChannel, (
      MethodCall call,
    ) async {
      if (call.method == 'isAccessibilityServiceEnabled') {
        return false;
      }
      if (call.method == 'requestAccessibilitySettings') {
        return true;
      }
      return null;
    });

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(keyboardChannel, (
      MethodCall call,
    ) async {
      if (call.method == 'isKeyboardEnabled') {
        return true;
      }
      if (call.method == 'isKeyboardSelectedAsDefault') {
        return true;
      }
      return true;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(accessibilityChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(keyboardChannel, null);
  });

  testWidgets('onboarding screen renders first step', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      ChangeNotifierProvider<ThemeProvider>(
        create: (_) => ThemeProvider(),
        child: const MaterialApp(home: OnboardingPage()),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.text('Welcome'), findsOneWidget);
    expect(find.text('Set up Rewordium your way in under a minute.'),
        findsOneWidget);
  }, variant: androidOnly);
}
