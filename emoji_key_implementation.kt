// First Emoji Key Implementation (setupLettersKeyboard method)
// Replace lines 609-613 with this:
// Emoji key
addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) {
    if (isHapticFeedbackEnabled) performHapticFeedback()
    isEmojiKeyboardShown = !isEmojiKeyboardShown
    isSymbolsShown = false
    setupKeyboard()
    Log.d(TAG, "Emoji key pressed, emoji keyboard shown: $isEmojiKeyboardShown")
}

// Second Emoji Key Implementation (setupSymbolsKeyboard method)
// Replace lines 798-801 with this:
// Emoji key
addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) {
    if (isHapticFeedbackEnabled) performHapticFeedback()
    isEmojiKeyboardShown = !isEmojiKeyboardShown
    isSymbolsShown = false
    setupKeyboard()
    Log.d(TAG, "Emoji key pressed, emoji keyboard shown: $isEmojiKeyboardShown")
}
