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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * One ranked candidate from the AOSP typing decoder.
 *
 * @property normalizedScore AOSP's `calcNormalizedScore(typed, word, score)`,
 *   the value AOSP compares against its auto-correction threshold.
 */
data class TypingSuggestion(
    val word: String,
    val score: Int,
    val type: Int,
    val normalizedScore: Float,
) {
    val isAppropriateForAutoCorrection: Boolean
        get() = (type and LatinImeNative.KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION) != 0

    val isExactMatch: Boolean
        get() = (type and LatinImeNative.KIND_FLAG_EXACT_MATCH) != 0
}

/**
 * High-level Kotlin owner for an in-memory AOSP v4 dictionary. Wraps a single
 * native handle returned by [LatinImeNative.nativeOpenInMemoryDict] and feeds
 * it from our shipped JSON wordlist + bigram assets.
 *
 * Single-instance-per-locale. Thread-safety:
 *   * [loadFromAssets] is suspending and guarded by [loadMutex] so concurrent
 *     preload calls (e.g. NlpManager preload race) coalesce to a single load.
 *   * [isValidWord] / [getUnigramProbability] are non-suspending and safe
 *     to call from any dispatcher once [isLoaded] is true.
 *   * [close] is safe to call exactly once at IME destroy.
 *
 * The native handle stays alive for the IME process lifetime. We do NOT
 * persist the dict to disk — it rebuilds fresh from JSON on each process
 * start. That keeps the dict in lockstep with what ships in assets and
 * sidesteps cache-invalidation entirely; the cost is ~100-300ms of startup
 * work, deferred off the main thread by the caller.
 */
class NativeDictionary {

    @Volatile private var handleRef: Long = LatinImeNative.INVALID_HANDLE
    @Volatile private var loaded: Boolean = false
    private val loadMutex = Mutex()
    private val unigramCount = AtomicLong(0)
    private val bigramCount = AtomicLong(0)

    val isLoaded: Boolean get() = loaded

    /**
     * Handle for downstream native calls (e.g. spelling, suggest). Returns
     * [LatinImeNative.INVALID_HANDLE] if [loadFromAssets] hasn't completed.
     */
    val handle: Long get() = if (loaded) handleRef else LatinImeNative.INVALID_HANDLE

    /**
     * Open the native dict and populate it from the already-parsed word list
     * ([words], dictionary casing -> 0..255 log-scale frequency) and bigram
     * table. The caller (LatinLanguageProvider) owns parsing/caching of the
     * JSON assets, so the multi-megabyte wordlist is decoded only once.
     * Idempotent — subsequent calls return immediately if a previous call
     * already finished. On any failure (native rejection, library not loaded)
     * the dictionary is left unloaded and the next caller will retry.
     *
     * @return true if the dict ended up populated; false otherwise.
     */
    suspend fun load(
        words: Map<String, Int>,
        offensiveWords: Set<String>,
        bigrams: Map<String, Map<String, Int>>,
    ): Boolean = loadMutex.withLock {
        if (loaded) return@withLock true
        if (!LatinImeNative.ensureLoaded()) {
            flogError { "NativeDictionary: rewordium_latinime not loaded — skipping native dict" }
            return@withLock false
        }
        val h = LatinImeNative.nativeOpenInMemoryDict()
        if (h == LatinImeNative.INVALID_HANDLE) {
            flogError { "NativeDictionary: nativeOpenInMemoryDict failed" }
            return@withLock false
        }
        val ok = try {
            populate(h, words, offensiveWords, bigrams)
        } catch (t: Throwable) {
            flogError { "NativeDictionary: populate failed: $t" }
            false
        }
        if (!ok) {
            // Roll back the native allocation so we don't leak the handle if
            // we won't expose it to callers anyway.
            LatinImeNative.nativeCloseDict(h)
            return@withLock false
        }
        handleRef = h
        loaded = true
        flogDebug {
            "NativeDictionary: loaded ${unigramCount.get()} unigrams + ${bigramCount.get()} bigrams"
        }
        true
    }

    /**
     * Drop the native dict. After this call [handle] returns [LatinImeNative
     * .INVALID_HANDLE] and downstream native calls must NOT be made.
     */
    fun close() {
        val h = handleRef
        if (h == LatinImeNative.INVALID_HANDLE) return
        handleRef = LatinImeNative.INVALID_HANDLE
        loaded = false
        LatinImeNative.nativeCloseDict(h)
    }

    /** Convenience spell-check that no-ops when the native dict isn't loaded. */
    fun isValidWord(word: String): Boolean {
        if (!loaded) return false
        return LatinImeNative.nativeIsValidWord(handleRef, word)
    }

    /**
     * Returns the unigram probability (0–255) for [word], or -1 if the
     * word isn't in the native dict or the dict isn't loaded yet. Used by
     * the upcoming A/B parity logging to compare against the Kotlin path.
     */
    fun getUnigramProbability(word: String): Int {
        if (!loaded) return -1
        return LatinImeNative.nativeGetUnigramProbability(handleRef, word)
    }

    /**
     * Returns up to [maxResults] words starting with [prefix], ranked by
     * combined unigram + bigram probability (bigram conditional on
     * [prevWord] if it's a known dict word). Empty list when the native
     * dict isn't loaded yet — the caller should fall back to its Kotlin
     * path in that case rather than treating "no results" as authoritative.
     *
     * No spatial/typo correction. Phase-5 ProximityInfo integration is
     * needed for that.
     */
    fun getCompletions(
        prefix: String,
        prevWord: String,
        maxResults: Int,
    ): List<String> {
        if (!loaded || maxResults <= 0) return emptyList()
        val arr = LatinImeNative.nativeGetCompletions(handleRef, prefix, prevWord, maxResults)
        return arr.toList()
    }

    /**
     * Runs the AOSP typing decoder for [typed] with per-letter tap
     * coordinates ([xs]/[ys], ProximityInfo pixel space) against the layout
     * behind [proximityHandle]. Returns an empty list when either handle is
     * missing, the word is too long, or anything goes wrong natively — the
     * caller treats that as "no native opinion" and falls back.
     */
    fun suggestForTyping(
        proximityHandle: Long,
        typed: String,
        xs: IntArray,
        ys: IntArray,
        prevWord: String,
        blockOffensive: Boolean,
        maxResults: Int,
    ): List<TypingSuggestion> {
        if (!loaded || proximityHandle == LatinImeNative.INVALID_HANDLE || maxResults <= 0) return emptyList()
        val codePoints = typed.codePoints().toArray()
        if (codePoints.isEmpty() || codePoints.size >= LatinImeNative.MAX_WORD_LENGTH) return emptyList()
        if (xs.size < codePoints.size || ys.size < codePoints.size) return emptyList()
        val scores = IntArray(maxResults)
        val types = IntArray(maxResults)
        val normalized = FloatArray(maxResults)
        val words = try {
            LatinImeNative.nativeSuggestForTyping(
                dictHandle = handleRef,
                proxHandle = proximityHandle,
                codePoints = codePoints,
                xs = xs,
                ys = ys,
                prevWord = prevWord,
                blockOffensive = blockOffensive,
                maxResults = maxResults,
                outScores = scores,
                outTypes = types,
                outNormalizedScores = normalized,
            )
        } catch (t: Throwable) {
            flogError { "NativeDictionary: nativeSuggestForTyping failed: $t" }
            return emptyList()
        }
        return List(words.size) { i -> TypingSuggestion(words[i], scores[i], types[i], normalized[i]) }
    }

    /**
     * Phase 6: push a personal-vocab word into the native dict so both
     * suggest (Phase 4) and glide (Phase 5) can surface it. Idempotent —
     * adding the same word twice updates its frequency (AOSP's writer
     * replaces the entry's UnigramProperty).
     *
     * Called from [LatinLanguageProvider] in two places:
     *   * Preload: bulk-loads every entry from [LearnedWordsStore]
     *     immediately after the base JSON dict is in.
     *   * Hot path: each new word learnWord() commits.
     *
     * No-op when the native dict isn't loaded (we're either still in
     * preload or the feature flag is off); the existing Kotlin paths
     * continue to track the word independently.
     *
     * @return true on successful add, false on invalid handle / empty word.
     */
    fun addLearnedWord(word: String, probability: Int): Boolean {
        if (!loaded || word.isEmpty()) return false
        val clamped = probability.coerceIn(0, 255)
        return LatinImeNative.nativeAddUnigram(handleRef, word, clamped, false)
    }

    /**
     * Drop a previously-added unigram so the native suggest path stops
     * surfacing it in the current session. Mirrors [addLearnedWord]; called
     * from the unlearn path in [LatinLanguageProvider] when the user signals
     * "no, not that word" via an immediate backspace right after committing.
     *
     * Without this, an unlearn that updates the in-memory map + learnedStore
     * still leaves the native dict pushing the rejected word until the next
     * IME process restart.
     *
     * No-op when the native dict isn't loaded. Returns false on invalid
     * handle, empty word, or "word not present" — all treatable as success
     * by the caller.
     */
    fun removeLearnedWord(word: String): Boolean {
        if (!loaded || word.isEmpty()) return false
        return LatinImeNative.nativeRemoveUnigram(handleRef, word)
    }

    private fun populate(
        h: Long,
        words: Map<String, Int>,
        offensiveWords: Set<String>,
        bigrams: Map<String, Map<String, Int>>,
    ): Boolean {
        var unigrams = 0L
        for ((word, freq) in words) {
            if (word.isEmpty()) continue
            // We intentionally swallow individual add failures — at scale a
            // handful of words can fail validation (e.g. AOSP rejects empty
            // code-point arrays) and the rest of the dict should still come up.
            if (LatinImeNative.nativeAddUnigram(h, word, freq.coerceIn(0, 255), word in offensiveWords)) {
                unigrams++
            }
        }
        unigramCount.set(unigrams)
        if (unigrams == 0L) {
            flogError { "NativeDictionary: zero unigrams added — refusing to expose empty dict" }
            return false
        }
        var bigramsAdded = 0L
        for ((prev, nextMap) in bigrams) {
            if (prev.isEmpty()) continue
            for ((next, freq) in nextMap) {
                if (next.isEmpty()) continue
                if (LatinImeNative.nativeAddBigram(h, prev, next, freq.coerceIn(0, 255))) {
                    bigramsAdded++
                }
            }
        }
        bigramCount.set(bigramsAdded)
        return true
    }
}
