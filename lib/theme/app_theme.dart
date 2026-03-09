import 'package:flutter/material.dart';
import 'package:m3e_design/m3e_design.dart';

class AppTheme {
  // ─── Brand Palette ───────────────────────────────────────────────
  // Light: fresh green primary + navy secondary + orange accent
  // Dark:  bright green primary on true dark backgrounds
  static const Color primaryColor = Color(0xFF009B6E);

  // Light mode colors
  static const Color secondaryColor = Color(0xFF1E3A8A);
  static const Color accentColor = Color(0xFFFFA726);
  static const Color lightBackgroundColor = Color(0xFFF8F9FB);
  static const Color lightCardColor = Color(0xFFFFFFFF);
  static const Color lightSurfaceContainer = Color(0xFFF1F5F9);
  static const Color errorColor = Color(0xFFE53935);
  static const Color warningColor = Color(0xFFFFC107);
  static const Color successColor = Color(0xFF4CAF50);
  static const Color lightTextPrimaryColor = Color(0xFF2D3748);
  static const Color lightTextSecondaryColor = Color(0xFF718096);
  static const Color disabledColor = Color(0xFFE2E8F0);

  // Dark mode colors
  static const Color darkPrimaryColor = Color(0xFFAFFF99);
  static const Color darkSecondaryColor = Color(0xFF93C5FD);
  static const Color darkAccentColor = Color(0xFFFFCC80);
  static const Color darkBackgroundColor = Color(0xFF121212);
  static const Color darkCardColor = Color(0xFF1E1E1E);
  static const Color darkSurfaceContainer = Color(0xFF2A2A2A);
  static const Color darkTextPrimaryColor = Color(0xFFECECEC);
  static const Color darkTextSecondaryColor = Color(0xFFAAAAAA);

  // ─── Theme Mode Tracking ────────────────────────────────────────
  static bool _isDarkMode = false;
  static bool get isDarkMode => _isDarkMode;

  static void toggleTheme() => _isDarkMode = !_isDarkMode;
  static void setDarkMode(bool isDark) => _isDarkMode = isDark;

  // ─── Dynamic Getters (backward compat) ──────────────────────────
  static Color get backgroundColor =>
      _isDarkMode ? darkBackgroundColor : lightBackgroundColor;
  static Color get scaffoldBackgroundColor => backgroundColor;
  static Color get cardColor => _isDarkMode ? darkCardColor : lightCardColor;
  static Color get surfaceContainerColor =>
      _isDarkMode ? darkSurfaceContainer : lightSurfaceContainer;
  static Color get textPrimaryColor =>
      _isDarkMode ? darkTextPrimaryColor : lightTextPrimaryColor;
  static Color get textSecondaryColor =>
      _isDarkMode ? darkTextSecondaryColor : lightTextSecondaryColor;

  // ─── Color Schemes ──────────────────────────────────────────────
  static ColorScheme get _lightScheme => const ColorScheme.light(
        primary: Color(0xFF009B6E),
        onPrimary: Colors.white,
        primaryContainer: Color(0xFFD1FAE5),
        onPrimaryContainer: Color(0xFF064E3B),
        secondary: Color(0xFF1E3A8A),
        onSecondary: Colors.white,
        secondaryContainer: Color(0xFFDBEAFE),
        onSecondaryContainer: Color(0xFF1E3A5F),
        tertiary: Color(0xFFFFA726),
        onTertiary: Colors.white,
        tertiaryContainer: Color(0xFFFFF3E0),
        onTertiaryContainer: Color(0xFFE65100),
        error: Color(0xFFE53935),
        onError: Colors.white,
        surface: Color(0xFFFFFFFF),
        onSurface: Color(0xFF2D3748),
        onSurfaceVariant: Color(0xFF718096),
        outline: Color(0xFFA0AEC0),
        outlineVariant: Color(0xFFE2E8F0),
        surfaceContainerLowest: Color(0xFFFFFFFF),
        surfaceContainerLow: Color(0xFFF8F9FB),
        surfaceContainer: Color(0xFFF1F5F9),
        surfaceContainerHigh: Color(0xFFE2E8F0),
        surfaceContainerHighest: Color(0xFFCBD5E1),
        inverseSurface: Color(0xFF2D3748),
        onInverseSurface: Color(0xFFF8F9FB),
        inversePrimary: Color(0xFFAFFF99),
        shadow: Colors.black,
        scrim: Colors.black,
      );

  static ColorScheme get _darkScheme => const ColorScheme.dark(
        primary: Color(0xFFAFFF99),
        onPrimary: Color(0xFF0A3D00),
        primaryContainer: Color(0xFF1B5E20),
        onPrimaryContainer: Color(0xFFCCFFBF),
        secondary: Color(0xFF93C5FD),
        onSecondary: Color(0xFF1E3A5F),
        secondaryContainer: Color(0xFF1E40AF),
        onSecondaryContainer: Color(0xFFBFDBFE),
        tertiary: Color(0xFFFFCC80),
        onTertiary: Color(0xFF7C4A03),
        tertiaryContainer: Color(0xFFF57C00),
        onTertiaryContainer: Color(0xFFFFF3E0),
        error: Color(0xFFEF9A9A),
        onError: Color(0xFF7F1D1D),
        surface: Color(0xFF1E1E1E),
        onSurface: Color(0xFFECECEC),
        onSurfaceVariant: Color(0xFFAAAAAA),
        outline: Color(0xFF6B7280),
        outlineVariant: Color(0xFF374151),
        surfaceContainerLowest: Color(0xFF121212),
        surfaceContainerLow: Color(0xFF181818),
        surfaceContainer: Color(0xFF252525),
        surfaceContainerHigh: Color(0xFF333333),
        surfaceContainerHighest: Color(0xFF424242),
        inverseSurface: Color(0xFFECECEC),
        onInverseSurface: Color(0xFF1E1E1E),
        inversePrimary: Color(0xFF009B6E),
        shadow: Colors.black,
        scrim: Colors.black,
      );

  // ─── Text Styles (backward compat – still used by older widgets) ─
  static TextStyle get headingLarge => TextStyle(
        fontSize: 28,
        fontWeight: FontWeight.w800,
        color: textPrimaryColor,
        letterSpacing: -0.5,
      );
  static TextStyle get headingMedium => TextStyle(
        fontSize: 24,
        fontWeight: FontWeight.w700,
        color: textPrimaryColor,
        letterSpacing: -0.25,
      );
  static TextStyle get headingSmall => TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w700,
        color: textPrimaryColor,
      );
  static TextStyle get bodyLarge => TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        color: textPrimaryColor,
      );
  static TextStyle get bodyMedium => TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        color: textPrimaryColor,
      );
  static TextStyle get bodySmall => TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        color: textSecondaryColor,
      );
  static TextStyle get buttonText => const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w700,
        color: Colors.white,
      );

  // ─── Button Styles (backward compat) ────────────────────────────
  static ButtonStyle get primaryButtonStyle => ButtonStyle(
        backgroundColor: WidgetStateProperty.all(primaryColor),
        foregroundColor: WidgetStateProperty.all(Colors.white),
        elevation: WidgetStateProperty.all(0),
        padding: WidgetStateProperty.all(
            const EdgeInsets.symmetric(horizontal: 24, vertical: 12)),
        shape: WidgetStateProperty.all(RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(28),
        )),
      );

  static ButtonStyle get secondaryButtonStyle => ButtonStyle(
        foregroundColor: WidgetStateProperty.all(primaryColor),
        side: WidgetStateProperty.all(const BorderSide(color: primaryColor)),
        padding: WidgetStateProperty.all(
            const EdgeInsets.symmetric(horizontal: 24, vertical: 12)),
        shape: WidgetStateProperty.all(RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(28),
        )),
      );

  // ─── Card Decoration (backward compat) ──────────────────────────
  static BoxDecoration get cardDecoration => BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(28),
        boxShadow: [
          BoxShadow(
            color: _isDarkMode
                ? Colors.black.withValues(alpha: 0.25)
                : Colors.black.withValues(alpha: 0.06),
            offset: const Offset(0, 2),
            blurRadius: 12,
          ),
        ],
      );

  // ─── M3E Theme Builder ──────────────────────────────────────────
  static ThemeData _buildTheme(ColorScheme scheme) {
    final m3e = M3ETheme.defaults(scheme);
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surfaceContainerLow,
      appBarTheme: AppBarTheme(
        backgroundColor: scheme.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        iconTheme: IconThemeData(color: scheme.onSurface),
        titleTextStyle: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w700,
          color: scheme.onSurface,
        ),
      ),
      cardTheme: CardThemeData(
        color: scheme.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(
              m3e.shapes.round.md.bottomLeft.x),
        ),
      ),
      iconTheme: IconThemeData(color: scheme.onSurfaceVariant),
      textTheme: _textTheme(scheme),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ButtonStyle(
          backgroundColor: WidgetStateProperty.all(scheme.primary),
          foregroundColor: WidgetStateProperty.all(scheme.onPrimary),
          elevation: WidgetStateProperty.all(0),
          padding: WidgetStateProperty.all(
              const EdgeInsets.symmetric(horizontal: 24, vertical: 14)),
          shape: WidgetStateProperty.all(RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(
                m3e.shapes.round.md.bottomLeft.x),
          )),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: ButtonStyle(
          foregroundColor: WidgetStateProperty.all(scheme.primary),
          side: WidgetStateProperty.all(
              BorderSide(color: scheme.outline)),
          padding: WidgetStateProperty.all(
              const EdgeInsets.symmetric(horizontal: 24, vertical: 14)),
          shape: WidgetStateProperty.all(RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(
                m3e.shapes.round.md.bottomLeft.x),
          )),
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: scheme.primaryContainer,
        foregroundColor: scheme.onPrimaryContainer,
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(
              m3e.shapes.round.lg.bottomLeft.x),
        ),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith<Color>((states) {
          if (states.contains(WidgetState.disabled)) return disabledColor;
          if (states.contains(WidgetState.selected)) return scheme.primary;
          return scheme.onSurfaceVariant;
        }),
        trackColor: WidgetStateProperty.resolveWith<Color>((states) {
          if (states.contains(WidgetState.disabled)) {
            return disabledColor.withValues(alpha: 0.5);
          }
          if (states.contains(WidgetState.selected)) {
            return scheme.primary.withValues(alpha: 0.5);
          }
          return scheme.surfaceContainerHighest;
        }),
        trackOutlineColor: WidgetStateProperty.resolveWith<Color>((states) {
          if (states.contains(WidgetState.selected)) return Colors.transparent;
          return scheme.outline;
        }),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainer,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(
              m3e.shapes.round.sm.bottomLeft.x),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(
              m3e.shapes.round.sm.bottomLeft.x),
          borderSide: BorderSide(color: scheme.primary, width: 2),
        ),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: scheme.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(m3e.shapes.round.lg.bottomLeft.x),
          ),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: scheme.surfaceContainerHigh,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(
              m3e.shapes.round.lg.bottomLeft.x),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: scheme.inverseSurface,
        contentTextStyle: TextStyle(color: scheme.onInverseSurface),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(
              m3e.shapes.round.sm.bottomLeft.x),
        ),
        behavior: SnackBarBehavior.floating,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: scheme.surfaceContainer,
        selectedColor: scheme.secondaryContainer,
        labelStyle: TextStyle(color: scheme.onSurface),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(
              m3e.shapes.round.xs.bottomLeft.x),
        ),
      ),
    );
    return withM3ETheme(base, override: m3e);
  }

  static TextTheme _textTheme(ColorScheme scheme) => TextTheme(
        displayLarge: TextStyle(
            fontSize: 57,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.5,
            color: scheme.onSurface),
        displayMedium: TextStyle(
            fontSize: 45,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.5,
            color: scheme.onSurface),
        displaySmall: TextStyle(
            fontSize: 36,
            fontWeight: FontWeight.w700,
            color: scheme.onSurface),
        headlineLarge: TextStyle(
            fontSize: 32,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.25,
            color: scheme.onSurface),
        headlineMedium: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.25,
            color: scheme.onSurface),
        headlineSmall: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w700,
            color: scheme.onSurface),
        titleLarge: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: scheme.onSurface),
        titleMedium: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.15,
            color: scheme.onSurface),
        titleSmall: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.1,
            color: scheme.onSurface),
        bodyLarge: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w400,
            color: scheme.onSurface),
        bodyMedium: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w400,
            color: scheme.onSurface),
        bodySmall: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w400,
            color: scheme.onSurfaceVariant),
        labelLarge: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.1,
            color: scheme.onSurface),
        labelMedium: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.5,
            color: scheme.onSurfaceVariant),
        labelSmall: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.5,
            color: scheme.onSurfaceVariant),
      );

  // ─── Public Theme Getters ───────────────────────────────────────
  static ThemeData get lightTheme => _buildTheme(_lightScheme);
  static ThemeData get darkTheme => _buildTheme(_darkScheme);
  static ThemeData get currentTheme => _isDarkMode ? darkTheme : lightTheme;
}
