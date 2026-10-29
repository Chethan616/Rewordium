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

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * The English word-pair language model (`ime/dict/bigrams.bin`, built by
 * `tools/build_english_ngrams.py` from the Tatoeba and swipe.futo.org sentences).
 *
 * [score] is log P(next | prev) on the dictionary's 0..255 frequency scale
 * ([fPerNat] steps per nat, fitted when the model was built), so it compares
 * directly with unigram frequencies: a pair scoring above the next word's
 * unigram frequency means the word is that much more likely after `prev`,
 * below it that much less likely. Every pair seen at
 * least three times is in the model, so an absent pair is itself evidence —
 * see [contextScore].
 *
 * Stored in flat arrays (about 2 MB for ~340k pairs) rather than nested maps,
 * since it lives in the keyboard process for as long as the keyboard does.
 */
class BigramModel private constructor(
    private val words: Array<String>,
    private val wordIndex: Map<String, Int>,
    /** ln(corpus count) * 8 per word. */
    private val logCounts: ByteArray,
    /** ln(corpus tokens). */
    private val logTotal: Double,
    /** Frequency-scale steps per nat of probability. */
    val fPerNat: Double,
    /** The scale's score for probability 1 (freq = intercept + fPerNat * ln P); NaN for older models. */
    val intercept: Double,
    /** Pairs seen fewer (weighted) times than this are not stored. */
    private val minPairCount: Double,
    /** Per word index: position in [offsets] of its continuations, or -1. */
    private val slotOf: IntArray,
    private val offsets: IntArray,
    private val nexts: IntArray,
    private val scores: ByteArray,
) {
    val pairCount: Int get() = nexts.size

    /** log P(next | prev) on the AOSP scale, or -1 when the pair is not in the model. */
    fun score(prev: String, next: String): Int {
        val p = wordIndex[prev] ?: return -1
        val n = wordIndex[next] ?: return -1
        val slot = slotOf[p]
        if (slot < 0) return -1
        var lo = offsets[slot]
        var hi = offsets[slot + 1] - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = nexts[mid]
            when {
                v < n -> lo = mid + 1
                v > n -> hi = mid - 1
                else -> return scores[mid].toInt() and 0xFF
            }
        }
        return -1
    }

    /**
     * log P(next | prev) on the AOSP scale for any pair: the stored score, or
     * — for a pair the corpus saw fewer than three times — [unigramFreq]
     * lowered by how surprising that absence is. If both words are common
     * ("the" ... "got"), independence would predict many sightings, so never
     * seeing the pair means it is that many times less likely than chance.
     */
    fun contextScore(prev: String, next: String, unigramFreq: Int): Int {
        val stored = score(prev, next)
        if (stored >= 0) return stored
        val p = wordIndex[prev] ?: return unigramFreq
        val n = wordIndex[next] ?: return unigramFreq
        val expected = kotlin.math.exp(((logCounts[p].toInt() and 0xFF) + (logCounts[n].toInt() and 0xFF)) / 8.0 - logTotal)
        if (expected <= minPairCount) return unigramFreq
        val penalty = kotlin.math.ln(expected / minPairCount) * fPerNat
        return unigramFreq - penalty.coerceAtMost(MAX_ABSENCE_PENALTY).toInt()
    }

    /** The words that follow [prev], strongest first. */
    fun continuations(prev: String): List<Pair<String, Int>> {
        val p = wordIndex[prev] ?: return emptyList()
        val slot = slotOf[p]
        if (slot < 0) return emptyList()
        val out = ArrayList<Pair<String, Int>>(offsets[slot + 1] - offsets[slot])
        for (i in offsets[slot] until offsets[slot + 1]) {
            out.add(words[nexts[i]] to (scores[i].toInt() and 0xFF))
        }
        out.sortByDescending { it.second }
        return out
    }

    /** The [limit] strongest words after [prev], strongest first, without sorting every continuation. */
    fun top(prev: String, limit: Int): List<Pair<String, Int>> {
        val p = wordIndex[prev] ?: return emptyList()
        val slot = slotOf[p]
        if (slot < 0 || limit <= 0) return emptyList()
        val from = offsets[slot]
        val to = offsets[slot + 1]
        // Scores are bytes, so a histogram finds the cut-off score in one pass.
        val histogram = IntArray(256)
        for (i in from until to) histogram[scores[i].toInt() and 0xFF]++
        var cutoff = 255
        var kept = 0
        while (cutoff > 0) {
            kept += histogram[cutoff]
            if (kept >= limit) break
            cutoff--
        }
        val out = ArrayList<Pair<String, Int>>(minOf(to - from, limit * 2))
        for (i in from until to) {
            val s = scores[i].toInt() and 0xFF
            if (s >= cutoff) out.add(words[nexts[i]] to s)
        }
        out.sortByDescending { it.second }
        return if (out.size > limit) out.subList(0, limit) else out
    }

    /** The probability a score on this model's scale stands for (NaN for models without an intercept). */
    fun probability(score: Int): Double = kotlin.math.exp((score - intercept) / fPerNat)

    /** Each word's [limit] strongest continuations (all of them for words with fewer). */
    fun forEachTopPair(limit: Int, block: (prev: String, next: String, score: Int) -> Unit) {
        for (w in words.indices) {
            if (slotOf[w] < 0) continue
            for ((next, score) in top(words[w], limit)) block(words[w], next, score)
        }
    }

    fun forEachPair(block: (prev: String, next: String, score: Int) -> Unit) {
        for (w in words.indices) {
            val slot = slotOf[w]
            if (slot < 0) continue
            for (i in offsets[slot] until offsets[slot + 1]) {
                block(words[w], words[nexts[i]], scores[i].toInt() and 0xFF)
            }
        }
    }

    companion object {
        private const val MAGIC = "RBG2"
        /** Version 3 models stored every pair seen at least this many times. */
        private const val V3_MIN_PAIR_COUNT = 3.0
        /** An absent pair lowers a word by at most this many steps (~4 nats, ~50x). */
        private const val MAX_ABSENCE_PENALTY = 38.0
        /** The "previous word" at the start of a sentence: its continuations are the likely first words. */
        const val SENTENCE_START = "<s>"
        /** The fitted scale of the shipped dictionary, for when no model is loaded. */
        const val DEFAULT_F_PER_NAT = 9.7

        val EMPTY = BigramModel(emptyArray(), emptyMap(), ByteArray(0), 0.0, DEFAULT_F_PER_NAT, Double.NaN, V3_MIN_PAIR_COUNT, IntArray(0), IntArray(1), IntArray(0), ByteArray(0))

        /** Reads the binary model; throws on a malformed file. */
        fun read(input: InputStream): BigramModel {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also { data.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == MAGIC) { "not a bigram model" }
            val version = data.readUnsignedByte()
            require(version == 3 || version == 4) { "unsupported bigram model version $version" }
            fun varint(): Int {
                var result = 0
                var shift = 0
                while (true) {
                    val b = data.read()
                    if (b < 0) throw EOFException()
                    result = result or ((b and 0x7F) shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            val logTotal = varint() / 1000.0
            val fPerNat = varint() / 1000.0
            val intercept = if (version >= 4) varint() / 1000.0 else Double.NaN
            val minPairCount = if (version >= 4) varint() / 1000.0 else V3_MIN_PAIR_COUNT
            val wordCount = varint()
            val logCounts = ByteArray(wordCount)
            val words = Array(wordCount) { i ->
                val bytes = ByteArray(varint())
                data.readFully(bytes)
                logCounts[i] = data.readUnsignedByte().toByte()
                String(bytes, Charsets.UTF_8)
            }
            val wordIndex = HashMap<String, Int>(wordCount * 2)
            words.forEachIndexed { i, w -> wordIndex[w] = i }
            val prevCount = varint()
            val slotOf = IntArray(wordCount) { -1 }
            val offsets = IntArray(prevCount + 1)
            var nexts = IntArray(1 shl 16)
            var scores = ByteArray(1 shl 16)
            var size = 0
            for (slot in 0 until prevCount) {
                val prev = varint()
                slotOf[prev] = slot
                offsets[slot] = size
                val n = varint()
                if (size + n > nexts.size) {
                    val capacity = maxOf(nexts.size * 2, size + n)
                    nexts = nexts.copyOf(capacity)
                    scores = scores.copyOf(capacity)
                }
                var last = 0
                repeat(n) {
                    last += varint()
                    nexts[size] = last
                    scores[size] = data.readUnsignedByte().toByte()
                    size++
                }
            }
            offsets[prevCount] = size
            return BigramModel(words, wordIndex, logCounts, logTotal, fPerNat, intercept, minPairCount, slotOf, offsets, nexts.copyOf(size), scores.copyOf(size))
        }
    }
}
