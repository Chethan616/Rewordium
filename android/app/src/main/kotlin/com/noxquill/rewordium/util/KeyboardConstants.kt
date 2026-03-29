package com.noxquill.rewordium.util

/**
 * Constants for keyboard settings and preferences.
 * Used for communication between the Flutter app and keyboard services.
 */
object KeyboardConstants {
    // SharedPreferences name
    const val PREFS_NAME = "keyboard_settings"
    
    // Setting keys
    const val KEY_DARK_MODE = "dark_mode"
    const val KEY_THEME_COLOR = "theme_color"
    const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
    const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
    const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
    const val KEY_AUTOCORRECT = "autocorrect"
    const val KEY_GLIDE_TYPING_ENABLED = "glide_typing_enabled"
    const val KEY_SPACEBAR_NAVIGATION_ENABLED = "spacebar_navigation_enabled"
    const val KEY_AI_SUGGESTIONS = "ai_suggestions"
    const val KEY_KEYBOARD_SELECTED_AS_DEFAULT = "keyboard_selected_as_default"
    const val KEY_PERSONAS = "personas"
    
    // Broadcast actions
    const val ACTION_SETTINGS_UPDATED = "com.noxquill.rewordium.ACTION_SETTINGS_UPDATED"
    const val ACTION_PERSONAS_UPDATED = "com.noxquill.rewordium.ACTION_PERSONAS_UPDATED"
}
