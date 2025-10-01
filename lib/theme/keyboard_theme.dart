import 'package:flutter/material.dart';

/// Keyboard theming system inspired by FlorisBoard's Snygg
class KeyboardTheme {
  // Colors
  final Color backgroundColor;
  final Color keyBackgroundColor;
  final Color keyPressedColor;
  final Color keyTextColor;
  final Color suggestionBarColor;
  final Color suggestionTextColor;
  final Color borderColor;
  final Color accentColor;
  
  // Dimensions
  final double keyHeight;
  final double keySpacing;
  final double keyBorderRadius;
  final double keyElevation;
  final double suggestionBarHeight;
  
  // Typography
  final TextStyle keyTextStyle;
  final TextStyle suggestionTextStyle;
  final TextStyle clipboardTextStyle;
  
  // Shadows
  final BoxShadow? keyShadow;
  
  const KeyboardTheme({
    required this.backgroundColor,
    required this.keyBackgroundColor,
    required this.keyPressedColor,
    required this.keyTextColor,
    required this.suggestionBarColor,
    required this.suggestionTextColor,
    required this.borderColor,
    required this.accentColor,
    required this.keyHeight,
    required this.keySpacing,
    required this.keyBorderRadius,
    required this.keyElevation,
    required this.suggestionBarHeight,
    required this.keyTextStyle,
    required this.suggestionTextStyle,
    required this.clipboardTextStyle,
    this.keyShadow,
  });
  
  /// Light theme similar to FlorisBoard
  factory KeyboardTheme.light() {
    return KeyboardTheme(
      backgroundColor: const Color(0xFFE8E8E8),
      keyBackgroundColor: Colors.white,
      keyPressedColor: const Color(0xFFD0D0D0),
      keyTextColor: Colors.black87,
      suggestionBarColor: const Color(0xFFF5F5F5),
      suggestionTextColor: Colors.black87,
      borderColor: const Color(0xFFBDBDBD),
      accentColor: const Color(0xFF2196F3),
      keyHeight: 56.0,
      keySpacing: 6.0,
      keyBorderRadius: 8.0,
      keyElevation: 1.0,
      suggestionBarHeight: 48.0,
      keyTextStyle: const TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w500,
        color: Colors.black87,
      ),
      suggestionTextStyle: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        color: Colors.black87,
      ),
      clipboardTextStyle: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        color: Colors.black87,
      ),
      keyShadow: BoxShadow(
        color: Colors.black.withOpacity(0.1),
        blurRadius: 2,
        offset: const Offset(0, 1),
      ),
    );
  }
  
  /// Dark theme similar to FlorisBoard
  factory KeyboardTheme.dark() {
    return KeyboardTheme(
      backgroundColor: const Color(0xFF1E1E1E),
      keyBackgroundColor: const Color(0xFF2D2D2D),
      keyPressedColor: const Color(0xFF3D3D3D),
      keyTextColor: Colors.white,
      suggestionBarColor: const Color(0xFF252525),
      suggestionTextColor: Colors.white,
      borderColor: const Color(0xFF404040),
      accentColor: const Color(0xFF64B5F6),
      keyHeight: 56.0,
      keySpacing: 6.0,
      keyBorderRadius: 8.0,
      keyElevation: 2.0,
      suggestionBarHeight: 48.0,
      keyTextStyle: const TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w500,
        color: Colors.white,
      ),
      suggestionTextStyle: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        color: Colors.white,
      ),
      clipboardTextStyle: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        color: Colors.white,
      ),
      keyShadow: BoxShadow(
        color: Colors.black.withOpacity(0.3),
        blurRadius: 4,
        offset: const Offset(0, 2),
      ),
    );
  }
  
  KeyboardTheme copyWith({
    Color? backgroundColor,
    Color? keyBackgroundColor,
    Color? keyPressedColor,
    Color? keyTextColor,
    Color? suggestionBarColor,
    Color? suggestionTextColor,
    Color? borderColor,
    Color? accentColor,
    double? keyHeight,
    double? keySpacing,
    double? keyBorderRadius,
    double? keyElevation,
    double? suggestionBarHeight,
    TextStyle? keyTextStyle,
    TextStyle? suggestionTextStyle,
    TextStyle? clipboardTextStyle,
    BoxShadow? keyShadow,
  }) {
    return KeyboardTheme(
      backgroundColor: backgroundColor ?? this.backgroundColor,
      keyBackgroundColor: keyBackgroundColor ?? this.keyBackgroundColor,
      keyPressedColor: keyPressedColor ?? this.keyPressedColor,
      keyTextColor: keyTextColor ?? this.keyTextColor,
      suggestionBarColor: suggestionBarColor ?? this.suggestionBarColor,
      suggestionTextColor: suggestionTextColor ?? this.suggestionTextColor,
      borderColor: borderColor ?? this.borderColor,
      accentColor: accentColor ?? this.accentColor,
      keyHeight: keyHeight ?? this.keyHeight,
      keySpacing: keySpacing ?? this.keySpacing,
      keyBorderRadius: keyBorderRadius ?? this.keyBorderRadius,
      keyElevation: keyElevation ?? this.keyElevation,
      suggestionBarHeight: suggestionBarHeight ?? this.suggestionBarHeight,
      keyTextStyle: keyTextStyle ?? this.keyTextStyle,
      suggestionTextStyle: suggestionTextStyle ?? this.suggestionTextStyle,
      clipboardTextStyle: clipboardTextStyle ?? this.clipboardTextStyle,
      keyShadow: keyShadow ?? this.keyShadow,
    );
  }
}
