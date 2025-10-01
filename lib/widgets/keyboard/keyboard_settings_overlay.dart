import 'package:flutter/material.dart';
import 'dart:async';
import 'keyboard_settings_popup.dart';
import '../../services/keyboard_settings_service.dart';

class KeyboardSettingsOverlay extends StatefulWidget {
  final Widget child;

  const KeyboardSettingsOverlay({
    Key? key,
    required this.child,
  }) : super(key: key);

  @override
  _KeyboardSettingsOverlayState createState() =>
      _KeyboardSettingsOverlayState();
}

class _KeyboardSettingsOverlayState extends State<KeyboardSettingsOverlay> {
  StreamSubscription<bool>? _settingsSubscription;
  OverlayEntry? _overlayEntry;

  @override
  void initState() {
    super.initState();
    _setupSettingsListener();
  }

  void _setupSettingsListener() {
    _settingsSubscription =
        KeyboardSettingsService.settingsStream.listen((isDarkMode) {
      _showKeyboardSettings(isDarkMode);
    });
  }

  void _showKeyboardSettings(bool isDarkMode) {
    if (_overlayEntry != null) {
      _hideKeyboardSettings();
    }

    _overlayEntry = OverlayEntry(
      builder: (context) => _buildOverlay(isDarkMode),
    );

    Overlay.of(context).insert(_overlayEntry!);
  }

  void _hideKeyboardSettings() {
    _overlayEntry?.remove();
    _overlayEntry = null;
  }

  Widget _buildOverlay(bool isDarkMode) {
    return Material(
      color: Colors.black.withOpacity(0.3),
      child: GestureDetector(
        onTap: _hideKeyboardSettings,
        child: Container(
          width: double.infinity,
          height: double.infinity,
          child: Center(
            child: GestureDetector(
              onTap: () {}, // Prevent tap from bubbling up
              child: KeyboardSettingsPopup(
                isDarkMode: isDarkMode,
                onClose: _hideKeyboardSettings,
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _settingsSubscription?.cancel();
    _hideKeyboardSettings();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}
