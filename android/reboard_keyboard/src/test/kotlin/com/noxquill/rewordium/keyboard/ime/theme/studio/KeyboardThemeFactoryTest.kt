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

import org.florisboard.lib.snygg.SnyggStylesheet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardThemeFactoryTest {
    private val template = File("src/main/assets/${KeyboardThemeFactory.TEMPLATE_ASSET}").readText()

    @Test
    fun `every curated theme makes a stylesheet the keyboard accepts`() {
        val themes = ThemePacks.all.flatMap { it.themes }
        assertTrue(themes.size >= 20)
        for (spec in themes) {
            val json = KeyboardThemeFactory.stylesheet(spec, template)
            val parsed = SnyggStylesheet.fromJson(json)
            assertTrue(parsed.isSuccess, "${spec.name}: ${parsed.exceptionOrNull()}")
        }
    }

    @Test
    fun `every shape, font and press style is valid`() {
        val base = ThemePacks.clean.themes.first()
        for (shape in KeyShape.entries) for (font in KeyFont.entries) {
            val spec = base.copy(shape = shape, pressedShape = KeyShape.entries[(shape.ordinal + 1) % KeyShape.entries.size], font = font, outline = true, bold = true)
            val parsed = SnyggStylesheet.fromJson(KeyboardThemeFactory.stylesheet(spec, template))
            assertTrue(parsed.isSuccess, "$shape/$font: ${parsed.exceptionOrNull()}")
        }
    }

    @Test
    fun `text stays readable on every surface`() {
        for (spec in ThemePacks.all.flatMap { it.themes }) {
            val c = KeyboardThemeFactory.colors(spec)
            assertTrue(Palette.contrast(c.letters, c.key) >= 4.5, "${spec.name}: letters on keys")
            assertTrue(Palette.contrast(c.onAccent, c.accent) >= 3.0, "${spec.name}: text on the accent")
            assertTrue(Palette.contrast(c.onSpecial, c.special) >= 3.0, "${spec.name}: text on function keys")
            assertTrue(Palette.contrast(c.onKeyPressed, c.keyPressed) >= 3.0, "${spec.name}: text on a pressed key")
        }
    }

    @Test
    fun `specs from anywhere are cleaned before use`() {
        val messy = KeyboardThemeSpec(name = "  ", background = "fff", keys = "#80FFFFFF", letters = "nope", accent = "#12ab34", shadow = 99, mascot = "")
        val clean = messy.sanitized()
        assertEquals("My theme", clean.name)
        assertEquals("#FFFFFF", clean.background)
        assertEquals("#FFFFFF", clean.keys)
        assertEquals("#E6E6EA", clean.letters)
        assertEquals("#12AB34", clean.accent)
        assertEquals(4, clean.shadow)
        assertNull(clean.mascot)
        val roundTrip = KeyboardThemeSpec.fromJson(ThemePacks.playful.themes.first().toJson())
        assertNotNull(roundTrip)
        assertEquals(ThemePacks.playful.themes.first(), roundTrip)
        assertNull(KeyboardThemeSpec.fromJson("{not json"))
    }
}
