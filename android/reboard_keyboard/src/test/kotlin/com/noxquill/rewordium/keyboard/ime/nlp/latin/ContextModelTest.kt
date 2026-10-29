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

package com.noxquill.rewordium.keyboard.ime.nlp.latin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextModelTest {
    // A phone QWERTY: 100 x 150 keys, rows offset by half a key.
    private val geometry = KeyGeometry(
        keyWidth = 100f,
        keyHeight = 150f,
        centers = buildMap {
            listOf("qwertyuiop" to 0f, "asdfghjkl" to 50f, "zxcvbnm" to 150f).forEachIndexed { row, (keys, offset) ->
                keys.forEachIndexed { i, c -> put(c.code, (offset + i * 100f + 50f) to (row * 150f + 75f)) }
            }
        },
    )

    /** Taps on the centre of each typed key, nudged by [nudge] (x, y) where given. */
    private fun taps(typed: String, nudge: Map<Int, Pair<Float, Float>> = emptyMap()): Pair<IntArray, IntArray> {
        val xs = IntArray(typed.length)
        val ys = IntArray(typed.length)
        typed.forEachIndexed { i, c ->
            val center = geometry.center(c.code)!!
            val (dx, dy) = nudge[i] ?: (0f to 0f)
            xs[i] = (center.first + dx).roundToInt()
            ys[i] = (center.second + dy).roundToInt()
        }
        return xs to ys
    }

    private fun cost(typed: String, intended: String, nudge: Map<Int, Pair<Float, Float>> = emptyMap()): Double {
        val (xs, ys) = taps(typed, nudge)
        return NoisyChannel.cost(typed, xs, ys, intended, geometry)
    }

    @Test
    fun `typing a word exactly is nearly free`() {
        assertTrue(cost("hello", "hello") < 0.01)
        assertTrue(cost("Hello", "hello") < 0.01, "case is the language model's business")
    }

    @Test
    fun `a tap on the edge of the neighbouring key is a cheap slip`() {
        // Meant "the", hit "r" right at its border with "t".
        val edge = cost("rhe", "the", nudge = mapOf(0 to (45f to 0f)))
        val centre = cost("rhe", "the")
        val farKey = cost("phe", "the")
        assertTrue(edge < centre, "an edge tap is more likely a slip than a centre tap")
        assertTrue(centre < farKey, "a neighbour is more likely than a key across the keyboard")
        assertTrue(edge < NoisyChannel.DROPPED)
    }

    @Test
    fun `swaps, drops, doubles and skipped apostrophes`() {
        assertEquals(NoisyChannel.SWAPPED, cost("teh", "the"), 1e-6)
        assertEquals(NoisyChannel.DROPPED, cost("helo", "hello"), 1e-6)
        assertEquals(NoisyChannel.DOUBLED, cost("helllo", "hello"), 1e-6)
        assertEquals(NoisyChannel.EXTRA, cost("hezllo", "hello"), 1e-6)
        assertEquals(NoisyChannel.SKIPPED_APOSTROPHE, cost("dont", "don't"), 1e-6)
    }

    /** Writes a model in the tools/build_english_ngrams.py format. */
    private fun model(
        counts: Map<String, Int>,
        total: Int,
        pairs: Map<String, Map<String, Int>>,
    ): BigramModel {
        val vocab = counts.keys.sorted()
        val index = vocab.withIndex().associate { it.value to it.index }
        val out = ByteArrayOutputStream()
        fun varint(value: Int) {
            var n = value
            while (true) {
                val b = n and 0x7F
                n = n ushr 7
                if (n != 0) out.write(b or 0x80) else { out.write(b); return }
            }
        }
        out.write("RBG2".toByteArray())
        out.write(3)
        varint((ln(total.toDouble()) * 1000).roundToInt())
        varint(9_700)
        varint(vocab.size)
        for (w in vocab) {
            val bytes = w.toByteArray()
            varint(bytes.size)
            out.write(bytes)
            out.write((ln(counts.getValue(w).toDouble()) * 8).roundToInt().coerceIn(0, 255))
        }
        varint(pairs.size)
        for (prev in pairs.keys.sortedBy { index[it] }) {
            varint(index.getValue(prev))
            val nexts = pairs.getValue(prev).entries.sortedBy { index[it.key] }
            varint(nexts.size)
            var last = 0
            for ((next, score) in nexts) {
                varint(index.getValue(next) - last)
                last = index.getValue(next)
                out.write(score)
            }
        }
        return BigramModel.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `stored pairs are read back exactly`() {
        val m = model(
            counts = mapOf("the" to 400_000, "rain" to 3_000, "rail" to 400, "a" to 300_000, "chance" to 2_000),
            total = 15_000_000,
            pairs = mapOf("the" to mapOf("rain" to 160, "rail" to 120), "a" to mapOf("chance" to 170)),
        )
        assertEquals(3, m.pairCount)
        assertEquals(160, m.score("the", "rain"))
        assertEquals(170, m.score("a", "chance"))
        assertEquals(-1, m.score("a", "rain"))
        assertEquals(listOf("rain" to 160, "rail" to 120), m.continuations("the"))
        assertEquals(emptyList(), m.continuations("rain"))
    }

    @Test
    fun `an absent pair of common words counts against the pair`() {
        val m = model(
            counts = mapOf("the" to 400_000, "got" to 50_000, "zebra" to 10, "rain" to 3_000),
            total = 15_000_000,
            pairs = mapOf("the" to mapOf("rain" to 160)),
        )
        // "the got": ~1,300 sightings expected by chance, fewer than 3 seen -> a big penalty (capped).
        assertTrue(m.contextScore("the", "got", unigramFreq = 155) <= 155 - 25)
        // "the zebra": rare enough that not seeing it says nothing.
        assertEquals(90, m.contextScore("the", "zebra", unigramFreq = 90))
        // Unknown words fall back to their unigram frequency.
        assertEquals(70, m.contextScore("the", "chethan", unigramFreq = 70))
        assertEquals(160, m.contextScore("the", "rain", unigramFreq = 120))
    }

    @Test
    fun `the shipped model loads`() {
        val asset = java.io.File("src/main/assets/ime/dict/bigrams.bin")
        if (!asset.exists()) return
        val m = asset.inputStream().use { BigramModel.read(it) }
        assertTrue(m.pairCount > 300_000)
        assertTrue(m.score("be", "there") > m.contextScore("be", "their", 179), "'be there' beats 'be their'")
        assertTrue(m.score("a", "chance") > m.contextScore("a", "cancer", 133), "'a chance' beats 'a cancer'")
        assertTrue(m.continuations("I").take(10).any { it.first == "think" }, "I think is a top continuation")
        assertTrue(m.continuations("I").take(10).none { it.first == "the" }, "I the is not")
        assertTrue(m.fPerNat in 8.0..16.0)
        assertTrue(m.probability(m.score("I", "think")) in 0.01..0.5, "probabilities are on the fitted scale")
        assertEquals(m.continuations("the").take(20), m.top("the", 20), "top() picks the same words as a full sort")
        val firstWords = m.top(BigramModel.SENTENCE_START, 5).map { it.first }
        assertTrue("I" in firstWords, "sentences often start with I: $firstWords")
    }

    /** Writes an n-gram model in the tools/build_english_ngrams.py format. */
    private fun ngrams(order: Int, contexts: Map<List<String>, List<Pair<String, Int>>>): NgramModel {
        val vocab = (contexts.keys.flatten() + contexts.values.flatten().map { it.first }).toSortedSet().toList()
        val index = vocab.withIndex().associate { it.value to it.index }
        val out = ByteArrayOutputStream()
        fun varint(value: Int) {
            var n = value
            while (true) {
                val b = n and 0x7F
                n = n ushr 7
                if (n != 0) out.write(b or 0x80) else { out.write(b); return }
            }
        }
        out.write("RNG1".toByteArray())
        out.write(1)
        out.write(order)
        varint(vocab.size)
        for (w in vocab) {
            val bytes = w.toByteArray()
            varint(bytes.size)
            out.write(bytes)
        }
        val sorted = contexts.keys.sortedWith { a, b ->
            a.zip(b).map { (x, y) -> index.getValue(x).compareTo(index.getValue(y)) }.firstOrNull { it != 0 } ?: 0
        }
        varint(sorted.size)
        for (context in sorted) {
            context.forEach { varint(index.getValue(it)) }
            val nexts = contexts.getValue(context)
            varint(nexts.size)
            for ((next, score) in nexts) {
                varint(index.getValue(next))
                out.write(score)
            }
        }
        return NgramModel.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `n-gram contexts are read back exactly`() {
        val m = ngrams(
            order = 4,
            contexts = mapOf(
                listOf("how", "are", "you") to listOf("doing" to 190, "today" to 170),
                listOf("<s>", "how", "are") to listOf("you" to 240),
                listOf("are", "you", "sure") to listOf("about" to 180),
            ),
        )
        assertEquals(4, m.order)
        assertEquals(3, m.contextCount)
        assertEquals(listOf("doing" to 190, "today" to 170), m.continuations(listOf("how", "are", "you")))
        assertEquals(240, m.score(listOf("<s>", "how", "are"), "you"))
        assertEquals(-1, m.score(listOf("how", "are", "you"), "about"))
        assertTrue(m.knows(listOf("are", "you", "sure")))
        assertTrue(!m.knows(listOf("are", "you")), "a context must have order - 1 words")
        assertEquals(emptyList(), m.continuations(listOf("you", "are", "how")))
    }

    @Test
    fun `the shipped n-gram models load and know common phrases`() {
        val dir = java.io.File("src/main/assets/ime/dict")
        val tri = java.io.File(dir, "trigrams.bin")
        val four = java.io.File(dir, "fourgrams.bin")
        if (!tri.exists() || !four.exists()) return
        val trigrams = tri.inputStream().use { NgramModel.read(it) }
        val fourgrams = four.inputStream().use { NgramModel.read(it) }
        assertEquals(3, trigrams.order)
        assertEquals(4, fourgrams.order)
        assertTrue(trigrams.contextCount > 100_000 && fourgrams.contextCount > 100_000)
        fun top(m: NgramModel, vararg context: String) = m.continuations(context.toList()).take(3).map { it.first }
        assertTrue("to" in top(trigrams, "I", "want"), "I want -> to")
        assertTrue("for" in top(trigrams, "thank", "you"), "thank you -> for")
        assertTrue("know" in top(trigrams, "let", "me"), "let me -> know")
        assertTrue("you" in top(fourgrams, "<s>", "how", "are"), "How are -> you")
        // Stock characters of the source corpus never become predictions.
        assertTrue(trigrams.continuations(listOf("<s>", "I")).none { it.first.equals("Sami", ignoreCase = true) })
        assertEquals(trigrams.continuations(listOf("I", "want")).first(), trigrams.best(listOf("I", "want")))
        val five = java.io.File(dir, "fivegrams.bin")
        if (five.exists()) {
            val fivegrams = five.inputStream().use { NgramModel.read(it) }
            assertEquals(5, fivegrams.order)
            assertTrue("doing" in top(fivegrams, "<s>", "how", "are", "you") || "doing" in top(fourgrams, "how", "are", "you"), "How are you -> doing")
        }
    }
}
