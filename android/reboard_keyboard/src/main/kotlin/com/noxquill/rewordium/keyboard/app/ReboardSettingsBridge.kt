/*
 * Copyright (C) 2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noxquill.rewordium.keyboard.app

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.noxquill.rewordium.keyboard.FlorisApplication
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionComponentName
import com.noxquill.rewordium.keyboard.nlpManager
import com.noxquill.rewordium.keyboard.themeManager
import dev.patrickgold.jetpref.datastore.model.LocalTime
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong

/**
 * Every ReBoard preference — including the ones no settings screen shows —
 * described in plain values, so the host app (Jade) can read, search, change
 * and reset them without knowing the keyboard's preference model.
 *
 * Values travel as strings: booleans as `true`/`false`, enums by constant
 * name, colours as `#RRGGBB` (or `dynamic`), times as `HH:MM`, themes as
 * `extensionId:componentId`, and structured data in its stored JSON form.
 */
object ReboardSettingsBridge {
    enum class Kind { BOOLEAN, INT, LONG, FLOAT, DOUBLE, TEXT, CHOICE, COLOR, TIME, THEME, DATA }

    data class Setting(
        val key: String,
        val group: String,
        val title: String,
        val summary: String?,
        val kind: Kind,
        val value: String,
        val defaultValue: String,
        val options: List<String> = emptyList(),
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        /** Internal state, developer tools and raw data: worth a second look before changing. */
        val advanced: Boolean = false,
    )

    /** [restore] is the exact previous value (never shortened), for undo. */
    data class Change(
        val key: String,
        val title: String,
        val previous: String,
        val current: String,
        val note: String? = null,
        val restore: String = previous,
    )

    data class ThemeOption(val id: String, val label: String, val night: Boolean)

    /** Settings screens the ReBoard app can open directly (`ui://ReBoard/<path>`). */
    val screens: Map<String, String> = linkedMapOf(
        "settings/home" to "All keyboard settings",
        "settings/theme" to "Keyboard theme",
        "settings/keyboard" to "Keyboard layout & feel",
        "settings/keyboard/input-feedback" to "Sounds & vibration",
        "settings/typing" to "Typing & suggestions",
        "settings/smartbar" to "Smartbar",
        "settings/gestures" to "Gestures & glide typing",
        "settings/dictionary" to "Dictionary",
        "settings/localization" to "Languages & layouts",
        "settings/clipboard" to "Clipboard",
        "settings/media" to "Emoji & media",
        "settings/sticker-studio" to "Sticker studio",
        "settings/other" to "Other",
        "settings/other/backup" to "Back up settings",
        "settings/other/restore" to "Restore settings",
    )

    private class Range(val min: Double, val max: Double, val step: Double)

    /** The ranges the settings screens' sliders allow. */
    private val ranges: Map<String, Range> = buildMap {
        fun r(min: Number, max: Number, step: Number, vararg keys: String) =
            keys.forEach { put(it, Range(min.toDouble(), max.toDouble(), step.toDouble())) }
        r(30, 300, 5, "clipboard__suggestion_timeout")
        r(0, 10, 1, "clipboard__history_num_grid_columns_portrait", "clipboard__history_num_grid_columns_landscape")
        r(0, 120, 5, "clipboard__history_auto_clean_old_after")
        r(0, 300, 10, "clipboard__history_auto_clean_sensitive_after")
        r(5, 100, 5, "clipboard__history_size_limit")
        r(0, 500, 10, "glide__trail_fade_duration")
        r(2, 40, 1, "glide__trail_width")
        r(50, 500, 25, "glide__preview_refresh_delay")
        r(400, 4000, 100, "gestures__swipe_velocity_threshold")
        r(12, 72, 1, "gestures__swipe_distance_threshold")
        r(1, 100, 1, "input_feedback__audio_volume", "input_feedback__haptic_vibration_duration", "input_feedback__haptic_vibration_strength")
        r(50, 150, 5, "keyboard__font_size_multiplier_portrait", "keyboard__font_size_multiplier_landscape")
        r(70, 90, 1, "keyboard__one_handed_mode_scale_factor")
        r(50, 150, 5, "keyboard__height_factor_portrait", "keyboard__height_factor_landscape")
        r(0, 10, 0.5, "keyboard__key_spacing_vertical", "keyboard__key_spacing_horizontal")
        r(0, 60, 1, "keyboard__bottom_offset_portrait", "keyboard__bottom_offset_landscape")
        r(100, 700, 10, "keyboard__long_press_delay")
        r(25, 150, 5, "theme__key_press_strength")
        r(4, 48, 1, "devtools__experimental_rounded_smartbar_radius")
        r(1, 5, 1, "emoji__suggestion_query_min_length")
        r(1, 10, 1, "emoji__suggestion_candidate_max_count")
    }

    private val internalKeys = setOf(
        "localization__active_subtype_id",
        "localization__subtypes",
        "emoji__last_panel_tab",
        "suggestion__force_incognito_mode_from_dynamic",
        "smartbar__shared_actions_auto_expand_collapse",
        "spelling__contacts_requested_on_first_run",
        "spelling__contacts_prompt_dismissed",
    )

    private val prefs by FlorisPreferenceStore

    /** Starts the keyboard's preference store in this process and waits until it has loaded. */
    suspend fun awaitReady(context: Context): Boolean {
        // The keyboard normally starts this from its service or settings activity, on the main thread.
        // On a device without the keyboard's native library (an unsupported CPU), starting it throws an
        // Error rather than an Exception; report "unavailable" instead of taking the whole app down.
        val app = try {
            withContext(Dispatchers.Main) { FlorisApplication.init(context.applicationContext) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.w("ReboardSettingsBridge", "Keyboard engine unavailable", e)
            return false
        }
        return withTimeoutOrNull(5_000) { app.preferenceStoreLoaded.first { it } } ?: false
    }

    /**
     * Forgets everything the keyboard learned from typing (personal words, word pairs, emoji associations).
     * The user dictionary and contact names stay. Returns false when the keyboard engine isn't available.
     */
    suspend fun forgetLearning(context: Context): Boolean {
        if (!awaitReady(context)) return false
        return runCatching {
            val nlp by context.applicationContext.nlpManager()
            nlp.forgetLearning()
        }.isSuccess
    }

    private const val UNAVAILABLE = "Keyboard settings aren’t available on this device right now."

    private fun entries(): List<PreferenceData<*>> =
        prefs.declaredPreferenceEntries.values.sortedBy { it.key }

    suspend fun all(context: Context): List<Setting> {
        if (!awaitReady(context)) return emptyList()
        return entries().map { describe(context, it) }
    }

    suspend fun get(context: Context, key: String): Setting? {
        if (!awaitReady(context)) return null
        return find(key)?.let { describe(context, it) }
    }

    suspend fun set(context: Context, key: String, raw: String): Result<Change> = runCatching {
        check(awaitReady(context)) { UNAVAILABLE }
        val pref = find(key) ?: error("There is no keyboard setting called \"$key\".")
        val before = describe(context, pref)
        val restore = exact(pref, before.kind)
        val (value, note) = parse(context, pref, raw.trim())
        @Suppress("UNCHECKED_CAST")
        (pref as PreferenceData<Any>).set(value).getOrThrow()
        Change(pref.key, before.title, before.value, describe(context, pref).value, note, restore)
    }

    suspend fun reset(context: Context, key: String): Result<Change> = runCatching {
        check(awaitReady(context)) { UNAVAILABLE }
        val pref = find(key) ?: error("There is no keyboard setting called \"$key\".")
        val before = describe(context, pref)
        val restore = exact(pref, before.kind)
        pref.reset().getOrThrow()
        Change(pref.key, before.title, before.value, describe(context, pref).value, restore = restore)
    }

    /** The current value in a form [set] accepts back unchanged. */
    @Suppress("UNCHECKED_CAST")
    private fun exact(pref: PreferenceData<*>, kind: Kind): String = when (kind) {
        Kind.DATA -> (pref.serializer as dev.patrickgold.jetpref.datastore.model.PreferenceSerializer<Any>).serialize(pref.get() as Any).orEmpty()
        else -> display(pref, pref.get(), kind)
    }

    /** Installed keyboard themes, for the day and night theme settings. */
    suspend fun themes(context: Context): List<ThemeOption> {
        if (!awaitReady(context)) return emptyList()
        // The theme manager observes LiveData on creation, which Android only allows on the main thread.
        val manager = withContext(Dispatchers.Main) { context.themeManager().value }
        return manager.indexedThemeConfigs.value.first
            // The theme shop's try-on archive is rewritten by every preview; it is never a theme to pick.
            .filterKeys { it.extensionId != com.noxquill.rewordium.keyboard.ime.theme.studio.ThemeStudio.PREVIEW_ID }
            .map { (id, theme) -> ThemeOption(id.toString(), theme.label, theme.isNightTheme) }
            .sortedWith(compareBy({ it.night }, { it.label }))
    }

    private fun find(key: String): PreferenceData<*>? {
        val wanted = key.trim().lowercase()
        return entries().firstOrNull { it.key == wanted }
    }

    private fun describe(context: Context, pref: PreferenceData<*>): Setting {
        val default = pref.default
        val kind = kindOf(pref.key, default)
        val range = ranges[pref.key]
        val group = pref.key.substringBefore("__")
        return Setting(
            key = pref.key,
            group = group,
            title = titleFor(context, pref.key),
            summary = summaryFor(context, pref.key),
            kind = kind,
            value = display(pref, pref.get(), kind),
            defaultValue = display(pref, default, kind),
            options = when {
                default is Enum<*> -> default.declaringJavaClass.enumConstants.orEmpty().map { (it as Enum<*>).name }
                kind == Kind.BOOLEAN -> listOf("true", "false")
                else -> emptyList()
            },
            min = range?.min,
            max = range?.max,
            step = range?.step,
            advanced = kind == Kind.DATA ||
                group == "internal" ||
                (group == "devtools" && !pref.key.contains("experimental_rounded_smartbar")) ||
                pref.key in internalKeys,
        )
    }

    private fun kindOf(key: String, default: Any?): Kind = when (default) {
        is Boolean -> Kind.BOOLEAN
        is Int -> Kind.INT
        is Long -> Kind.LONG
        is Float -> Kind.FLOAT
        is Double -> Kind.DOUBLE
        is String -> Kind.TEXT
        is Enum<*> -> Kind.CHOICE
        is Color -> Kind.COLOR
        is LocalTime -> Kind.TIME
        is ExtensionComponentName -> if (key.contains("theme_id")) Kind.THEME else Kind.DATA
        else -> Kind.DATA
    }

    @Suppress("UNCHECKED_CAST")
    private fun display(pref: PreferenceData<*>, value: Any?, kind: Kind): String {
        if (value == null) return ""
        return when (kind) {
            Kind.COLOR -> {
                val color = value as Color
                if (color == Color.Unspecified) "dynamic" else "#%06X".format(color.toArgb() and 0xFFFFFF)
            }
            Kind.DATA -> (pref.serializer as dev.patrickgold.jetpref.datastore.model.PreferenceSerializer<Any>)
                .serialize(value).orEmpty().let { if (it.length > 600) it.take(600) + "…" else it }
            else -> (pref.serializer as dev.patrickgold.jetpref.datastore.model.PreferenceSerializer<Any>).serialize(value).orEmpty()
        }
    }

    /** Turns [raw] into a value of the preference's own type, or explains why it can't. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun parse(context: Context, pref: PreferenceData<*>, raw: String): Pair<Any, String?> {
        val default = pref.default
        val range = ranges[pref.key]
        fun clampNote(asked: Double, used: Double) =
            if (asked != used) "Adjusted to $used, the closest allowed value (${range?.min}–${range?.max})." else null
        return when (val kind = kindOf(pref.key, default)) {
            Kind.BOOLEAN -> when (raw.lowercase()) {
                "true", "on", "yes", "1", "enabled", "enable" -> true to null
                "false", "off", "no", "0", "disabled", "disable" -> false to null
                else -> error("\"${pref.key}\" is on/off; use true or false.")
            }
            Kind.INT, Kind.LONG, Kind.FLOAT, Kind.DOUBLE -> {
                val asked = raw.removeSuffix("%").trim().toDoubleOrNull()
                    ?: error("\"${pref.key}\" needs a number.")
                var used = asked
                if (range != null) {
                    used = asked.coerceIn(range.min, range.max)
                    used = range.min + ((used - range.min) / range.step).roundToLong() * range.step
                }
                val value: Any = when (kind) {
                    Kind.INT -> used.roundToLong().toInt()
                    Kind.LONG -> used.roundToLong()
                    Kind.FLOAT -> used.toFloat()
                    else -> used
                }
                value to clampNote(asked, used)
            }
            Kind.CHOICE -> {
                val constants = (default as Enum<*>).declaringJavaClass.enumConstants.orEmpty().map { it as Enum<*> }
                val normalized = raw.trim().uppercase().replace(' ', '_').replace('-', '_')
                val match = constants.firstOrNull { it.name == normalized }
                    ?: constants.firstOrNull { it.name.contains(normalized) }
                    ?: error("\"${pref.key}\" accepts: ${constants.joinToString { it.name }}.")
                match to null
            }
            Kind.COLOR -> {
                val lowered = raw.lowercase()
                if (lowered in setOf("dynamic", "default", "system", "auto", "unspecified", "material you")) {
                    Color.Unspecified to null
                } else {
                    val hex = raw.removePrefix("#")
                    val argb = when (hex.length) {
                        6 -> (0xFF000000L or hex.toLong(16)).toInt()
                        8 -> hex.toLong(16).toInt()
                        else -> error("Colours look like #RRGGBB, or \"dynamic\" for wallpaper colours.")
                    }
                    Color(argb) to null
                }
            }
            Kind.THEME -> {
                val themes = themes(context)
                val match = themes.firstOrNull { it.id.equals(raw, ignoreCase = true) }
                    ?: themes.firstOrNull { it.label.equals(raw, ignoreCase = true) }
                    ?: themes.firstOrNull { it.label.contains(raw, ignoreCase = true) }
                    ?: error("Unknown theme. Installed themes: ${themes.joinToString { "${it.label} (${it.id})" }}.")
                (ExtensionComponentName.from(match.id)) to null
            }
            Kind.TEXT -> raw to null
            Kind.TIME, Kind.DATA -> {
                val serializer = pref.serializer as dev.patrickgold.jetpref.datastore.model.PreferenceSerializer<Any>
                val value = runCatching { serializer.deserialize(raw) }.getOrNull()
                    ?: error("That value doesn't fit \"${pref.key}\". Current value: ${display(pref, pref.get(), kind)}")
                value to null
            }
        }
    }

    private fun titleFor(context: Context, key: String): String {
        val orientation = when {
            key.endsWith("_portrait") -> " (portrait)"
            key.endsWith("_landscape") -> " (landscape)"
            else -> ""
        }
        val base = key.removeSuffix("_portrait").removeSuffix("_landscape")
        val label = string(context, "pref__${key}__label") ?: string(context, "pref__${base}__label")
        return (label ?: humanize(base.substringAfter("__"))) + orientation
    }

    private fun summaryFor(context: Context, key: String): String? {
        val base = key.removeSuffix("_portrait").removeSuffix("_landscape")
        return (string(context, "pref__${key}__summary") ?: string(context, "pref__${base}__summary"))
            ?.takeIf { !it.contains('%') }
    }

    @SuppressLint("DiscouragedApi")
    private fun string(context: Context, name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) null else runCatching { context.getString(id) }.getOrNull()
    }

    private fun humanize(raw: String): String =
        raw.replace("__", " ").replace('_', ' ').trim().replaceFirstChar { it.uppercase() }
}
