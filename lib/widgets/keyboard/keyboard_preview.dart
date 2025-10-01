import 'package:flutter/material.dart';

class KeyboardPreview extends StatelessWidget {
  final String themeColor;
  final bool isDarkMode;

  const KeyboardPreview({
    Key? key,
    required this.themeColor,
    required this.isDarkMode,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    // Parse theme color
    final Color accentColor =
        Color(int.parse(themeColor.substring(1, 7), radix: 16) + 0xFF000000);

    // Set colors based on dark mode
    final Color keyBackgroundColor =
        isDarkMode ? const Color(0xFF333333) : Colors.white;
    final Color keyTextColor = isDarkMode ? Colors.white : Colors.black;
    final Color specialKeyBackgroundColor =
        isDarkMode ? const Color(0xFF222222) : const Color(0xFFD1D1D6);
    final Color keyboardBackgroundColor =
        isDarkMode ? const Color(0xFF1C1C1E) : const Color(0xFFD1D1D6);

    return Container(
      height: 200,
      decoration: BoxDecoration(
        color: keyboardBackgroundColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      padding: const EdgeInsets.all(8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Top bar with AI button and suggestions
          Row(
            children: [
              // AI Button
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: accentColor,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Text(
                  'AI',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              // Suggestion chips
              Expanded(
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color:
                            isDarkMode ? const Color(0xFF333333) : Colors.white,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: isDarkMode
                              ? Colors.grey.shade700
                              : Colors.grey.shade300,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color:
                            isDarkMode ? const Color(0xFF333333) : Colors.white,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: isDarkMode
                              ? Colors.grey.shade700
                              : Colors.grey.shade300,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // QWERTY row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: 'QWERTYUIOP'
                .split('')
                .map((letter) => _buildKey(
                      letter,
                      keyBackgroundColor,
                      keyTextColor,
                    ))
                .toList(),
          ),
          const SizedBox(height: 4),

          // ASDF row
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: 'ASDFGHJKL'
                  .split('')
                  .map((letter) => _buildKey(
                        letter,
                        keyBackgroundColor,
                        keyTextColor,
                      ))
                  .toList(),
            ),
          ),
          const SizedBox(height: 4),

          // ZXCV row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildSpecialKey(
                '⇧',
                specialKeyBackgroundColor,
                keyTextColor,
                1.5,
              ),
              ...'ZXCVBNM'
                  .split('')
                  .map((letter) => _buildKey(
                        letter,
                        keyBackgroundColor,
                        keyTextColor,
                      ))
                  .toList(),
              _buildSpecialKey(
                '⌫',
                specialKeyBackgroundColor,
                keyTextColor,
                1.5,
              ),
            ],
          ),
          const SizedBox(height: 4),

          // Bottom row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildSpecialKey(
                '123',
                specialKeyBackgroundColor,
                keyTextColor,
                1.5,
              ),
              _buildSpecialKey(
                '🌐',
                specialKeyBackgroundColor,
                keyTextColor,
                1.0,
              ),
              _buildKey(
                'space',
                keyBackgroundColor,
                keyTextColor,
                5.0,
              ),
              _buildSpecialKey(
                '😊',
                specialKeyBackgroundColor,
                keyTextColor,
                1.0,
              ),
              _buildSpecialKey(
                'return',
                accentColor,
                Colors.white,
                1.5,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildKey(String text, Color backgroundColor, Color textColor,
      [double widthFactor = 1.0]) {
    return Container(
      width: 24 * widthFactor,
      height: 32,
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(6),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 1,
            offset: const Offset(0, 1),
          ),
        ],
      ),
      alignment: Alignment.center,
      child: Text(
        text,
        style: TextStyle(
          color: textColor,
          fontSize: text == 'space' ? 10 : 14,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }

  Widget _buildSpecialKey(
      String text, Color backgroundColor, Color textColor, double widthFactor) {
    return Container(
      width: 24 * widthFactor,
      height: 32,
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(6),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 1,
            offset: const Offset(0, 1),
          ),
        ],
      ),
      alignment: Alignment.center,
      child: Text(
        text,
        style: TextStyle(
          color: textColor,
          fontSize: 12,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }
}
