/*
 * Copyright (C) 2022-2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.nlp.latin

import android.content.Context
import android.content.SharedPreferences
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.appContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.dictionary.DictionaryManager
import com.noxquill.rewordium.keyboard.ime.dictionary.LearnedWordsStore
import com.noxquill.rewordium.keyboard.ime.editor.EditorContent
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingProvider
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingResult
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionProvider
import com.noxquill.rewordium.keyboard.ime.nlp.WordSuggestionCandidate
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.latin"
        private const val LEARNED_BIGRAMS_PREFS = "reboard_learned_bigrams"
        private const val MAX_LEARNED_SCORE = 200
        private const val BIGRAM_BOOST_FACTOR = 2.0
        private const val RECENCY_BOOST = 30
        private const val MAX_RECENCY_WORDS = 20

        // Binary cache magic header — bump when format changes.
        private const val CACHE_MAGIC = 0x52420002.toInt() // 'RB' + version 2
        private const val WORD_CACHE_NAME = "dict_words.bin"
        private const val BIGRAM_CACHE_NAME = "dict_bigrams.bin"

        // Adaptive learned swipe typing thresholds.
        // Gboard-balanced cadence: rebuild the glide-classifier Pruner after
        // every 3 newly learned words so a freshly-typed name surfaces in
        // glide candidates within seconds rather than after dozens of commits.
        private const val LEARNED_REFRESH_THRESHOLD = 3
        // First-time-seen personal words bootstrap to this frequency so they
        // can actually win against common dict words in glide ranking.
        //
        // 30 was below mid-tier dict words like "veteran" (~70-80) — a glide
        // of "chethan" would still rank "veteran" first because its frequency
        // overwhelmed the shape-match advantage. 120 puts personal words
        // above all mid-frequency dict words and only below the top-tier
        // English vocabulary ("the", "and", etc.) which is what we want:
        // personal names should beat shape-similar uncommon words, but a
        // mis-glide near "the"-shape paths should still surface "the".
        private const val NEW_WORD_BOOTSTRAP_FREQ = 120
        private const val LEARN_WORD_MIN_LEN = 2
        private const val LEARN_WORD_MAX_LEN = 40
        // Letters + apostrophe only — exclude URLs, numbers, symbols.
        private val LEARN_WORD_PATTERN = Regex("^[A-Za-z']+$")
    }

    override val providerId = ProviderId

    private val appContext by context.appContext()
    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    // Bigram data: previousWord -> (nextWord -> score)
    private val bigramData = guardedByLock { mutableMapOf<String, Map<String, Int>>() }
    private val bigramDataSerializer = MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), Int.serializer())
    )

    // User-learned bigrams stored in SharedPreferences
    private val learnedBigrams = guardedByLock { mutableMapOf<String, MutableMap<String, Int>>() }
    private var learnedBigramsPrefs: SharedPreferences? = null

    // Track the last committed word for bigram learning
    private var lastCommittedWord: String? = null

    // Recently typed words for recency boost (LRU-style)
    private val recentWords = ArrayDeque<String>(MAX_RECENCY_WORDS + 5)

    // ── Adaptive learned swipe typing (Section B of the plan) ────────────────
    // Per-user vocabulary persisted across IME process restarts. Loaded on
    // preload() and merged into wordData so glide ranking automatically
    // boosts personal words. Bumped on every word commit via learnWord().
    private val learnedStore = LearnedWordsStore(context)
    private val learnedSinceLastRefresh = AtomicInteger(0)
    private val _wordDataDirtyFlow = MutableSharedFlow<Subtype>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    /** Emitted when learned-word additions warrant a glide-classifier rebuild. */
    val wordDataDirtyFlow: SharedFlow<Subtype> = _wordDataDirtyFlow.asSharedFlow()
    private val prefs by FlorisPreferenceStore

    override suspend fun create() {
        // No-op
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        wordData.withLock { data ->
            if (data.isEmpty()) {
                val loaded = loadWordCache(appContext) ?: run {
                    // Cache miss: parse JSON and write cache for next time.
                    val rawData = appContext.assets.readText("ime/dict/data.json")
                    val parsed = Json.decodeFromString(wordDataSerializer, rawData)
                    saveWordCache(appContext, parsed)
                    parsed
                }
                data.putAll(loaded)
            }
        }
        // Load bigram dictionary
        bigramData.withLock { data ->
            if (data.isEmpty()) {
                try {
                    val loaded = loadBigramCache(appContext) ?: run {
                        val rawBigrams = appContext.assets.readText("ime/dict/bigrams.json")
                        val parsed = Json.decodeFromString(bigramDataSerializer, rawBigrams)
                        saveBigramCache(appContext, parsed)
                        parsed
                    }
                    data.putAll(loaded)
                } catch (e: Exception) {
                    flogDebug { "Failed to load bigrams: ${e.message}" }
                }
            }
        }
        // Load user-learned bigrams from SharedPreferences
        if (learnedBigramsPrefs == null) {
            learnedBigramsPrefs = appContext.getSharedPreferences(LEARNED_BIGRAMS_PREFS, Context.MODE_PRIVATE)
        }
        loadLearnedBigrams()

        // Merge persisted personal vocabulary into wordData so the glide
        // decoder sees user-learned words on the very first suggestion call
        // after process restart (the snapshot taken by setWordData reflects
        // this merged state).
        learnedStore.ensureLoaded()
        val learned = learnedStore.snapshot(subtype.primaryLocale)
        if (learned.isNotEmpty()) {
            wordData.withLock { data ->
                for ((word, entry) in learned) {
                    val current = data[word] ?: 0
                    // maxOf so we don't clobber high-frequency dictionary
                    // entries with low-freq learned counterparts.
                    data[word] = maxOf(current, entry.f.coerceIn(0, 255))
                }
            }
        }

        // Merge manually-added User Dictionary entries (Room DB) so the glide
        // shape matcher recognizes them. Without this, words the user types
        // into Settings → User Dictionary are invisible to swipe input.
        // queryAll() returns ALL entries regardless of locale so we ignore
        // locale here — global vocab feeds glide for every active subtype.
        runCatching {
            val dao = DictionaryManager.default().florisUserDictionaryDao() ?: return@runCatching
            val entries = dao.queryAll()
            if (entries.isEmpty()) return@runCatching
            wordData.withLock { data ->
                for (entry in entries) {
                    val word = entry.word.trim().lowercase()
                    if (!LEARN_WORD_PATTERN.matches(word)) continue
                    val current = data[word] ?: 0
                    // 64 floor mirrors Gboard's bias toward user-added entries:
                    // they should outrank random low-freq dictionary noise but
                    // can still be overridden by truly common dictionary words.
                    data[word] = maxOf(current, entry.freq.coerceIn(64, 255))
                }
            }
        }.onFailure { e -> flogDebug { "Failed to merge user dictionary: ${e.message}" } }
        Unit
    }

    /**
     * Live import a single User Dictionary entry into [wordData] and trigger
     * a glide-classifier rebuild. Called from UserDictionaryScreen after the
     * Room insert succeeds so the user sees the new word in glide without
     * waiting for the next IME process restart.
     */
    suspend fun importUserDictionaryEntry(subtype: Subtype, rawWord: String, freq: Int) {
        val word = rawWord.trim().lowercase()
        if (word.length < LEARN_WORD_MIN_LEN || word.length > LEARN_WORD_MAX_LEN) return
        if (!LEARN_WORD_PATTERN.matches(word)) return
        wordData.withLock { data ->
            val current = data[word] ?: 0
            data[word] = maxOf(current, freq.coerceIn(64, 255))
        }
        // Rebuild glide Pruner immediately — unlike learnWord(), there's no
        // need to debounce a single explicit user-add action.
        _wordDataDirtyFlow.tryEmit(subtype)
    }

    /**
     * Adaptive learning entry point. Called from EditorInstance.commitChar
     * (on space/punctuation) and KeyboardManager.commitGesture (on glide
     * commit) via NlpManager.learnWord facade.
     *
     * Validates the word, bumps frequency in [wordData], persists via
     * [learnedStore], and emits a dirty signal when enough new words have
     * accumulated to warrant a glide-classifier rebuild.
     *
     * Hot path — runs once per word commit. All work is microseconds: one
     * regex check, one map mutation, one channel offer. Pruner rebuild
     * (the expensive part) is debounced via [LEARNED_REFRESH_THRESHOLD].
     */
    suspend fun learnWord(subtype: Subtype, rawWord: String) {
        if (!prefs.dictionary.learnPersonalWords.get()) return
        val word = rawWord.trim().lowercase()
        if (word.length < LEARN_WORD_MIN_LEN || word.length > LEARN_WORD_MAX_LEN) return
        if (!LEARN_WORD_PATTERN.matches(word)) return

        // First-time-seen words (NOT in built-in dict, NOT previously learned)
        // get bootstrapped to a competitive frequency floor. Without this,
        // a freshly-typed personal word like "evaru" has freq 1/255 ≈ 0.004
        // — far below common dict words — and the glide shape-matcher's
        // frequency-weighted ranking puts dict words above it every time,
        // even when "evaru" geometrically matches the swipe better.
        //
        // 30 puts the new word in the mid-frequency band where it can win
        // a clear shape match while still ranking below truly common words
        // for ambiguous swipes.
        val isNewWord: Boolean
        wordData.withLock { data ->
            val current = data[word] ?: 0
            isNewWord = current == 0
            val next = if (isNewWord) NEW_WORD_BOOTSTRAP_FREQ else (current + 1).coerceAtMost(255)
            data[word] = next
        }
        // Persist using a larger delta on first learn so the learned-store
        // row reflects the bootstrap (otherwise an IME restart would drop
        // the word back to freq=1).
        learnedStore.bump(
            subtype.primaryLocale,
            word,
            freqDelta = if (isNewWord) NEW_WORD_BOOTSTRAP_FREQ else 1,
        )

        // First-time-seen personal words deserve an immediate Pruner rebuild
        // so the user sees the effect on their next swipe — not after they
        // commit three random words. For repeat reinforcement, still
        // debounce via the threshold to avoid rebuilding for every keystroke.
        if (isNewWord) {
            learnedSinceLastRefresh.set(0)
            _wordDataDirtyFlow.tryEmit(subtype)
        } else if (learnedSinceLastRefresh.incrementAndGet() >= LEARNED_REFRESH_THRESHOLD) {
            learnedSinceLastRefresh.set(0)
            _wordDataDirtyFlow.tryEmit(subtype)
        }
    }

    // ── Binary dictionary cache helpers ─────────────────────────────────────

    private fun cacheDir(ctx: Context): File = ctx.cacheDir.also { it.mkdirs() }

    @Suppress("DEPRECATION")
    private fun appVersionCode(ctx: Context): Long = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    } catch (_: Exception) { 0L }

    /**
     * Try loading word frequencies from the binary cache.
     * Returns null on cache miss, version mismatch, corrupt data, or I/O failure.
     * Format: magic(Int) + appVersionCode(Long) + count(Int) + [word(UTF) + score(Int)]*
     */
    private fun loadWordCache(ctx: Context): Map<String, Int>? {
        val file = File(cacheDir(ctx), WORD_CACHE_NAME)
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream(), 65_536)).use { dis ->
                if (dis.readInt() != CACHE_MAGIC) return null
                if (dis.readLong() != appVersionCode(ctx)) return null // stale — will rebuild
                val count = dis.readInt()
                val map = HashMap<String, Int>(count * 2)
                repeat(count) {
                    val word = dis.readUTF()
                    val score = dis.readInt()
                    map[word] = score
                }
                map
            }
        } catch (_: Exception) {
            file.delete() // corrupt cache — recreate next time
            null
        }
    }

    private fun saveWordCache(ctx: Context, data: Map<String, Int>) {
        try {
            val file = File(cacheDir(ctx), WORD_CACHE_NAME)
            DataOutputStream(BufferedOutputStream(file.outputStream(), 65_536)).use { dos ->
                dos.writeInt(CACHE_MAGIC)
                dos.writeLong(appVersionCode(ctx))
                dos.writeInt(data.size)
                for ((word, score) in data) {
                    dos.writeUTF(word)
                    dos.writeInt(score)
                }
            }
        } catch (_: Exception) { /* non-fatal — next start will retry */ }
    }

    /**
     * Try loading bigram frequencies from the binary cache.
     * Format: magic(Int) + appVersionCode(Long) + outerCount(Int) +
     *         [prevWord(UTF) + innerCount(Int) + [nextWord(UTF) + score(Int)]*]*
     */
    private fun loadBigramCache(ctx: Context): Map<String, Map<String, Int>>? {
        val file = File(cacheDir(ctx), BIGRAM_CACHE_NAME)
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream(), 65_536)).use { dis ->
                if (dis.readInt() != CACHE_MAGIC) return null
                if (dis.readLong() != appVersionCode(ctx)) return null
                val outerCount = dis.readInt()
                val map = HashMap<String, Map<String, Int>>(outerCount * 2)
                repeat(outerCount) {
                    val prev = dis.readUTF()
                    val innerCount = dis.readInt()
                    val inner = HashMap<String, Int>(innerCount * 2)
                    repeat(innerCount) {
                        val next = dis.readUTF()
                        val score = dis.readInt()
                        inner[next] = score
                    }
                    map[prev] = inner
                }
                map
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun saveBigramCache(ctx: Context, data: Map<String, Map<String, Int>>) {
        try {
            val file = File(cacheDir(ctx), BIGRAM_CACHE_NAME)
            DataOutputStream(BufferedOutputStream(file.outputStream(), 65_536)).use { dos ->
                dos.writeInt(CACHE_MAGIC)
                dos.writeLong(appVersionCode(ctx))
                dos.writeInt(data.size)
                for ((prev, inner) in data) {
                    dos.writeUTF(prev)
                    dos.writeInt(inner.size)
                    for ((next, score) in inner) {
                        dos.writeUTF(next)
                        dos.writeInt(score)
                    }
                }
            }
        } catch (_: Exception) { /* non-fatal */ }
    }

    /**
     * Load user-learned bigrams from SharedPreferences.
     * Format: key = "prev_word", value = "next1:score1,next2:score2,..."
     */
    private suspend fun loadLearnedBigrams() {
        learnedBigrams.withLock { data ->
            if (data.isEmpty()) {
                learnedBigramsPrefs?.all?.forEach { (key, value) ->
                    if (value is String && value.isNotBlank()) {
                        val nextWords = mutableMapOf<String, Int>()
                        value.split(",").forEach { entry ->
                            val parts = entry.split(":")
                            if (parts.size == 2) {
                                nextWords[parts[0]] = parts[1].toIntOrNull() ?: 1
                            }
                        }
                        if (nextWords.isNotEmpty()) {
                            data[key] = nextWords
                        }
                    }
                }
            }
        }
    }

    /**
     * Save a learned bigram to SharedPreferences.
     */
    private suspend fun saveLearnedBigram(prevWord: String, nextWord: String) {
        learnedBigrams.withLock { data ->
            val nextWords = data.getOrPut(prevWord) { mutableMapOf() }
            val current = nextWords.getOrDefault(nextWord, 0)
            nextWords[nextWord] = (current + 5).coerceAtMost(MAX_LEARNED_SCORE)

            // Persist to SharedPreferences
            val serialized = nextWords.entries.joinToString(",") { "${it.key}:${it.value}" }
            learnedBigramsPrefs?.edit()?.putString(prevWord, serialized)?.apply()
        }
    }

    /**
     * Extract the previous word from text before the cursor.
     * Handles edge cases like multiple spaces, punctuation, etc.
     */
    private fun extractPreviousWord(textBeforeCursor: CharSequence): String? {
        val text = textBeforeCursor.toString().trimEnd()
        if (text.isBlank()) return null
        val lastSpace = text.lastIndexOf(' ')
        val word = if (lastSpace >= 0) text.substring(lastSpace + 1) else text
        val cleaned = word.lowercase().trim { !it.isLetter() && it != '\'' }
        return cleaned.takeIf { it.length >= 1 }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val query = word.trim().lowercase()
        if (query.isBlank()) return SpellingResult.unspecified()

        val isKnown = wordData.withLock { it.containsKey(query) }
        if (isKnown) return SpellingResult.validWord()

        // Phase 1: fast prefix + edit-distance 1 candidates.
        val editCandidates = wordData.withLock { data ->
            data.asSequence()
                .filter { (candidate, _) ->
                    candidate != query &&
                    (candidate.startsWith(query.take(2)) || isEditDistance1(query, candidate))
                }
                .sortedByDescending { (_, score) -> score }
                .map { it.key }
                .take(maxSuggestionCount)
                .toList()
        }

        if (editCandidates.isNotEmpty()) {
            return SpellingResult.typo(editCandidates.toTypedArray())
        }

        // Phase 2: phonetic (Soundex) fallback — catches phonetically plausible misspellings
        // that differ by more than 1 edit (e.g. "freind" → "friend", "nite" → "night").
        val querySoundex = soundex(query)
        val phoneticCandidates = wordData.withLock { data ->
            data.asSequence()
                .filter { (candidate, _) ->
                    candidate != query && soundex(candidate) == querySoundex
                }
                .sortedByDescending { (_, score) -> score }
                .map { it.key }
                .take(maxSuggestionCount)
                .toList()
        }

        return if (phoneticCandidates.isNotEmpty()) {
            SpellingResult.typo(phoneticCandidates.toTypedArray())
        } else {
            SpellingResult.typo(emptyArray())
        }
    }

    /**
     * Soundex phonetic encoding. Returns a 4-character code (letter + 3 digits) that groups
     * words with similar consonant patterns, enabling phonetic spell correction.
     * Example: "color" and "colour" both encode to "C460".
     */
    private fun soundex(word: String): String {
        if (word.isEmpty()) return "0000"
        val table = mapOf(
            'b' to '1', 'f' to '1', 'p' to '1', 'v' to '1',
            'c' to '2', 'g' to '2', 'j' to '2', 'k' to '2',
            'q' to '2', 's' to '2', 'x' to '2', 'z' to '2',
            'd' to '3', 't' to '3',
            'l' to '4',
            'm' to '5', 'n' to '5',
            'r' to '6',
        )
        val upper = word.lowercase()
        val code = StringBuilder()
        code.append(upper[0].uppercaseChar())
        var prev = table[upper[0]] ?: '0'
        for (i in 1 until upper.length) {
            val curr = table[upper[i]] ?: '0'
            if (curr != '0' && curr != prev) {
                code.append(curr)
                if (code.length == 4) break
            }
            prev = curr
        }
        while (code.length < 4) code.append('0')
        return code.toString()
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val composingWord = content.composingText.ifBlank { content.currentWordText }.trim().lowercase()

        // Extract previous word for bigram context
        val textBeforeCursor = content.textBeforeSelection
        val previousWord = extractPreviousWord(
            if (composingWord.isNotBlank() && textBeforeCursor.endsWith(composingWord, ignoreCase = true)) {
                textBeforeCursor.substring(0, (textBeforeCursor.length - composingWord.length).coerceAtLeast(0))
            } else {
                textBeforeCursor
            }
        )

        // Get bigram suggestions for the previous word
        val bigramSuggestions = if (previousWord != null) {
            getBigramSuggestions(previousWord, composingWord)
        } else {
            emptyMap()
        }

        // If composing word is blank, return pure next-word predictions
        if (composingWord.isBlank()) {
            if (previousWord == null || bigramSuggestions.isEmpty()) return emptyList()
            return bigramSuggestions.entries
                .sortedByDescending { it.value }
                .take(maxCandidateCount)
                .map { (word, score) ->
                    WordSuggestionCandidate(
                        text = word,
                        confidence = (score / 255.0).coerceIn(0.0, 1.0),
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                }
        }

        // Composing word is non-blank: blend prefix matches with bigram + recency boosts
        return wordData.withLock { data ->
            val results = mutableListOf<Pair<String, Int>>()

            // 1. Exact match always first (auto-complete the current word)
            val exactScore = data[composingWord]
            if (exactScore != null) {
                val bigramScore = bigramSuggestions[composingWord] ?: 0
                results.add(composingWord to (exactScore + bigramScore + 100).coerceAtMost(600))
            }

            // 2. Prefix matches
            data.asSequence()
                .filter { (candidate, _) -> candidate.startsWith(composingWord) && candidate != composingWord }
                .forEach { (candidate, score) ->
                    val bigramScore = bigramSuggestions[candidate] ?: 0
                    val recencyBoost = if (candidate in recentWords) RECENCY_BOOST else 0
                    val boostedScore = if (bigramScore > 0) {
                        (score + (bigramScore * BIGRAM_BOOST_FACTOR).toInt() + recencyBoost).coerceAtMost(600)
                    } else {
                        (score + recencyBoost).coerceAtMost(600)
                    }
                    results.add(candidate to boostedScore)
                }

            // 3. Fuzzy matches (edit distance 1) if we have fewer than maxCandidateCount
            if (results.size < maxCandidateCount && composingWord.length >= 3) {
                val existingWords = results.map { it.first }.toSet()
                data.asSequence()
                    .filter { (candidate, _) ->
                        candidate !in existingWords &&
                        candidate.length in (composingWord.length - 1)..(composingWord.length + 1) &&
                        isEditDistance1(composingWord, candidate)
                    }
                    .take(maxCandidateCount - results.size)
                    .forEach { (candidate, score) ->
                        val bigramScore = bigramSuggestions[candidate] ?: 0
                        // Fuzzy matches get a slight penalty
                        val penalizedScore = ((score * 0.8).toInt() + (bigramScore * BIGRAM_BOOST_FACTOR * 0.5).toInt()).coerceAtMost(400)
                        results.add(candidate to penalizedScore)
                    }
            }

            results
                .sortedByDescending { (_, score) -> score }
                .take(maxCandidateCount)
                .map { (candidate, score) ->
                    WordSuggestionCandidate(
                        text = candidate,
                        confidence = (score / 600.0).coerceIn(0.0, 1.0),
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                }
        }
    }

    /**
     * Check if two words have edit distance of exactly 1
     * (one substitution, insertion, or deletion apart).
     * Optimized: avoids full DP matrix, bails early.
     */
    private fun isEditDistance1(a: String, b: String): Boolean {
        val lenDiff = a.length - b.length
        if (lenDiff < -1 || lenDiff > 1) return false
        if (a.length == b.length) {
            // Substitution: exactly one char differs
            var diffs = 0
            for (i in a.indices) {
                if (a[i] != b[i]) { diffs++; if (diffs > 1) return false }
            }
            return diffs == 1
        }
        // Insertion or deletion
        val longer = if (a.length > b.length) a else b
        val shorter = if (a.length > b.length) b else a
        var i = 0; var j = 0; var diffs = 0
        while (i < longer.length && j < shorter.length) {
            if (longer[i] != shorter[j]) { diffs++; if (diffs > 1) return false; i++ }
            else { i++; j++ }
        }
        return true
    }

    /**
     * Get combined bigram suggestions from both static and learned bigrams.
     * If composingWord is non-blank, only returns bigrams that start with it.
     */
    private suspend fun getBigramSuggestions(previousWord: String, composingWord: String): Map<String, Int> {
        val prev = previousWord.lowercase()
        val result = mutableMapOf<String, Int>()

        // Static bigrams
        bigramData.withLock { data ->
            data[prev]?.forEach { (word, score) ->
                if (composingWord.isBlank() || word.startsWith(composingWord)) {
                    result[word] = (result.getOrDefault(word, 0) + score).coerceAtMost(255)
                }
            }
        }

        // Learned bigrams (higher weight since personalized)
        learnedBigrams.withLock { data ->
            data[prev]?.forEach { (word, score) ->
                if (composingWord.isBlank() || word.startsWith(composingWord)) {
                    val learnedBoost = (score * 1.2).toInt() // Slightly favor learned patterns
                    result[word] = (result.getOrDefault(word, 0) + learnedBoost).coerceAtMost(255)
                }
            }
        }

        return result
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
        val accepted = candidate.text.toString().trim().lowercase()
        if (accepted.isBlank()) return

        // Boost the accepted word's frequency
        wordData.withLock { data ->
            val current = data.getOrDefault(accepted, 0)
            data[accepted] = (current + 2).coerceAtMost(255)
        }

        // Also persist for adaptive learned swipe typing — accepted
        // suggestions are stronger signal than manual commits (+2 vs +1).
        // The persistence layer is internally guarded by the same pref +
        // validation regex as [learnWord].
        if (prefs.dictionary.learnPersonalWords.get() &&
            accepted.length in LEARN_WORD_MIN_LEN..LEARN_WORD_MAX_LEN &&
            LEARN_WORD_PATTERN.matches(accepted)
        ) {
            learnedStore.bump(subtype.primaryLocale, accepted, freqDelta = 2)
            if (learnedSinceLastRefresh.incrementAndGet() >= LEARNED_REFRESH_THRESHOLD) {
                learnedSinceLastRefresh.set(0)
                _wordDataDirtyFlow.tryEmit(subtype)
            }
        }

        // Track recency
        recentWords.remove(accepted)
        recentWords.addFirst(accepted)
        if (recentWords.size > MAX_RECENCY_WORDS) recentWords.removeLast()

        // Learn bigram: if we have a previous word, save the pair
        lastCommittedWord?.let { prev ->
            if (prev.isNotBlank() && prev != accepted) {
                saveLearnedBigram(prev, accepted)
            }
        }

        // Update last committed word
        lastCommittedWord = accepted
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
        // When the user reverts a previously-accepted suggestion (typically
        // an autocorrect that produced the wrong word), we treat it as a
        // strong negative signal against that word — they explicitly said
        // "no, not this one". Decrement its frequency so the keyboard
        // stops aggressively pushing it. Same path as removeSuggestion but
        // softer: we don't delete the entry, just down-weight it.
        val reverted = candidate.text.toString().trim().lowercase()
        if (reverted.isBlank()) return
        if (!LEARN_WORD_PATTERN.matches(reverted)) return

        wordData.withLock { data ->
            val current = data[reverted] ?: return@withLock
            // Drop by 3 — strong enough that two reverts in a row will pull
            // the word out of the top suggestions, but not enough to nuke
            // a high-frequency dict word from a single accident.
            val next = (current - 3).coerceAtLeast(0)
            if (next == 0) data.remove(reverted) else data[reverted] = next
        }
        // Also reflect the demotion in the persisted learned store so it
        // survives IME restart.
        if (prefs.dictionary.learnPersonalWords.get()) {
            learnedStore.bump(subtype.primaryLocale, reverted, freqDelta = -3)
        }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        val key = candidate.text.toString().trim().lowercase()
        return wordData.withLock { data -> data.remove(key) != null }
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word.lowercase(), 0) / 255.0 }
    }

    override suspend fun getFrequencyMap(subtype: Subtype): Map<String, Double> {
        return wordData.withLock { data -> data.mapValues { it.value / 255.0 } }
    }

    override suspend fun destroy() {
        // No-op
    }
}
