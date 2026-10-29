package com.noxquill.rewordium.data

import android.content.Context
import android.content.SharedPreferences
import com.noxquill.rewordium.util.KeyboardConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Rewordium app's own preferences — look, avatar, feel — as observable
 * state, so the theme and screens update the moment anything (a settings
 * row or Jade) changes them.
 */
object AppSettings {
    enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

    data class Palette(val id: String, val label: String, val seed: Long?)

    /**
     * `electric` is the hand-tuned, high-contrast look from the Material 3
     * Expressive showcase; `dynamic` follows the wallpaper (Android 12+); the
     * rest are expressive seed palettes.
     */
    val palettes = listOf(
        Palette("electric", "Electric", 0xFF7A2BE0),
        Palette("dynamic", "Wallpaper", null),
        Palette("rewordium", "Rewordium", 0xFF2458D7),
        Palette("berry", "Berry", 0xFF8D32C6),
        Palette("bubblegum", "Bubblegum", 0xFFE64389),
        Palette("sunset", "Sunset", 0xFFE5583A),
        Palette("citrus", "Citrus", 0xFFE0A100),
        Palette("mint", "Mint", 0xFF1E9E6A),
        Palette("ocean", "Ocean", 0xFF00799E),
        Palette("lavender", "Lavender", 0xFF7B5CD6),
    )

    val avatars = listOf(
        "flower", "clover", "triangle", "square", "blob", "ghost", "circle", "drop", "star",
        "droid", "mech", "alien", "hexagon", "cat", "cloud", "pill", "pebble", "puddle",
    )

    private const val APP_PREFS = "rewordium_app"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PALETTE = "palette"
    private const val KEY_AVATAR = "mascot_type"
    private const val KEY_AI_REWRITE = "ai_rewrite_enabled"

    private lateinit var app: SharedPreferences
    private lateinit var keyboard: SharedPreferences

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _palette = MutableStateFlow(defaultPalette())
    val palette: StateFlow<String> = _palette.asStateFlow()

    private val _avatar = MutableStateFlow("flower")
    val avatar: StateFlow<String> = _avatar.asStateFlow()

    private val _haptics = MutableStateFlow(true)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _keyboardAiRewrite = MutableStateFlow(false)
    val keyboardAiRewrite: StateFlow<Boolean> = _keyboardAiRewrite.asStateFlow()

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        keyboard = context.applicationContext.getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        reload()
    }

    /** Re-reads every value from storage — after a backup is restored, say. */
    fun reload() {
        if (!::app.isInitialized) return
        _themeMode.value = runCatching { ThemeMode.valueOf(app.getString(KEY_THEME_MODE, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        _palette.value = app.getString(KEY_PALETTE, null)?.takeIf { id -> palettes.any { it.id == id } } ?: defaultPalette()
        _avatar.value = app.getString(KEY_AVATAR, null)?.takeIf { it in avatars } ?: "flower"
        _haptics.value = keyboard.getBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true)
        _keyboardAiRewrite.value = keyboard.getBoolean(KEY_AI_REWRITE, false)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        app.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setPalette(id: String) {
        if (palettes.none { it.id == id }) return
        _palette.value = id
        app.edit().putString(KEY_PALETTE, id).apply()
    }

    fun setAvatar(type: String) {
        if (type !in avatars) return
        _avatar.value = type
        app.edit().putString(KEY_AVATAR, type).apply()
    }

    fun setHaptics(enabled: Boolean) {
        _haptics.value = enabled
        keyboard.edit().putBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun setKeyboardAiRewrite(enabled: Boolean) {
        _keyboardAiRewrite.value = enabled
        keyboard.edit().putBoolean(KEY_AI_REWRITE, enabled).apply()
    }

    private fun defaultPalette() = "electric"
}
