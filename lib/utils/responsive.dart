import 'package:flutter/material.dart';

/// Responsive design utilities for consistent sizing across all devices.
///
/// Based on a reference design of 375×812 (standard phone portrait).
/// All dimension helpers scale proportionally so layouts look identical
/// on small phones, large phones, and tablets.
class Responsive {
  Responsive._();

  static const double _designWidth = 375.0;
  static const double _designHeight = 812.0;

  /// Obtain responsive helpers scoped to the current screen.
  static ResponsiveData of(BuildContext context) {
    final mq = MediaQuery.of(context);
    return ResponsiveData._(mq.size, mq.padding);
  }
}

class ResponsiveData {
  final Size _size;
  final EdgeInsets _viewPadding;

  const ResponsiveData._(this._size, this._viewPadding);

  // ── Raw screen info ──────────────────────────────────────────────
  double get width => _size.width;
  double get height => _size.height;

  // ── Scale factors ────────────────────────────────────────────────
  double get _sw => _size.width / Responsive._designWidth;
  double get _sh => _size.height / Responsive._designHeight;

  /// Balanced scale (average of width & height), clamped to avoid extremes.
  double get scale => ((_sw + _sh) / 2).clamp(0.8, 1.4);

  // ── Device classification ────────────────────────────────────────
  bool get isSmallPhone => _size.width < 360;
  bool get isPhone => _size.width >= 360 && _size.width < 600;
  bool get isTablet => _size.width >= 600;

  // ── Dimension scalers ────────────────────────────────────────────
  /// Scale a width-axis value (margins, horizontal padding, widths).
  double w(double v) => v * _sw;

  /// Scale a height-axis value (vertical padding, heights).
  double h(double v) => v * _sh;

  /// Scale a balanced value (border radius, icon size, general spacing).
  double r(double v) => v * scale;

  /// Scale a font-size value.
  double sp(double v) => v * scale;

  // ── Common layout constants ──────────────────────────────────────
  /// Standard text-input field height (~38 % of screen).
  double get inputFieldHeight => _size.height * 0.38;

  /// Standard horizontal page padding.
  double get pagePaddingH => w(16);

  // ── Padding helpers ──────────────────────────────────────────────
  EdgeInsets symmetric({double horizontal = 0, double vertical = 0}) =>
      EdgeInsets.symmetric(horizontal: w(horizontal), vertical: h(vertical));

  EdgeInsets all(double v) => EdgeInsets.all(r(v));

  EdgeInsets fromLTRB(double l, double t, double right, double b) =>
      EdgeInsets.fromLTRB(w(l), h(t), w(right), h(b));
}
