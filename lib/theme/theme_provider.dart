import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'app_theme.dart';
import 'package:rewordium/services/keyboard_service.dart';
import 'package:rewordium/services/rewordium_keyboard_service.dart';

class ThemeProvider extends ChangeNotifier {
  // Theme preferences keys
  static const String _themePreferenceKey = 'is_dark_mode';
  static const String _themeModeKey = 'theme_mode'; // 'system', 'light', 'dark'
  static const String _dynamicColorsEnabledKey = 'dynamic_colors_enabled';

  // Current theme mode (system, light, dark)
  ThemeMode _themeMode = ThemeMode.system;
  ThemeMode get themeMode => _themeMode;

  // Resolved dark mode state (accounts for system brightness)
  bool _isDarkMode = false;
  bool get isDarkMode => _isDarkMode;

  final KeyboardService _keyboardService = KeyboardService();
  bool _useDynamicColors = true;
  bool get useDynamicColors => _useDynamicColors;
  String? _lastKeyboardAccentHex;

  // Get light and dark themes for MaterialApp
  ThemeData get lightTheme => AppTheme.lightTheme;
  ThemeData get darkTheme => AppTheme.darkTheme;

  // Legacy getter for compatibility
  ThemeData get theme => _isDarkMode ? AppTheme.darkTheme : AppTheme.lightTheme;

  // Constructor loads saved preferences
  ThemeProvider() {
    _loadThemePreference();
  }

  // Load saved theme preference with migration from old boolean format
  Future<void> _loadThemePreference() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedMode = prefs.getString(_themeModeKey);

      if (savedMode != null) {
        // New format
        switch (savedMode) {
          case 'light':
            _themeMode = ThemeMode.light;
            _isDarkMode = false;
          case 'dark':
            _themeMode = ThemeMode.dark;
            _isDarkMode = true;
          default:
            _themeMode = ThemeMode.system;
            _isDarkMode = _getSystemBrightness() == Brightness.dark;
        }
      } else {
        // Migrate from old boolean format
        final oldPref = prefs.getBool(_themePreferenceKey);
        if (oldPref != null) {
          _themeMode = oldPref ? ThemeMode.dark : ThemeMode.light;
          _isDarkMode = oldPref;
          // Save in new format
          await prefs.setString(_themeModeKey, oldPref ? 'dark' : 'light');
        } else {
          // First launch: default to system
          _themeMode = ThemeMode.system;
          _isDarkMode = _getSystemBrightness() == Brightness.dark;
        }
      }

      AppTheme.setDarkMode(_isDarkMode);
      _useDynamicColors = prefs.getBool(_dynamicColorsEnabledKey) ?? false;
      notifyListeners();
    } catch (e) {
      debugPrint('Error loading theme preference: $e');
    }
  }

  // Get system brightness
  Brightness _getSystemBrightness() {
    return WidgetsBinding.instance.platformDispatcher.platformBrightness;
  }

  // Call this when system brightness changes (from didChangePlatformBrightness)
  void updateSystemBrightness() {
    if (_themeMode == ThemeMode.system) {
      final systemDark = _getSystemBrightness() == Brightness.dark;
      if (_isDarkMode != systemDark) {
        _isDarkMode = systemDark;
        AppTheme.setDarkMode(_isDarkMode);
        _updateKeyboard(_isDarkMode);
        notifyListeners();
      }
    }
  }

  // Save theme mode preference
  Future<void> _saveThemePreference(String mode) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_themeModeKey, mode);
    } catch (e) {
      debugPrint('Error saving theme preference: $e');
    }
  }

  // Set theme mode (system, light, dark)
  void setThemeMode(ThemeMode mode) {
    _themeMode = mode;
    switch (mode) {
      case ThemeMode.light:
        _isDarkMode = false;
        _saveThemePreference('light');
      case ThemeMode.dark:
        _isDarkMode = true;
        _saveThemePreference('dark');
      case ThemeMode.system:
        _isDarkMode = _getSystemBrightness() == Brightness.dark;
        _saveThemePreference('system');
    }
    AppTheme.setDarkMode(_isDarkMode);
    _updateKeyboard(_isDarkMode);
    notifyListeners();
  }

  // Toggle between light and dark mode (legacy support)
  void toggleTheme() {
    if (_themeMode == ThemeMode.system) {
      // If on system, toggle to opposite of current
      setThemeMode(_isDarkMode ? ThemeMode.light : ThemeMode.dark);
    } else {
      setThemeMode(_isDarkMode ? ThemeMode.light : ThemeMode.dark);
    }
  }

  // Set specific theme (legacy support)
  void setDarkMode(bool isDark) {
    setThemeMode(isDark ? ThemeMode.dark : ThemeMode.light);
  }

  // Notify native keyboard of theme change
  void _updateKeyboard(bool isDark) {
    _keyboardService.updateKeyboardTheme(isDark).then((_) {
      debugPrint('Keyboard theme update completed.');
    }).catchError((error) {
      debugPrint('Error calling updateKeyboardTheme: $error');
    });

    RewordiumKeyboardService.setDarkMode(isDark).catchError((error) {
      debugPrint('Error syncing keyboard dark mode: $error');
    });
  }

  Future<void> setDynamicColorsEnabled(bool enabled) async {
    if (_useDynamicColors == enabled) {
      return;
    }
    _useDynamicColors = enabled;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(_dynamicColorsEnabledKey, enabled);
    } catch (e) {
      debugPrint('Error saving dynamic color preference: $e');
    }
    notifyListeners();
  }

  void syncKeyboardAccent(Color color) {
    final hex =
        '#${color.toARGB32().toRadixString(16).padLeft(8, '0').substring(2).toUpperCase()}';
    if (_lastKeyboardAccentHex == hex) {
      return;
    }
    _lastKeyboardAccentHex = hex;
    RewordiumKeyboardService.updateThemeColor(hex).catchError((error) {
      debugPrint('Error syncing keyboard accent color: $error');
    });
  }
}
