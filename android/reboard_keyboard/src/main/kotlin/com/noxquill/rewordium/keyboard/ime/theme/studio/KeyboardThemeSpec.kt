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

package com.noxquill.rewordium.keyboard.ime.theme.studio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** How a key reacts when pressed. */
enum class KeyPressAnimation(val label: String) {
    NONE("Still"),
    BOUNCE("Bounce"),
    POP("Pop"),
    SQUISH("Squish"),
    WOBBLE("Wobble"),
    JELLY("Jelly"),
}

/** The silhouette of a key. Every shape is a real Snygg shape, so it is also what the keyboard draws. */
enum class KeyShape(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    SOFT("Soft"),
    PILL("Pill"),
    BUBBLE("Bubble"),
    LEAF("Leaf"),
    CUT("Cut"),
    TICKET("Ticket"),
}

/** The typeface on the keys. Bundled fonts are copied into the theme so it renders the same everywhere. */
enum class KeyFont(val label: String, val resource: String?, val family: String?) {
    SYSTEM("System", null, null),
    ROUNDED("Rounded", "fredoka", "Rewordium Rounded"),
    SOFT("Soft", "comfortaa", "Rewordium Soft"),
    MONO("Mono", "space_mono_bold", "Rewordium Mono"),
    SERIF("Serif", "instrument_serif_regular", "Rewordium Serif"),
}

/**
 * A keyboard theme as the Theme shop, the builder, Jade and the community share it: a handful of colours
 * and choices that [KeyboardThemeFactory] turns into a full Snygg stylesheet. Colours are `#RRGGBB`.
 * Small and stable on purpose: it is stored with each installed theme and published as JSON.
 */
@Serializable
data class KeyboardThemeSpec(
    val name: String,
    val night: Boolean = false,
    /** Behind the keys. */
    val background: String,
    /** Letter keys. */
    val keys: String,
    /** Letters on the keys. */
    val letters: String,
    /** Enter, pressed states, the glide trail and highlights. */
    val accent: String,
    /** Shift, delete, symbols and other function keys; derived from the keys and accent when null. */
    val special: String? = null,
    val shape: KeyShape = KeyShape.ROUNDED,
    /** The shape a key morphs into while pressed; null keeps its shape. */
    @SerialName("pressed_shape") val pressedShape: KeyShape? = null,
    /** A thin outline around keys in the letter colour. */
    val outline: Boolean = false,
    /** Key shadow, 0 (flat) to 4 dp. */
    val shadow: Int = 1,
    val font: KeyFont = KeyFont.SYSTEM,
    val bold: Boolean = false,
    /** The bot on the space bar while this theme is applied (a bot type), or null to leave it as is. */
    val mascot: String? = null,
    /** "playful", "expressive", "professional", "clean" or "" — for grouping in the shop. */
    val style: String = "",
    val author: String = "",
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    /** The same theme with colours normalised and values in range, safe to install from any source. */
    fun sanitized(): KeyboardThemeSpec = copy(
        name = name.trim().take(40).ifBlank { "My theme" },
        background = normalizeHex(background) ?: "#1F1F24",
        keys = normalizeHex(keys) ?: "#3A3A42",
        letters = normalizeHex(letters) ?: "#E6E6EA",
        accent = normalizeHex(accent) ?: "#7A2BE0",
        special = special?.let { normalizeHex(it) },
        shadow = shadow.coerceIn(0, 4),
        mascot = mascot?.takeIf { it.isNotBlank() }?.take(20),
        style = style.take(20),
        author = author.take(40),
    )

    /** [sanitized], with the letters swapped for black or white when they wouldn't read on the keys. */
    fun readable(): KeyboardThemeSpec {
        val s = sanitized()
        return if (Palette.contrast(s.letters, s.keys) >= 4.5) s else s.copy(letters = Palette.onColor(s.keys))
    }

    /** True when the letters read well on the keys (WCAG AA for normal text). */
    val lettersReadable: Boolean get() = Palette.contrast(sanitized().letters, sanitized().keys) >= 4.5

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }

        /** A good text colour (near-black or white) on [background] (`#RRGGBB`). */
        fun textOn(background: String): String = Palette.onColor(normalizeHex(background) ?: "#FFFFFF")

        /** Mixes two `#RRGGBB` colours ([t] = 0 is [a], 1 is [b]). */
        fun mix(a: String, b: String, t: Double): String = Palette.mix(normalizeHex(a) ?: "#FFFFFF", normalizeHex(b) ?: "#000000", t)

        fun fromJson(text: String): KeyboardThemeSpec? =
            runCatching { JSON.decodeFromString(serializer(), text).sanitized() }.getOrNull()

        /** `#RGB`, `#RRGGBB` or `#AARRGGBB` (any case, with or without `#`) as `#RRGGBB`; null if not a colour. */
        fun normalizeHex(value: String): String? {
            val hex = value.trim().removePrefix("#")
            val six = when {
                hex.length == 3 && hex.all { it.isLetterOrDigit() } -> hex.map { "$it$it" }.joinToString("")
                hex.length == 6 -> hex
                hex.length == 8 -> hex.substring(2)
                else -> return null
            }
            return if (six.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) "#" + six.uppercase() else null
        }
    }
}

/** Colour arithmetic on `#RRGGBB` strings, for deriving a full palette from a few choices. */
internal object Palette {
    fun rgb(hex: String): Triple<Int, Int, Int> {
        val v = hex.removePrefix("#").toLong(16).toInt()
        return Triple((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
    }

    fun hex(r: Int, g: Int, b: Int) = "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    fun mix(a: String, b: String, t: Double): String {
        val (r1, g1, b1) = rgb(a)
        val (r2, g2, b2) = rgb(b)
        fun m(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return hex(m(r1, r2), m(g1, g2), m(b1, b2))
    }

    /** Snygg's `#RRGGBBAA`. */
    fun alpha(hex: String, alpha: Double): String = hex + "%02X".format((alpha.coerceIn(0.0, 1.0) * 255).toInt())

    fun luminance(hex: String): Double {
        val (r, g, b) = rgb(hex)
        fun c(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * c(r) + 0.7152 * c(g) + 0.0722 * c(b)
    }

    fun contrast(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Near-black or near-white, whichever reads better on [background]. */
    fun onColor(background: String): String =
        if (contrast(background, "#15161A") >= contrast(background, "#FFFFFF")) "#15161A" else "#FFFFFF"

    /** [preferred] if it reads on [background] (contrast >= 3), else the better of black and white. */
    fun readable(preferred: String, background: String): String =
        if (contrast(preferred, background) >= 3.0) preferred else onColor(background)
}
