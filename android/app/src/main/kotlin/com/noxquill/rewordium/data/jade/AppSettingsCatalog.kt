package com.noxquill.rewordium.data.jade

import android.content.Context
import com.noxquill.rewordium.data.AppSettings
import com.noxquill.rewordium.data.KeyboardPersonas
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge.Change
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge.Kind
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge.Setting

/** Rewordium's own settings, described the same way as the keyboard's so Jade can treat them alike. */
object AppSettingsCatalog {
    const val PREFIX = "app__"

    fun all(context: Context): List<Setting> {
        val personas = KeyboardPersonas.all(context).map { it.name }
        return listOf(
            Setting(
                key = "app__theme_mode", group = "app", title = "App light or dark mode",
                summary = "How the Rewordium app itself looks (not the keyboard).",
                kind = Kind.CHOICE, value = AppSettings.themeMode.value.name, defaultValue = "SYSTEM",
                options = AppSettings.ThemeMode.entries.map { it.name },
            ),
            Setting(
                key = "app__palette", group = "app", title = "App colour palette",
                summary = "dynamic follows the wallpaper; the others are expressive colour themes for the app.",
                kind = Kind.CHOICE, value = AppSettings.palette.value, defaultValue = "dynamic",
                options = AppSettings.palettes.map { it.id },
            ),
            Setting(
                key = "app__avatar", group = "app", title = "Your avatar bot",
                summary = "The bot that represents the user in chats and on Home.",
                kind = Kind.CHOICE, value = AppSettings.avatar.value, defaultValue = "flower",
                options = AppSettings.avatars,
            ),
            Setting(
                key = "app__haptics", group = "app", title = "App touch feedback",
                summary = "Small vibrations when tapping things in the Rewordium app.",
                kind = Kind.BOOLEAN, value = AppSettings.haptics.value.toString(), defaultValue = "true",
                options = listOf("true", "false"),
            ),
            Setting(
                key = "app__keyboard_ai_rewrite", group = "app", title = "AI writing tools in the keyboard",
                summary = "Shows Rewordium's AI rewrite tools while typing with the keyboard.",
                kind = Kind.BOOLEAN, value = AppSettings.keyboardAiRewrite.value.toString(), defaultValue = "false",
                options = listOf("true", "false"),
            ),
            Setting(
                key = "app__active_persona", group = "app", title = "Keyboard writing persona",
                summary = "The voice the keyboard's AI rewrite uses.",
                kind = Kind.CHOICE, value = KeyboardPersonas.active(context), defaultValue = "Happy",
                options = personas,
            ),
        )
    }

    fun owns(key: String) = key.startsWith(PREFIX)

    fun set(context: Context, key: String, raw: String): Result<Change> = runCatching {
        val before = all(context).firstOrNull { it.key == key } ?: error("There is no app setting called \"$key\".")
        val value = raw.trim()
        fun choice(options: List<String>) = options.firstOrNull { it.equals(value, ignoreCase = true) }
            ?: options.firstOrNull { it.contains(value, ignoreCase = true) }
            ?: error("\"$key\" accepts: ${options.joinToString()}.")
        fun bool() = when (value.lowercase()) {
            "true", "on", "yes", "1" -> true
            "false", "off", "no", "0" -> false
            else -> error("\"$key\" is on/off; use true or false.")
        }
        when (key) {
            "app__theme_mode" -> AppSettings.setThemeMode(AppSettings.ThemeMode.valueOf(choice(before.options)))
            "app__palette" -> AppSettings.setPalette(choice(before.options))
            "app__avatar" -> AppSettings.setAvatar(choice(before.options))
            "app__haptics" -> AppSettings.setHaptics(bool())
            "app__keyboard_ai_rewrite" -> AppSettings.setKeyboardAiRewrite(bool())
            "app__active_persona" -> {
                val name = choice(before.options)
                KeyboardPersonas.activate(context, KeyboardPersonas.all(context).first { it.name == name })
            }
        }
        val after = all(context).first { it.key == key }
        Change(key, before.title, before.value, after.value)
    }

    fun reset(context: Context, key: String): Result<Change> {
        val setting = all(context).firstOrNull { it.key == key }
            ?: return Result.failure(IllegalArgumentException("There is no app setting called \"$key\"."))
        return set(context, key, setting.defaultValue)
    }
}
