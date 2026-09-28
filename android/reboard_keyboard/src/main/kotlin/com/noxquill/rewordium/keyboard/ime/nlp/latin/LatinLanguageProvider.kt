/*
 * Copyright (C) 2022-2026 The ReBoard Contributors
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
import com.noxquill.rewordium.keyboard.BuildConfig
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.appContext
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.dictionary.DictionaryManager
import com.noxquill.rewordium.keyboard.ime.dictionary.LearnedBigramsStore
import com.noxquill.rewordium.keyboard.ime.dictionary.LearnedWordsStore
import com.noxquill.rewordium.keyboard.ime.editor.EditorContent
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingProvider
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingResult
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionProvider
import com.noxquill.rewordium.keyboard.ime.nlp.TypedWordSuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.WordSuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.engine.ContactsLoader
import com.noxquill.rewordium.keyboard.ime.nlp.engine.NativeDictionary
import com.noxquill.rewordium.keyboard.ime.nlp.engine.TypingProximity
import com.noxquill.rewordium.keyboard.ime.nlp.engine.TypingSuggestion
import com.noxquill.rewordium.keyboard.lib.FlorisLocale
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * English (Latin-script) suggestion + spelling provider.
 *
 * Vocabulary model
 * ----------------
 *  * [baseWords] — the shipped dictionary (`ime/dict/data.json`), ~170k
 *    words in dictionary casing ("I", "London", "iPhone", "don't") with
 *    AOSP-style log-scale frequencies (0..255, each step a factor of 1.15).
 *    Immutable after load.
 *  * [personalWords] — the user's own vocabulary: words typed repeatedly,
 *    accepted after an autocorrect undo, User Dictionary entries and contact
 *    names. Kept separate so personal learning can never corrupt (or
 *    "unlearn") dictionary words.
 *
 * Suggestions and autocorrect run through the AOSP LatinIME typing decoder
 * ([NativeDictionary.suggestForTyping]) fed with real tap coordinates from
 * [TypingProximity]. The Kotlin prefix/edit-distance path remains as a
 * fallback for devices where the native library is unavailable.
 */
class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.latin"
        private const val LEARNED_BIGRAMS_PREFS = "reboard_learned_bigrams"
        private const val MAX_RECENCY_WORDS = 20
        // Multiplicative boost applied to recently-used words in the glide
        // classifier's frequency view.
        private const val GLIDE_RECENCY_BOOST = 1.15

        // Binary cache magic header — bump when the cached format or the
        // meaning of the frequencies changes.
        private const val CACHE_MAGIC = 0x52420003.toInt() // 'RB' + version 3 (AOSP scale)
        private const val WORD_CACHE_NAME = "dict_words_v3.bin"

        private const val WORDLIST_ASSET = "ime/dict/data.json"
        private const val BIGRAMS_ASSET = "ime/dict/bigrams.json"
        private const val OFFENSIVE_ASSET = "ime/dict/offensive.json"

        // Rebuild the glide classifier's index after this many newly
        // graduated personal words (not on every commit — rebuilding walks
        // the whole vocabulary).
        private const val LEARNED_REFRESH_THRESHOLD = 3

        // Personal-vocabulary frequencies on the AOSP scale. A personal word
        // becomes a real (suggestable, never-autocorrected) word after it has
        // been committed twice; its frequency then grows slowly with use but
        // stays below the very common function words ("the" = 222).
        private const val PERSONAL_GRADUATION_COUNT = 2
        private const val PERSONAL_BASE_FREQ = 120
        private const val PERSONAL_MAX_FREQ = 160
        // Explicitly accepted words (autocorrect undone, verbatim suggestion
        // picked, User Dictionary) are trusted immediately.
        private const val ACCEPTED_WORD_COUNT = 12
        // Contact names: known words, a bit rarer than words the user types.
        private const val CONTACT_NAME_FREQ = 130
        // Dictionary words get a small usage-based boost (at most +15 steps).
        private const val MAX_USAGE_BOOST = 15

        private const val LEARN_WORD_MIN_LEN = 2
        private const val LEARN_WORD_MAX_LEN = 40
        // Letters + apostrophe only — exclude URLs, numbers, symbols.
        private val LEARN_WORD_PATTERN = Regex("^[\\p{L}'’]+$")

        // AOSP LatinIME auto-correction thresholds (config-auto-correction-thresholds.xml).
        const val AUTOCORRECT_THRESHOLD_MODEST = 0.185f
        const val AUTOCORRECT_THRESHOLD_AGGRESSIVE = 0.067f

        // Abbreviations at least this common may replace their lowercase
        // spelling ("usa" -> "USA"); rarer ones never do ("nad" -> "NAD").
        private const val COMMON_ABBREVIATION_FREQ = 130

        /** Letters directly after these belong to a token, not to prose ("B.Sc", "site.com", "#tbt"). */
        private val ATTACHING_CHARS = setOf('.', '@', '#', '/', '\\', '_', ':', '=', '&', '+', '~', '$', '%')

        // Spelling fallback: only common words are targets, and only for
        // longer inputs (short words are too easy to mangle).
        private const val SPELL_FALLBACK_MIN_FREQ = 100
        private const val SPELL_FALLBACK_MIN_FREQ_TWO_EDITS = 110
        private const val SPELL_FALLBACK_MIN_LENGTH = 6
        // Near-equal decoder candidates: prefer one at least this much more
        // common (10 steps on the AOSP scale is ~4x).
        private const val TIE_BREAK_SCORE_RATIO = 0.88f
        private const val TIE_BREAK_MIN_FREQ_GAIN = 10

        private const val NATIVE_MAX_RESULTS = 12
        private const val DISPLAY_MAX_RESULTS = 8

        private const val NLP_STATE_PREFS = "reboard_nlp_state"
        private const val LEGACY_VOCAB_CLEANED_KEY = "legacy_personal_vocab_cleaned_v1"
    }

    override val providerId = ProviderId

    private val appContext by context.appContext()
    private val prefs by FlorisPreferenceStore

    private val wordMapSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val bigramSerializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), Int.serializer()))
    private val json = Json { ignoreUnknownKeys = true }

    /** Shipped dictionary, dictionary casing -> frequency. Immutable after load. */
    @Volatile private var baseWords: Map<String, Int> = emptyMap()
    /** lowercase -> dictionary casing, for words whose canonical form has capitals. */
    @Volatile private var lowerIndex: Map<String, String> = emptyMap()
    @Volatile private var offensiveWords: Set<String> = emptySet()
    /** Common lowercase dictionary words by first letter, for the spelling fallback. */
    @Volatile private var spellIndex: Map<Char, List<String>> = emptyMap()
    /** Static bigrams, dictionary casing: previous word -> (next word -> score). */
    @Volatile private var bigramData: Map<String, Map<String, Int>> = emptyMap()

    /** Personal vocabulary that is "real" (graduated / accepted / contacts / user dict) -> frequency. */
    private val personalWords = ConcurrentHashMap<String, Int>()
    /** Words the user undid an autocorrection for during this session. */
    private val sessionAccepted = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // User-learned bigrams: persisted per-locale via [LearnedBigramsStore].
    private val learnedBigramsStore = LearnedBigramsStore(context)
    private var legacyLearnedBigramsPrefs: SharedPreferences? = null
    @Volatile private var lastCommittedWord: String? = null

    // Per-user usage counts persisted across restarts (lowercase keys).
    private val learnedStore = LearnedWordsStore(context)
    private val learnedSinceLastRefresh = AtomicInteger(0)

    /** Set by NlpManager per editor: false in URL / e-mail / password / filter fields. */
    @Volatile var autocorrectAllowedForField: Boolean = true

    /** AOSP typing decoder dictionary. */
    internal val nativeDictionary = NativeDictionary()

    private val _wordDataDirtyFlow = MutableSharedFlow<Subtype>(replay = 0, extraBufferCapacity = 1)
    /** Emitted when vocabulary changes warrant a glide-classifier rebuild. */
    val wordDataDirtyFlow: SharedFlow<Subtype> = _wordDataDirtyFlow.asSharedFlow()

    override suspend fun create() {
        // No-op
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        if (baseWords.isEmpty()) {
            // Caches written by versions before the AOSP-scale dictionary.
            File(cacheDir(appContext), "dict_words.bin").delete()
            File(cacheDir(appContext), "dict_bigrams.bin").delete()
            val loaded = loadWordCache(appContext) ?: run {
                val parsed = json.decodeFromString(wordMapSerializer, appContext.assets.readText(WORDLIST_ASSET))
                saveWordCache(appContext, parsed)
                parsed
            }
            val index = HashMap<String, String>()
            for ((word, freq) in loaded) {
                if (word.any { it.isUpperCase() }) {
                    val lower = word.lowercase()
                    if (lower in loaded) continue
                    val existing = index[lower]
                    if (existing == null || (loaded[existing] ?: 0) < freq) index[lower] = word
                }
            }
            lowerIndex = index
            baseWords = loaded
            spellIndex = buildSpellIndex(loaded)
            offensiveWords = runCatching {
                json.decodeFromString(ListSerializer(String.serializer()), appContext.assets.readText(OFFENSIVE_ASSET)).toHashSet()
            }.getOrElse { emptySet() }
            bigramData = runCatching {
                json.decodeFromString(bigramSerializer, appContext.assets.readText(BIGRAMS_ASSET))
            }.getOrElse { e ->
                flogDebug { "Failed to load bigrams: ${e.message}" }
                emptyMap()
            }
        }

        if (legacyLearnedBigramsPrefs == null) {
            legacyLearnedBigramsPrefs = appContext.getSharedPreferences(LEARNED_BIGRAMS_PREFS, Context.MODE_PRIVATE)
        }
        learnedBigramsStore.ensureLoaded(legacyLearnedBigramsPrefs)
        learnedStore.ensureLoaded()

        rebuildPersonalWords(subtype)

        if (BuildConfig.ENABLE_NATIVE_SUGGESTER) {
            runCatching {
                if (!nativeDictionary.isLoaded) {
                    val words = HashMap<String, Int>(baseWords.size + personalWords.size)
                    words.putAll(baseWords)
                    for ((word, freq) in personalWords) {
                        words[word] = max(words[word] ?: 0, freq)
                    }
                    applyUsageBoosts(subtype, words)
                    val ok = nativeDictionary.load(words, offensiveWords, mergedBigramsForNative(subtype))
                    flogDebug { "LatinLanguageProvider: native dict load = $ok" }
                } else {
                    for ((word, freq) in personalWords) nativeDictionary.addLearnedWord(word, freq)
                }
                cleanLegacyPersonalVocabulary(subtype)
            }.onFailure { e -> flogDebug { "LatinLanguageProvider: native dict load threw: $e" } }
        }
        _wordDataDirtyFlow.tryEmit(subtype)
        Unit
    }

    private fun buildSpellIndex(words: Map<String, Int>): Map<Char, List<String>> {
        val index = HashMap<Char, ArrayList<String>>()
        for ((word, freq) in words) {
            if (freq < SPELL_FALLBACK_MIN_FREQ || word.length < 4) continue
            if (!word.all { it.isLowerCase() || it == '\'' }) continue
            index.getOrPut(word[0]) { ArrayList() }.add(word)
        }
        return index
    }

    /** Rebuilds [personalWords] from the learned store, User Dictionary and contacts. */
    private suspend fun rebuildPersonalWords(subtype: Subtype) {
        for ((word, entry) in learnedStore.snapshot(subtype.primaryLocale)) {
            if (canonicalDictionaryForm(word) != null) continue
            personalFreqForCount(entry.f)?.let { personalWords[word] = it }
        }
        runCatching {
            val dao = DictionaryManager.default().florisUserDictionaryDao() ?: return@runCatching
            for (entry in dao.queryAll()) {
                val word = entry.word.trim()
                if (word.isEmpty() || !LEARN_WORD_PATTERN.matches(word)) continue
                if (canonicalDictionaryForm(word) == word) continue
                personalWords[word] = max(personalWords[word] ?: 0, PERSONAL_MAX_FREQ)
            }
        }.onFailure { e -> flogDebug { "Failed to merge user dictionary: ${e.message}" } }
        if (prefs.spelling.useContacts.get()) {
            for (token in ContactsLoader.loadNameTokens(appContext)) {
                if (canonicalDictionaryForm(token) != null) continue
                personalWords[token] = max(personalWords[token] ?: 0, CONTACT_NAME_FREQ)
            }
        }
    }

    /**
     * One-time migration. Before autocorrect existed, every typo typed twice
     * ("dont", "teh", "becuase") graduated into the personal vocabulary and
     * would now be treated as a deliberate word — so existing users would
     * never see those corrected. Drop learned non-dictionary words that the
     * decoder confidently corrects; genuine personal words (names, slang)
     * have no confident correction and are kept.
     */
    private fun cleanLegacyPersonalVocabulary(subtype: Subtype) {
        if (!nativeDictionary.isLoaded) return
        val state = appContext.getSharedPreferences(NLP_STATE_PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean(LEGACY_VOCAB_CLEANED_KEY, false)) return
        var removed = 0
        for ((word, entry) in learnedStore.snapshot(subtype.primaryLocale)) {
            if (entry.f < PERSONAL_GRADUATION_COUNT || canonicalDictionaryForm(word) != null) continue
            val ctx = TypingContext(word, null, atSentenceStart = false)
            val results = decodeTyping(ctx, NATIVE_MAX_RESULTS) ?: return
            val correction = decideAutocorrect(
                ctx = ctx,
                typedKnown = false,
                results = results,
                threshold = AUTOCORRECT_THRESHOLD_MODEST,
                respectPersonal = false,
            )
            if (correction != null && !correction.equals(word, ignoreCase = true)) {
                learnedStore.set(subtype.primaryLocale, word, 0)
                personalWords.remove(word)
                nativeDictionary.removeLearnedWord(word)
                removed++
            }
        }
        state.edit().putBoolean(LEGACY_VOCAB_CLEANED_KEY, true).apply()
        flogDebug { "LatinLanguageProvider: removed $removed legacy typos from personal vocabulary" }
    }

    /** Small, bounded boost for dictionary words the user types often. */
    private fun applyUsageBoosts(subtype: Subtype, words: MutableMap<String, Int>) {
        for ((word, entry) in learnedStore.snapshot(subtype.primaryLocale)) {
            val canonical = canonicalDictionaryForm(word) ?: continue
            val base = baseWords[canonical] ?: continue
            val boost = min(MAX_USAGE_BOOST, entry.f / 4)
            if (boost > 0) words[canonical] = min(255, base + boost)
        }
    }

    private fun mergedBigramsForNative(subtype: Subtype): Map<String, Map<String, Int>> {
        val merged = HashMap<String, MutableMap<String, Int>>()
        for ((prev, next) in bigramData) merged[prev] = HashMap(next)
        for ((prev, next) in learnedBigramsStore.snapshot(subtype.primaryLocale)) { // once per load
            val cPrev = canonicalForm(prev) ?: continue
            val inner = merged.getOrPut(cPrev) { HashMap() }
            for ((word, score) in next) {
                val cWord = canonicalForm(word) ?: continue
                // Learned transitions are strong evidence of the user's phrasing.
                inner[cWord] = max(inner[cWord] ?: 0, (150 + score).coerceAtMost(240))
            }
        }
        return merged
    }

    private fun personalFreqForCount(count: Int): Int? {
        if (count < PERSONAL_GRADUATION_COUNT) return null
        return (PERSONAL_BASE_FREQ + (count - PERSONAL_GRADUATION_COUNT) * 2).coerceAtMost(PERSONAL_MAX_FREQ)
    }

    // ── Vocabulary lookups ───────────────────────────────────────────────────

    /** Dictionary casing of [word] if it is a dictionary word (case-insensitively), else null. */
    private fun canonicalDictionaryForm(word: String): String? {
        val base = baseWords
        if (word in base) return word
        val lower = word.lowercase()
        if (lower in base) return lower
        return lowerIndex[lower]
    }

    /** Canonical casing of [word] in the full vocabulary (dictionary or personal), else null. */
    private fun canonicalForm(word: String): String? {
        canonicalDictionaryForm(word)?.let { return it }
        if (personalWords.containsKey(word)) return word
        val lower = word.lowercase()
        return if (personalWords.containsKey(lower)) lower else null
    }

    /**
     * True when [typed] is a word the user legitimately means as typed, so it
     * must never be autocorrected: an exact dictionary word, a dictionary
     * word capitalised at the start of a sentence ("Hello"), a personal word,
     * a contact or a word the user explicitly kept this session. Note that a
     * lowercase proper noun ("london") is *not* valid — correcting it to
     * "London" is exactly what Gboard does.
     */
    fun isKnownWord(typed: String): Boolean {
        if (typed.isEmpty()) return false
        val base = baseWords
        if (typed in base) return true
        val lower = typed.lowercase()
        if (typed.length > 1 && typed[0].isUpperCase() && typed.substring(1) == typed.substring(1).lowercase()) {
            if (lower in base) return true
        }
        if (personalWords.containsKey(typed) || personalWords.containsKey(lower)) return true
        return lower in sessionAccepted
    }

    private fun frequencyOf(word: String): Int {
        return personalWords[word] ?: baseWords[word] ?: 0
    }

    // ── Personal learning ────────────────────────────────────────────────────

    /**
     * Adaptive learning entry point. Called from EditorInstance.commitChar
     * (on space/punctuation) and KeyboardManager.commitGesture (on glide
     * commit) via NlpManager.learnWord. Hot path — map updates only.
     */
    suspend fun learnWord(subtype: Subtype, rawWord: String) {
        if (!prefs.dictionary.learnPersonalWords.get()) return
        val word = rawWord.trim()
        if (word.length < LEARN_WORD_MIN_LEN || word.length > LEARN_WORD_MAX_LEN) return
        if (!LEARN_WORD_PATTERN.matches(word)) return
        val key = word.lowercase()
        learnedStore.bump(subtype.primaryLocale, key, freqDelta = 1)
        if (canonicalDictionaryForm(word) != null) return
        val count = learnedStore.countOf(subtype.primaryLocale, key)
        val freq = personalFreqForCount(count) ?: return
        val graduated = !personalWords.containsKey(key)
        personalWords[key] = freq
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
            nativeDictionary.addLearnedWord(key, freq)
        }
        if (graduated || learnedSinceLastRefresh.incrementAndGet() >= LEARNED_REFRESH_THRESHOLD) {
            learnedSinceLastRefresh.set(0)
            _wordDataDirtyFlow.tryEmit(subtype)
        }
    }

    /**
     * Called when the user immediately deletes a word they just committed.
     * Undoes the usage bump of that commit. Dictionary words are never
     * demoted — backspacing to fix punctuation must not make the keyboard
     * forget "hello".
     */
    suspend fun unlearnWord(subtype: Subtype, rawWord: String) {
        val word = rawWord.trim()
        if (word.length < LEARN_WORD_MIN_LEN || word.length > LEARN_WORD_MAX_LEN) return
        if (!LEARN_WORD_PATTERN.matches(word)) return
        val key = word.lowercase()
        learnedStore.bump(subtype.primaryLocale, key, freqDelta = -1)
        if (canonicalDictionaryForm(word) != null) return
        val count = learnedStore.countOf(subtype.primaryLocale, key)
        if (personalFreqForCount(count) == null && personalWords.remove(key) != null) {
            if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
                nativeDictionary.removeLearnedWord(key)
            }
            _wordDataDirtyFlow.tryEmit(subtype)
        }
    }

    /**
     * The user explicitly kept [rawWord] as typed — undid an autocorrection
     * or picked the verbatim suggestion. Trust it immediately (and for all
     * future sessions) so it is never autocorrected again.
     */
    suspend fun acceptTypedWord(subtype: Subtype, rawWord: String, persist: Boolean = true) {
        val word = rawWord.trim()
        if (word.isEmpty() || word.length > LEARN_WORD_MAX_LEN) return
        val key = word.lowercase()
        sessionAccepted.add(key)
        // Incognito: respect the choice for this session only, store nothing.
        if (!persist) return
        if (!LEARN_WORD_PATTERN.matches(word) || canonicalDictionaryForm(word) != null) return
        if (!prefs.dictionary.learnPersonalWords.get()) return
        learnedStore.set(subtype.primaryLocale, key, ACCEPTED_WORD_COUNT)
        val freq = personalFreqForCount(ACCEPTED_WORD_COUNT) ?: PERSONAL_BASE_FREQ
        personalWords[key] = freq
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
            nativeDictionary.addLearnedWord(key, freq)
        }
        _wordDataDirtyFlow.tryEmit(subtype)
    }

    /**
     * Hot-reload contact name tokens after READ_CONTACTS is granted at
     * runtime. Does not require an IME restart.
     */
    suspend fun reloadContacts(subtype: Subtype) = withContext(Dispatchers.IO) {
        if (!prefs.spelling.useContacts.get()) return@withContext
        val tokens = ContactsLoader.loadNameTokens(appContext)
        if (tokens.isEmpty()) return@withContext
        var added = 0
        for (token in tokens) {
            if (canonicalDictionaryForm(token) != null) continue
            personalWords[token] = max(personalWords[token] ?: 0, CONTACT_NAME_FREQ)
            if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded &&
                nativeDictionary.addLearnedWord(token, CONTACT_NAME_FREQ)
            ) {
                added++
            }
        }
        flogDebug { "LatinLanguageProvider: reloadContacts added $added tokens" }
        _wordDataDirtyFlow.tryEmit(subtype)
    }

    /**
     * Live import of a single User Dictionary entry (Settings → User
     * Dictionary) so it works for typing and glide without an IME restart.
     */
    suspend fun importUserDictionaryEntry(subtype: Subtype, rawWord: String, freq: Int) {
        val word = rawWord.trim()
        if (word.length < LEARN_WORD_MIN_LEN || word.length > LEARN_WORD_MAX_LEN) return
        if (!LEARN_WORD_PATTERN.matches(word)) return
        if (canonicalDictionaryForm(word) == word) return
        val target = (PERSONAL_BASE_FREQ + freq / 8).coerceIn(PERSONAL_BASE_FREQ, PERSONAL_MAX_FREQ)
        personalWords[word] = max(personalWords[word] ?: 0, target)
        if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
            nativeDictionary.addLearnedWord(word, target)
        }
        _wordDataDirtyFlow.tryEmit(subtype)
    }

    // ── Binary dictionary cache ──────────────────────────────────────────────

    private fun cacheDir(ctx: Context): File = ctx.cacheDir.also { it.mkdirs() }

    /**
     * Identifies the installed APK. The version code alone is not enough:
     * builds that share one (test builds before a release) can ship a
     * different dictionary, and a stale cache would silently keep the old one.
     */
    @Suppress("DEPRECATION")
    private fun dictionaryCacheStamp(ctx: Context): Long = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        (versionCode shl 42) xor info.lastUpdateTime
    } catch (_: Exception) { 0L }

    /**
     * Loads word frequencies from the binary cache. Returns null on cache
     * miss, version mismatch, corrupt data, or I/O failure.
     * Format: magic(Int) + dictionaryCacheStamp(Long) + count(Int) + [word(UTF) + score(Int)]*
     */
    private fun loadWordCache(ctx: Context): Map<String, Int>? {
        val file = File(cacheDir(ctx), WORD_CACHE_NAME)
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream(), 65_536)).use { dis ->
                if (dis.readInt() != CACHE_MAGIC) return null
                if (dis.readLong() != dictionaryCacheStamp(ctx)) return null
                val count = dis.readInt()
                val map = HashMap<String, Int>(count * 2)
                repeat(count) {
                    val word = dis.readUTF()
                    map[word] = dis.readInt()
                }
                map
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun saveWordCache(ctx: Context, data: Map<String, Int>) {
        try {
            val file = File(cacheDir(ctx), WORD_CACHE_NAME)
            DataOutputStream(BufferedOutputStream(file.outputStream(), 65_536)).use { dos ->
                dos.writeInt(CACHE_MAGIC)
                dos.writeLong(dictionaryCacheStamp(ctx))
                dos.writeInt(data.size)
                for ((word, score) in data) {
                    dos.writeUTF(word)
                    dos.writeInt(score)
                }
            }
        } catch (_: Exception) { /* non-fatal — next start will retry */ }
    }

    // ── Context helpers ──────────────────────────────────────────────────────

    /** Last word of [textBeforeCursor] with surrounding punctuation stripped, case preserved. */
    private fun extractPreviousWord(textBeforeCursor: CharSequence): String? {
        val text = textBeforeCursor.toString().trimEnd()
        if (text.isBlank()) return null
        val lastSpace = text.lastIndexOf(' ')
        val word = if (lastSpace >= 0) text.substring(lastSpace + 1) else text
        val cleaned = word.trim { !it.isLetter() && it != '\'' && it != '’' }
        return cleaned.takeIf { it.isNotEmpty() }
    }

    private fun isSentenceStart(textBeforeWord: CharSequence): Boolean {
        val trimmed = textBeforeWord.toString().trimEnd()
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        return last == '.' || last == '!' || last == '?' || last == '\n'
    }

    private class TypingContext(
        val typed: String,
        val previousWord: String?,
        val atSentenceStart: Boolean,
        /** Attached to the text before it ("B.Tech", "e.g", "site.com", "#tag", "@name"). */
        val attachedToPrevious: Boolean = false,
    )

    private fun typingContextOf(content: EditorContent): TypingContext? {
        val typed = content.composingText.ifBlank { content.currentWordText }.trim()
        if (typed.isEmpty()) return null
        val before = content.textBeforeSelection
        val beforeWord = if (before.endsWith(typed)) before.substring(0, before.length - typed.length) else before
        // A previous word only counts as context within the same sentence.
        val sentenceStart = isSentenceStart(beforeWord)
        val previous = if (sentenceStart) null else extractPreviousWord(beforeWord)
        val attached = beforeWord.isNotEmpty() && beforeWord.last().let { it.isDigit() || it in ATTACHING_CHARS }
        return TypingContext(typed, previous, sentenceStart, attached)
    }

    /**
     * Whether the word being composed may be autocorrected when [separator]
     * is typed after it. A single letter before a period is an initial or
     * part of an abbreviation ("B. Edward", "B.Tech", "i.e.", "e.g."), so it
     * is kept exactly as typed - including "i", which is otherwise "I".
     */
    fun allowsAutocorrectBefore(content: EditorContent, separator: String): Boolean {
        if (separator != ".") return true
        val typed = content.composingText.ifBlank { content.currentWordText }.trim()
        return typed.length != 1
    }

    // ── Autocorrect ──────────────────────────────────────────────────────────

    private fun autocorrectThreshold(): Float? {
        if (!prefs.correction.autoCorrect.get() || !autocorrectAllowedForField) return null
        return when (prefs.correction.autoCorrectLevel.get()) {
            AutoCorrectLevel.MODEST -> AUTOCORRECT_THRESHOLD_MODEST
            AutoCorrectLevel.AGGRESSIVE -> AUTOCORRECT_THRESHOLD_AGGRESSIVE
        }
    }

    /** Applies the typed word's leading capital to [word] ("Teh" -> "The"). */
    private fun applyTypedCase(typed: String, word: String): String {
        if (typed.isEmpty() || word.isEmpty()) return word
        if (typed[0].isUpperCase() && word[0].isLowerCase()) {
            return word.replaceFirstChar { it.titlecase() }
        }
        return word
    }

    private fun isCorrectableInput(typed: String): Boolean {
        if (typed.length > 32) return false
        if (typed.any { it.isDigit() }) return false
        if (!typed.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }) return false
        // Mostly-caps words ("NASA", "OMG") are deliberate.
        if (typed.length > 1 && typed.count { it.isUpperCase() } * 2 > typed.length) return false
        return true
    }

    /**
     * AOSP LatinIME's auto-correction decision, extended with explicit rules
     * and a "clear winner" rule for long, badly mangled words. Returns the
     * replacement text (already cased like the input) or null to keep the
     * word as typed.
     */
    private fun decideAutocorrect(
        ctx: TypingContext,
        typedKnown: Boolean,
        results: List<TypingSuggestion>,
        threshold: Float?,
        respectPersonal: Boolean = true,
    ): String? {
        if (threshold == null) return null
        val typed = ctx.typed
        if (!isCorrectableInput(typed) || ctx.attachedToPrevious) return null
        val lower = typed.lowercase()
        if (respectPersonal && (lower in sessionAccepted || personalWords.containsKey(typed) || personalWords.containsKey(lower))) {
            return null
        }
        AutocorrectRules.replacementFor(lower, ctx.previousWord?.lowercase() ?: "", ctx.atSentenceStart)?.let {
            return applyTypedCase(typed, it)
        }
        if (typedKnown) return null
        // Single letters are initials, list markers or shorthand ("B", "u",
        // "r") - like Gboard, never turn them into a word ("B" -> "By").
        if (typed.length == 1) return null
        val top = pickCorrectionTarget(typed, results)
            ?: return spellingFallback(typed)?.let { applyTypedCase(typed, it) }
        if (!top.isAppropriateForAutoCorrection) return null
        // Completing a word on space is fine when one letter is missing
        // ("becaus" -> "because"); longer extensions turn names and new
        // words into something else ("srinivas" -> "srinivasan").
        if (top.word.lowercase().startsWith(lower) && top.word.length - typed.length >= 2) return null
        if (top.normalizedScore >= threshold) return applyTypedCase(typed, top.word)
        // Clear winner: a long word whose best correction is a single word two
        // edits away and far ahead of every alternative ("tommorow").
        if (typed.length >= 6 && ' ' !in top.word && top.normalizedScore >= 0.10f &&
            top.word[0].equals(typed[0], ignoreCase = true) &&
            damerauLevenshtein(lower, top.word.lowercase()) <= 2
        ) {
            val runnerUp = results.firstOrNull {
                it !== top && it.word != typed && !it.word.equals(top.word, ignoreCase = true)
            }
            if (runnerUp == null || top.normalizedScore >= 4f * runnerUp.normalizedScore) {
                return applyTypedCase(typed, top.word)
            }
        }
        if (top.normalizedScore < 0.10f) {
            return spellingFallback(typed)?.let { applyTypedCase(typed, it) }
        }
        return null
    }

    /**
     * AOSP's typing decoder only explores substitutions between neighbouring
     * keys, so phonetic misspellings ("calander", "desicion", "expierence")
     * are invisible to it. For longer words it found nothing confident for,
     * look for a common dictionary word with the same first letter one edit
     * away (two for 8+ letters) and accept only a unique, clearly more
     * frequent winner.
     */
    private fun spellingFallback(typed: String): String? {
        val lower = typed.lowercase()
        val n = lower.length
        if (n < SPELL_FALLBACK_MIN_LENGTH) return null
        val maxDistance = if (n >= 8) 2 else 1
        val bucket = spellIndex[lower[0]] ?: return null
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var bestFreq = -1
        var secondFreq = -1
        for (word in bucket) {
            if (abs(word.length - n) > maxDistance) continue
            val distance = damerauLevenshtein(lower, word)
            if (distance > maxDistance) continue
            val freq = baseWords[word] ?: continue
            if (distance == 2 && freq < SPELL_FALLBACK_MIN_FREQ_TWO_EDITS) continue
            if (distance < bestDistance) {
                secondFreq = -1
                bestDistance = distance
                best = word
                bestFreq = freq
            } else if (distance == bestDistance) {
                if (freq > bestFreq) {
                    secondFreq = bestFreq
                    best = word
                    bestFreq = freq
                } else if (freq > secondFreq) {
                    secondFreq = freq
                }
            }
        }
        // A unique, clear winner only: nothing else at the same distance within ~4x.
        if (secondFreq >= 0 && bestFreq - secondFreq < TIE_BREAK_MIN_FREQ_GAIN) return null
        return best
    }

    private fun isAllCaps(word: String): Boolean {
        return word.length >= 2 && word.any { it.isLetter() } && word.all { !it.isLetter() || it.isUpperCase() }
    }

    /**
     * Chooses which decoder result a typo may be replaced with. AOSP ranks
     * purely by score; on top of that:
     *  * an ALL-CAPS abbreviation is only a target when it is just the
     *    capitalisation of the input and a common one ("usa" -> "USA",
     *    "tv" -> "TV"), never for "nad" -> "NAD" when "and" was meant;
     *  * a lowercase word beats its capitalised twin at a near-equal score
     *    ("parliment" -> "parliament", not "Parliament");
     *  * a plain plural beats a possessive at a comparable score
     *    ("potatos" -> "potatoes", not "potato's");
     *  * among near-equal candidates a much more common word wins
     *    ("suer" -> "sure", not "sued").
     */
    private fun pickCorrectionTarget(typed: String, results: List<TypingSuggestion>): TypingSuggestion? {
        val candidates = results.filter { it.word != typed }
        fun acceptable(candidate: TypingSuggestion): Boolean {
            val word = candidate.word
            if (!isAllCaps(word) || isAllCaps(typed)) return true
            return word.equals(typed, ignoreCase = true) && frequencyOf(word) >= COMMON_ABBREVIATION_FREQ
        }
        var top = candidates.firstOrNull { acceptable(it) } ?: return null
        if (typed[0].isLowerCase() && top.word[0].isUpperCase() && !isAllCaps(top.word)) {
            val lowerTwin = top.word.lowercase()
            candidates.firstOrNull {
                it.word == lowerTwin && it.normalizedScore >= 0.9f * top.normalizedScore
            }?.let { top = it }
        }
        if (top.word.endsWith("'s") && typed.none { it == '\'' || it == '’' }) {
            candidates.firstOrNull {
                it !== top && it.word.none { c -> c == '\'' || c == ' ' } && acceptable(it) &&
                    it.normalizedScore >= 0.75f * top.normalizedScore
            }?.let { top = it }
        }
        var preferred = top
        var preferredFreq = frequencyOf(top.word)
        for (candidate in candidates) {
            if (candidate === top || ' ' in candidate.word) continue
            if (candidate.normalizedScore < TIE_BREAK_SCORE_RATIO * top.normalizedScore) continue
            if (!acceptable(candidate) || !candidate.isAppropriateForAutoCorrection) continue
            val freq = frequencyOf(candidate.word)
            if (freq >= preferredFreq + TIE_BREAK_MIN_FREQ_GAIN) {
                preferred = candidate
                preferredFreq = freq
            }
        }
        // The winner inherits the leader's confidence for the threshold check.
        if (preferred !== top) {
            top = preferred.copy(normalizedScore = max(preferred.normalizedScore, top.normalizedScore))
        }
        return top
    }

    private fun decodeTyping(ctx: TypingContext, maxResults: Int): List<TypingSuggestion>? {
        if (!BuildConfig.ENABLE_NATIVE_SUGGESTER || !nativeDictionary.isLoaded) return null
        val prev = ctx.previousWord?.let { canonicalForm(it) ?: it } ?: ""
        val blockOffensive = prefs.suggestion.blockPossiblyOffensive.get()
        return TypingProximity.withCoordinates(ctx.typed) { handle, xs, ys ->
            nativeDictionary.suggestForTyping(handle, ctx.typed, xs, ys, prev, blockOffensive, maxResults)
        }
    }

    /**
     * Synchronous autocorrect decision for exactly the current composing word.
     * Used by NlpManager when the space/punctuation key arrives before the
     * debounced suggestion pass for the latest letter has finished — without
     * this, fast typists would get the correction computed for the *previous*
     * prefix ("thi" → "this" applied to "thin").
     */
    fun autocorrectFor(content: EditorContent): SuggestionCandidate? {
        val threshold = autocorrectThreshold() ?: return null
        val ctx = typingContextOf(content) ?: return null
        val results = decodeTyping(ctx, NATIVE_MAX_RESULTS) ?: emptyList()
        val typedKnown = isKnownWord(ctx.typed)
        val correction = decideAutocorrect(ctx, typedKnown, results, threshold) ?: return null
        if (correction == ctx.typed) return null
        return WordSuggestionCandidate(
            text = correction,
            confidence = 1.0,
            isEligibleForAutoCommit = true,
            sourceProvider = this,
        )
    }

    // ── SuggestionProvider / SpellingProvider ────────────────────────────────

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val ctx = typingContextOf(content)
        if (ctx == null || content.composingText.isBlank() && content.currentWordText.isBlank()) {
            return nextWordPredictions(subtype, content.textBeforeSelection, maxCandidateCount)
        }
        val results = decodeTyping(ctx, NATIVE_MAX_RESULTS)
        if (results == null) {
            return kotlinSuggestions(subtype, ctx, maxCandidateCount)
        }
        val typed = ctx.typed
        val typedKnown = isKnownWord(typed)
        val correction = decideAutocorrect(ctx, typedKnown, results, autocorrectThreshold())
            ?.takeIf { it != typed }

        val out = ArrayList<SuggestionCandidate>(DISPLAY_MAX_RESULTS)
        val seen = HashSet<String>()
        fun add(candidate: SuggestionCandidate) {
            if (out.size < DISPLAY_MAX_RESULTS && seen.add(candidate.text.toString())) out.add(candidate)
        }
        if (correction != null) {
            // Gboard layout: bold correction in the centre, the literal input
            // on the left so one tap keeps what was typed.
            add(WordSuggestionCandidate(correction, confidence = 1.0, isEligibleForAutoCommit = true, sourceProvider = this))
            add(TypedWordSuggestionCandidate(typed, confidence = 0.99, sourceProvider = this))
        } else if (typedKnown) {
            add(WordSuggestionCandidate(typed, confidence = 1.0, sourceProvider = this))
        }
        var rank = 0
        for (result in results) {
            if (result.word == typed && typedKnown) continue
            val shown = applyTypedCase(typed, result.word)
            if (shown == typed && !typedKnown) continue
            val confidence = (0.9 - rank * 0.05).coerceAtLeast(0.1)
            add(WordSuggestionCandidate(shown, confidence = confidence, sourceProvider = this))
            rank++
        }
        if (out.size < 3) {
            // Thin decoder output (e.g. first letter only): pad with prefix
            // completions ranked by the n-gram model.
            val prev = ctx.previousWord?.let { canonicalForm(it) ?: it } ?: ""
            for (word in nativeDictionary.getCompletions(typed.lowercase(), prev, maxCandidateCount)) {
                add(WordSuggestionCandidate(applyTypedCase(typed, word), confidence = 0.3, sourceProvider = this))
            }
        }
        return out
    }

    /** Next-word predictions after a space, from static + learned bigrams. */
    private suspend fun nextWordPredictions(
        subtype: Subtype,
        textBeforeCursor: CharSequence,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        if (isSentenceStart(textBeforeCursor)) return emptyList()
        val previous = extractPreviousWord(textBeforeCursor) ?: return emptyList()
        val bigrams = getBigramSuggestions(subtype.primaryLocale, previous, "")
        if (bigrams.isEmpty()) return emptyList()
        return bigrams.entries
            .sortedByDescending { it.value }
            .take(maxCandidateCount)
            .map { (word, score) ->
                WordSuggestionCandidate(
                    text = word,
                    confidence = (score / 255.0).coerceIn(0.0, 1.0),
                    sourceProvider = this,
                )
            }
    }

    /**
     * Fallback when the native decoder is unavailable (32-bit devices, native
     * load failure): prefix completions plus single-edit fuzzy matches. Never
     * auto-commits — without the spatial model we are not confident enough.
     */
    private suspend fun kotlinSuggestions(
        subtype: Subtype,
        ctx: TypingContext,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        val typed = ctx.typed
        val lower = typed.lowercase()
        val bigrams = ctx.previousWord?.let { getBigramSuggestions(subtype.primaryLocale, it, lower) } ?: emptyMap()
        val results = HashMap<String, Int>()
        val known = isKnownWord(typed)
        if (known) results[typed] = 1000
        fun consider(word: String, freq: Int) {
            if (freq <= 0) return
            val wl = word.lowercase()
            if (wl.startsWith(lower) && wl != lower) {
                results[word] = max(results[word] ?: 0, freq + (bigrams[word] ?: 0))
            } else if (lower.length >= 3 && wl.length in (lower.length - 1)..(lower.length + 1) &&
                wl != lower && isEditDistance1(lower, wl)
            ) {
                results[word] = max(results[word] ?: 0, (freq * 0.8).toInt() + (bigrams[word] ?: 0) / 2)
            }
        }
        for ((word, freq) in baseWords) consider(word, freq)
        for ((word, freq) in personalWords) consider(word, freq)
        return results.entries
            .sortedByDescending { it.value }
            .take(maxCandidateCount)
            .mapIndexed { index, (word, _) ->
                WordSuggestionCandidate(
                    text = applyTypedCase(typed, word),
                    confidence = (1.0 - index * 0.1).coerceAtLeast(0.1),
                    sourceProvider = this,
                )
            }
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
        val query = word.trim()
        if (query.isBlank()) return SpellingResult.unspecified()
        if (isKnownWord(query) || !isCorrectableInput(query)) return SpellingResult.validWord()

        val ctx = TypingContext(query, precedingWords.lastOrNull(), precedingWords.isEmpty())
        val native = decodeTyping(ctx, maxSuggestionCount.coerceAtLeast(3) + 2)
        if (native != null) {
            val suggestions = native.asSequence()
                .map { applyTypedCase(query, it.word) }
                .filter { it != query }
                .distinct()
                .take(maxSuggestionCount)
                .toList()
            return SpellingResult.typo(suggestions.toTypedArray())
        }

        val lower = query.lowercase()
        val candidates = baseWords.asSequence()
            .filter { (candidate, freq) ->
                freq > 0 && candidate.length in (lower.length - 1)..(lower.length + 1) &&
                    isEditDistance1(lower, candidate.lowercase())
            }
            .sortedByDescending { (_, freq) -> freq }
            .map { applyTypedCase(query, it.key) }
            .take(maxSuggestionCount)
            .toList()
        return SpellingResult.typo(candidates.toTypedArray())
    }

    /**
     * True when [a] and [b] are exactly one substitution, insertion or
     * deletion apart.
     */
    private fun isEditDistance1(a: String, b: String): Boolean {
        val lenDiff = a.length - b.length
        if (lenDiff < -1 || lenDiff > 1) return false
        if (a.length == b.length) {
            var diffs = 0
            for (i in a.indices) {
                if (a[i] != b[i]) { diffs++; if (diffs > 1) return false }
            }
            return diffs == 1
        }
        val longer = if (a.length > b.length) a else b
        val shorter = if (a.length > b.length) b else a
        var i = 0; var j = 0; var diffs = 0
        while (i < longer.length && j < shorter.length) {
            if (longer[i] != shorter[j]) { diffs++; if (diffs > 1) return false; i++ }
            else { i++; j++ }
        }
        return true
    }

    /** Optimal string alignment distance (adjacent transpositions count as one edit). */
    private fun damerauLevenshtein(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[n][m]
    }

    /**
     * Combined static + learned bigram continuations of [previousWord], in
     * dictionary casing. If [composingWord] is non-blank, only continuations
     * starting with it (case-insensitively) are returned.
     */
    private suspend fun getBigramSuggestions(
        locale: FlorisLocale,
        previousWord: String,
        composingWord: String,
    ): Map<String, Int> {
        val result = HashMap<String, Int>()
        val prefix = composingWord.lowercase()
        val canonicalPrev = canonicalForm(previousWord) ?: previousWord
        bigramData[canonicalPrev]?.forEach { (word, score) ->
            if (prefix.isEmpty() || word.lowercase().startsWith(prefix)) {
                result[word] = (result.getOrDefault(word, 0) + score).coerceAtMost(255)
            }
        }
        // The user's own phrasing is a stronger signal than corpus statistics.
        learnedBigramsStore.continuationsOf(locale, previousWord.lowercase()).forEach { (word, score) ->
            if (prefix.isEmpty() || word.startsWith(prefix)) {
                val shown = canonicalForm(word) ?: word
                result[shown] = (result.getOrDefault(shown, 0) + (score * 1.2).toInt()).coerceAtMost(255)
            }
        }
        return result
    }

    /**
     * Language-model evidence for words following [previousWord], used by the
     * glide decoder: word (dictionary casing and lowercase) -> log-probability
     * bonus in nats (0..2.5). Static corpus bigrams and the user's own learned
     * transitions are combined; empty when there is no evidence.
     */
    fun bigramBonuses(locale: FlorisLocale, previousWord: String): Map<String, Double> {
        val result = HashMap<String, Double>()
        val canonicalPrev = canonicalForm(previousWord) ?: previousWord
        bigramData[canonicalPrev]?.forEach { (word, score) ->
            val bonus = score / 255.0 * 2.5
            result[word] = max(result[word] ?: 0.0, bonus)
            result[word.lowercase()] = max(result[word.lowercase()] ?: 0.0, bonus)
        }
        learnedBigramsStore.continuationsOf(locale, previousWord.lowercase()).forEach { (word, score) ->
            // Each learned occurrence adds 5 to the score (see LearnedBigramsStore.bump).
            val bonus = (score * 12).coerceAtMost(255) / 255.0 * 2.5
            result[word] = max(result[word] ?: 0.0, bonus)
            canonicalForm(word)?.let { result[it] = max(result[it] ?: 0.0, bonus) }
        }
        return result
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
        val accepted = candidate.text.toString().trim()
        if (accepted.isBlank()) return
        if (candidate is TypedWordSuggestionCandidate) {
            acceptTypedWord(subtype, accepted)
        } else if (prefs.dictionary.learnPersonalWords.get() &&
            accepted.length in LEARN_WORD_MIN_LEN..LEARN_WORD_MAX_LEN &&
            LEARN_WORD_PATTERN.matches(accepted)
        ) {
            // Accepted suggestions are stronger usage signal than manual commits.
            learnedStore.bump(subtype.primaryLocale, accepted.lowercase(), freqDelta = 2)
        }
        lastCommittedWord?.let { prev ->
            if (prev.isNotBlank() && !prev.equals(accepted, ignoreCase = true)) {
                learnedBigramsStore.bump(subtype.primaryLocale, prev.lowercase(), accepted.lowercase())
            }
        }
        lastCommittedWord = accepted
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
        // The user undid an accepted suggestion; don't let that commit count
        // as usage of the word.
        val reverted = candidate.text.toString().trim()
        if (reverted.isBlank() || !LEARN_WORD_PATTERN.matches(reverted)) return
        if (prefs.dictionary.learnPersonalWords.get()) {
            learnedStore.bump(subtype.primaryLocale, reverted.lowercase(), freqDelta = -2)
        }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        val word = candidate.text.toString().trim()
        val key = word.lowercase()
        val removed = personalWords.remove(word) != null || personalWords.remove(key) != null
        if (removed) {
            learnedStore.set(subtype.primaryLocale, key, 0)
            if (BuildConfig.ENABLE_NATIVE_SUGGESTER && nativeDictionary.isLoaded) {
                nativeDictionary.removeLearnedWord(word)
                nativeDictionary.removeLearnedWord(key)
            }
            _wordDataDirtyFlow.tryEmit(subtype)
        }
        return removed
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        val base = baseWords
        val list = ArrayList<String>(base.size + personalWords.size)
        for ((word, freq) in base) {
            // Profanity and "valid but never suggest" entries stay out of glide.
            if (freq > 0 && word !in offensiveWords) list.add(word)
        }
        for (word in personalWords.keys) {
            if (word !in base) list.add(word)
        }
        return list
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return frequencyOf(canonicalForm(word) ?: word) / 255.0
    }

    /**
     * Read-only frequency view (0..1 of the AOSP log scale) for the glide
     * classifier, without copying the ~170k-entry vocabulary. Recently used
     * words get a small boost so they win ties against shape-similar
     * dictionary words.
     */
    override suspend fun getFrequencyMap(subtype: Subtype): Map<String, Double> {
        val recent = learnedStore.mostRecent(subtype.primaryLocale, MAX_RECENCY_WORDS)
        val base = baseWords
        return object : AbstractMap<String, Double>() {
            override val entries: Set<Map.Entry<String, Double>>
                get() = (base.keys + personalWords.keys).mapTo(HashSet()) { word ->
                    java.util.AbstractMap.SimpleImmutableEntry(word, valueOf(word))
                }

            private fun valueOf(word: String): Double {
                val value = frequencyOf(word) / 255.0
                return if (word.lowercase() in recent) value * GLIDE_RECENCY_BOOST else value
            }

            override fun get(key: String): Double? {
                if (key !in base && !personalWords.containsKey(key)) return null
                return valueOf(key)
            }

            override fun containsKey(key: String): Boolean = key in base || personalWords.containsKey(key)
        }
    }

    override suspend fun destroy() {
        // No-op
    }
}
