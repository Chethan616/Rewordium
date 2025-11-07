import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'app_theme.dart';
import 'package:rewordium/services/keyboard_service.dart';

class ThemeProvider extends ChangeNotifier {
  // Theme preferences key
  static const String _themePreferenceKey = 'is_dark_mode';

  // Track whether the app is in dark mode
  bool _isDarkMode = false;
  bool get isDarkMode => _isDarkMode;

  final KeyboardService _keyboardService = KeyboardService();

  // Get current theme
  ThemeData get theme => _isDarkMode ? AppTheme.darkTheme : AppTheme.lightTheme;

  // Constructor loads saved preferences
  ThemeProvider() {
    _loadThemePreference();
  }

  // Load saved theme preference
  Future<void> _loadThemePreference() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _isDarkMode = prefs.getBool(_themePreferenceKey) ?? false;
      AppTheme.setDarkMode(_isDarkMode);
      notifyListeners();
    } catch (e) {
      debugPrint('Error loading theme preference: $e');
    }
  }

  // Save theme preference
  Future<void> _saveThemePreference(bool isDark) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(_themePreferenceKey, isDark);
    } catch (e) {
      debugPrint('Error saving theme preference: $e');
    }
  }

  // Toggle between light and dark mode
  void toggleTheme() {
    _isDarkMode = !_isDarkMode;
    AppTheme.setDarkMode(_isDarkMode);
    // Notify native keyboard
    _keyboardService.updateKeyboardTheme(_isDarkMode).then((_) {
      print("Keyboard theme update call completed from toggleTheme.");
    }).catchError((error) {
      print("Error calling updateKeyboardTheme from toggleTheme: $error");
    });
    _saveThemePreference(_isDarkMode);
    notifyListeners();
  }

  // Set specific theme
  void setDarkMode(bool isDark) {
    _isDarkMode = isDark;
    AppTheme.setDarkMode(_isDarkMode);
    _keyboardService.updateKeyboardTheme(_isDarkMode).then((_) {
      print("Keyboard theme update call completed from setDarkMode.");
    }).catchError((error) {
      print("Error calling updateKeyboardTheme from setDarkMode: $error");
    });
    _saveThemePreference(_isDarkMode);
    notifyListeners();
  }
}
