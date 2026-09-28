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
import kotlin.math.hypot

/**
 * Pure-Kotlin extractor that turns our Snygg `TextKey` layout into the int /
 * float arrays AOSP's [ProximityInfo] needs as constructor input, following
 * the conventions of AOSP's `com.android.inputmethod.keyboard.ProximityInfo`:
 *
 *   * key x / y are the key's **top-left** corner and width / height its
 *     visible size — AOSP derives key centres as `x + width / 2` and tests
 *     "touch is on key" against these rectangles;
 *   * sweet spots (the statistically most likely touch point and spread per
 *     key) are the hit-box centre shifted by AOSP's default Holo touch
 *     position correction for the three letter rows. Passing all-zero sweet
 *     spots (as an earlier version did) makes AOSP place every key centre
 *     at (0, 0) because non-null sweet-spot arrays enable the correction
 *     path unconditionally;
 *   * every printable key participates (letters, apostrophe, comma, period
 *     and the space bar) so the decoder can recover space-bar misses such as
 *     "thisis" → "this is". Digits are excluded; they never occur in words.
 *
 * The descriptor is immutable; rebuild on layout change (subtype switch,
 * keyboard resize, one-handed mode, ...).
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

    /**
     * Visible-bounds centre of the key producing [codePoint] (case-folded),
     * or null when the layout has no such key. Used to synthesise tap
     * positions for letters we have no touch data for (resumed words,
     * pasted text, glide-then-tap composites), exactly like AOSP's
     * `Keyboard.getCoordinates()`.
     */
    fun centerOf(codePoint: Int): Pair<Int, Int>? {
        val lower = Character.toLowerCase(codePoint)
        for (i in keyCharCodes.indices) {
            if (keyCharCodes[i] == lower || Character.toLowerCase(keyCharCodes[i]) == lower) {
                return (keyXCoordinates[i] + keyWidths[i] / 2) to (keyYCoordinates[i] + keyHeights[i] / 2)
            }
        }
        return null
    }

    companion object {
        /** Mirrors `defines.h` — must equal AOSP's `MAX_PROXIMITY_CHARS_SIZE`. */
        const val MAX_PROXIMITY_CHARS_SIZE = 16

        /** AOSP-standard proximity grid resolution. */
        const val GRID_WIDTH = 32
        const val GRID_HEIGHT = 16

        /** Sentinel padding for unused proximity-cell slots (AOSP convention). */
        const val NOT_A_CODE = -1

        /** AOSP `ProximityInfo.DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS`. */
        private const val DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS = 0.15f

        /** AOSP `ProximityInfo.SEARCH_DISTANCE`, in most-common-key widths. */
        private const val SEARCH_DISTANCE = 1.2f

        /**
         * AOSP `touch_position_correction_data_holo`: per letter row, the mean
         * touch offset (x, y as a fraction of the key's hit box) and the
         * sweet-spot radius (fraction of the hit-box diagonal). Users hit
         * lower rows increasingly below the key centre.
         */
        private val TOUCH_POSITION_CORRECTION = arrayOf(
            floatArrayOf(0.0038756f, -0.0005677f, 0.1577026f),
            floatArrayOf(-0.0236678f, 0.0381731f, 0.1529972f),
            floatArrayOf(-0.0086827f, 0.0880847f, 0.1522819f),
        )

        private const val CODE_SPACE = 32

        /** Geometry of one key: visible rectangle plus touch hit box. */
        private class KeySpec(
            val code: Int,
            val left: Float,
            val top: Float,
            val width: Float,
            val height: Float,
            val hitLeft: Float,
            val hitTop: Float,
            val hitWidth: Float,
            val hitHeight: Float,
        )

        private fun participates(code: Int): Boolean {
            return code == CODE_SPACE || code == '\''.code || code == ','.code || code == '.'.code ||
                (code > CODE_SPACE && Character.isLetter(code))
        }

        /**
         * Build a descriptor from the active keyboard's full key list and its
         * rendered pixel size. Returns null when the layout has no letter keys
         * (numeric / phone keyboards) or hasn't been laid out yet.
         */
        fun fromTextKeys(
            allKeys: List<TextKey>,
            keyboardWidth: Int,
            keyboardHeight: Int,
        ): KeyboardLayoutDescriptor? {
            if (keyboardWidth <= 0 || keyboardHeight <= 0) return null
            val specs = allKeys.mapNotNull { key ->
                val code = (key.data as? KeyData)?.code ?: 0
                val v = key.visibleBounds
                val t = key.touchBounds
                if (participates(code) && v.width > 0f && v.height > 0f) {
                    KeySpec(
                        code = Character.toLowerCase(code),
                        left = v.left, top = v.top, width = v.width, height = v.height,
                        hitLeft = t.left, hitTop = t.top, hitWidth = t.width, hitHeight = t.height,
                    )
                } else {
                    null
                }
            }
            return fromSpecs(specs, keyboardWidth, keyboardHeight)
        }

        /**
         * A generic phone-sized QWERTY layout, used by the decoder before the
         * real keyboard has been laid out (system spell checker, background
         * vocabulary maintenance). Tap positions are then synthesised from
         * key centres, so only relative key geometry matters.
         */
        fun standardQwerty(): KeyboardLayoutDescriptor? {
            val width = 1080
            val rowHeight = 150
            val keyWidth = width / 10f
            val gapX = 6f
            val gapY = 12f
            val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            val offsets = listOf(0f, keyWidth / 2f, keyWidth * 1.5f)
            val specs = ArrayList<KeySpec>()
            for ((r, row) in rows.withIndex()) {
                for ((i, ch) in row.withIndex()) {
                    val hitLeft = offsets[r] + i * keyWidth
                    val hitTop = (r * rowHeight).toFloat()
                    specs.add(
                        KeySpec(
                            code = ch.code,
                            left = hitLeft + gapX, top = hitTop + gapY,
                            width = keyWidth - 2 * gapX, height = rowHeight - 2 * gapY,
                            hitLeft = hitLeft, hitTop = hitTop, hitWidth = keyWidth, hitHeight = rowHeight.toFloat(),
                        ),
                    )
                }
            }
            val bottomTop = 3f * rowHeight
            fun bottomKey(code: Int, startKeys: Float, widthKeys: Float) = KeySpec(
                code = code,
                left = startKeys * keyWidth + gapX, top = bottomTop + gapY,
                width = widthKeys * keyWidth - 2 * gapX, height = rowHeight - 2 * gapY,
                hitLeft = startKeys * keyWidth, hitTop = bottomTop,
                hitWidth = widthKeys * keyWidth, hitHeight = rowHeight.toFloat(),
            )
            specs.add(bottomKey(','.code, 1.5f, 1f))
            specs.add(bottomKey(CODE_SPACE, 3.5f, 4f))
            specs.add(bottomKey('.'.code, 7.5f, 1f))
            return fromSpecs(specs, width, 4 * rowHeight)
        }

        private fun fromSpecs(
            allSpecs: List<KeySpec>,
            keyboardWidth: Int,
            keyboardHeight: Int,
        ): KeyboardLayoutDescriptor? {
            val keys = allSpecs.distinctBy { it.code }
            val letterKeys = keys.filter { Character.isLetter(it.code) }
            if (letterKeys.isEmpty()) return null

            val count = keys.size
            val xs = IntArray(count)
            val ys = IntArray(count)
            val ws = IntArray(count)
            val hs = IntArray(count)
            val codes = IntArray(count)
            for ((i, key) in keys.withIndex()) {
                xs[i] = key.left.toInt()
                ys[i] = key.top.toInt()
                ws[i] = key.width.toInt().coerceAtLeast(1)
                hs[i] = key.height.toInt().coerceAtLeast(1)
                codes[i] = key.code
            }

            // Most-common dimensions = median across letter keys, robust to the
            // oversized space bar and edge keys.
            val letterWidths = letterKeys.map { it.width.toInt() }.sorted()
            val letterHeights = letterKeys.map { it.height.toInt() }.sorted()
            val mostCommonW = letterWidths[letterWidths.size / 2].coerceAtLeast(1)
            val mostCommonH = letterHeights[letterHeights.size / 2].coerceAtLeast(1)

            // Letter rows, top to bottom, identified by their hit-box top edge.
            val letterRowTops = letterKeys
                .map { it.hitTop.toInt() }
                .distinct()
                .sorted()

            val defaultRadius = DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS *
                hypot(mostCommonW.toFloat(), mostCommonH.toFloat())
            val sweetSpotXs = FloatArray(count)
            val sweetSpotYs = FloatArray(count)
            val sweetSpotRadii = FloatArray(count)
            for ((i, key) in keys.withIndex()) {
                val hitW = key.hitWidth.coerceAtLeast(1f)
                val hitH = key.hitHeight.coerceAtLeast(1f)
                sweetSpotXs[i] = key.hitLeft + hitW / 2f
                sweetSpotYs[i] = key.hitTop + hitH / 2f
                sweetSpotRadii[i] = defaultRadius
                val row = letterRowTops.indexOf(key.hitTop.toInt())
                if (row in TOUCH_POSITION_CORRECTION.indices) {
                    val correction = TOUCH_POSITION_CORRECTION[row]
                    sweetSpotXs[i] += correction[0] * hitW
                    sweetSpotYs[i] += correction[1] * hitH
                    sweetSpotRadii[i] = correction[2] * hypot(hitW, hitH)
                }
            }

            val proximity = buildProximityGrid(
                keyboardWidth = keyboardWidth,
                keyboardHeight = keyboardHeight,
                xs = xs,
                ys = ys,
                ws = ws,
                hs = hs,
                codes = codes,
                searchDistance = SEARCH_DISTANCE * mostCommonW,
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
         * the keyboard area, list the keys whose rectangle lies within
         * [searchDistance] of the cell centre (nearest first, at most
         * [MAX_PROXIMITY_CHARS_SIZE]) — AOSP's `computeNearestNeighbors`.
         * Returns a flat array indexed as
         * `out[((gy * GRID_WIDTH + gx) * MAX_PROXIMITY_CHARS_SIZE) + slot]`,
         * with unused slots set to [NOT_A_CODE].
         */
        private fun buildProximityGrid(
            keyboardWidth: Int,
            keyboardHeight: Int,
            xs: IntArray,
            ys: IntArray,
            ws: IntArray,
            hs: IntArray,
            codes: IntArray,
            searchDistance: Float,
        ): IntArray {
            // AOSP sizes cells with integer ceiling division; match it so the
            // native grid lookup and ours address the same cells.
            val cellW = ((keyboardWidth + GRID_WIDTH - 1) / GRID_WIDTH).toFloat()
            val cellH = ((keyboardHeight + GRID_HEIGHT - 1) / GRID_HEIGHT).toFloat()
            val out = IntArray(GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE)
            out.fill(NOT_A_CODE)
            val thresholdSq = searchDistance * searchDistance
            val keyCount = codes.size
            val distSq = FloatArray(keyCount)
            val order = arrayOfNulls<Int>(keyCount)
            for (gy in 0 until GRID_HEIGHT) {
                val py = (gy + 0.5f) * cellH
                for (gx in 0 until GRID_WIDTH) {
                    val px = (gx + 0.5f) * cellW
                    var candidates = 0
                    for (k in 0 until keyCount) {
                        val dx = maxOf(0f, xs[k] - px, px - (xs[k] + ws[k]))
                        val dy = maxOf(0f, ys[k] - py, py - (ys[k] + hs[k]))
                        val d = dx * dx + dy * dy
                        if (d < thresholdSq) {
                            distSq[k] = d
                            order[candidates++] = k
                        }
                    }
                    val sorted = order.copyOfRange(0, candidates).sortedBy { distSq[it!!] }
                    val base = (gy * GRID_WIDTH + gx) * MAX_PROXIMITY_CHARS_SIZE
                    for (i in 0 until minOf(MAX_PROXIMITY_CHARS_SIZE, sorted.size)) {
                        out[base + i] = codes[sorted[i]!!]
                    }
                }
            }
            return out
        }
    }
}
