import 'package:flutter/material.dart';
import 'package:m3e_collection/m3e_collection.dart';

enum ButtonType { primary, secondary }

class CustomButton extends StatelessWidget {
  final String text;
  final Function()? onPressed;
  final double? width;
  final double? height;
  final IconData? icon;
  final ButtonType type;
  final bool isLoading;
  final Color? customColor;

  const CustomButton({
    super.key,
    required this.text,
    required this.onPressed,
    this.width,
    this.height,
    this.icon,
    this.type = ButtonType.primary,
    this.isLoading = false,
    this.customColor,
  });

  ButtonM3ESize _sizeFromHeight() {
    final h = height ?? 50;
    if (h <= 36) return ButtonM3ESize.xs;
    if (h <= 44) return ButtonM3ESize.sm;
    if (h <= 64) return ButtonM3ESize.md;
    return ButtonM3ESize.lg;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final m3eSize = _sizeFromHeight();

    final ButtonM3EStyle m3eStyle;
    final Color? overrideColor;

    if (type == ButtonType.primary) {
      m3eStyle = ButtonM3EStyle.filled;
      overrideColor = customColor;
    } else {
      m3eStyle = ButtonM3EStyle.outlined;
      overrideColor = customColor;
    }

    Widget label;
    if (isLoading) {
      final loaderColor = type == ButtonType.primary
          ? (customColor != null ? Colors.white : colorScheme.onPrimary)
          : (customColor ?? colorScheme.primary);
      label = SizedBox(
        width: 24,
        height: 24,
        child: LoadingIndicatorM3E(
          color: loaderColor,
          constraints: const BoxConstraints(maxWidth: 24, maxHeight: 24),
        ),
      );
    } else {
      label = Text(text);
    }

    Widget button = ButtonM3E(
      onPressed: isLoading ? null : onPressed,
      label: label,
      icon: (!isLoading && icon != null) ? Icon(icon) : null,
      style: m3eStyle,
      size: m3eSize,
      shape: ButtonM3EShape.round,
    );

    // Apply custom color overlay via theme override when needed
    if (overrideColor != null) {
      final customScheme = type == ButtonType.primary
          ? colorScheme.copyWith(
              primary: overrideColor,
              onPrimary: Colors.white,
            )
          : colorScheme.copyWith(
              primary: overrideColor,
              outline: overrideColor,
            );
      button = Theme(
        data: Theme.of(context).copyWith(colorScheme: customScheme),
        child: button,
      );
    }

    if (width != null) {
      return SizedBox(width: width, child: button);
    }
    return button;
  }
}