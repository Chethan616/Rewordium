/*
 * Copyright (C) 2024-2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.dictionary

import android.content.Context
import com.noxquill.rewordium.keyboard.lib.FlorisLocale
import com.noxquill.rewordium.keyboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight per-locale store for words the IME has *learned from the user*
 * (manually-typed or glide-typed novel words) — the persistent backing for
 * adaptive learned swipe typing.
 *
 * Why this exists, separate from the existing UserDictionary database:
 *   * UserDictionary entries are explicitly added by the user via settings.
 *     This store is fed automatically by [LatinLanguageProvider.learnWord]
 *     every time the user commits a novel word with space/punctuation/glide.
 *   * UserDictionary is Room-backed (heavy). For per-keystroke writes we
 *     want sub-millisecond non-blocking [bump] semantics — a flat JSON file
 *     with debounced async writes hits that without a schema migration.
 *
 * File layout in `context.filesDir/learned_words.json`:
 * ```json
 * {
 *   "en-US": { "chethan": {"f":12,"t":1763824819}, "frfr": {"f":4,"t":1763824900} },
 *   "te-IN": { "bagunnava": {"f":7,"t":1763820000} }
 * }
 * ```
 *
 * Threading:
 *   * [bump] is non-suspending and safe to call from any dispatcher (it
 *     mutates a ConcurrentHashMap and posts to a CONFLATED channel).
 *   * The background writer runs on `Dispatchers.IO`, debounces 2s,
 *     and writes atomically (temp file + rename).
 *   * [ensureLoaded] holds a Mutex to make initial load idempotent under
 *     concurrent provider preloads.
 */
class LearnedWordsStore(private val context: Context) {

    @Serializable
    data class LearnedEntry(
        val f: Int,           // frequency, capped at 255
        val t: Long,          // last-used epoch seconds
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val perLocale = ConcurrentHashMap<String, ConcurrentHashMap<String, LearnedEntry>>()
    private val dirtyChannel = Channel<Unit>(Channel.CONFLATED)
    private val loadMutex = Mutex()
    @Volatile private var loaded = false

    private val storeFile: File by lazy { File(context.filesDir, FILE_NAME) }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mapSerializer = MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), LearnedEntry.serializer()),
    )

    init {
        // Background writer: receives signals on every bump(), debounces 2s,
        // coalesces further signals during the wait, then atomically writes.
        scope.launch {
            for (@Suppress("UNUSED_VARIABLE") signal in dirtyChannel) {
                delay(WRITE_DEBOUNCE_MS)
                // Drain any signals that arrived during the debounce.
                while (dirtyChannel.tryReceive().isSuccess) { /* coalesce */ }
                writeToDisk()
            }
        }
    }

    /** Idempotent. Loads the JSON file into memory on first call. */
    suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            try {
                if (storeFile.exists()) {
                    val text = storeFile.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) {
                        val parsed = json.decodeFromString(mapSerializer, text)
                        for ((locale, words) in parsed) {
                            perLocale[locale] = ConcurrentHashMap(words)
                        }
                    }
                }
            } catch (e: Exception) {
                // Corrupt file or read failure — start fresh, don't crash the IME.
                flogError { "LearnedWordsStore: failed to load ($e), starting empty" }
            } finally {
                loaded = true
            }
        }
    }

    /**
     * Returns a snapshot of [locale]'s learned words. Safe to iterate;
     * callers should treat the result as immutable.
     */
    fun snapshot(locale: FlorisLocale): Map<String, LearnedEntry> {
        val sub = perLocale[locale.languageTag()] ?: return emptyMap()
        return HashMap(sub) // defensive copy
    }

    /**
     * Bump the frequency of [word] under [locale]. Non-suspending. Hot path
     * called once per word commit — must be cheap and non-blocking.
     */
    fun bump(locale: FlorisLocale, word: String, freqDelta: Int = 1) {
        val tag = locale.languageTag()
        val now = System.currentTimeMillis() / 1000
        val sub = perLocale.getOrPut(tag) { ConcurrentHashMap() }
        val current = sub[word]
        val newFreq = ((current?.f ?: 0) + freqDelta).coerceIn(0, MAX_FREQ)
        sub[word] = LearnedEntry(f = newFreq, t = now)
        if (sub.size > LOCALE_CAP) {
            // Soft eviction: drop the bottom ~10% by recency-decayed score.
            // Runs at most every Nth bump (when cap is exceeded), not per call.
            evictLocale(sub, now)
        }
        // Signal the debounced writer. CONFLATED → never blocks on the channel.
        dirtyChannel.trySend(Unit)
    }

    /**
     * Drop the lowest-scoring [EVICT_FRACTION] of entries when [sub] outgrows
     * [LOCALE_CAP]. Score = freq × exp(-(age in seconds) / DECAY_SECONDS).
     * Worst case is one O(n log n) sort when n = LOCALE_CAP (cheap at ~5000).
     */
    private fun evictLocale(sub: ConcurrentHashMap<String, LearnedEntry>, nowSec: Long) {
        val ranked = sub.entries
            .map { e -> e.key to scoreOf(e.value, nowSec) }
            .sortedBy { it.second } // ascending: lowest first
        val toDrop = (LOCALE_CAP * EVICT_FRACTION).toInt().coerceAtLeast(1)
        for (i in 0 until minOf(toDrop, ranked.size)) {
            sub.remove(ranked[i].first)
        }
    }

    private fun scoreOf(entry: LearnedEntry, nowSec: Long): Double {
        val ageSec = (nowSec - entry.t).coerceAtLeast(0).toDouble()
        // exp(-age/30days). 30 days ≈ 2_592_000 sec.
        val decay = Math.exp(-ageSec / DECAY_SECONDS)
        return entry.f.toDouble() * decay
    }

    private fun writeToDisk() {
        try {
            // Snapshot a plain Map for serialization — ConcurrentHashMap
            // serializes fine but we want a stable view at write time.
            val snapshot: Map<String, Map<String, LearnedEntry>> =
                perLocale.mapValues { (_, v) -> HashMap(v) }
            val tmp = File(storeFile.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(mapSerializer, snapshot), Charsets.UTF_8)
            // Atomic rename — readers see either the old file or the new file,
            // never a half-written one.
            if (!tmp.renameTo(storeFile)) {
                // Fallback: overwrite (some filesystems don't support rename-over).
                storeFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            flogError { "LearnedWordsStore: write failed ($e)" }
        }
    }

    private companion object {
        const val FILE_NAME = "learned_words.json"
        const val WRITE_DEBOUNCE_MS = 2000L
        const val MAX_FREQ = 255
        const val LOCALE_CAP = 5000
        const val EVICT_FRACTION = 0.10
        const val DECAY_SECONDS = 2_592_000.0 // 30 days
    }
}
