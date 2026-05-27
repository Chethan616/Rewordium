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

package com.noxquill.rewordium.keyboard.ime.nlp.engine

import com.noxquill.rewordium.keyboard.ime.keyboard.KeyData
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKey

/**
 * Pure-Kotlin extractor that turns our Snygg `TextKey` layout into the int /
 * float arrays AOSP's [ProximityInfo] needs as constructor input. Built so the
 * native side can be invoked with one method call passing pre-shaped arrays
 * — no per-key JNI roundtrips. The descriptor is immutable; rebuild on layout
 * change (subtype switch, shift / numeric mode flip).
 *
 * Phase 5c deliverable. Phase 5d will marshal these arrays across JNI into a
 * native ProximityInfo handle.
 *
 * Sizes / shapes match the AOSP constructor (see `proximity_info.h:31-37`):
 *   * keyCount-length int arrays for x, y, width, height, charCode
 *   * keyCount-length float arrays for sweet-spot triplet (all zeros — we
 *     don't ship per-device touch-position calibration)
 *   * Flat [GRID_WIDTH × GRID_HEIGHT × MAX_PROXIMITY_CHARS_SIZE] int array
 *     where each cell holds the 16 nearest letter-key code points (padded
 *     with [NOT_A_CODE])
 *
 * Only character keys with positive Unicode letter code points feed into the
 * descriptor — shift, delete, enter, language-switch etc. don't participate
 * in spatial correction.
 */
class KeyboardLayoutDescriptor private constructor(
    val keyboardWidth: Int,
    val keyboardHeight: Int,
    val mostCommonKeyWidth: Int,
    val mostCommonKeyHeight: Int,
    val keyXCoordinates: IntArray,
    val keyYCoordinates: IntArray,
    val keyWidths: IntArray,
    val keyHeights: IntArray,
    val keyCharCodes: IntArray,
    val sweetSpotCenterXs: FloatArray,
    val sweetSpotCenterYs: FloatArray,
    val sweetSpotRadii: FloatArray,
    val proximityChars: IntArray,
) {
    val keyCount: Int get() = keyCharCodes.size

    companion object {
        /** Mirrors `defines.h:38` — must equal AOSP's `MAX_PROXIMITY_CHARS_SIZE`. */
        const val MAX_PROXIMITY_CHARS_SIZE = 16

        /** AOSP-standard proximity grid resolution. */
        const val GRID_WIDTH = 32
        const val GRID_HEIGHT = 16

        /** Sentinel padding for unused proximity-cell slots (AOSP convention). */
        const val NOT_A_CODE = -1

        /**
         * Build a descriptor from the active keyboard's full key list and its
         * rendered pixel size. Non-letter keys (shift, enter, space, …) are
         * filtered out — proximity correction only operates over the typeable
         * letter set.
         *
         * Returns null when the layout has no letter keys (numeric / phone
         * keyboards). Callers should skip native glide on that subtype.
         */
        fun fromTextKeys(
            allKeys: List<TextKey>,
            keyboardWidth: Int,
            keyboardHeight: Int,
        ): KeyboardLayoutDescriptor? {
            if (keyboardWidth <= 0 || keyboardHeight <= 0) return null
            val letters = allKeys.mapNotNull { key ->
                val code = (key.data as? KeyData)?.code ?: 0
                if (code > 0 && Character.isLetter(code) && !key.visibleBounds.isEmpty()) {
                    key to code
                } else null
            }
            if (letters.isEmpty()) return null

            val count = letters.size
            val xs = IntArray(count)
            val ys = IntArray(count)
            val ws = IntArray(count)
            val hs = IntArray(count)
            val codes = IntArray(count)
            for ((i, pair) in letters.withIndex()) {
                val (key, code) = pair
                val r = key.visibleBounds
                xs[i] = r.center.x.toInt()
                ys[i] = r.center.y.toInt()
                ws[i] = (r.right - r.left).toInt()
                hs[i] = (r.bottom - r.top).toInt()
                codes[i] = code
            }

            // Most-common dimensions = median across letter keys. Median is
            // robust to a handful of outsized keys (e.g. an enlarged 'A'
            // edge key) without needing a full histogram.
            val mostCommonW = ws.copyOf().also { it.sort() }[count / 2]
            val mostCommonH = hs.copyOf().also { it.sort() }[count / 2]

            // Sweet-spot data: zeros. AOSP treats radius == 0 as
            // "no calibration data for this key" and falls back to the
            // mostCommonKeyWidth heuristic. We don't ship per-device
            // touch-position calibration today, so zero is correct.
            val zeros = FloatArray(count)
            val sweetSpotXs = zeros
            val sweetSpotYs = FloatArray(count)
            val sweetSpotRadii = FloatArray(count)

            val proximity = buildProximityGrid(
                keyboardWidth = keyboardWidth,
                keyboardHeight = keyboardHeight,
                xs = xs,
                ys = ys,
                codes = codes,
            )

            return KeyboardLayoutDescriptor(
                keyboardWidth = keyboardWidth,
                keyboardHeight = keyboardHeight,
                mostCommonKeyWidth = mostCommonW,
                mostCommonKeyHeight = mostCommonH,
                keyXCoordinates = xs,
                keyYCoordinates = ys,
                keyWidths = ws,
                keyHeights = hs,
                keyCharCodes = codes,
                sweetSpotCenterXs = sweetSpotXs,
                sweetSpotCenterYs = sweetSpotYs,
                sweetSpotRadii = sweetSpotRadii,
                proximityChars = proximity,
            )
        }

        /**
         * For each (gx, gy) cell in a [GRID_WIDTH]×[GRID_HEIGHT] grid spanning
         * the keyboard area, find the [MAX_PROXIMITY_CHARS_SIZE] nearest
         * letter-key code points (by Euclidean distance from cell center to
         * key center). Returns a flat int array indexed as
         * `out[((gy * GRID_WIDTH + gx) * MAX_PROXIMITY_CHARS_SIZE) + slot]`,
         * with unused slots set to [NOT_A_CODE].
         *
         * O(grid × keyCount) = ~32 × 16 × ~30 letters ≈ 15k distance ops at
         * descriptor build time — well below 10 ms on modern hardware,
         * acceptable on layout-switch events. Skipped on the hot stroke path.
         */
        private fun buildProximityGrid(
            keyboardWidth: Int,
            keyboardHeight: Int,
            xs: IntArray,
            ys: IntArray,
            codes: IntArray,
        ): IntArray {
            val cellW = keyboardWidth.toFloat() / GRID_WIDTH
            val cellH = keyboardHeight.toFloat() / GRID_HEIGHT
            val out = IntArray(GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE)
            // Pre-fill with NOT_A_CODE so any unused slot is well-defined.
            out.fill(NOT_A_CODE)

            val keyCount = xs.size
            // Re-used per cell: parallel arrays of (distSquared, keyIndex)
            // we partial-sort to extract the top [MAX_PROXIMITY_CHARS_SIZE].
            val distSq = FloatArray(keyCount)
            val keyIdx = IntArray(keyCount)

            for (gy in 0 until GRID_HEIGHT) {
                val py = (gy + 0.5f) * cellH
                for (gx in 0 until GRID_WIDTH) {
                    val px = (gx + 0.5f) * cellW
                    for (k in 0 until keyCount) {
                        val dx = (xs[k] - px)
                        val dy = (ys[k] - py)
                        distSq[k] = dx * dx + dy * dy
                        keyIdx[k] = k
                    }
                    // Partial selection of top-K — simple insertion-sort
                    // bubble for first K positions. K=16 and keyCount~30
                    // makes a full sort ~free; not worth a heap.
                    val take = minOf(MAX_PROXIMITY_CHARS_SIZE, keyCount)
                    for (i in 0 until take) {
                        var minAt = i
                        var minVal = distSq[i]
                        for (j in i + 1 until keyCount) {
                            if (distSq[j] < minVal) {
                                minAt = j
                                minVal = distSq[j]
                            }
                        }
                        if (minAt != i) {
                            val td = distSq[i]; distSq[i] = distSq[minAt]; distSq[minAt] = td
                            val tk = keyIdx[i]; keyIdx[i] = keyIdx[minAt]; keyIdx[minAt] = tk
                        }
                    }
                    val baseOut = (gy * GRID_WIDTH + gx) * MAX_PROXIMITY_CHARS_SIZE
                    for (i in 0 until take) {
                        out[baseOut + i] = codes[keyIdx[i]]
                    }
                }
            }
            return out
        }
    }
}
