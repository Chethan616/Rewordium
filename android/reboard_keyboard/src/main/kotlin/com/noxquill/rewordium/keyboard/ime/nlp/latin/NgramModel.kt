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
 * A longer-context English language model (`ime/dict/trigrams.bin` with
 * two-word contexts, `fourgrams.bin` with three, `fivegrams.bin` with four; built by
 * `tools/build_english_ngrams.py`): for a context like "I want", the words
 * most likely to come next ("to", "you", "a", ...), each with
 * log P(next | context) on the same 0..255 scale as [BigramModel].
 *
 * Only well-attested contexts and their strongest continuations are stored, so
 * callers back off to shorter contexts when a context or word is missing. The
 * context may start with [BigramModel.SENTENCE_START].
 */
class NgramModel private constructor(
    val order: Int,
    private val wordIndex: Map<String, Int>,
    private val words: Array<String>,
    /** Context word indices packed [bits] bits each, ascending. */
    private val contextKeys: LongArray,
    private val offsets: IntArray,
    private val nexts: IntArray,
    private val scores: ByteArray,
) {
    val contextCount: Int get() = contextKeys.size
    private val bits = bitsFor(order)

    private fun slot(context: List<String>): Int {
        if (context.size != order - 1 || contextKeys.isEmpty()) return -1
        var key = 0L
        for (word in context) {
            val i = wordIndex[word] ?: return -1
            key = (key shl bits) or i.toLong()
        }
        val s = contextKeys.binarySearch(key)
        return if (s >= 0) s else -1
    }

    /** True when [context] (exactly order - 1 words) is a stored context. */
    fun knows(context: List<String>): Boolean = slot(context) >= 0

    /** The likeliest words after [context], strongest first; empty when the context is unknown. */
    fun continuations(context: List<String>): List<Pair<String, Int>> {
        val s = slot(context)
        if (s < 0) return emptyList()
        return (offsets[s] until offsets[s + 1]).map { words[nexts[it]] to (scores[it].toInt() and 0xFF) }
    }

    /** The strongest continuation of [context] with its score, or null when the context is unknown. */
    fun best(context: List<String>): Pair<String, Int>? {
        val s = slot(context)
        if (s < 0 || offsets[s] == offsets[s + 1]) return null
        val i = offsets[s]
        return words[nexts[i]] to (scores[i].toInt() and 0xFF)
    }

    /** log P(next | context) on the 0..255 scale, or -1 when not stored. */
    fun score(context: List<String>, next: String): Int {
        val s = slot(context)
        if (s < 0) return -1
        val n = wordIndex[next] ?: return -1
        for (i in offsets[s] until offsets[s + 1]) {
            if (nexts[i] == n) return scores[i].toInt() and 0xFF
        }
        return -1
    }

    companion object {
        private const val MAGIC = "RNG1"

        /** Bits per context word: (order - 1) of them must fit a non-negative Long. */
        private fun bitsFor(order: Int) = if (order <= 4) 21 else 15

        fun empty(order: Int) = NgramModel(order, emptyMap(), emptyArray(), LongArray(0), IntArray(1), IntArray(0), ByteArray(0))

        /** Reads the binary model; throws on a malformed file. */
        fun read(input: InputStream): NgramModel {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also { data.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == MAGIC) { "not an n-gram model" }
            require(data.readUnsignedByte() == 1) { "unsupported n-gram model version" }
            val order = data.readUnsignedByte()
            require(order in 3..5) { "unsupported n-gram order $order" }
            val bits = bitsFor(order)
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
            val wordCount = varint()
            require(wordCount < (1 shl bits)) { "too many words" }
            val words = Array(wordCount) {
                val bytes = ByteArray(varint())
                data.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
            val wordIndex = HashMap<String, Int>(wordCount * 2)
            words.forEachIndexed { i, w -> wordIndex[w] = i }
            val contextCount = varint()
            val keys = LongArray(contextCount)
            val offsets = IntArray(contextCount + 1)
            var nexts = IntArray(1 shl 18)
            var scores = ByteArray(1 shl 18)
            var size = 0
            for (c in 0 until contextCount) {
                var key = 0L
                repeat(order - 1) { key = (key shl bits) or varint().toLong() }
                keys[c] = key
                offsets[c] = size
                val n = varint()
                if (size + n > nexts.size) {
                    val capacity = maxOf(nexts.size * 2, size + n)
                    nexts = nexts.copyOf(capacity)
                    scores = scores.copyOf(capacity)
                }
                repeat(n) {
                    nexts[size] = varint()
                    scores[size] = data.readUnsignedByte().toByte()
                    size++
                }
            }
            offsets[contextCount] = size
            return NgramModel(order, wordIndex, words, keys, offsets, nexts.copyOf(size), scores.copyOf(size))
        }
    }
}
