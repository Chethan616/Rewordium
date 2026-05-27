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

package com.noxquill.rewordium.keyboard.ime.text.gestures

import android.content.Context
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.nlp.engine.KeyboardLayoutDescriptor
import com.noxquill.rewordium.keyboard.ime.nlp.engine.LatinImeNative
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKey
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import com.noxquill.rewordium.keyboard.nlpManager

/**
 * Native gesture classifier — implements [GlideTypingClassifier] by routing
 * strokes through AOSP's `Suggest` pipeline via JNI. The handle lifecycle:
 *
 *   * [setLayout] (called by GlideTypingManager when the keyboard renders)
 *     extracts a [KeyboardLayoutDescriptor] from the live `TextKey` list and
 *     hands it to native via [LatinImeNative.nativeOpenProximityInfo].
 *     Previous handle (if any) is closed first.
 *   * [addGesturePoint] buffers raw (x, y, t) tuples into parallel int
 *     arrays for the duration of a stroke.
 *   * [getSuggestions] calls [LatinImeNative.nativeSuggestForGesture] with
 *     the dict handle (fetched from [NlpManager.nativeDictHandle]) and the
 *     buffered points; returns the ranked candidate list.
 *   * [clear] resets the point buffer between strokes; layout handle
 *     survives across strokes until [setLayout] is called again.
 *
 * Graceful degradation: when [LatinImeNative.ensureLoaded] is false or the
 * dict / proximity handle is missing, [getSuggestions] returns empty. The
 * caller (GlideTypingManager) treats that as "no native results"; the
 * Phase 8 work will keep the Kotlin classifier wired as a fallback for that
 * window. Until then, the user just sees no glide suggestions if native is
 * broken — strictly preferable to a crash.
 */
class NativeGlideTypingClassifier(context: Context) : GlideTypingClassifier {

    private val nlpManager by context.nlpManager()

    @Volatile private var proximityInfoHandle: Long = LatinImeNative.INVALID_HANDLE
    @Volatile private var currentSubtype: Subtype? = null

    /** Native dict is owned by LatinLanguageProvider; we only need our own
     *  proximity-info handle to be live for the classifier to be ready. The
     *  dict-handle availability is re-checked per-call in [getSuggestions]. */
    override val ready: Boolean
        get() = LatinImeNative.ensureLoaded() &&
            proximityInfoHandle != LatinImeNative.INVALID_HANDLE

    private val xs = ArrayList<Int>(MAX_POINTS_HINT)
    private val ys = ArrayList<Int>(MAX_POINTS_HINT)
    private val ts = ArrayList<Long>(MAX_POINTS_HINT)

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        val t = if (position.t != 0L) position.t else System.currentTimeMillis()
        xs.add(position.x.toInt())
        ys.add(position.y.toInt())
        ts.add(t)
    }

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        if (!LatinImeNative.ensureLoaded()) return
        if (keyViews.isEmpty()) return
        // Close any prior layout's proximity info before replacing.
        val prev = proximityInfoHandle
        if (prev != LatinImeNative.INVALID_HANDLE) {
            proximityInfoHandle = LatinImeNative.INVALID_HANDLE
            LatinImeNative.nativeCloseProximityInfo(prev)
        }
        // Derive total keyboard pixel size from the union of key bounds —
        // good enough for AOSP's grid sizing without needing a separate
        // measurement pipeline.
        val maxRight = keyViews.maxOfOrNull { it.visibleBounds.right } ?: return
        val maxBottom = keyViews.maxOfOrNull { it.visibleBounds.bottom } ?: return
        val descriptor = KeyboardLayoutDescriptor.fromTextKeys(
            allKeys = keyViews,
            keyboardWidth = maxRight.toInt(),
            keyboardHeight = maxBottom.toInt(),
        )
        if (descriptor == null) {
            // No letter keys (numeric / phone keyboard) — leave handle null
            // so getSuggestions returns empty for this subtype.
            currentSubtype = subtype
            return
        }
        proximityInfoHandle = LatinImeNative.nativeOpenProximityInfo(
            keyboardWidth = descriptor.keyboardWidth,
            keyboardHeight = descriptor.keyboardHeight,
            gridWidth = KeyboardLayoutDescriptor.GRID_WIDTH,
            gridHeight = KeyboardLayoutDescriptor.GRID_HEIGHT,
            mostCommonKeyWidth = descriptor.mostCommonKeyWidth,
            mostCommonKeyHeight = descriptor.mostCommonKeyHeight,
            proximityChars = descriptor.proximityChars,
            keyCount = descriptor.keyCount,
            keyXCoordinates = descriptor.keyXCoordinates,
            keyYCoordinates = descriptor.keyYCoordinates,
            keyWidths = descriptor.keyWidths,
            keyHeights = descriptor.keyHeights,
            keyCharCodes = descriptor.keyCharCodes,
            sweetSpotCenterXs = descriptor.sweetSpotCenterXs,
            sweetSpotCenterYs = descriptor.sweetSpotCenterYs,
            sweetSpotRadii = descriptor.sweetSpotRadii,
        )
        currentSubtype = subtype
        flogDebug {
            "NativeGlideTypingClassifier: layout set, ${descriptor.keyCount} letter keys, " +
                "handle=$proximityInfoHandle"
        }
    }

    override fun setWordData(subtype: Subtype, force: Boolean) {
        // Native dict population is owned by LatinLanguageProvider.preload();
        // this classifier just reads whatever handle is current. Phase 6 will
        // wire learned-word refresh through here.
        currentSubtype = subtype
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        clear()
        for (position in pointerData.positions) {
            addGesturePoint(position)
        }
    }

    override fun getSuggestions(
        maxSuggestionCount: Int,
        gestureCompleted: Boolean,
    ): List<CharSequence> {
        if (proximityInfoHandle == LatinImeNative.INVALID_HANDLE) return emptyList()
        if (xs.isEmpty() || maxSuggestionCount <= 0) return emptyList()

        val dictHandle = nlpManager.nativeDictHandle
        if (dictHandle == LatinImeNative.INVALID_HANDLE) return emptyList()

        // Normalize timestamps to first-sample-relative ms so they fit in Int
        // (AOSP uses int * for times; System.currentTimeMillis is Long and
        // long since exceeds Int.MAX as raw epoch ms — only deltas matter).
        val n = xs.size
        val xsArr = IntArray(n) { xs[it] }
        val ysArr = IntArray(n) { ys[it] }
        val t0 = ts[0]
        val tsArr = IntArray(n) { (ts[it] - t0).toInt() }

        val raw = LatinImeNative.nativeSuggestForGesture(
            dictHandle = dictHandle,
            proxHandle = proximityInfoHandle,
            xs = xsArr,
            ys = ysArr,
            ts = tsArr,
            prevWord = "", // bigram context handled in Phase 6
            maxResults = maxSuggestionCount,
        )
        return raw.toList()
    }

    override fun clear() {
        xs.clear()
        ys.clear()
        ts.clear()
    }

    /**
     * Release the native ProximityInfo handle. Call when the classifier is
     * permanently going out of scope (IME tear-down). Not currently invoked
     * by GlideTypingManager (the classifier lives as long as the manager),
     * but exposed for completeness and for tests that recycle handles.
     */
    fun releaseNative() {
        val h = proximityInfoHandle
        if (h != LatinImeNative.INVALID_HANDLE) {
            proximityInfoHandle = LatinImeNative.INVALID_HANDLE
            LatinImeNative.nativeCloseProximityInfo(h)
        }
    }

    private companion object {
        const val MAX_POINTS_HINT = 256
    }
}
