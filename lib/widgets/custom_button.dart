import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

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

  // In lib/widgets/custom_button.dart

@override
Widget build(BuildContext context) {
  final Color backgroundColor = type == ButtonType.primary
      ? (customColor ?? AppTheme.primaryColor)
      : Colors.transparent;

  final Color textColor = type == ButtonType.primary
      ? Colors.white
      : (customColor ?? AppTheme.primaryColor);

  final Color borderColor = (customColor ?? AppTheme.primaryColor)
      .withOpacity(type == ButtonType.primary ? 0.0 : 0.5);

  final double buttonHeight = height ?? 50;
  final double buttonWidth = width ?? double.infinity;
  final double fontSize = buttonHeight <= 40 ? 14 : 16;

  // The core button content, which will be shared
  final buttonContent = Ink(
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(16),
      border: type == ButtonType.secondary
          ? Border.all(color: borderColor, width: 1.5)
          : null,
    ),
    child: Center(
      child: isLoading
          ? SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: textColor,
              ),
            )
          : FittedBox(
              fit: BoxFit.scaleDown,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (icon != null) ...[
                      Icon(icon, size: 18, color: textColor),
                      const SizedBox(width: 6),
                    ],
                    Text(
                      text,
                      style: TextStyle(
                        color: textColor,
                        fontSize: fontSize,
                        fontWeight: FontWeight.w600,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ),
    ),
  );

  return SizedBox(
    width: buttonWidth,
    height: buttonHeight,
    // Use a GestureDetector to reliably capture taps over the entire area.
    child: GestureDetector(
      onTap: isLoading ? null : onPressed,
      // This is the key: it prevents taps from passing through empty areas.
      behavior: HitTestBehavior.opaque,
      // The Material provides the background color and clipping for the InkWell's ripple.
      child: Material(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(16),
        // The InkWell is now just for the ripple effect, not for tap detection.
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          child: buttonContent,
        ),
      ),
    ),
  );
}
}