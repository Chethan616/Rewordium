/*
 * Copyright (C) 2021-2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import androidx.lifecycle.MutableLiveData
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.clipboardManager
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardItem
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ItemType
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.editor.ComposingWord
import com.noxquill.rewordium.keyboard.ime.editor.EditorContent
import com.noxquill.rewordium.keyboard.ime.editor.EditorRange
import com.noxquill.rewordium.keyboard.ime.editor.InputAttributes
import com.noxquill.rewordium.keyboard.ime.media.emoji.EmojiSuggestionProvider
import com.noxquill.rewordium.keyboard.ime.nlp.han.HanShapeBasedLanguageProvider
import com.noxquill.rewordium.keyboard.ime.nlp.latin.LatinLanguageProvider
import com.noxquill.rewordium.keyboard.ime.text.key.KeyVariation
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.lib.util.NetworkUtils
import com.noxquill.rewordium.keyboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import org.florisboard.lib.kotlin.collectLatestIn
import org.florisboard.lib.kotlin.guardedByLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"
private const val MAX_CURATED_CANDIDATES = 8
private const val MAX_CURATED_TEXT_CANDIDATES = 6
private const val MAX_CURATED_EMOJI_CANDIDATES = 2

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableLiveData(0)
    private val debugOverlayVersionSource = AtomicInteger(0)

    // Holds the last suggestion coroutine job so we can cancel it if a newer request arrives,
    // implementing "latest-wins" cancellation and avoiding wasted CPU on superseded requests.
    private var suggestJob: Job? = null

    /**
     * The composing word the latest suggestion pass was computed for, paired
     * with that pass's auto-commit (autocorrect) candidate. The space key
     * only trusts it when it matches the word currently on screen; see
     * [getAutoCommitCandidate].
     */
    @Volatile private var autoCommitState: Pair<String, SuggestionCandidate?> = "" to null

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value
            ?.get(subtype.punctuationRule) ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    /**
     * Tag-along forward to the active suggestion provider's personal-vocab
     * learning hook, called from EditorInstance.commitChar and
     * KeyboardManager.commitGesture. No-op for providers that don't support
     * it (e.g. Han CJK), so commit-site callers don't need to type-check.
     *
     * Fire-and-forget: scoped to the NlpManager background dispatcher so
     * the calling thread (UI / IME input pipeline) never blocks.
     */
    fun learnWord(subtype: Subtype, word: String) {
        if (word.isBlank() || !isLearningAllowed()) return
        scope.launch {
            val provider = getSuggestionProvider(subtype)
            if (provider is LatinLanguageProvider) {
                provider.learnWord(subtype, word)
            }
        }
    }

    /**
     * Forgets everything the keyboard learned from typing (personal words, word pairs, emoji
     * associations) for the active language. The user dictionary and contacts are kept.
     */
    suspend fun forgetLearning() {
        val subtype = subtypeManager.activeSubtype
        (getSuggestionProvider(subtype) as? LatinLanguageProvider)?.forgetLearning(subtype)
        emojiSuggestionProvider.forgetLearning()
    }

    fun unlearnWord(subtype: Subtype, word: String) {
        if (word.isBlank()) return
        scope.launch {
            val provider = getSuggestionProvider(subtype)
            if (provider is LatinLanguageProvider) {
                provider.unlearnWord(subtype, word)
            }
        }
    }

    /**
     * The user undid an autocorrection with Backspace: keep [original] from
     * now on and don't count the correction as usage of [corrected].
     */
    fun onAutoCorrectionReverted(subtype: Subtype, original: String, corrected: String) {
        if (original.isBlank()) return
        autoCommitState = "" to null
        scope.launch {
            val provider = getSuggestionProvider(subtype)
            if (provider is LatinLanguageProvider) {
                provider.acceptTypedWord(
                    subtype,
                    original,
                    persist = !keyboardManager.activeState.isIncognitoMode,
                )
                provider.notifySuggestionReverted(
                    subtype,
                    WordSuggestionCandidate(corrected, sourceProvider = provider),
                )
            }
            suggest(subtype, editorInstance.activeContent)
        }
    }

    /**
     * Forwards a panel-driven emoji pick to the emoji suggestion provider so
     * it can persist a `(previousWord → emoji)` association. Smartbar accepts
     * already flow through `notifySuggestionAccepted` — this is the parallel
     * entry point for the emoji palette, where there's no SuggestionCandidate
     * round-trip but the context is still meaningful.
     *
     * `previousWord` is extracted from the active editor content at the
     * pick site so a stale `lastPreviousWord` in the provider doesn't lead
     * us to record an association against the wrong context.
     */
    fun notifyEmojiPickedFromPalette(subtype: Subtype, emojiValue: String) {
        if (emojiValue.isBlank() || !isLearningAllowed()) return
        val textBefore = editorInstance.activeContent.textBeforeSelection.toString().trimEnd()
        if (textBefore.isBlank()) return
        val lastSpace = textBefore.lastIndexOf(' ')
        val raw = if (lastSpace >= 0) textBefore.substring(lastSpace + 1) else textBefore
        val previousWord = raw.lowercase().trim { !it.isLetter() && it != '\'' }
        if (previousWord.isEmpty()) return
        scope.launch {
            emojiSuggestionProvider.recordPalettePick(subtype, emojiValue, previousWord)
        }
    }

    /**
     * Forwards an explicit User Dictionary add (from Settings UI) into the
     * Latin provider's wordData so the glide-classifier picks the new word
     * up immediately — no IME restart needed.
     */
    fun importUserDictionaryEntry(subtype: Subtype, word: String, freq: Int) {
        if (word.isBlank()) return
        scope.launch {
            val provider = getSuggestionProvider(subtype)
            if (provider is LatinLanguageProvider) {
                provider.importUserDictionaryEntry(subtype, word, freq)
            }
        }
    }

    /**
     * Hot-reload contact names into the native dict after READ_CONTACTS is
     * granted at runtime. Triggers a fresh suggestion cycle so the user sees
     * contact-aware candidates on the very next keypress.
     */
    fun reloadContactsNow() {
        scope.launch {
            val subtype = subtypeManager.activeSubtype
            val provider = getSuggestionProvider(subtype)
            if (provider is LatinLanguageProvider) {
                provider.reloadContacts(subtype)
            }
            suggest(subtype, editorInstance.activeContent)
        }
    }

    /**
     * Forwards [LatinLanguageProvider.wordDataDirtyFlow] up to the IME so
     * the glide-typing manager can rebuild its classifier index when enough
     * personal words have been learned to warrant it.
     *
     * Resolved lazily on first access, and cached — the Latin provider is a
     * singleton in the providers map, so we can capture its flow once and
     * avoid the runBlocking dance on every getter access. Non-Latin layouts
     * just see no dirty signals, which is correct (glide adaptation today
     * only applies to Latin-script subtypes).
     */
    val wordDataDirtyFlow: SharedFlow<Subtype> by lazy {
        val latin = runBlocking {
            providers.withLock { it[LatinLanguageProvider.ProviderId] }?.provider
        } as? LatinLanguageProvider
        latin?.wordDataDirtyFlow
            ?: MutableSharedFlow<Subtype>(replay = 0).asSharedFlow()
    }

    /**
     * Current AOSP native dictionary handle from [LatinLanguageProvider], or
     * 0 (== [LatinImeNative.INVALID_HANDLE]) if the native dict isn't loaded
     * yet (process still in preload, or `ENABLE_NATIVE_SUGGESTER` is off).
     *
     * Used by [NativeGlideTypingClassifier] to drive AOSP's gesture-aware
     * Suggest pipeline from the same trie that powers text suggestions.
     * Cheap to call — no locks held across the read; the underlying value
     * is a single volatile Long on the provider side.
     */
    val nativeDictHandle: Long get() {
        val latin = runBlocking {
            providers.withLock { it[LatinLanguageProvider.ProviderId] }?.provider
        } as? LatinLanguageProvider
        return latin?.nativeDictionary?.handle ?: 0L
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence,
        breakIterators: BreakIteratorGroup,
        localLastCommitPosition: Int,
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition,
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                getSuggestionProvider(subtype).forcesSuggestionOn
            }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get() ||
            prefs.emoji.suggestionEnabled.get() ||
            providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun suggest(subtype: Subtype, content: EditorContent) {
        val reqTime = SystemClock.uptimeMillis()
        // Cancel any in-flight suggestion request — latest keystroke wins.
        suggestJob?.cancel()
        suggestJob = scope.launch {
            // 50 ms debounce: if a newer suggest() call arrives and cancels this job before the
            // delay elapses, no NLP work is done — eliminating redundant dictionary lookups
            // during rapid typing bursts.
            delay(50)
            val learningAllowed = isLearningAllowed()
            emojiSuggestionProvider.learningAllowed = learningAllowed
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> emptyList()
                else -> {
                    val provider = getSuggestionProvider(subtype)
                    if (provider is LatinLanguageProvider) {
                        provider.autocorrectAllowedForField = isAutocorrectAllowedForField()
                        provider.learningAllowed = learningAllowed
                    }
                    provider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = MAX_CURATED_CANDIDATES,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
            }
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    autoCommitState = content.composingText.toString() to
                        suggestions.firstOrNull { it.isEligibleForAutoCommit }
                    internalSuggestions = reqTime to curateCandidates(buildList {
                        addAll(suggestions)        // text predictions first (Gboard behavior)
                        addAll(emojiSuggestions)    // emojis at end of strip
                    })
                }
            }
        }
    }

    /**
     * Whether what is typed now may be learned (personal words, word pairs, emoji associations). Never in
     * private mode, never when the app asks for no personalised learning (incognito tabs, banking apps), and
     * never in password, e-mail, URL or number fields: what is typed there is credentials and addresses,
     * not the user's vocabulary.
     */
    fun isLearningAllowed(): Boolean {
        if (keyboardManager.activeState.isIncognitoMode) return false
        if (keyboardManager.activeState.keyVariation != KeyVariation.NORMAL) return false
        val info = editorInstance.activeInfo
        if (info.imeOptions.flagNoPersonalizedLearning) return false
        val attributes = info.inputAttributes
        if (attributes.type != InputAttributes.Type.TEXT) return false
        return when (attributes.variation) {
            InputAttributes.Variation.PASSWORD,
            InputAttributes.Variation.VISIBLE_PASSWORD,
            InputAttributes.Variation.WEB_PASSWORD,
            InputAttributes.Variation.URI,
            InputAttributes.Variation.EMAIL_ADDRESS,
            InputAttributes.Variation.WEB_EMAIL_ADDRESS -> false
            else -> true
        }
    }

    /**
     * Autocorrect only makes sense in prose fields: never in URL, e-mail,
     * password, filter or phonetic inputs, where "fixing" words breaks them.
     */
    private fun isAutocorrectAllowedForField(): Boolean {
        if (keyboardManager.activeState.keyVariation != KeyVariation.NORMAL) return false
        val attributes = editorInstance.activeInfo.inputAttributes
        if (attributes.type != InputAttributes.Type.TEXT) return false
        return when (attributes.variation) {
            InputAttributes.Variation.URI,
            InputAttributes.Variation.EMAIL_ADDRESS,
            InputAttributes.Variation.WEB_EMAIL_ADDRESS,
            InputAttributes.Variation.PASSWORD,
            InputAttributes.Variation.VISIBLE_PASSWORD,
            InputAttributes.Variation.WEB_PASSWORD,
            InputAttributes.Variation.FILTER,
            InputAttributes.Variation.PERSON_NAME,
            InputAttributes.Variation.POSTAL_ADDRESS,
            InputAttributes.Variation.PHONETIC -> false
            else -> true
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val reqTime = SystemClock.uptimeMillis()
        autoCommitState = "" to null
        scope.launch {
            // Glide typing already returns candidates in confidence order. Curate
            // without re-sorting so its best result remains the first result.
            internalSuggestions = reqTime to curateCandidates(suggestions, sortByConfidence = false)
        }
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        scope.launch {
            internalSuggestions = reqTime to emptyList()
        }
    }

    /**
     * The candidate a word separator (space, punctuation) should commit in
     * place of the composing word, or null to keep the word as typed.
     *
     * Suggestions are computed asynchronously with a short debounce, so when
     * the user types fast the latest pass may still describe the previous
     * prefix — applying it would turn "thin" into "this". For the Latin
     * provider we therefore only trust a pass computed for exactly the word
     * on screen and otherwise decide synchronously (a single native decode,
     * a few milliseconds), like AOSP LatinIME does. [separator] is the key
     * that ends the word (a single letter before "." is never corrected).
     */
    fun getAutoCommitCandidate(separator: String = " "): SuggestionCandidate? {
        val subtype = subtypeManager.activeSubtype
        val provider = runBlocking { getSuggestionProvider(subtype) }
        if (provider !is LatinLanguageProvider) {
            return activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
        }
        if (!isAutocorrectAllowedForField()) return null
        provider.autocorrectAllowedForField = true
        val content = editorInstance.activeContent
        val composing = content.composingText.toString()
        if (composing.isBlank()) return null
        // A separator typed inside a word splits it ("lo|ve" + space): there is no finished word to correct.
        if (!ComposingWord.cursorAtEnd(content.localComposing, content.localSelection.start)) return null
        if (!provider.allowsAutocorrectBefore(content, separator)) return null
        val state = autoCommitState
        if (state.first == composing) return state.second
        return try {
            provider.autocorrectFor(content)
        } catch (e: Exception) {
            null
        }
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    if (candidate is ClipboardSuggestionCandidate) {
                        assembleCandidates()
                    } else {
                        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                    }
                }
            }
        }
    }

    /**
     * Glide language model: log-probability bonuses for words that follow
     * [previousWord] (see [LatinLanguageProvider.bigramBonuses]).
     */
    fun getBigramBonuses(subtype: Subtype, previousWord: String): Map<String, Double> {
        if (previousWord.isBlank()) return emptyMap()
        return runBlocking {
            (getSuggestionProvider(subtype) as? LatinLanguageProvider)
                ?.bigramBonuses(subtype.primaryLocale, previousWord)
                ?: emptyMap()
        }
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    /**
     * Returns a snapshot of all word frequencies as a single map. Prefer this over repeated
     * [getFrequencyForWord] calls when many words need scoring (e.g. glide typing classifier).
     */
    fun getFrequencyMap(subtype: Subtype): Map<String, Double> {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyMap(subtype) }
    }

    private suspend fun assembleCandidates() {
        val rawCandidates = when {
            isSuggestionOn() -> {
                clipboardSuggestionProvider.suggest(
                    subtype = Subtype.DEFAULT,
                    content = editorInstance.activeContent,
                    maxCandidateCount = MAX_CURATED_CANDIDATES,
                    allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                ).ifEmpty {
                    buildList {
                        internalSuggestionsGuard.withLock {
                            addAll(internalSuggestions.second)
                        }
                    }
                }
            }
            else -> emptyList()
        }
        val candidates = curateCandidates(rawCandidates)
        activeCandidates = candidates
        autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
    }

    /**
     * Normalizes the final candidate stream before it reaches the smartbar.
     * Providers can overlap (for example, a learned word can also be present
     * in the system dictionary), so the UI should not spend a slot showing the
     * same word twice. Text candidates stay ahead of emoji, while confidence
     * determines priority within each group.
     */
    private fun curateCandidates(
        candidates: List<SuggestionCandidate>,
        sortByConfidence: Boolean = true,
    ): List<SuggestionCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val seen = HashSet<String>()
        val unique = candidates.filter { candidate ->
            val key = candidate.text.toString().trim().lowercase(Locale.ROOT)
            key.isNotEmpty() && seen.add(key)
        }

        val textCandidates = unique
            .filterNot { it is EmojiSuggestionCandidate }
            .let { values ->
                if (!sortByConfidence) {
                    values
                } else {
                    values.withIndex()
                        .sortedWith(
                            compareByDescending<IndexedValue<SuggestionCandidate>> {
                                it.value.isEligibleForAutoCommit
                            }
                                .thenByDescending { it.value.confidence }
                                .thenBy { it.index },
                        )
                        .map { it.value }
                }
            }
            .take(MAX_CURATED_TEXT_CANDIDATES)

        val emojiCandidates = unique
            .filterIsInstance<EmojiSuggestionCandidate>()
            .take(MAX_CURATED_EMOJI_CANDIDATES)

        return (textCandidates + emojiCandidates).take(MAX_CURATED_CANDIDATES)
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {
            return
        }
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = list1.isNullOrEmpty() && list2.isNullOrEmpty() || isSelection
        scope.launch {
            prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
            prefs.smartbar.sharedActionsExpanded.set(isExpanded)
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        val version = debugOverlayVersionSource.incrementAndGet()
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.postValue(version)
    }

    fun clearDebugOverlay() {
        val version = debugOverlayVersionSource.incrementAndGet()
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.postValue(version)
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() {
        }

        override suspend fun preload(subtype: Subtype) {
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(
                        ClipboardSuggestionCandidate(
                            currentItem,
                            sourceProvider = this@ClipboardSuggestionProvider,
                            context = context,
                        ),
                    )
                    if (currentItem.isSensitive) {
                        return@buildList
                    }
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(
                                    ClipboardSuggestionCandidate(
                                        clipboardItem = currentItem.copy(
                                            text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                                match.value.substring(1, match.value.length - 1)
                                            } else {
                                                match.value
                                            },
                                        ),
                                        sourceProvider = this@ClipboardSuggestionProvider,
                                        context = context,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> {
            return emptyList()
        }

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
            return 0.0
        }

        override suspend fun destroy() {
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                it.id != lastItemId &&
                    contentText.isBlank() &&
                    !currentItem.text.isNullOrBlank() &&
                    !blankStrRegex.matches(currentItem.text)
            }
    }
}
