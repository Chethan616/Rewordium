/*
 * Copyright (C) 2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.text.gestures

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Utility for pre-processing raw gesture paths before they are fed into the classifier.
 *
 * Two operations are applied in sequence:
 *  1. **Douglas-Peucker simplification** — removes collinear/noisy intermediate points while
 *     preserving direction changes (corners). This reduces jitter from micro-tremor and
 *     aliasing without losing the shape information the classifier depends on.
 *  2. The resulting simplified [StatisticalGlideTypingClassifier.Gesture] can be resampled
 *     to the target point count as usual.
 */
object GesturePathSmoother {

    /**
     * Applies Ramer-Douglas-Peucker simplification to [gesture] and returns a new
     * [StatisticalGlideTypingClassifier.Gesture] with noise-reduced points.
     *
     * @param gesture  The raw gesture collected from touch events.
     * @param epsilon  Maximum allowed perpendicular deviation (pixels). Points closer than
     *                 this to the line between their neighbours are removed.
     *                 A value of 2.0 px removes sub-pixel jitter without affecting shape.
     */
    fun simplify(
        gesture: StatisticalGlideTypingClassifier.Gesture,
        epsilon: Float = 2.0f,
    ): StatisticalGlideTypingClassifier.Gesture {
        val n = gesture.pointCount
        if (n <= 2) return gesture

        // Extract point list from the gesture's public API.
        val xs = FloatArray(n) { i -> gesture.getX(i) }
        val ys = FloatArray(n) { i -> gesture.getY(i) }

        // Run RDP on the raw index array.
        val keepFlags = BooleanArray(n) { false }
        keepFlags[0] = true
        keepFlags[n - 1] = true
        rdp(xs, ys, 0, n - 1, epsilon, keepFlags)

        // Build a new Gesture from the kept points.
        val result = StatisticalGlideTypingClassifier.Gesture()
        for (i in 0 until n) {
            if (keepFlags[i]) {
                result.addPoint(xs[i], ys[i])
            }
        }

        // Guard: if simplification removed too many points (degenerate path), return original.
        return if (result.pointCount < 2) gesture else result
    }

    /**
     * Recursive Ramer-Douglas-Peucker implementation that marks points to keep in [keepFlags].
     */
    private fun rdp(
        xs: FloatArray,
        ys: FloatArray,
        start: Int,
        end: Int,
        epsilon: Float,
        keepFlags: BooleanArray,
    ) {
        if (end <= start + 1) return

        val x1 = xs[start]; val y1 = ys[start]
        val x2 = xs[end];   val y2 = ys[end]

        var maxDist = 0f
        var maxIdx = start

        for (i in start + 1 until end) {
            val dist = perpendicularDistance(xs[i], ys[i], x1, y1, x2, y2)
            if (dist > maxDist) {
                maxDist = dist
                maxIdx = i
            }
        }

        if (maxDist > epsilon) {
            keepFlags[maxIdx] = true
            rdp(xs, ys, start, maxIdx, epsilon, keepFlags)
            rdp(xs, ys, maxIdx, end, epsilon, keepFlags)
        }
    }

    /**
     * Perpendicular distance from point (px, py) to the infinite line through (x1,y1)-(x2,y2).
     * Returns the absolute value of the cross-product divided by the segment length.
     */
    private fun perpendicularDistance(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.000001f) return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        return abs(dy * px - dx * py + x2 * y1 - y2 * x1) / len
    }
}
