import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import '../services/rewordium_keyboard_service.dart';
import '../screens/keyboard_quick_settings_screen.dart';

/// Shows the keyboard quick settings screen as a full-page push.
void openKeyboardQuickSettings(BuildContext context) {
  Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) => const KeyboardQuickSettingsScreen(),
    ),
  );
}
