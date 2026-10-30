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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Turns a [KeyboardThemeSpec] into a Snygg stylesheet. The bundled Material stylesheet is the template, so
 * every keyboard surface (keys, popups, smartbar, clipboard, emoji, panels) is covered. Its colour
 * variables get the spec's derived palette, and the playful layer goes on top:
 *  * shaped keys that morph while pressed, function keys tinted with the accent, a pill space bar;
 *  * popups that pop out in the accent, pill-shaped suggestion chips, the auto-correction in the accent;
 *  * the theme's own typeface on keys, popups and suggestions.
 */
object KeyboardThemeFactory {
    const val TEMPLATE_ASSET = "ime/theme/com.noxquill.reboardstyle/stylesheets/reboard_material_light.json"

    private val json = Json { prettyPrint = false }

    /** The colours every surface is drawn with, derived from the spec's few choices. */
    data class Colors(
        val background: String,
        val backgroundAlt: String,
        val key: String,
        val keyPressed: String,
        val onKeyPressed: String,
        val letters: String,
        val lettersDim: String,
        val lettersDisabled: String,
        val accent: String,
        val onAccent: String,
        val accentStrong: String,
        val onAccentStrong: String,
        val special: String,
        val onSpecial: String,
        val punctuation: String,
        val popup: String,
        val indicator: String,
        val highlight: String,
    )

    fun colors(spec: KeyboardThemeSpec): Colors {
        val s = spec.sanitized()
        val accentStrong = if (s.night) Palette.mix(s.accent, "#FFFFFF", 0.22) else Palette.mix(s.accent, "#000000", 0.18)
        val special = s.special ?: Palette.mix(s.keys, s.accent, if (s.night) 0.30 else 0.22)
        val keyPressed = Palette.mix(s.keys, s.accent, 0.5)
        return Colors(
            background = s.background,
            backgroundAlt = Palette.mix(s.background, s.keys, 0.5),
            key = s.keys,
            keyPressed = keyPressed,
            onKeyPressed = Palette.readable(s.letters, keyPressed),
            letters = s.letters,
            lettersDim = Palette.mix(s.letters, s.keys, 0.35),
            lettersDisabled = Palette.mix(s.letters, s.keys, 0.6),
            accent = s.accent,
            onAccent = Palette.onColor(s.accent),
            accentStrong = accentStrong,
            onAccentStrong = Palette.onColor(accentStrong),
            special = special,
            onSpecial = Palette.readable(s.letters, special),
            punctuation = Palette.mix(s.keys, s.accent, 0.12),
            popup = Palette.mix(s.keys, s.letters, if (s.night) 0.10 else 0.04),
            indicator = Palette.mix(s.letters, s.background, 0.7),
            // The auto-correction and focused tabs: the accent where it reads on the background.
            highlight = Palette.readable(s.accent, s.background),
        )
    }

    fun shape(shape: KeyShape): String = when (shape) {
        KeyShape.SQUARE -> "rounded-corner(3dp, 3dp, 3dp, 3dp)"
        KeyShape.ROUNDED -> "rounded-corner(9dp, 9dp, 9dp, 9dp)"
        KeyShape.SOFT -> "rounded-corner(32%, 32%, 32%, 32%)"
        KeyShape.PILL -> "rounded-corner(50%, 50%, 50%, 50%)"
        KeyShape.BUBBLE -> "rounded-corner(46%, 46%, 46%, 10%)"
        KeyShape.LEAF -> "rounded-corner(50%, 8%, 50%, 8%)"
        KeyShape.CUT -> "cut-corner(7dp, 7dp, 7dp, 7dp)"
        KeyShape.TICKET -> "rounded-corner(4dp, 18dp, 4dp, 18dp)"
    }

    /** The font file inside the theme archive for [font], or null for the system font. */
    fun fontPath(font: KeyFont): String? = font.resource?.let { "fonts/$it.ttf" }

    /** A complete stylesheet for [spec], built on [template] (the bundled Material stylesheet's JSON). */
    fun stylesheet(spec: KeyboardThemeSpec, template: String): String {
        val s = spec.sanitized()
        val c = colors(s)
        val root = json.parseToJsonElement(template).jsonObject.toMutableMap()

        // 1. The template's colour and shape variables, on the spec's palette.
        val keyShape = shape(s.shape)
        root["@defines"] = obj(
            "--accent" to c.accent,
            "--accent-text" to c.onAccent,
            "--accent-pressed" to c.accentStrong,
            "--accent-pressed-text" to c.onAccentStrong,
            "--tertiary" to c.accentStrong,
            "--tertiary-text" to c.onAccentStrong,
            "--bg" to c.background,
            "--bg-alt" to c.backgroundAlt,
            "--key-bg" to c.key,
            "--key-bg-pressed" to c.keyPressed,
            "--key-fg" to c.letters,
            "--key-fg-dim" to c.lettersDim,
            "--key-fg-disabled" to c.lettersDisabled,
            "--popup-bg" to c.popup,
            "--popup-bg-focus" to c.accent,
            "--indicator" to c.indicator,
            "--one-hand-bg" to c.special,
            "--one-hand-fg" to c.onSpecial,
            "--incognito" to Palette.alpha(c.letters, 0.07),
            "--r-sm" to keyShape,
            "--r-md" to "rounded-corner(18dp, 18dp, 18dp, 18dp)",
            "--r-lg" to "rounded-corner(24dp, 24dp, 24dp, 24dp)",
            "--r-xl" to "rounded-corner(30dp, 30dp, 30dp, 30dp)",
            "--r-pill" to "rounded-corner(50%, 50%, 50%, 50%)",
        )

        // 2. The theme's typeface.
        val family = s.font.family
        val weight = when {
            s.font == KeyFont.MONO -> "700"
            s.font == KeyFont.SERIF -> "400"
            s.bold -> "700"
            family != null -> "500"
            else -> null
        }
        fun MutableMap<String, JsonElement>.typeface() {
            if (family != null) put("font-family", JsonPrimitive("`$family`"))
            if (weight != null) put("font-weight", JsonPrimitive(weight)) else if (s.bold) put("font-weight", JsonPrimitive("bold"))
        }
        if (family != null) {
            root["@font `$family`"] = JsonArray(listOf(obj("src" to "uri(`flex:/${fontPath(s.font)}`)", "font-weight" to (weight ?: "400"))))
        }

        fun rule(name: String, vararg props: Pair<String, String>, extra: MutableMap<String, JsonElement>.() -> Unit = {}) {
            val current = (root[name] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            for ((k, v) in props) current[k] = JsonPrimitive(v)
            current.extra()
            root[name] = JsonObject(current)
        }

        // 3. Keys: shape, depth, outline and type; a press lights the key up in the accent and morphs it.
        rule("key", "shape" to keyShape, "shadow-elevation" to "${s.shadow}dp") {
            if (s.outline) {
                put("border-color", JsonPrimitive(Palette.alpha(c.letters, 0.35)))
                put("border-width", JsonPrimitive("1dp"))
            }
            typeface()
        }
        rule("key:pressed", "background" to c.keyPressed, "foreground" to c.onKeyPressed, "shape" to shape(s.pressedShape ?: s.shape)) { typeface() }
        // Hints sit in the top-end corner; on very round keys move them in so the corner doesn't clip them.
        val roundCorner = s.shape in setOf(KeyShape.SOFT, KeyShape.PILL, KeyShape.BUBBLE, KeyShape.LEAF, KeyShape.TICKET)
        rule("key-hint", "padding" to if (roundCorner) "0dp 3dp 6dp 0dp" else "0dp 1dp 2dp 0dp") { typeface(); put("font-size", JsonPrimitive("11sp")) }

        // Function keys wear the accent tint; a press fills them with the accent. Every rule repeats the
        // typeface explicitly — a selector this specific isn't guaranteed to fall back to the base "key"
        // rule's font, so without it bold/the theme's font silently drop on these keys (shift, delete, the
        // ?123/ABC toggle, the number-pad switch) while every plain letter key keeps it.
        for (code in listOf("-7", "-5", "-11", "-201,-202,-203", "-204,-205")) {
            rule("key[code=$code]", "background" to c.special, "foreground" to c.onSpecial) { typeface() }
            rule("key[code=$code]:pressed", "background" to c.accent, "foreground" to c.onAccent, "shape" to shape(s.pressedShape ?: s.shape)) { typeface() }
        }
        rule("key[code=-201,-202,-203]", "shape" to "rounded-corner(50%, 50%, 50%, 50%)") { typeface() }
        rule("key[code=-11][shiftstate=`shifted_manual`]", "background" to c.accent, "foreground" to c.onAccent) { typeface() }
        rule("key[code=-11][shiftstate=`shifted_automatic`]", "background" to c.accent, "foreground" to c.onAccent) { typeface() }
        rule("key[code=-11][shiftstate=`caps_lock`]", "background" to c.accentStrong, "foreground" to c.onAccentStrong) {
            put("border-color", JsonPrimitive(c.onAccentStrong))
            put("border-width", JsonPrimitive("2dp"))
            typeface()
        }
        // Comma and full stop: a whisper of the accent, so the bottom row reads as a group.
        for (code in listOf("44", "46")) {
            rule("key[code=$code]", "background" to c.punctuation, "foreground" to c.letters) { typeface() }
            rule("key[code=$code]:pressed", "background" to c.keyPressed, "foreground" to c.onKeyPressed) { typeface() }
        }
        // Enter is the hero: the accent, round, and stronger when pressed.
        rule("key[code=10]", "background" to c.accent, "foreground" to c.onAccent, "shape" to "rounded-corner(50%, 50%, 50%, 50%)", "shadow-elevation" to "${(s.shadow + 1).coerceAtMost(4)}dp") { typeface() }
        rule("key[code=10]:pressed", "background" to c.accentStrong, "foreground" to c.onAccentStrong) { typeface() }
        // The space bar is a pill in every theme.
        rule("key[code=32]", "shape" to "rounded-corner(50%, 50%, 50%, 50%)", "foreground" to c.lettersDim) { typeface() }
        rule("key[code=32]:pressed", "background" to c.keyPressed, "shape" to "rounded-corner(50%, 50%, 50%, 50%)") { typeface() }

        // 4. Popups pop: big, soft, raised, the focused letter in an accent pill.
        rule("key-popup-box", "shape" to "rounded-corner(20dp, 20dp, 20dp, 20dp)", "shadow-elevation" to "8dp", "font-size" to "26sp") { typeface() }
        rule("key-popup-element:focus", "background" to c.accent, "foreground" to c.onAccent, "shape" to "rounded-corner(50%, 50%, 50%, 50%)")
        rule("media-emoji-key-popup-box", "shape" to "rounded-corner(20dp, 20dp, 20dp, 20dp)", "shadow-elevation" to "8dp")
        rule("media-emoji-key-popup-element:focus", "background" to Palette.alpha(c.accent, 0.35), "shape" to "rounded-corner(50%, 50%, 50%, 50%)")

        // 5. Suggestions: pill chips, the auto-correction bold in the accent.
        rule("smartbar") { typeface() }
        rule("smartbar-candidate-word", "shape" to "rounded-corner(50%, 50%, 50%, 50%)", "padding" to "10dp 4dp")
        rule("smartbar-candidate-word:pressed", "background" to c.keyPressed, "foreground" to c.onKeyPressed)
        rule("smartbar-candidate-word-text[auto-commit=1]", "font-weight" to "bold", "foreground" to c.highlight)
        rule("smartbar-candidate-clip", "shape" to "rounded-corner(50%, 50%, 50%, 50%)")
        rule("smartbar-action-key", "shape" to "rounded-corner(50%, 50%, 50%, 50%)")
        rule("smartbar-action-tile", "shape" to "rounded-corner(28%, 28%, 28%, 28%)")
        rule("inline-autofill-chip", "background" to c.key, "foreground" to c.letters) {
            put("shape", JsonPrimitive("rounded-corner(50%, 50%, 50%, 50%)"))
        }

        // 6. Panels and the glide trail.
        rule("media-emoji-tab:focus", "foreground" to c.highlight)
        rule("glide-trail", "foreground" to c.accent)
        rule("clipboard-item", "shape" to "rounded-corner(20dp, 20dp, 20dp, 20dp)")
        rule("extracted-landscape-input-field", "border-color" to Palette.alpha(c.letters, 0.4))
        rule("smartbar-actions-editor-tile[code=-991]", "foreground" to "#E5484D")

        return json.encodeToString(JsonObject.serializer(), JsonObject(root))
    }

    private fun obj(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })
}
