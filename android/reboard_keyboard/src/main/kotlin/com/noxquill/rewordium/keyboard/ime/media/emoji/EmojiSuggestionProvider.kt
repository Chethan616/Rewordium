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

package com.noxquill.rewordium.keyboard.ime.media.emoji

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.stream.Collectors
import android.content.Context
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.dictionary.LearnedEmojiAssociationsStore
import com.noxquill.rewordium.keyboard.ime.editor.EditorContent
import com.noxquill.rewordium.keyboard.ime.nlp.EmojiSuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionProvider
import com.noxquill.rewordium.keyboard.lib.FlorisLocale
import io.github.reactivecircus.cache4k.Cache

/**
 * Provides emoji suggestions within a text input context.
 *
 * This class handles the following tasks:
 * - Initializes and maintains a list of supported emojis.
 * - Generates and returns emoji suggestions based on user input and preferences.
 *
 * @param context The application context.
 */
class EmojiSuggestionProvider(private val context: Context) : SuggestionProvider {
    override val providerId = "org.florisboard.nlp.providers.emoji"

    private val prefs by FlorisPreferenceStore
    private val lettersRegex = "^[A-Za-z]*$".toRegex()

    private val cachedEmojiMappings = Cache.Builder().build<FlorisLocale, EmojiDataBySkinTone>()

    /**
     * Persistent store of `previousWord → emoji` associations the user has
     * built up by picking emojis from the smartbar over time. Drives the
     * context-aware boost in [scoreFor] and the blank-composing prediction
     * branch in [suggest]. Loaded lazily on first [preload].
     */
    private val learnedAssociations = LearnedEmojiAssociationsStore(context)

    /**
     * Last previous word seen on the most recent [suggest] call. We need
     * this on [notifySuggestionAccepted] (which only receives the candidate,
     * not the editor content) so we can persist the `(prev, emoji)` pair the
     * user just committed.
     */
    @Volatile private var lastPreviousWord: String = ""

    /**
     * Rolling window of recently-committed emoji values, used to suppress
     * "spammy" repeats — matches Gboard's behavior where if you just sent 😂,
     * the next suggestion strip won't push the same emoji again. Bounded by
     * [RECENT_SUPPRESSION_WINDOW] so old commits drop off.
     *
     * Synchronized writes via the synchronized() block in
     * [notifySuggestionAccepted]; reads in [suggest] use a defensive copy.
     */
    private val recentlyCommitted = ArrayDeque<String>()

    /**
     * Session-scoped dedup: tracks the last composing query and the emoji
     * values it produced so we don't re-flash the exact same emojis when
     * the user keeps typing the same word across keystrokes.
     */
    @Volatile private var lastSuggestedQuery: String = ""
    @Volatile private var lastSuggestedValues: List<String> = emptyList()

    /**
     * Emojis surfaced for the **current composing word** that the user has
     * not yet accepted. When the composing word ends (commit, cursor move,
     * non-extending query), this set is drained into [sessionRejected] —
     * an implicit "you saw it, you didn't pick it, so stop showing it".
     *
     * Reset is detected in [suggest] by comparing the new query against
     * [lastSuggestedQuery]: anything that isn't a prefix-extension of the
     * previous query counts as a new word session.
     */
    private val pendingShown = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * IME-session-scoped rejection list. Once an emoji is in here, it is
     * filtered out of every future suggestion strip for the rest of the
     * IME's lifetime — matches Gboard's "I implicitly said no" behavior.
     * Cleared when [notifySuggestionAccepted] explicitly accepts the emoji
     * (positive signal overrides past rejection).
     */
    private val sessionRejected = java.util.Collections.synchronizedSet(HashSet<String>())

    override suspend fun create() {
    }

    override suspend fun preload(subtype: Subtype) {
        subtype.locales().forEach { locale ->
            cachedEmojiMappings.get(locale) {
                EmojiData.get(context, locale).bySkinTone
            }
        }
        // Also warm the root.txt asset that MediaInputLayout opens — without
        // this, the first emoji-panel open pays a ~200KB asset parse on top
        // of the supported-emoji filter and the panel feels frozen.
        EmojiData.get(context, "ime/media/emoji/root.txt")
        // Read learned (prevWord → emoji) associations off disk so the very
        // first suggest() call after process start can already use them.
        learnedAssociations.ensureLoaded()
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean
    ): List<SuggestionCandidate> {
        val preferredSkinTone = prefs.emoji.preferredSkinTone.get()
        val showName = prefs.emoji.suggestionCandidateShowName.get()
        // Extract the previous committed word so we can both bias scoring on
        // active composing AND surface learned associations on a blank
        // composing region (e.g. user just typed "lol " and we predict 😂
        // before any character of the next word is typed).
        val previousWord = extractPreviousWord(content.textBeforeSelection).orEmpty()
        lastPreviousWord = previousWord
        val effectiveMax = maxCandidateCount.coerceAtMost(GBOARD_MAX_CANDIDATES)
        val rawQuery = validateInputQuery(content.composingText)
        if (rawQuery == null) {
            // Composing text is empty / invalid — a word boundary. Flush any
            // unconfirmed emojis from the last word into session-rejected.
            flushPendingToRejected()
            lastSuggestedQuery = ""
            // Blank-composing branch: if we have a known previous word and
            // the user has historically picked emojis after it, surface the
            // top learned associations as pure next-token predictions.
            if (previousWord.isNotBlank() && prefs.emoji.suggestionEnabled.get()) {
                val emojis = cachedEmojiMappings.get(subtype.primaryLocale)
                    ?.get(preferredSkinTone) ?: return emptyList()
                val learnedTop = learnedAssociations.topEmojisFor(
                    subtype.primaryLocale, previousWord, effectiveMax,
                )
                if (learnedTop.isEmpty()) return emptyList()
                val byValue = emojis.associateBy { it.value }
                val predictions = learnedTop.mapNotNull { value ->
                    byValue[value]?.let { emoji ->
                        EmojiSuggestionCandidate(
                            emoji = emoji,
                            showName = showName,
                            sourceProvider = this@EmojiSuggestionProvider,
                        )
                    }
                }
                return predictions
            }
            return emptyList()
        }
        val query = rawQuery
        // Emoji names + keywords are short. Anything past ~20 chars cannot
        // be a substring of an emoji name, so the entire scan is wasted —
        // and on glide-typed long words this scan ran on every keystroke
        // and made the suggestion bar feel laggy.
        if (query.length > MAX_QUERY_LENGTH) return emptyList()
        val emojis = cachedEmojiMappings.get(subtype.primaryLocale)?.get(preferredSkinTone) ?: emptyList()
        val q = query.lowercase()
        // Word-boundary flush happens only when composing empties (handled
        // above in the rawQuery==null branch). Within a single composing
        // word, even if the user backspaces a character, we do NOT flush —
        // they're still working on the same word and the previously-shown
        // emoji shouldn't be banned mid-edit.
        // Snapshot the suppression list once per call so the hot stream below
        // doesn't synchronize for every emoji. Includes both the anti-spam
        // recent-commit window and the implicit-reject session set.
        val suppressed = synchronized(recentlyCommitted) { recentlyCommitted.toSet() } +
            synchronized(sessionRejected) { sessionRejected.toSet() }
        val locale = subtype.primaryLocale
        val candidates = withContext(Dispatchers.Default) {
            emojis.parallelStream()
                // Drop emojis the user just committed (matches Gboard's
                // anti-spam behavior — no 😂😂😂 stream).
                .filter { it.value !in suppressed }
                .map { emoji ->
                    val base = scoreFor(emoji, q)
                    // Context boost: if the user has historically picked
                    // this emoji after the current previousWord, nudge the
                    // confidence up. Saturated learned score (100) buys a
                    // +0.10 boost — enough to flip a tier-3 (0.80) keyword
                    // match into the same band as a tier-2 (0.88) prefix
                    // match, but never enough to surface a 0.0 stranger.
                    val boost = if (base > 0.0 && previousWord.isNotBlank()) {
                        val learned = learnedAssociations.scoreFor(locale, previousWord, emoji.value)
                        if (learned > 0) (learned / 100.0) * LEARNED_ASSOC_BOOST else 0.0
                    } else 0.0
                    emoji to (base + boost).coerceAtMost(1.0)
                }
                // High-confidence filter: anything below MIN_CONFIDENCE is
                // considered a weak guess and gets dropped. Gboard is quiet
                // by default; this is what enforces that.
                .filter { (_, s) -> s >= MIN_CONFIDENCE }
                .sorted { (_, a), (_, b) -> b.compareTo(a) }
                // Hard-cap to the lesser of the caller's max and our
                // absolute ceiling. Emojis share the suggestion strip
                // with text candidates; we never crowd words out.
                .limit(effectiveMax.toLong())
                .map { (emoji, _) ->
                    EmojiSuggestionCandidate(
                        emoji = emoji,
                        showName = showName,
                        sourceProvider = this@EmojiSuggestionProvider,
                    )
                }
                .collect(Collectors.toList())
        }
        // Session dedup: if the query and resulting emojis are identical
        // to the last call, return the cached list without visual churn.
        val candidateValues = candidates.map { (it as EmojiSuggestionCandidate).emoji.value }
        if (q == lastSuggestedQuery && candidateValues == lastSuggestedValues) {
            return candidates
        }
        lastSuggestedQuery = q
        lastSuggestedValues = candidateValues
        // Record what was surfaced for this composing word so that if the
        // user moves on without picking any of them, they get implicitly
        // rejected on the next word boundary.
        if (candidateValues.isNotEmpty()) {
            synchronized(pendingShown) { pendingShown.addAll(candidateValues) }
        }
        return candidates
    }

    /**
     * Drain the per-word pending-shown set into the session-wide rejection
     * set. Called on word boundaries (composing emptied, query reset, new
     * field). The session set persists for the rest of the IME's lifetime
     * unless [notifySuggestionAccepted] explicitly clears an entry.
     */
    private fun flushPendingToRejected() {
        synchronized(pendingShown) {
            if (pendingShown.isEmpty()) return
            synchronized(sessionRejected) { sessionRejected.addAll(pendingShown) }
            pendingShown.clear()
        }
    }

    /**
     * Tiered confidence score. Models how confident Gboard is in an emoji
     * suggestion: exact word matches dominate, prefix matches are strong,
     * substring matches only count when the query covers most of the
     * emoji's name (so "hap" matches "happy" but not "happiness").
     */
    private fun scoreFor(emoji: Emoji, q: String): Double {
        // Hardcoded alias override for common internet slang
        val aliasTarget = EMOJI_ALIASES[q]
        if (aliasTarget != null) {
            return if (emoji.value == aliasTarget) 1.0 else 0.0
        }

        val name = emoji.name.lowercase()
        // Tier 1 — exact match on full name: top confidence.
        if (name == q) return 1.0
        // Tier 2 — name begins with the query: strong prefix match.
        // Require ≥3 chars to avoid firing on "ha", "sm" etc. — Gboard
        // doesn't surface emojis on ultra-short ambiguous prefixes.
        if (q.length >= 3 && name.startsWith(q)) return 0.88
        // Tier 3 — exact keyword equality: high confidence.
        // ("gun" → keyword "gun" on 🔫, even though name is "pistol")
        for (kw in emoji.keywords) {
            if (kw.equals(q, ignoreCase = true)) return 0.80
        }
        // Tier 4 — keyword starts with query: still pretty good.
        for (kw in emoji.keywords) {
            if (kw.startsWith(q, ignoreCase = true)) return 0.70
        }
        // Tier 5 — query is a substring of the name, weighted by coverage.
        // "hap" inside "happiness" is 3/9 = 0.33 → BELOW MIN_CONFIDENCE,
        // so it gets dropped. That's the point: weak partials don't fire.
        if (name.contains(q)) {
            val coverage = q.length.toDouble() / name.length
            return 0.55 * coverage
        }
        return 0.0
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 20

        private val EMOJI_ALIASES = mapOf(
            "lol" to "😂",
            "lmao" to "🤣",
            "ok" to "👍",
            "yes" to "👍",
            "no" to "👎",
            "love" to "❤️",
            "hi" to "👋",
            "bye" to "👋",
            "hmm" to "🤔",
            "omg" to "😱",
            "wow" to "😮",
            "cool" to "😎",
            "done" to "✅",
            "please" to "🙏",
            "pls" to "🙏",
            "thanks" to "🙏",
            "thx" to "🙏"
        )

        /**
         * Hard cap on emojis in any single suggestion strip. Two is the
         * Gboard sweet spot — one strong pick + one alternate (e.g. typing
         * "fire" surfaces 🔥 + 🚒), without crowding text candidates out of
         * the strip. The actual count honours both this ceiling and the
         * caller's [maxCandidateCount] argument.
         */
        const val GBOARD_MAX_CANDIDATES = 2

        /**
         * Multiplicative boost applied to [scoreFor]'s base confidence when
         * the candidate emoji has been historically picked after the active
         * previousWord. 0.10 at saturation (learned=100) is enough to flip
         * a borderline candidate into the kept set, but cannot promote a
         * zero-base candidate into the strip on context alone.
         */
        const val LEARNED_ASSOC_BOOST = 0.10

        /**
         * Anything below this confidence is treated as a guess and dropped.
         * At 0.80 only exact-name (1.0), name-prefix (0.88), and
         * exact-keyword (0.80) matches pass — keyword-prefix (0.70) and
         * substring matches are filtered out. This is the "name typed
         * correctly" bar the user asked for: typing "good" surfaces 👍
         * (exact keyword), typing "goo" surfaces nothing.
         */
        const val MIN_CONFIDENCE = 0.80

        /**
         * Window of recently-committed emojis to suppress in upcoming
         * suggestion strips. 8 is roughly "the last sentence or two of
         * typing" — long enough that you don't get the same emoji back,
         * short enough that legitimate re-use eventually surfaces.
         */
        const val RECENT_SUPPRESSION_WINDOW = 8
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        if (candidate !is EmojiSuggestionCandidate) {
            return
        }
        // Track for the anti-spam filter regardless of whether the user
        // wants history saved — this is purely a session-scoped UX guard.
        synchronized(recentlyCommitted) {
            recentlyCommitted.remove(candidate.emoji.value)
            recentlyCommitted.addFirst(candidate.emoji.value)
            while (recentlyCommitted.size > RECENT_SUPPRESSION_WINDOW) {
                recentlyCommitted.removeLast()
            }
        }
        // Positive signal overrides any prior implicit rejection of this
        // emoji — drop it from both the per-word pending list and the
        // session-wide rejection set so it stays eligible going forward.
        synchronized(pendingShown) { pendingShown.remove(candidate.emoji.value) }
        synchronized(sessionRejected) { sessionRejected.remove(candidate.emoji.value) }
        if (prefs.emoji.suggestionUpdateHistory.get()) {
            EmojiHistoryHelper.markEmojiUsed(prefs, candidate.emoji)
        }
        // Persist the (previousWord → emoji) association so future strips
        // weight this emoji higher when the same previousWord shows up
        // again. Survives IME restart via the JSON-backed store.
        val prev = lastPreviousWord
        if (prev.isNotBlank()) {
            learnedAssociations.bump(subtype.primaryLocale, prev, candidate.emoji.value)
        }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // No-op
    }

    /**
     * Called by [NlpManager.notifyEmojiPickedFromPalette] when the user picks
     * an emoji directly from the panel (vs. accepting one from the smartbar
     * strip). Persists the `(previousWord → emojiValue)` association so the
     * smartbar can predict the same emoji in the same context next time.
     *
     * Lower delta than `notifySuggestionAccepted` (2 vs 3) because a palette
     * pick is a weaker context signal — the user opened the panel and
     * browsed, the previous word didn't drive the choice as directly as
     * accepting an inline suggestion did.
     */
    fun recordPalettePick(subtype: Subtype, emojiValue: String, previousWord: String) {
        if (emojiValue.isBlank() || previousWord.isBlank()) return
        learnedAssociations.bump(subtype.primaryLocale, previousWord, emojiValue, delta = 2)
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate) = false

    override suspend fun getListOfWords(subtype: Subtype) = emptyList<String>()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String) = 0.0

    override suspend fun destroy() {
        cachedEmojiMappings.invalidateAll()
    }

    /**
     * Extracts the last whitespace-delimited word from [textBeforeCursor].
     * Mirrors the helper in [LatinLanguageProvider.extractPreviousWord] so
     * emoji context-prediction uses the same notion of "previous word" as
     * text bigram prediction.
     */
    private fun extractPreviousWord(textBeforeCursor: CharSequence): String? {
        val text = textBeforeCursor.toString().trimEnd()
        if (text.isBlank()) return null
        val lastSpace = text.lastIndexOf(' ')
        val word = if (lastSpace >= 0) text.substring(lastSpace + 1) else text
        val cleaned = word.lowercase().trim { !it.isLetter() && it != '\'' }
        return cleaned.takeIf { it.isNotEmpty() }
    }

    /**
     * Validates the user input query for emoji suggestions.
     */
    private fun validateInputQuery(composingText: CharSequence): String? {
        val prefix = prefs.emoji.suggestionType.get().prefix
        val queryMinLength = prefs.emoji.suggestionQueryMinLength.get() + prefix.length
        if (prefix.isNotEmpty() && !composingText.startsWith(prefix)) {
            return null
        }
        if (composingText.length < queryMinLength) {
            return null
        }
        val emojiPartialName = composingText.substring(prefix.length)
        if (!lettersRegex.matches(emojiPartialName)) {
            return null
        }
        return emojiPartialName
    }
}

private fun String.containsWeighted(other: String, ignoreCase: Boolean = false): Double = let { str ->
    if (str.contains(other, ignoreCase = ignoreCase)) {
        other.length.toDouble() / str.length.toDouble()
    } else {
        0.0
    }
}
