import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  // Light mode colors
  static const Color primaryColor = Color(0xFF009B6E);
  static const Color secondaryColor = Color(0xFF1E3A8A);
  static const Color accentColor = Color(0xFFFFA726);
  static const Color lightBackgroundColor = Color(0xFFF8F9FB);
  static const Color lightCardColor = Colors.white;
  static const Color errorColor = Color(0xFFE53935);
  static const Color warningColor = Color(0xFFFFC107);
  static const Color successColor = Color(0xFF4CAF50);
  static const Color lightTextPrimaryColor = Color(0xFF2D3748);
  static const Color lightTextSecondaryColor = Color(0xFF718096);
  static const Color disabledColor = Color(0xFFE2E8F0);

  // Dark mode colors
  static const Color darkBackgroundColor = Color(0xFF121212);
  static const Color darkCardColor = Color(0xFF1E1E1E);
  static const Color darkTextPrimaryColor = Color(0xFFECECEC);
  static const Color darkTextSecondaryColor = Color(0xFFAAAAAA);

  // Current theme values that will change based on mode
  static Color get backgroundColor =>
      _isDarkMode ? darkBackgroundColor : lightBackgroundColor;
  static Color get scaffoldBackgroundColor =>
      _isDarkMode ? darkBackgroundColor : lightBackgroundColor;
  static Color get cardColor => _isDarkMode ? darkCardColor : lightCardColor;
  static Color get textPrimaryColor =>
      _isDarkMode ? darkTextPrimaryColor : lightTextPrimaryColor;
  static Color get textSecondaryColor =>
      _isDarkMode ? darkTextSecondaryColor : lightTextSecondaryColor;

  // Theme mode tracking
  static bool _isDarkMode = false;
  static bool get isDarkMode => _isDarkMode;

  // Method to toggle theme
  static void toggleTheme() {
    _isDarkMode = !_isDarkMode;
  }

  // Set specific theme
  static void setDarkMode(bool isDark) {
    _isDarkMode = isDark;
  }

  // Text styles
  static TextStyle get headingLarge => GoogleFonts.poppins(
        fontSize: 28,
        fontWeight: FontWeight.bold,
        color: textPrimaryColor,
        letterSpacing: -0.5,
      );

  static TextStyle get headingMedium => GoogleFonts.poppins(
        fontSize: 24,
        fontWeight: FontWeight.bold,
        color: textPrimaryColor,
        letterSpacing: -0.5,
      );

  static TextStyle get headingSmall => GoogleFonts.poppins(
        fontSize: 20,
        fontWeight: FontWeight.w600,
        color: textPrimaryColor,
      );

  static TextStyle get bodyLarge => GoogleFonts.inter(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        color: textPrimaryColor,
      );

  static TextStyle get bodyMedium => GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        color: textPrimaryColor,
      );

  static TextStyle get bodySmall => GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        color: textSecondaryColor,
      );

  static TextStyle get buttonText => GoogleFonts.inter(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: Colors.white,
      );

  // Button styles
  static ButtonStyle get primaryButtonStyle => ButtonStyle(
        backgroundColor: MaterialStateProperty.all(primaryColor),
        foregroundColor: MaterialStateProperty.all(Colors.white),
        elevation: MaterialStateProperty.all(0),
        padding: MaterialStateProperty.all(const EdgeInsets.symmetric(horizontal: 24, vertical: 12)),
        shape: MaterialStateProperty.all(RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        )),
      );

  static ButtonStyle get secondaryButtonStyle => ButtonStyle(
        foregroundColor: MaterialStateProperty.all(primaryColor),
        side: MaterialStateProperty.all(const BorderSide(color: primaryColor)),
        padding: MaterialStateProperty.all(const EdgeInsets.symmetric(horizontal: 24, vertical: 12)),
        shape: MaterialStateProperty.all(RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        )),
      );

  // Card styles
  static BoxDecoration get cardDecoration => BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: _isDarkMode
                ? Colors.black.withOpacity(0.2)
                : Colors.black.withOpacity(0.04),
            offset: const Offset(0, 2),
            blurRadius: 8,
          ),
        ],
      );

  // Theme data
  static ThemeData get lightTheme => ThemeData(
        useMaterial3: true,
        primaryColor: primaryColor,
        scaffoldBackgroundColor: lightBackgroundColor,
        colorScheme: ColorScheme.light(
          primary: primaryColor,
          secondary: secondaryColor,
          error: errorColor,
          background: lightBackgroundColor,
          surface: lightCardColor,
        ),
        appBarTheme: AppBarTheme(
          backgroundColor: lightCardColor,
          elevation: 0,
          iconTheme: const IconThemeData(color: lightTextPrimaryColor),
          titleTextStyle: headingSmall,
        ),
        cardTheme: CardThemeData(
          color: lightCardColor,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        iconTheme: const IconThemeData(color: lightTextSecondaryColor),
        textTheme: TextTheme(
          headlineLarge: headingLarge,
          headlineMedium: headingMedium,
          headlineSmall: headingSmall,
          bodyLarge: bodyLarge,
          bodyMedium: bodyMedium,
          bodySmall: bodySmall,
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: primaryButtonStyle,
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: secondaryButtonStyle,
        ),
        switchTheme: SwitchThemeData(
          thumbColor: MaterialStateProperty.resolveWith<Color>((states) {
            if (states.contains(MaterialState.disabled)) {
              return disabledColor;
            }
            if (states.contains(MaterialState.selected)) {
              return primaryColor;
            }
            return lightCardColor;
          }),
          trackColor: MaterialStateProperty.resolveWith<Color>((states) {
            if (states.contains(MaterialState.disabled)) {
              return disabledColor.withOpacity(0.5);
            }
            if (states.contains(MaterialState.selected)) {
              return primaryColor.withOpacity(0.5);
            }
            return lightTextSecondaryColor.withOpacity(0.3);
          }),
        ),
      );

  static ThemeData get darkTheme => ThemeData(
        useMaterial3: true,
        primaryColor: primaryColor,
        scaffoldBackgroundColor: darkBackgroundColor,
        colorScheme: ColorScheme.dark(
          primary: primaryColor,
          secondary: secondaryColor,
          error: errorColor,
          background: darkBackgroundColor,
          surface: darkCardColor,
        ),
        appBarTheme: AppBarTheme(
          backgroundColor: darkCardColor,
          elevation: 0,
          iconTheme: const IconThemeData(color: darkTextPrimaryColor),
          titleTextStyle: headingSmall.copyWith(color: darkTextPrimaryColor),
        ),
        cardTheme: CardThemeData(
          color: darkCardColor,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        iconTheme: const IconThemeData(color: darkTextSecondaryColor),
        textTheme: TextTheme(
          headlineLarge: headingLarge.copyWith(color: darkTextPrimaryColor),
          headlineMedium: headingMedium.copyWith(color: darkTextPrimaryColor),
          headlineSmall: headingSmall.copyWith(color: darkTextPrimaryColor),
          bodyLarge: bodyLarge.copyWith(color: darkTextPrimaryColor),
          bodyMedium: bodyMedium.copyWith(color: darkTextPrimaryColor),
          bodySmall: bodySmall.copyWith(color: darkTextSecondaryColor),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: primaryButtonStyle,
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: secondaryButtonStyle,
        ),
        switchTheme: SwitchThemeData(
          thumbColor: MaterialStateProperty.resolveWith<Color>((states) {
            if (states.contains(MaterialState.disabled)) {
              return disabledColor;
            }
            if (states.contains(MaterialState.selected)) {
              return primaryColor;
            }
            return darkCardColor;
          }),
          trackColor: MaterialStateProperty.resolveWith<Color>((states) {
            if (states.contains(MaterialState.disabled)) {
              return disabledColor.withOpacity(0.5);
            }
            if (states.contains(MaterialState.selected)) {
              return primaryColor.withOpacity(0.5);
            }
            return darkTextSecondaryColor.withOpacity(0.3);
          }),
        ),
      );

  // Get current theme based on mode
  static ThemeData get currentTheme => _isDarkMode ? darkTheme : lightTheme;
}
