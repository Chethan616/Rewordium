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

/** The Theme shop's curated collections. */
object ThemePacks {
    data class Pack(val id: String, val title: String, val tagline: String, val themes: List<KeyboardThemeSpec>)

    private fun t(
        name: String, night: Boolean, background: String, keys: String, letters: String, accent: String, special: String,
        shape: KeyShape, pressed: KeyShape? = null, font: KeyFont = KeyFont.SYSTEM, bold: Boolean = false,
        outline: Boolean = false, shadow: Int = 1, mascot: String? = null, style: String,
    ) = KeyboardThemeSpec(
        name = name, night = night, background = background, keys = keys, letters = letters, accent = accent,
        special = special, shape = shape, pressedShape = pressed, outline = outline, shadow = shadow, font = font,
        bold = bold, mascot = mascot, style = style, author = "Rewordium",
    )

    val playful = Pack(
        "playful", "Playful", "Candy colours, squishy keys and a buddy on the space bar",
        listOf(
            t("Bubblegum Pop", false, "#FFE4F1", "#FFFFFF", "#5B2245", "#FF4FA3", "#FFC1DF", KeyShape.BUBBLE, KeyShape.PILL, KeyFont.ROUNDED, bold = true, shadow = 2, mascot = "blob", style = "playful"),
            t("Lemon Fizz", false, "#FFF4BF", "#FFFFFF", "#4A3B00", "#FFB400", "#FFE38A", KeyShape.SOFT, KeyShape.PILL, KeyFont.ROUNDED, bold = true, shadow = 2, mascot = "star", style = "playful"),
            t("Grape Soda", true, "#1E1033", "#33204F", "#F3E8FF", "#B07CFF", "#4A2E73", KeyShape.SOFT, KeyShape.PILL, KeyFont.ROUNDED, bold = true, shadow = 2, mascot = "ghost", style = "playful"),
            t("Minty Bot", false, "#DDF7EC", "#FFFFFF", "#0D3B2C", "#14B87A", "#B8EED8", KeyShape.LEAF, KeyShape.SOFT, KeyFont.ROUNDED, shadow = 2, mascot = "droid", style = "playful"),
            t("Cotton Candy", false, "#E8E6FF", "#FFFFFF", "#2E2A5C", "#7C6CFF", "#FFD1EC", KeyShape.BUBBLE, KeyShape.PILL, KeyFont.SOFT, bold = true, shadow = 2, mascot = "cloud", style = "playful"),
            t("Arcade", true, "#0E0B1F", "#1E1A3A", "#FFFFFF", "#FF3D7F", "#2B2459", KeyShape.CUT, KeyShape.SQUARE, KeyFont.MONO, outline = true, shadow = 0, mascot = "mech", style = "playful"),
        ),
    )

    val expressive = Pack(
        "expressive", "Expressive", "Bold Material 3 Expressive colour with shaped keys that morph",
        listOf(
            t("Coral Reef", false, "#FFDAD3", "#FFF4F1", "#3B0A04", "#FF5A3C", "#FFB4A6", KeyShape.SOFT, KeyShape.PILL, bold = true, style = "expressive"),
            t("Electric Lime", true, "#12160A", "#232B12", "#ECFFC2", "#C8FF3D", "#3A4A12", KeyShape.PILL, KeyShape.SOFT, bold = true, style = "expressive"),
            t("Ocean Pop", false, "#D3ECFF", "#FFFFFF", "#002B4A", "#0B84D9", "#A6D5FF", KeyShape.TICKET, KeyShape.PILL, style = "expressive"),
            t("Sunset", true, "#2A0F12", "#47191E", "#FFE7DC", "#FF8A3D", "#6A2329", KeyShape.SOFT, KeyShape.PILL, bold = true, style = "expressive"),
            t("Violet Bloom", true, "#1A1028", "#2B1D40", "#F1E6FF", "#E07BFF", "#3E2A5C", KeyShape.BUBBLE, KeyShape.PILL, style = "expressive"),
            t("Tangerine", false, "#FFE8CC", "#FFF8F0", "#3D1F00", "#FF8A00", "#FFD199", KeyShape.LEAF, KeyShape.PILL, bold = true, style = "expressive"),
        ),
    )

    val professional = Pack(
        "professional", "Professional", "Calm, precise and easy on the eyes all day",
        listOf(
            t("Graphite", true, "#18191B", "#2A2C30", "#E8EAED", "#8AB4F8", "#34373C", KeyShape.ROUNDED, shadow = 0, style = "professional"),
            t("Navy", true, "#0F1A2B", "#1B2A40", "#E6EDF7", "#5C9DFF", "#23354F", KeyShape.ROUNDED, shadow = 0, style = "professional"),
            t("Paper", false, "#F4F1EA", "#FFFFFF", "#2B2A27", "#2F6F5E", "#E6E1D6", KeyShape.SQUARE, font = KeyFont.SERIF, outline = true, shadow = 0, style = "professional"),
            t("Slate", false, "#E3E7EC", "#FFFFFF", "#1F2933", "#3B5B7A", "#CDD5DE", KeyShape.ROUNDED, style = "professional"),
            t("Midnight Gold", true, "#0B0B0D", "#19191C", "#F2EBDD", "#D4A64A", "#232327", KeyShape.SQUARE, font = KeyFont.SERIF, outline = true, shadow = 0, style = "professional"),
            t("Terminal", true, "#0A0F0A", "#121A12", "#9DFF9D", "#3DDC84", "#182418", KeyShape.CUT, font = KeyFont.MONO, shadow = 0, style = "professional"),
        ),
    )

    val clean = Pack(
        "clean", "Clean Android", "Pixel-simple, Material You calm",
        listOf(
            t("Pixel Light", false, "#EDF0F5", "#FFFFFF", "#1B1C1F", "#0B57D0", "#D3E3FD", KeyShape.ROUNDED, shadow = 0, style = "clean"),
            t("Pixel Dark", true, "#1B1C1F", "#2F3034", "#E3E3E6", "#A8C7FA", "#3F4552", KeyShape.ROUNDED, shadow = 0, style = "clean"),
            t("Android Green", false, "#E8F5E9", "#FFFFFF", "#1B2B1D", "#1E8E4F", "#C7E9CD", KeyShape.SOFT, shadow = 0, style = "clean"),
            t("Material Lilac", false, "#F3EDF7", "#FFFFFF", "#1D1B20", "#6750A4", "#E8DEF8", KeyShape.ROUNDED, shadow = 0, style = "clean"),
            t("Material Night", true, "#141218", "#2B2930", "#E6E0E9", "#D0BCFF", "#4A4458", KeyShape.ROUNDED, shadow = 0, style = "clean"),
            t("Pure Black", true, "#000000", "#141414", "#FFFFFF", "#8AB4F8", "#1F1F1F", KeyShape.ROUNDED, shadow = 0, style = "clean"),
        ),
    )

    val all: List<Pack> = listOf(playful, expressive, professional, clean)
}
