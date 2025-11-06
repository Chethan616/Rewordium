package com.noxquill.rewordium.keyboard.util

object KeyboardConstants {
    // SharedPreferences Keys
    const val PREFS_NAME = "FlutterSharedPreferences" // Sourced from MainActivity
    const val KEY_DARK_MODE = "flutter.darkMode" // Sourced from MainActivity
    const val KEY_THEME_COLOR = "flutter.themeColor" // Sourced from MainActivity
    const val KEY_GRADIENT_THEME = "flutter.gradientTheme" // New gradient theme support
    const val KEY_PERSONAS = "keyboard_personas"
    const val KEY_HAPTIC_FEEDBACK = "flutter.hapticFeedback"
    const val KEY_AUTO_CAPITALIZE = "flutter.autoCapitalize"
    const val KEY_DOUBLE_SPACE_PERIOD = "flutter.doubleSpacePeriod"
    const val KEY_AUTOCORRECT = "flutter.autocorrect"
    const val KEY_AUTO_CORRECTION = "flutter.autoCorrection" // New unified auto-correction key
    const val KEY_TEXT_EXPANSION = "flutter.textExpansion" // New text expansion feature
    const val KEY_ONE_HANDED_MODE = "flutter.oneHandedMode" // New one-handed mode feature
    const val KEY_RECENT_EMOJIS = "flutter.recentEmojis" // NEW
    const val KEY_CLIPBOARD_FAVORITES = "flutter.clipboardFavorites" // For storing favorite clipboard items
    
    const val ACTION_SETTINGS_UPDATED = "com.noxquill.rewordium.SETTINGS_UPDATED"
    const val ACTION_FORCE_THEME_REFRESH = "com.noxquill.rewordium.FORCE_THEME_REFRESH"


    // Intent Actions
    const val ACTION_THEME_UPDATED = "com.noxquill.rewordium.THEME_UPDATED" // Sourced from MainActivity
    const val ACTION_PERSONAS_UPDATED = "com.noxquill.rewordium.PERSONAS_UPDATED" // Sourced from MainActivity

    // UI Constants
    const val SWIPE_THRESHOLD = 50f
    const val EMOJI_COLUMNS = 8
    const val EMOJI_ROWS = 4
    const val MAX_RECENT_EMOJIS = 40 // NEW

    // Logging Tag
    const val TAG = "RewordiumAIKeyboard"

    // Memory Management
    const val MEMORY_CHECK_INTERVAL = 30000L // 30 seconds
    const val MAX_CACHE_SIZE = 50
    const val MEMORY_THRESHOLD = 0.75 // 75% of max memory

    // Turbo Delete Timings
    const val INITIAL_DELETE_DELAY = 500L
    const val REPEAT_DELETE_DELAY = 80L
    const val TURBO_DELETE_DELAY = 30L
    const val MAX_DELETES_BEFORE_TURBO = 8
    const val WORD_DELETE_DELAY = 3000L
}