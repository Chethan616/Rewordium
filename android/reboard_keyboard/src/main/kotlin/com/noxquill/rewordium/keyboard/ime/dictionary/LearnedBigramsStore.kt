/*
 * Copyright (C) 2024-2026 The ReBoard Contributors
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
import android.content.SharedPreferences
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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-locale persistent store for user-learned bigrams (previous-word →
 * next-word frequency). Sister of [LearnedWordsStore]; same write pattern
 * (CONFLATED dirty channel + 2s debounce + atomic temp+rename) so a kill-9
 * during a write can never corrupt the on-disk file.
 *
 * Replaces the previous SharedPreferences-backed bigram store
 * (`reboard_learned_bigrams` prefs file) which had three issues:
 *   * No eviction → could grow without bound
 *   * No atomic write → process-kill mid-apply() could corrupt entries
 *   * No per-locale separation
 *
 * File layout in `context.filesDir/learned_bigrams.json`:
 * ```json
 * {
 *   "en-US": {
 *     "hello": { "rupak": 12, "chethan": 4 },
 *     "good":  { "morning": 8 }
 *   }
 * }
 * ```
 *
 * The [migrateFromPreferences] one-shot reads the legacy prefs file on first
 * [ensureLoaded] and folds those entries into the JSON store before clearing
 * the prefs — so users upgrading don't lose their existing bigram history.
 */
class LearnedBigramsStore(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // perLocale[localeTag][prevWord][nextWord] = score (0..MAX_SCORE)
    private val perLocale = ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>>()
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
        MapSerializer(
            String.serializer(),
            MapSerializer(String.serializer(), Int.serializer()),
        ),
    )

    init {
        scope.launch {
            for (@Suppress("UNUSED_VARIABLE") signal in dirtyChannel) {
                delay(WRITE_DEBOUNCE_MS)
                while (dirtyChannel.tryReceive().isSuccess) { /* coalesce */ }
                writeToDisk()
            }
        }
    }

    /** Idempotent. Loads JSON from disk and migrates legacy SharedPreferences. */
    suspend fun ensureLoaded(legacyPrefs: SharedPreferences? = null) {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            try {
                if (storeFile.exists()) {
                    val text = storeFile.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) {
                        val parsed = json.decodeFromString(mapSerializer, text)
                        for ((locale, prevMap) in parsed) {
                            val sub = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
                            for ((prev, nextMap) in prevMap) {
                                sub[prev] = ConcurrentHashMap(nextMap)
                            }
                            perLocale[locale] = sub
                        }
                    }
                }
            } catch (e: Exception) {
                flogError { "LearnedBigramsStore: failed to load ($e), starting empty" }
            }
            if (legacyPrefs != null && !storeFile.exists()) {
                // Only migrate when the new file doesn't exist yet — once we
                // have a JSON file we treat it as authoritative.
                migrateFromPreferences(legacyPrefs)
            }
            loaded = true
        }
    }

    /**
     * Read every key/value from the legacy `reboard_learned_bigrams`
     * SharedPreferences file (format: key=prevWord, value="next1:s1,next2:s2,…"),
     * fold into the in-memory map under the default locale tag, then clear
     * the prefs file. Failure here is non-fatal — we just continue with an
     * empty store.
     *
     * The legacy prefs file had no locale separation, so we bucket the
     * migrated entries under [LEGACY_LOCALE_TAG] ("en-US"). Users running
     * non-Latin locales already had no learned bigrams in the legacy path
     * (the bigram learn callsite was only on the Latin provider), so this
     * is the safe default.
     */
    private fun migrateFromPreferences(prefs: SharedPreferences) {
        try {
            val all = prefs.all
            if (all.isEmpty()) return
            val sub = perLocale.getOrPut(LEGACY_LOCALE_TAG) { ConcurrentHashMap() }
            for ((prevWord, value) in all) {
                if (value !is String || value.isBlank()) continue
                val inner = sub.getOrPut(prevWord) { ConcurrentHashMap() }
                for (entry in value.split(",")) {
                    val parts = entry.split(":")
                    if (parts.size != 2) continue
                    val score = parts[1].toIntOrNull() ?: continue
                    inner[parts[0]] = score.coerceIn(0, MAX_SCORE)
                }
            }
            prefs.edit().clear().apply()
            dirtyChannel.trySend(Unit)
        } catch (e: Exception) {
            flogError { "LearnedBigramsStore: legacy migration failed ($e)" }
        }
    }

    /**
     * Snapshot of [locale]'s `previousWord → (nextWord → score)` map.
     * Safe to iterate; callers should treat the result as immutable.
     */
    fun snapshot(locale: FlorisLocale): Map<String, Map<String, Int>> {
        val sub = perLocale[locale.languageTag()] ?: return emptyMap()
        return sub.mapValues { (_, inner) -> HashMap(inner) }
    }

    /**
     * Bump the `prevWord → nextWord` transition score for [locale]. Hot path
     * — non-suspending, non-blocking. Score saturates at [MAX_SCORE].
     */
    fun bump(locale: FlorisLocale, prevWord: String, nextWord: String, delta: Int = 5) {
        val tag = locale.languageTag()
        val sub = perLocale.getOrPut(tag) { ConcurrentHashMap() }
        val inner = sub.getOrPut(prevWord) { ConcurrentHashMap() }
        val current = inner[nextWord] ?: 0
        inner[nextWord] = (current + delta).coerceIn(0, MAX_SCORE)
        if (sub.size > LOCALE_PREV_CAP) {
            evictLocale(sub)
        }
        dirtyChannel.trySend(Unit)
    }

    /**
     * Drop the lowest-scoring previous-word buckets when [sub] outgrows
     * [LOCALE_PREV_CAP]. Ranking key = sum of the bucket's transition scores
     * — buckets that have never been reinforced go first.
     */
    private fun evictLocale(sub: ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>) {
        val ranked = sub.entries
            .map { e -> e.key to e.value.values.sum() }
            .sortedBy { it.second }
        val toDrop = (LOCALE_PREV_CAP * EVICT_FRACTION).toInt().coerceAtLeast(1)
        for (i in 0 until minOf(toDrop, ranked.size)) {
            sub.remove(ranked[i].first)
        }
    }

    private fun writeToDisk() {
        try {
            val snapshot: Map<String, Map<String, Map<String, Int>>> =
                perLocale.mapValues { (_, prevMap) ->
                    prevMap.mapValues { (_, inner) -> HashMap(inner) }
                }
            val tmp = File(storeFile.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(mapSerializer, snapshot), Charsets.UTF_8)
            if (!tmp.renameTo(storeFile)) {
                storeFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            flogError { "LearnedBigramsStore: write failed ($e)" }
        }
    }

    private companion object {
        const val FILE_NAME = "learned_bigrams.json"
        const val WRITE_DEBOUNCE_MS = 2000L
        const val MAX_SCORE = 200
        const val LOCALE_PREV_CAP = 5000
        const val EVICT_FRACTION = 0.10
        const val LEGACY_LOCALE_TAG = "en-US"
    }
}
