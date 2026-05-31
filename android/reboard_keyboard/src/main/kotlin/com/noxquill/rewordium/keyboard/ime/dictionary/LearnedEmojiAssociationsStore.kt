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
 * Per-locale persistent store for learned `previousWord → emoji` associations.
 * Sister of [LearnedWordsStore] / [LearnedBigramsStore]; same write pattern
 * (CONFLATED dirty channel + 2s debounce + atomic temp+rename).
 *
 * The model: every time the user picks an emoji from the smartbar after a
 * non-blank previous word, we bump `score[prevWord][emojiValue]`. Reads
 * surface the top-K emojis for the current previousWord, both as:
 *   * a blank-composing prediction strip (no current word typed yet, but
 *     prev word is known — e.g. user just typed "lol " and we predict 😂),
 *   * a small bonus on `scoreFor` when the candidate emoji is associated
 *     with the active previousWord (so "ha" + prev="lol" leans 😂 over 🤣).
 *
 * Layout in `context.filesDir/learned_emoji_associations.json`:
 * ```json
 * {
 *   "en-US": {
 *     "lol":   { "😂": 12, "🤣": 4 },
 *     "love":  { "❤️": 9, "🥰": 3 }
 *   }
 * }
 * ```
 */
class LearnedEmojiAssociationsStore(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // perLocale[localeTag][prevWord][emojiValue] = score
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

    /** Idempotent. Loads JSON from disk on first call. */
    suspend fun ensureLoaded() {
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
                            for ((prev, emojiMap) in prevMap) {
                                sub[prev] = ConcurrentHashMap(emojiMap)
                            }
                            perLocale[locale] = sub
                        }
                    }
                }
            } catch (e: Exception) {
                flogError { "LearnedEmojiAssociationsStore: load failed ($e), starting empty" }
            }
            loaded = true
        }
    }

    /**
     * Top-K emoji values associated with [previousWord] for [locale], sorted
     * by descending score. Empty list when nothing's learned yet (the common
     * case for the first session on a fresh install).
     */
    fun topEmojisFor(locale: FlorisLocale, previousWord: String, n: Int): List<String> {
        if (n <= 0 || previousWord.isBlank()) return emptyList()
        val sub = perLocale[locale.languageTag()] ?: return emptyList()
        val inner = sub[previousWord.lowercase()] ?: return emptyList()
        return inner.entries
            .sortedByDescending { it.value }
            .asSequence()
            .take(n)
            .map { it.key }
            .toList()
    }

    /**
     * Score for the specific `previousWord → emojiValue` pair, or 0 if not
     * learned. Used by the suggestion provider to nudge `scoreFor` upward
     * for emojis the user typically picks in this context.
     */
    fun scoreFor(locale: FlorisLocale, previousWord: String, emojiValue: String): Int {
        if (previousWord.isBlank() || emojiValue.isBlank()) return 0
        val sub = perLocale[locale.languageTag()] ?: return 0
        return sub[previousWord.lowercase()]?.get(emojiValue) ?: 0
    }

    /**
     * Hot path — non-suspending. Called from `notifySuggestionAccepted` when
     * an emoji candidate is committed. Saturates at [MAX_SCORE] so a single
     * outlier word doesn't overwhelm the ranker forever.
     */
    fun bump(locale: FlorisLocale, previousWord: String, emojiValue: String, delta: Int = 3) {
        if (previousWord.isBlank() || emojiValue.isBlank()) return
        val tag = locale.languageTag()
        val sub = perLocale.getOrPut(tag) { ConcurrentHashMap() }
        val inner = sub.getOrPut(previousWord.lowercase()) { ConcurrentHashMap() }
        val current = inner[emojiValue] ?: 0
        inner[emojiValue] = (current + delta).coerceIn(0, MAX_SCORE)
        if (sub.size > LOCALE_PREV_CAP) {
            evictLocale(sub)
        }
        dirtyChannel.trySend(Unit)
    }

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
            flogError { "LearnedEmojiAssociationsStore: write failed ($e)" }
        }
    }

    private companion object {
        const val FILE_NAME = "learned_emoji_associations.json"
        const val WRITE_DEBOUNCE_MS = 2000L
        const val MAX_SCORE = 100
        const val LOCALE_PREV_CAP = 2000
        const val EVICT_FRACTION = 0.10
    }
}
