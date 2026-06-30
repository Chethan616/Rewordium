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
import com.noxquill.rewordium.keyboard.ime.dictionary.LearnedBigramsStore
import com.noxquill.rewordium.keyboard.ime.dictionary.LearnedWordsStore
import com.noxquill.rewordium.keyboard.ime.editor.EditorContent
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingProvider
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingResult
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionProvider
import com.noxquill.rewordium.keyboard.ime.nlp.WordSuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.engine.ContactsLoader
import com.noxquill.rewordium.keyboard.ime.nlp.engine.NativeDictionary
import com.noxquill.rewordium.keyboard.BuildConfig
import com.noxquill.rewordium.keyboard.lib.FlorisLocale
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
        private const val BIGRAM_BOOST_FACTOR = 2.0
        private const val RECENCY_BOOST = 30
        private const val MAX_RECENCY_WORDS = 20
        // Multiplicative boost applied to recently-learned words in the
        // glide-classifier's frequency map. Breaks ties between graduated
        // personal words (cap = 1.0) and shape-similar dict words also at
        // cap. 1.15 is small enough that it never lets a low-freq word beat
        // a much higher-freq one, but large enough to consistently win
        // against equally-frequent stale candidates.
        private const val GLIDE_RECENCY_BOOST = 1.15

        // Binary cache magic header — bump when format changes.
        private const val CACHE_MAGIC = 0x52420002.toInt() // 'RB' + version 2
        private const val WORD_CACHE_NAME = "dict_words.bin"
        private const val BIGRAM_CACHE_NAME = "dict_bigrams.bin"

        // Adaptive learned swipe typing thresholds.
        // Gboard-balanced cadence: rebuild the glide-classifier Pruner after
        // every 3 newly learned words so a freshly-typed name surfaces in
        // glide candidates within seconds rather than after dozens of commits.
        private const val LEARNED_REFRESH_THRESHOLD = 3
        // First-time-seen personal words bootstrap to MAX frequency so they
        // can decisively win shape-based glide ranking against any dict word.
        //
        // Earlier we tried 30 (lost to most dict words) and then 120 (still
        // lost to top-tier vocabulary like "chatham" / "tropical" for shape-
        // similar personal names like "chethan" / "rupak"). The product call
        // is: when a user types a personal word, they almost certainly want
        // it back — so we tie on frequency with the top vocabulary and let
        // the geometric shape + length-match bonus decide. Mistyped one-offs
        // are demoted via notifySuggestionReverted (-3 per revert).
        private const val NEW_WORD_BOOTSTRAP_FREQ = 255
        private const val LEARN_WORD_MIN_LEN = 2
        private const val LEARN_WORD_MAX_LEN = 40
        // Contact name tokens get a probability between common-word range
        // (low 100s) and the bootstrap freq for words the user has actually
        // typed (255). 220 keeps them competitive without overriding the
        // user's actual typing history.
        private const val CONTACT_NAME_PROBABILITY = 220
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

    // User-learned bigrams: persisted per-locale via [LearnedBigramsStore]
    // (atomic JSON file at filesDir/learned_bigrams.json). Replaces the
    // older SharedPreferences-backed scheme — we still open the legacy prefs
    // once at preload time so existing users get migrated, then we clear it.
    private val learnedBigramsStore = LearnedBigramsStore(context)
    private var legacyLearnedBigramsPrefs: SharedPreferences? = null

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

    // Phase 4: native AOSP-backed dictionary. Loaded asynchronously during
    // preload() when ENABLE_NATIVE_SUGGESTER is on. While [nativeDictionary
    // .isLoaded] is false, suggest()/spell() fall through to the existing
    // Kotlin path so a slow first-load doesn't break the keyboard. Once
    // loaded, phase 4d will route the active path to native.
    internal val nativeDictionary = NativeDictionary()
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
        // Load user-learned bigrams from the persistent JSON store. On the
        // very first call after upgrade, [ensureLoaded] also reads the
        // legacy SharedPreferences file and folds those entries in before
        // clearing the prefs, so existing users keep their bigram history.
        if (legacyLearnedBigramsPrefs == null) {
            legacyLearnedBigramsPrefs = appContext.getSharedPreferences(
                LEARNED_BIGRAMS_PREFS,
                Context.MODE_PRIVATE,
            )
        }
        learnedBigramsStore.ensureLoaded(legacyLearnedBigramsPrefs)

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

        // Seed contact display-name tokens. Contact names get a
        // high probability (220) so they win over common-word
        // shape collisions but stay just below the absolute-top
        // bootstrap (255) reserved for words the user has
        // explicitly typed at least once. No-op when the
        // useContacts pref is off or READ_CONTACTS isn't granted.
        var contactTokens: Set<String>? = null
        var contactBigrams: List<Pair<String, String>>? = null
        if (prefs.spelling.useContacts.get()) {
            contactTokens = ContactsLoader.loadNameTokens(appContext)
            if (contactTokens.isNotEmpty()) {
                wordData.withLock { data ->
                    for (token in contactTokens) {
                        val current = data[token] ?: 0
                        data[token] = maxOf(current, CONTACT_NAME_PROBABILITY)
                    }
                }
            }
            contactBigrams = ContactsLoader.loadNameBigrams(appContext)
            if (contactBigrams.isNotEmpty()) {
                bigramData.withLock { data ->
                    for ((prev, next) in contactBigrams) {
                        val inner = (data[prev] as? MutableMap<String, Int>) ?: data[prev]?.toMutableMap() ?: mutableMapOf()
                        val current = inner[next] ?: 0
                        inner[next] = maxOf(current, CONTACT_NAME_PROBABILITY)
                        data[prev] = inner
                    }
                }
            }
        }

        // Phase 4b: kick off native AOSP dict population. This runs on the
        // same IO context as the Kotlin path above so by the time preload()
        // returns, the native dict is ready. ~100-300ms cost on first call;
        // subsequent preload() calls are no-ops (NativeDictionary guards
        // with its own load mutex). Gated behind ENABLE_NATIVE_SUGGESTER —
        // when off, the native dict never opens and downstream phase 4d
        // routing decisions evaluate to "use Kotlin path".
        //
        // Phase 6: after the base dict loads, also merge in this locale's
        // LearnedWordsStore entries so personal vocab (chethan, rupak, …)
        // is in the SAME native dict from the very first suggest / glide
        // call after process start. This avoids the second-BinaryDictionary
        // + DictionaryFacilitator dance — the in-memory v4 dict is
        // mutable, so we just push entries into it.
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER) {
            runCatching {
                val ok = nativeDictionary.loadFromAssets(appContext)
                flogDebug { "LatinLanguageProvider: native dict load = $ok" }
                if (ok && nativeDictionary.isLoaded) {
                    var merged = 0
                    wordData.withLock { data ->
                        for ((word, freq) in data) {
                            if (nativeDictionary.addLearnedWord(word, freq)) merged++
                        }
                    }
                    flogDebug { "LatinLanguageProvider: merged $merged personal/contact words into native dict" }

                    if (contactBigrams != null) {
                        for ((_, next) in contactBigrams) {
                            nativeDictionary.addLearnedWord(next, CONTACT_NAME_PROBABILITY)
                        }
                    }
                }
            }.onFailure { e ->
                flogDebug { "LatinLanguageProvider: native dict load threw: $e" }
            }
        }
        Unit
    }

    /**
     * Hot-reload contact name tokens into an already-loaded native dict after
     * the user grants READ_CONTACTS at runtime (via the smartbar prompt or the
     * native app). Does not require an IME restart.
     *
     * Adds individual name tokens at [CONTACT_NAME_PROBABILITY] and also seeds
     * adjacent-name bigrams (e.g. "john"→"smith") so the suggester can predict
     * last names after the user types a contact's first name. Triggers a glide
     * classifier rebuild so swipe input benefits immediately.
     */
    suspend fun reloadContacts(subtype: Subtype) = withContext(Dispatchers.IO) {
        if (!prefs.spelling.useContacts.get()) return@withContext
        val tokens = ContactsLoader.loadNameTokens(appContext)
        if (tokens.isEmpty()) return@withContext

        // Push into the Kotlin word-frequency table (Kotlin suggestion path).
        wordData.withLock { data ->
            for (token in tokens) {
                val current = data[token] ?: 0
                data[token] = maxOf(current, CONTACT_NAME_PROBABILITY)
            }
        }

        // Push into native dict (native suggestion/glide path).
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
            var added = 0
            for (token in tokens) {
                if (nativeDictionary.addLearnedWord(token, CONTACT_NAME_PROBABILITY)) added++
            }
            // Seed first→last name bigrams for sequence prediction.
            val bigrams = ContactsLoader.loadNameBigrams(appContext)
            for ((prev, next) in bigrams) {
                nativeDictionary.addLearnedWord(next, CONTACT_NAME_PROBABILITY)
            }
            flogDebug { "LatinLanguageProvider: reloadContacts added $added tokens, ${bigrams.size} bigram pairs" }
        }

        // Rebuild glide classifier so swipe picks up the new vocab.
        _wordDataDirtyFlow.tryEmit(subtype)
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
        
        val clampedFreq = freq.coerceIn(64, 255)
        wordData.withLock { data ->
            val current = data[word] ?: 0
            data[word] = maxOf(current, clampedFreq)
        }
        
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
            nativeDictionary.addLearnedWord(word, clampedFreq)
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
        // get bootstrapped to NEW_WORD_BOOTSTRAP_FREQ so they immediately
        // compete with the top dict tier on next swipe — see the constant
        // docstring for the rationale on tying with max frequency.
        val isFirstTime: Boolean
        val isGraduating: Boolean
        wordData.withLock { data ->
            val current = data[word] ?: 0
            isFirstTime = current == 0
            isGraduating = current == 1
            val next = if (current == 0) {
                1
            } else if (current == 1) {
                NEW_WORD_BOOTSTRAP_FREQ
            } else {
                (current + 1).coerceAtMost(255)
            }
            data[word] = next
        }
        // Persist using a larger delta on graduation so the learned-store
        // row reflects the bootstrap.
        learnedStore.bump(
            subtype.primaryLocale,
            word,
            freqDelta = if (isGraduating) NEW_WORD_BOOTSTRAP_FREQ else 1,
        )
        if (learnedSinceLastRefresh.incrementAndGet() >= LEARNED_REFRESH_THRESHOLD) {
            learnedSinceLastRefresh.set(0)
            _wordDataDirtyFlow.tryEmit(subtype)
        }

        // Phase 6 incremental update: push the same word into the native
        // dict so the AOSP-backed suggest (Phase 4) + glide (Phase 5) paths
        // see it on the very next call.
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
            if (!isFirstTime) {
                // If it's the second time (graduating) or later, boost it to 255.
                // We skip the first time to avoid demoting existing high-freq system words to 1.
                nativeDictionary.addLearnedWord(word, 255)
            }
        }

        // Graduating personal words (typed 2 times) deserve an immediate Pruner rebuild
        // so the user sees the effect on their next swipe. For repeat reinforcement, still
        // debounce via the threshold to avoid rebuilding for every keystroke.
        // We DO NOT rebuild on `isFirstTime` because that triggers for every normal dictionary word typed!
        if (isGraduating) {
            learnedSinceLastRefresh.set(0)
            _wordDataDirtyFlow.tryEmit(subtype)
        }
    }

    suspend fun unlearnWord(subtype: Subtype, rawWord: String) {
        val word = rawWord.trim().lowercase()
        if (word.length < LEARN_WORD_MIN_LEN || word.length > LEARN_WORD_MAX_LEN) return
        if (!LEARN_WORD_PATTERN.matches(word)) return

        // We explicitly ALLOW unlearning built-in system words (like "wasn't") by forcing
        // their frequency to 1. This prevents the glide classifier from constantly 
        // suggesting them over valid alternatives (like "want").

        // Symmetric purge across all three suggestion sources so the rejected
        // word stops surfacing IMMEDIATELY (Kotlin suggest path, native
        // suggest path, and glide classifier) — not just after the next
        // IME process restart.
        wordData.withLock { data -> data[word] = 1 }

        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
            // Use addLearnedWord with freq 1 to OVERRIDE the system dictionary's frequency!
            // Do NOT use removeLearnedWord, because that simply removes our override and 
            // reverts back to the system dictionary's default high frequency.
            nativeDictionary.addLearnedWord(word, 1)
        }

        // Persist the demotion so it survives process restarts.
        // It will be reloaded into wordData and pushed to the NativeDictionary with freq 1 on boot.
        learnedStore.set(subtype.primaryLocale, word, 1)

        // Force a glide-classifier rebuild so swipe input drops the word too.
        _wordDataDirtyFlow.tryEmit(subtype)
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
     * Persist a `prevWord → nextWord` transition for [locale]. Delegates to
     * [LearnedBigramsStore.bump]; that path is non-blocking and debounces
     * disk writes by 2s under the hood.
     */
    private fun saveLearnedBigram(locale: FlorisLocale, prevWord: String, nextWord: String) {
        learnedBigramsStore.bump(locale, prevWord, nextWord)
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

        // Phase 4d: native suggest path. Active only when the feature flag
        // is on AND the dict actually loaded AND the user has typed
        // something. With a BLANK composing word we'd otherwise call native
        // with an empty prefix → native returns the top-K unigrams by raw
        // frequency (the/of/and/to/…) every time → "too generic, same
        // suggestions at start" bug. For blank composing we instead fall
        // through to the existing bigram-only prediction path below.
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded
                && composingWord.isNotBlank()) {
            val nativeResults = nativeDictionary.getCompletions(
                prefix = composingWord,
                prevWord = previousWord ?: "",
                maxResults = maxCandidateCount,
            )
            if (nativeResults.isNotEmpty()) {
                // Confidence falls off linearly across the top-K so the
                // smartbar's "best guess" highlight lands on the right
                // candidate. Native results are already AOSP-probability-
                // ranked, so position-based confidence preserves that order.
                return nativeResults.mapIndexed { index, word ->
                    val confidence = 1.0 - (index.toDouble() / maxCandidateCount.coerceAtLeast(1))
                    WordSuggestionCandidate(
                        text = word,
                        confidence = confidence.coerceIn(0.0, 1.0),
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                }
            }
            // else fall through to Kotlin path
        }

        // Get bigram suggestions for the previous word
        val bigramSuggestions = if (previousWord != null) {
            getBigramSuggestions(subtype.primaryLocale, previousWord, composingWord)
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
    private suspend fun getBigramSuggestions(
        locale: FlorisLocale,
        previousWord: String,
        composingWord: String,
    ): Map<String, Int> {
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

        // Learned bigrams from the persistent JSON store. Higher weight than
        // static bigrams because the user's own typing history is a stronger
        // signal than corpus frequencies.
        learnedBigramsStore.snapshot(locale)[prev]?.forEach { (word, score) ->
            if (composingWord.isBlank() || word.startsWith(composingWord)) {
                val learnedBoost = (score * 1.2).toInt()
                result[word] = (result.getOrDefault(word, 0) + learnedBoost).coerceAtMost(255)
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
                saveLearnedBigram(subtype.primaryLocale, prev, accepted)
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
        // Recency boost: words the user has typed most recently get a small
        // multiplier so they reliably outrank shape-similar dict words at
        // the same nominal frequency. Without this, after a context reset
        // (Send / clear), a graduated personal word at freq=255 ties with
        // top-tier dict entries also at freq=255 — shape decides — and the
        // user's word often loses by a hair. With the boost, "okays" stays
        // ahead of "okra" / "okay" / "owners" until the user actually starts
        // typing other words.
        //
        // Recency lives in [learnedStore], so this boost is persistent
        // across IME process restarts — not just within a session.
        //
        // This map is consumed exclusively by the glide classifier
        // ([StatisticalGlideTypingClassifier.setWordData]); returning values
        // marginally above 1.0 is intentional and fits the classifier's
        // multiplicative scoring at line 384.
        val recent = learnedStore.mostRecent(subtype.primaryLocale, MAX_RECENCY_WORDS)
        return wordData.withLock { data ->
            data.mapValues { (word, freq) ->
                val base = freq / 255.0
                if (word in recent) base * GLIDE_RECENCY_BOOST else base
            }
        }
    }

    override suspend fun destroy() {
        // No-op
    }
}
