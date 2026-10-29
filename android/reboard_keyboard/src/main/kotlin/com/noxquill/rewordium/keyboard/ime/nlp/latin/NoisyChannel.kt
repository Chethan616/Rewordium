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

/** Letter-key centres and the common key size of the keyboard on screen. */
class KeyGeometry(
    val keyWidth: Float,
    val keyHeight: Float,
    private val centers: Map<Int, Pair<Float, Float>>,
) {
    /** Centre of the key typing [codePoint] (case-folded), or null when the layout has no such key. */
    fun center(codePoint: Int): Pair<Float, Float>? = centers[Character.toLowerCase(codePoint)]
}

/**
 * The noisy channel: how likely it is that someone who meant [intended] ended
 * up with the letters they typed, given where each tap landed. Returned as a
 * cost in nats (−log probability, up to a constant); lower is more likely.
 *
 * It is a weighted Damerau–Levenshtein alignment:
 *  * typing a letter costs how far the tap was from the intended key's
 *    centre, under a Gaussian finger spread — hitting the edge of the
 *    neighbouring key is cheap, a key two rows away is not;
 *  * a dropped letter, an extra letter (cheaper when doubled) and two
 *    swapped letters each cost a fixed amount, matching how rarely people
 *    make them per letter;
 *  * a skipped apostrophe ("dont" for "don't") is nearly free.
 *
 * Combined with a language model it forms the usual score for a candidate:
 * log P(typed | word) + log P(word | context).
 */
object NoisyChannel {
    /** Finger landing spread, in key sizes. */
    const val SPREAD = 0.36
    const val DROPPED = 5.0
    const val EXTRA = 5.0
    const val DOUBLED = 4.0
    const val SWAPPED = 4.5
    const val SKIPPED_APOSTROPHE = 0.7
    /** A letter with no key on the layout that differs from what was typed. */
    const val NO_KEY_MISMATCH = 9.0

    /**
     * [xs]/[ys] are the tap positions for each code point of [typed], in the
     * same coordinates as [geometry] (key centres when no tap was recorded).
     */
    fun cost(typed: String, xs: IntArray, ys: IntArray, intended: String, geometry: KeyGeometry): Double {
        val t = typed.codePoints().toArray()
        val w = intended.codePoints().toArray()
        val n = t.size
        val m = w.size
        val twoSigmaSq = 2.0 * SPREAD * SPREAD
        fun tap(i: Int): Pair<Float, Float>? {
            if (i < xs.size && i < ys.size && xs[i] >= 0 && ys[i] >= 0) return xs[i].toFloat() to ys[i].toFloat()
            return geometry.center(t[i])
        }
        fun substitute(i: Int, j: Int): Double {
            val same = Character.toLowerCase(t[i]) == Character.toLowerCase(w[j])
            val target = geometry.center(w[j])
            val at = tap(i)
            if (target == null || at == null) return if (same) 0.0 else NO_KEY_MISMATCH
            val dx = (at.first - target.first) / geometry.keyWidth
            val dy = (at.second - target.second) / geometry.keyHeight
            return (dx * dx + dy * dy) / twoSigmaSq
        }
        val d = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 1..n) d[i][0] = d[i - 1][0] + insertion(t, i - 1)
        for (j in 1..m) d[0][j] = d[0][j - 1] + deletion(w, j - 1)
        for (i in 1..n) {
            for (j in 1..m) {
                var best = d[i - 1][j - 1] + substitute(i - 1, j - 1)
                best = minOf(best, d[i][j - 1] + deletion(w, j - 1))
                best = minOf(best, d[i - 1][j] + insertion(t, i - 1))
                if (i >= 2 && j >= 2 &&
                    Character.toLowerCase(t[i - 1]) == Character.toLowerCase(w[j - 2]) &&
                    Character.toLowerCase(t[i - 2]) == Character.toLowerCase(w[j - 1])
                ) {
                    best = minOf(best, d[i - 2][j - 2] + SWAPPED)
                }
                d[i][j] = best
            }
        }
        return d[n][m]
    }

    private fun deletion(word: IntArray, j: Int): Double =
        if (word[j] == '\''.code || word[j] == '’'.code) SKIPPED_APOSTROPHE else DROPPED

    private fun insertion(typed: IntArray, i: Int): Double =
        if (i > 0 && Character.toLowerCase(typed[i]) == Character.toLowerCase(typed[i - 1])) DOUBLED else EXTRA
}
