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

/**
 * The word the cursor is in, on both sides of the cursor.
 *
 * The composing word used to be read from the text *before* the cursor only.
 * With the cursor inside a word ("lo|v") that made the composing word "lo",
 * so picking the suggestion "love" replaced just "lo" and left the "v"
 * behind ("lovev"). Like AOSP LatinIME's recorrection, the composing word now
 * spans the whole word around the cursor, and replacing it replaces exactly
 * that range. These helpers are pure so they can be unit tested.
 */
object ComposingWord {
    /** Never look further than this past the cursor (keeps per-keystroke work bounded). */
    const val MAX_TAIL = 48

    /**
     * How many characters right after the cursor continue the current word:
     * letters, digits, combining marks, joiners, and an apostrophe followed by
     * a letter ("do|n't" -> 3). Zero when the cursor ends the word.
     */
    fun lengthAfterCursor(textAfterCursor: CharSequence): Int {
        val limit = minOf(textAfterCursor.length, MAX_TAIL)
        var i = 0
        while (i < limit) {
            val cp = Character.codePointAt(textAfterCursor, i)
            val size = Character.charCount(cp)
            if (isWordCodePoint(cp)) {
                i += size
                continue
            }
            if ((cp == '\''.code || cp == '’'.code) && i + size < limit &&
                Character.isLetter(Character.codePointAt(textAfterCursor, i + size))
            ) {
                i += size
                continue
            }
            break
        }
        return i.coerceAtMost(limit)
    }

    private fun isWordCodePoint(cp: Int): Boolean {
        if (Character.isLetterOrDigit(cp)) return true
        return when (Character.getType(cp)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            -> true
            else -> cp == 0x200C || cp == 0x200D // zero-width non-joiner / joiner
        }
    }

    /**
     * Extends [wordBeforeCursor] (a local range ending at the cursor, as the
     * word break iterator reports it) over the rest of the word after the
     * cursor. Invalid input, or a word that doesn't end at the cursor, is
     * returned unchanged.
     */
    fun aroundCursor(wordBeforeCursor: EditorRange, cursor: Int, textAfterCursor: CharSequence): EditorRange {
        if (wordBeforeCursor.isNotValid || wordBeforeCursor.end != cursor) return wordBeforeCursor
        val tail = lengthAfterCursor(textAfterCursor)
        return if (tail == 0) wordBeforeCursor else EditorRange(wordBeforeCursor.start, wordBeforeCursor.end + tail)
    }

    /** What replacing [word] in [text] with [replacement] produces, and where the cursor lands. */
    data class Replacement(val textBefore: String, val textAfter: String, val cursor: Int)

    /**
     * Replaces the local range [word] of [text] with [replacement]. The cursor
     * lands right after the replacement; everything outside [word] is kept.
     */
    fun replace(text: String, word: EditorRange, replacement: String): Replacement {
        val start = word.start.coerceIn(0, text.length)
        val end = word.end.coerceIn(start, text.length)
        val before = text.substring(0, start) + replacement
        return Replacement(textBefore = before, textAfter = text.substring(end), cursor = before.length)
    }

    /** True when the cursor sits at the end of [word] (typing extends the word rather than splitting it). */
    fun cursorAtEnd(word: EditorRange, cursor: Int): Boolean = word.isValid && word.end == cursor
}
