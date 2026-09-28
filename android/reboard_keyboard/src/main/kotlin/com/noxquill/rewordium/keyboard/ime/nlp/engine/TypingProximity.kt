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
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Spatial model for the typing decoder: owns the native AOSP ProximityInfo
 * handle that describes the letter layout currently on screen, and records
 * where the user actually tapped for each letter of the word being typed.
 *
 * Real tap positions are what let the decoder tell "hitting the edge of the
 * neighbouring key" (a likely typo) from "hitting the centre of the key"
 * (intentional) — the core of Gboard-style autocorrect for large fingers.
 *
 * Threading: [update] runs on the UI thread during composition, decodes run
 * on the NLP dispatcher. A read/write lock guarantees a handle is never
 * freed while a decode is using it.
 */
object TypingProximity {
    private const val MAX_RECORDED_TAPS = 64

    private val lock = ReentrantReadWriteLock()
    @Volatile private var handle: Long = LatinImeNative.INVALID_HANDLE
    @Volatile private var descriptor: KeyboardLayoutDescriptor? = null
    @Volatile private var layoutSignature: Int = 0

    // Generic QWERTY used until the real keyboard has been laid out (system
    // spell checker, background vocabulary maintenance). Created lazily.
    @Volatile private var fallbackHandle: Long = LatinImeNative.INVALID_HANDLE
    @Volatile private var fallbackDescriptor: KeyboardLayoutDescriptor? = null

    private class Tap(val code: Int, val x: Int, val y: Int)

    private val taps = ArrayDeque<Tap>(MAX_RECORDED_TAPS)

    /**
     * Rebuilds the native ProximityInfo when the letter layout changed (new
     * subtype, keyboard resized, one-handed mode, ...). Cheap no-op when the
     * key geometry is unchanged, so it is safe to call on every recomposition.
     */
    fun update(keys: List<TextKey>) {
        if (keys.isEmpty() || !LatinImeNative.ensureLoaded()) return
        val signature = signatureOf(keys)
        if (signature == layoutSignature && handle != LatinImeNative.INVALID_HANDLE) return
        val maxRight = keys.maxOfOrNull { it.touchBounds.right } ?: return
        val maxBottom = keys.maxOfOrNull { it.touchBounds.bottom } ?: return
        val newDescriptor = KeyboardLayoutDescriptor.fromTextKeys(
            allKeys = keys,
            keyboardWidth = maxRight.toInt(),
            keyboardHeight = maxBottom.toInt(),
        ) ?: return
        val newHandle = try {
            LatinImeNative.nativeOpenProximityInfo(
                keyboardWidth = newDescriptor.keyboardWidth,
                keyboardHeight = newDescriptor.keyboardHeight,
                gridWidth = KeyboardLayoutDescriptor.GRID_WIDTH,
                gridHeight = KeyboardLayoutDescriptor.GRID_HEIGHT,
                mostCommonKeyWidth = newDescriptor.mostCommonKeyWidth,
                mostCommonKeyHeight = newDescriptor.mostCommonKeyHeight,
                proximityChars = newDescriptor.proximityChars,
                keyCount = newDescriptor.keyCount,
                keyXCoordinates = newDescriptor.keyXCoordinates,
                keyYCoordinates = newDescriptor.keyYCoordinates,
                keyWidths = newDescriptor.keyWidths,
                keyHeights = newDescriptor.keyHeights,
                keyCharCodes = newDescriptor.keyCharCodes,
                sweetSpotCenterXs = newDescriptor.sweetSpotCenterXs,
                sweetSpotCenterYs = newDescriptor.sweetSpotCenterYs,
                sweetSpotRadii = newDescriptor.sweetSpotRadii,
            )
        } catch (t: Throwable) {
            LatinImeNative.INVALID_HANDLE
        }
        if (newHandle == LatinImeNative.INVALID_HANDLE) return
        lock.write {
            val old = handle
            handle = newHandle
            descriptor = newDescriptor
            layoutSignature = signature
            synchronized(taps) { taps.clear() }
            if (old != LatinImeNative.INVALID_HANDLE) {
                LatinImeNative.nativeCloseProximityInfo(old)
            }
        }
        flogDebug { "TypingProximity: layout updated, ${newDescriptor.keyCount} keys" }
    }

    /** Records the position of a tap that produced the character [code]. */
    fun recordTap(code: Int, x: Float, y: Float) {
        if (code <= 0) return
        synchronized(taps) {
            if (taps.size >= MAX_RECORDED_TAPS) taps.removeFirst()
            taps.addLast(Tap(Character.toLowerCase(code), x.toInt(), y.toInt()))
        }
    }

    /** Drops the most recent tap (the user deleted the last character). */
    fun removeLastTap() {
        synchronized(taps) { taps.removeLastOrNull() }
    }

    /**
     * Runs [block] with the live ProximityInfo handle and per-letter tap
     * coordinates for [word], holding the read lock so the handle stays valid
     * for the duration. Coordinates come from the recorded taps when they line
     * up with [word], otherwise from key centres (AOSP does the same for
     * resumed words). Returns null when no layout is available.
     */
    fun <T> withCoordinates(word: String, block: (handle: Long, xs: IntArray, ys: IntArray) -> T): T? {
        if (handle == LatinImeNative.INVALID_HANDLE) ensureFallback()
        lock.read {
            val live = handle != LatinImeNative.INVALID_HANDLE && descriptor != null
            val h = if (live) handle else fallbackHandle
            val desc = (if (live) descriptor else fallbackDescriptor) ?: return null
            if (h == LatinImeNative.INVALID_HANDLE) return null
            val codePoints = word.codePoints().toArray()
            val n = codePoints.size
            val xs = IntArray(n)
            val ys = IntArray(n)
            val recorded = if (!live) null else synchronized(taps) {
                if (taps.size >= n) taps.subList(taps.size - n, taps.size).toList() else null
            }
            val aligned = recorded != null && recorded.indices.all { i ->
                recorded[i].code == Character.toLowerCase(codePoints[i])
            }
            val maxX = desc.keyboardWidth - 1
            val maxY = desc.keyboardHeight - 1
            for (i in 0 until n) {
                if (aligned) {
                    // Clamp: AOSP indexes its proximity grid with these values
                    // without bounds checks.
                    xs[i] = recorded!![i].x.coerceIn(0, maxX)
                    ys[i] = recorded[i].y.coerceIn(0, maxY)
                } else {
                    val center = desc.centerOf(codePoints[i])
                    xs[i] = center?.first?.coerceIn(0, maxX) ?: LatinImeNative.NOT_A_COORDINATE
                    ys[i] = center?.second?.coerceIn(0, maxY) ?: LatinImeNative.NOT_A_COORDINATE
                }
            }
            return block(h, xs, ys)
        }
    }

    private fun ensureFallback() {
        if (fallbackHandle != LatinImeNative.INVALID_HANDLE || !LatinImeNative.ensureLoaded()) return
        val desc = KeyboardLayoutDescriptor.standardQwerty() ?: return
        val h = try {
            LatinImeNative.nativeOpenProximityInfo(
                keyboardWidth = desc.keyboardWidth,
                keyboardHeight = desc.keyboardHeight,
                gridWidth = KeyboardLayoutDescriptor.GRID_WIDTH,
                gridHeight = KeyboardLayoutDescriptor.GRID_HEIGHT,
                mostCommonKeyWidth = desc.mostCommonKeyWidth,
                mostCommonKeyHeight = desc.mostCommonKeyHeight,
                proximityChars = desc.proximityChars,
                keyCount = desc.keyCount,
                keyXCoordinates = desc.keyXCoordinates,
                keyYCoordinates = desc.keyYCoordinates,
                keyWidths = desc.keyWidths,
                keyHeights = desc.keyHeights,
                keyCharCodes = desc.keyCharCodes,
                sweetSpotCenterXs = desc.sweetSpotCenterXs,
                sweetSpotCenterYs = desc.sweetSpotCenterYs,
                sweetSpotRadii = desc.sweetSpotRadii,
            )
        } catch (t: Throwable) {
            LatinImeNative.INVALID_HANDLE
        }
        if (h == LatinImeNative.INVALID_HANDLE) return
        lock.write {
            if (fallbackHandle == LatinImeNative.INVALID_HANDLE) {
                fallbackHandle = h
                fallbackDescriptor = desc
            } else {
                LatinImeNative.nativeCloseProximityInfo(h)
            }
        }
    }

    private fun signatureOf(keys: List<TextKey>): Int {
        var result = keys.size
        for (key in keys) {
            val code = (key.data as? KeyData)?.code ?: 0
            val r = key.visibleBounds
            val t = key.touchBounds
            result = 31 * result + code
            result = 31 * result + r.left.toInt()
            result = 31 * result + r.top.toInt()
            result = 31 * result + r.width.toInt()
            result = 31 * result + r.height.toInt()
            result = 31 * result + t.top.toInt()
            result = 31 * result + t.height.toInt()
        }
        return result
    }
}
