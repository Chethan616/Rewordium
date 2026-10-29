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

package com.noxquill.rewordium.keyboard.ime.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Editing a word in the middle and then picking a suggestion must replace the
 * whole word — the "I lov Rewordium" -> "love" -> "Lovev" bug.
 *
 * [FakeEditor] follows Android's BaseInputConnection semantics for the calls
 * the keyboard makes, so each test asserts the complete resulting text.
 */
class ComposingWordTest {

    /** A text field: text, cursor and composing region, driven like an InputConnection. */
    private class FakeEditor(var text: String, var cursor: Int) {
        var composing: IntRange? = null

        fun setComposingRegion(start: Int, end: Int) {
            composing = minOf(start, end) until maxOf(start, end)
        }

        /** Replaces the composing region (or inserts at the cursor) and leaves the result composing. */
        fun setComposingText(t: String) {
            val (start, end) = composing?.let { it.first to it.last + 1 } ?: (cursor to cursor)
            text = text.substring(0, start) + t + text.substring(end)
            cursor = start + t.length
            composing = start until cursor
        }

        fun finishComposingText() {
            composing = null
        }

        fun commitText(t: String) {
            val (start, end) = composing?.let { it.first to it.last + 1 } ?: (cursor to cursor)
            text = text.substring(0, start) + t + text.substring(end)
            cursor = start + t.length
            composing = null
        }

        fun deleteBefore(n: Int) {
            text = text.substring(0, cursor - n) + text.substring(cursor)
            cursor -= n
            composing = null
        }

        /** The word the keyboard now composes: the word before the cursor, extended past it. */
        fun currentWord(): EditorRange {
            val before = text.substring(0, cursor)
            var start = cursor
            while (start > 0 && (before[start - 1].isLetterOrDigit() || before[start - 1] == '\'')) start--
            val wordBefore = if (start < cursor) EditorRange(start, cursor) else EditorRange.Unspecified
            return ComposingWord.aroundCursor(wordBefore, cursor, text.substring(cursor))
        }

        /** What [AbstractEditorInstance.finalizeComposingText] does when a suggestion is picked. */
        fun pickSuggestion(suggestion: String) {
            val word = currentWord()
            setComposingRegion(word.start, word.end)
            setComposingText(suggestion)
            finishComposingText()
        }
    }

    @Test
    fun `lov to love replaces the whole word`() {
        // "I liv Rewordium": cursor between i and v, delete the i, type o.
        val editor = FakeEditor("I liv Rewordium", cursor = 4)
        editor.deleteBefore(1)
        editor.commitText("o")
        assertEquals("I lov Rewordium", editor.text)
        assertEquals(4, editor.cursor)

        val word = editor.currentWord()
        assertEquals(EditorRange(2, 5), word, "the composing word must include the v after the cursor")
        editor.pickSuggestion("love")

        assertEquals("I love Rewordium", editor.text)
        assertEquals(6, editor.cursor)
    }

    @Test
    fun `the old before-cursor-only region reproduces the bug`() {
        val editor = FakeEditor("I lov Rewordium", cursor = 4)
        editor.setComposingRegion(2, 4) // what the keyboard used to compose: "lo"
        editor.setComposingText("love")
        editor.finishComposingText()
        assertEquals("I lovev Rewordium", editor.text)
    }

    @Test
    fun `punctuation, capitals and following words are kept`() {
        val editor = FakeEditor("Well, I lov, Rewordium!", cursor = 10)
        editor.pickSuggestion("love")
        assertEquals("Well, I love, Rewordium!", editor.text)

        val capital = FakeEditor("Lo|ve you".replace("|", ""), cursor = 2)
        capital.pickSuggestion("Love")
        assertEquals("Love you", capital.text)
    }

    @Test
    fun `editing an earlier word after typing more`() {
        val editor = FakeEditor("I liv Rewordium and use it daily", cursor = 4)
        editor.deleteBefore(1)
        editor.commitText("o")
        editor.pickSuggestion("love")
        assertEquals("I love Rewordium and use it daily", editor.text)
        // The cursor sits after the corrected word, ready to keep editing there.
        assertEquals("I love", editor.text.substring(0, editor.cursor))
    }

    @Test
    fun `repeated picks replace the same word each time`() {
        val editor = FakeEditor("I lov Rewordium", cursor = 4)
        editor.pickSuggestion("love")
        editor.pickSuggestion("loved")
        editor.pickSuggestion("lovely")
        assertEquals("I lovely Rewordium", editor.text)
        assertEquals(8, editor.cursor)
    }

    @Test
    fun `cursor at the word's end changes nothing`() {
        assertEquals(EditorRange(2, 5), ComposingWord.aroundCursor(EditorRange(2, 5), 5, " Rewordium"))
        assertEquals(EditorRange(2, 5), ComposingWord.aroundCursor(EditorRange(2, 5), 5, ", next"))
        assertEquals(EditorRange(2, 5), ComposingWord.aroundCursor(EditorRange(2, 5), 5, ""))
    }

    @Test
    fun `contractions, digits and accents continue the word`() {
        assertEquals(3, ComposingWord.lengthAfterCursor("n't go"))
        assertEquals(2, ComposingWord.lengthAfterCursor("'s"))
        assertEquals(0, ComposingWord.lengthAfterCursor("' quoted"))
        assertEquals(3, ComposingWord.lengthAfterCursor("2go now"))
        assertEquals(2, ComposingWord.lengthAfterCursor("fé "))
        assertEquals(3, ComposingWord.lengthAfterCursor("és rest")) // e + combining acute + s
    }

    @Test
    fun `stops at spaces, punctuation, urls and emoji`() {
        assertEquals(0, ComposingWord.lengthAfterCursor(" word"))
        assertEquals(1, ComposingWord.lengthAfterCursor("v."))
        assertEquals(3, ComposingWord.lengthAfterCursor("com/path"))
        assertEquals(0, ComposingWord.lengthAfterCursor("😀 hi"))
    }

    @Test
    fun `the tail is bounded`() {
        val long = "a".repeat(500)
        assertEquals(ComposingWord.MAX_TAIL, ComposingWord.lengthAfterCursor(long))
    }

    @Test
    fun `a word that does not end at the cursor is left alone`() {
        assertEquals(EditorRange(0, 2), ComposingWord.aroundCursor(EditorRange(0, 2), 4, "ab"))
        assertEquals(EditorRange.Unspecified, ComposingWord.aroundCursor(EditorRange.Unspecified, 4, "ab"))
    }

    @Test
    fun `splitting a word is not a finished word`() {
        // "lo|ve" + space splits the word: no autocorrect, nothing learned.
        assertFalse(ComposingWord.cursorAtEnd(EditorRange(2, 6), 4))
        assertTrue(ComposingWord.cursorAtEnd(EditorRange(2, 6), 6))
        assertFalse(ComposingWord.cursorAtEnd(EditorRange.Unspecified, 0))
    }

    @Test
    fun `replace keeps everything outside the word`() {
        val r = ComposingWord.replace("Hey lov, Rewordium", EditorRange(4, 7), "love")
        assertEquals("Hey love", r.textBefore)
        assertEquals(", Rewordium", r.textAfter)
        assertEquals(8, r.cursor)
        // Out-of-range input is clamped rather than corrupting text.
        val clamped = ComposingWord.replace("abc", EditorRange(2, 99), "X")
        assertEquals("abX", clamped.textBefore)
        assertEquals("", clamped.textAfter)
    }
}
