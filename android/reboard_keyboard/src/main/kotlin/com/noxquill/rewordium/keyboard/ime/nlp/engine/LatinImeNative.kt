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

import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import com.noxquill.rewordium.keyboard.lib.devtools.flogError

/**
 * Kotlin facade for the `rewordium_latinime` native library.
 *
 * Phase 1 only exposes a smoke-test surface ([helloFromNative], [nativeAbiVersion])
 * to prove the NDK + CMake toolchain is wired correctly end-to-end. Phase 2 adds
 * the AOSP LatinIME-derived suggestion + gesture decoder JNI methods on top of
 * the same loader; the library name and load-time invariants below do not change.
 *
 * Library load is lazy via `Lazy` so we don't pay the cost on classes that
 * happen to import this file but never call a native method. If a build ships
 * with a mismatched native ABI version (e.g. someone updated the .cpp signatures
 * but forgot to rebuild), [ensureLoaded] surfaces it as a hard error at first
 * use rather than letting a later call crash inside the JNI boundary.
 */
object LatinImeNative {
    /** Bump in lockstep with `nativeAbiVersion` in `latinime_jni.cpp`. */
    private const val EXPECTED_ABI_VERSION = 5

    /** Sentinel for "open failed" — Kotlin must not pass 0 back to native. */
    const val INVALID_HANDLE: Long = 0L

    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("rewordium_latinime")
            val actual = nativeAbiVersion()
            if (actual != EXPECTED_ABI_VERSION) {
                flogError {
                    "rewordium_latinime: ABI mismatch (kotlin expects " +
                        "$EXPECTED_ABI_VERSION, native reports $actual). Rebuild required."
                }
                false
            } else {
                flogDebug { "rewordium_latinime: loaded, ABI v$actual" }
                true
            }
        } catch (t: UnsatisfiedLinkError) {
            flogError { "rewordium_latinime: load failed ($t)" }
            false
        } catch (t: Throwable) {
            flogError { "rewordium_latinime: load failed unexpectedly ($t)" }
            false
        }
    }

    /**
     * Idempotent. Returns true if the native library is loaded and the
     * JNI surface matches what this Kotlin layer expects. Callers that
     * gate native paths behind a feature flag should also gate on this
     * so a broken build silently falls back to the Kotlin path.
     */
    fun ensureLoaded(): Boolean = loaded

    /**
     * Smoke-test entry point. Returns a fixed greeting string from the
     * native side. Exists so phase-1 verification can `assertEquals` a
     * known value end-to-end without depending on any decoder state.
     */
    external fun helloFromNative(): String

    /**
     * Reports the JNI ABI version baked into the .so. Used by [loaded] to
     * detect Kotlin/native skew at load time.
     */
    external fun nativeAbiVersion(): Int

    // ── In-memory v4 dictionary ───────────────────────────────────────────
    //
    // Build pattern:
    //   val handle = LatinImeNative.nativeOpenInMemoryDict()
    //   try {
    //       wordlist.forEach { (w, p) -> nativeAddUnigram(handle, w, p) }
    //       bigrams.forEach   { (prev, w, p) -> nativeAddBigram(handle, prev, w, p) }
    //       // use handle for nativeIsValidWord / suggest calls
    //   } finally {
    //       nativeCloseDict(handle)
    //   }
    //
    // Handles are opaque 64-bit pointers in the native heap. Treat them as
    // unforgeable: never construct a Long and pass it in; only ever round-
    // trip values returned by [nativeOpenInMemoryDict]. The native side
    // dereferences without bounds checking.

    /**
     * Allocate an empty in-memory v4 dictionary backed by AOSP's
     * Ver4PatriciaTrieWritingHelper. Returns [INVALID_HANDLE] on failure
     * (out of memory, ABI mismatch, etc.) — callers must check.
     */
    external fun nativeOpenInMemoryDict(): Long

    /**
     * Free the native dictionary referenced by [handle]. Must be called
     * exactly once per successful [nativeOpenInMemoryDict] result;
     * subsequent use of the handle is undefined behavior.
     */
    external fun nativeCloseDict(handle: Long)

    /**
     * Add a unigram entry. [probability] is 0–255 (AOSP's standard range,
     * matches our existing JSON wordlist). Returns false on invalid
     * handle, empty word, or AOSP rejection.
     */
    external fun nativeAddUnigram(handle: Long, word: String, probability: Int): Boolean

    /**
     * Add a bigram entry chaining [prevWord] → [word]. Both endpoints
     * should already exist as unigrams (the AOSP writer enforces this).
     */
    external fun nativeAddBigram(handle: Long, prevWord: String, word: String, probability: Int): Boolean

    /**
     * Spell-check entry point. Returns true if the word is in the
     * dictionary at this handle. Case-sensitive — for case-insensitive
     * lookup, lowercase the word in Kotlin before calling.
     */
    external fun nativeIsValidWord(handle: Long, word: String): Boolean

    /**
     * Returns the unigram probability (0–255) for [word], or -1 if the
     * word is not in the dictionary. Primarily used by phase-4 calibration
     * tests to A/B compare against the Kotlin path's frequency map.
     */
    external fun nativeGetUnigramProbability(handle: Long, word: String): Int

    /**
     * Walks the dictionary trie, returns words starting with [prefix]
     * ranked by combined unigram + bigram probability. When [prevWord] is
     * a known dict word, candidates' scores reflect the bigram conditional
     * probability `P(word | prevWord)`; otherwise the score collapses to
     * the unigram probability.
     *
     * No spatial/typo correction yet — that needs the AOSP `Suggest` +
     * `ProximityInfo` integration (a follow-up). For now, returns exact
     * prefix matches only.
     *
     * Empty [prefix] is legal: with a valid [prevWord] it returns pure
     * next-word predictions ranked by bigram probability.
     */
    external fun nativeGetCompletions(
        handle: Long,
        prefix: String,
        prevWord: String,
        maxResults: Int,
    ): Array<String>

    // ── ProximityInfo lifecycle (Phase 5d.1) ──────────────────────────────
    //
    // Builds an AOSP ProximityInfo from the int/float arrays produced by
    // KeyboardLayoutDescriptor.fromTextKeys(). Pair with the corresponding
    // close call on layout change. Phase 5d.2 will add the actual gesture
    // suggest method that consumes this handle alongside the dict handle.

    /**
     * Allocate a native ProximityInfo from pre-shaped layout data. See
     * `KeyboardLayoutDescriptor` for the array shapes the AOSP constructor
     * requires. Returns [INVALID_HANDLE] on bad input. The caller owns the
     * returned handle and must pair it with exactly one [nativeCloseProximityInfo].
     */
    external fun nativeOpenProximityInfo(
        keyboardWidth: Int,
        keyboardHeight: Int,
        gridWidth: Int,
        gridHeight: Int,
        mostCommonKeyWidth: Int,
        mostCommonKeyHeight: Int,
        proximityChars: IntArray,
        keyCount: Int,
        keyXCoordinates: IntArray,
        keyYCoordinates: IntArray,
        keyWidths: IntArray,
        keyHeights: IntArray,
        keyCharCodes: IntArray,
        sweetSpotCenterXs: FloatArray,
        sweetSpotCenterYs: FloatArray,
        sweetSpotRadii: FloatArray,
    ): Long

    /** Free a ProximityInfo handle returned by [nativeOpenProximityInfo]. */
    external fun nativeCloseProximityInfo(handle: Long)

    /**
     * Runs AOSP's full Suggest pipeline for a single glide stroke. Returns
     * the ranked top-[maxResults] word candidates as a `String[]`, sorted
     * best-first.
     *
     * @param dictHandle  Dictionary handle from [nativeOpenInMemoryDict]
     * @param proxHandle  ProximityInfo handle from [nativeOpenProximityInfo]
     * @param xs / ys / ts  Parallel int arrays of touch (x, y, time) samples
     *                      for the swipe. All three must be the same length.
     *                      Time is milliseconds (any monotonic origin is fine
     *                      — AOSP only uses time deltas).
     * @param prevWord    Previous committed word for bigram context; "" if
     *                    sentence-initial.
     * @param maxResults  Cap on returned candidates. Also sizes internal
     *                    AOSP buffers, so pass a reasonable value (6–10).
     *
     * Empty array on invalid handle, empty stroke, or no candidates.
     */
    external fun nativeSuggestForGesture(
        dictHandle: Long,
        proxHandle: Long,
        xs: IntArray,
        ys: IntArray,
        ts: IntArray,
        prevWord: String,
        maxResults: Int,
    ): Array<String>
}
