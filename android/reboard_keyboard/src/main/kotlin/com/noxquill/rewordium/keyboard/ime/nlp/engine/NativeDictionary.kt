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

import android.content.Context
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import com.noxquill.rewordium.keyboard.lib.devtools.flogError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.android.readText
import java.util.concurrent.atomic.AtomicLong

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
     * Open the native dict and populate it from `ime/dict/data.json` +
     * `ime/dict/bigrams.json`. Idempotent — subsequent calls return
     * immediately if a previous call already finished. On any failure (JSON
     * parse, native rejection, library not loaded) the dictionary is left
     * unloaded and the next caller will retry.
     *
     * @return true if the dict ended up populated; false otherwise.
     */
    suspend fun loadFromAssets(context: Context): Boolean = loadMutex.withLock {
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
            populateFromAssets(context, h)
        } catch (t: Throwable) {
            flogError { "NativeDictionary: populateFromAssets failed: $t" }
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
        return LatinImeNative.nativeAddUnigram(handleRef, word, clamped)
    }

    private fun populateFromAssets(context: Context, h: Long): Boolean {
        val json = Json { ignoreUnknownKeys = true }
        // Unigrams: { "word": 0..255, ... }
        val wordlistText = context.assets.readText(WORDLIST_ASSET_PATH)
        val wordlistObj = json.parseToJsonElement(wordlistText).jsonObject
        var unigrams = 0L
        for ((word, freqEl) in wordlistObj) {
            if (word.isEmpty()) continue
            val freq = freqEl.jsonPrimitive.intOrNull?.coerceIn(0, 255) ?: continue
            // We intentionally swallow individual add failures — at scale a
            // handful of words can fail validation (e.g. AOSP rejects empty
            // code-point arrays) and the rest of the dict should still come up.
            if (LatinImeNative.nativeAddUnigram(h, word, freq)) {
                unigrams++
            }
        }
        unigramCount.set(unigrams)
        if (unigrams == 0L) {
            flogError { "NativeDictionary: zero unigrams added — refusing to expose empty dict" }
            return false
        }

        // Bigrams: { "prev_word": { "next_word": 0..255, ... }, ... }
        var bigrams = 0L
        runCatching {
            val bigramText = context.assets.readText(BIGRAMS_ASSET_PATH)
            val bigramObj = json.parseToJsonElement(bigramText).jsonObject
            for ((prev, nextEl) in bigramObj) {
                val nextObj = nextEl as? JsonObject ?: continue
                if (prev.isEmpty()) continue
                for ((next, freqEl) in nextObj) {
                    if (next.isEmpty()) continue
                    val freq = freqEl.jsonPrimitive.intOrNull?.coerceIn(0, 255) ?: continue
                    if (LatinImeNative.nativeAddBigram(h, prev, next, freq)) {
                        bigrams++
                    }
                }
            }
        }.onFailure { e ->
            // Bigrams are optional; we still consider the dict loaded with
            // unigrams alone. flogDebug so it doesn't shout in release logs.
            flogDebug { "NativeDictionary: bigram load skipped/partial: $e" }
        }
        bigramCount.set(bigrams)
        return true
    }

    private companion object {
        const val WORDLIST_ASSET_PATH = "ime/dict/data.json"
        const val BIGRAMS_ASSET_PATH = "ime/dict/bigrams.json"
    }
}
