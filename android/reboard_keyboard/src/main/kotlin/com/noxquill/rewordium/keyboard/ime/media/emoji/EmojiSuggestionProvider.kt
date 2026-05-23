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
        val query = validateInputQuery(content.composingText) ?: return emptyList()
        // Emoji names + keywords are short. Anything past ~20 chars cannot
        // be a substring of an emoji name, so the entire scan is wasted —
        // and on glide-typed long words this scan ran on every keystroke
        // and made the suggestion bar feel laggy.
        if (query.length > MAX_QUERY_LENGTH) return emptyList()
        val emojis = cachedEmojiMappings.get(subtype.primaryLocale)?.get(preferredSkinTone) ?: emptyList()
        // Snapshot the suppression list once per call so the hot stream below
        // doesn't synchronize for every emoji.
        val suppressed = synchronized(recentlyCommitted) { recentlyCommitted.toSet() }
        val q = query.lowercase()
        val candidates = withContext(Dispatchers.Default) {
            emojis.parallelStream()
                // Drop emojis the user just committed (matches Gboard's
                // anti-spam behavior — no 😂😂😂 stream).
                .filter { it.value !in suppressed }
                .map { emoji -> emoji to scoreFor(emoji, q) }
                // High-confidence filter: anything below MIN_CONFIDENCE is
                // considered a weak guess and gets dropped. Gboard is quiet
                // by default; this is what enforces that.
                .filter { (_, s) -> s >= MIN_CONFIDENCE }
                .sorted { (_, a), (_, b) -> b.compareTo(a) }
                // Hard-cap to the lesser of the caller's max and our
                // absolute ceiling. Emojis share the suggestion strip
                // with text candidates; we never crowd words out.
                .limit(maxCandidateCount.coerceAtMost(GBOARD_MAX_CANDIDATES).toLong())
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
        return candidates
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

        /** Hard cap on emojis in any single suggestion strip. */
        const val GBOARD_MAX_CANDIDATES = 2

        /**
         * Anything below this confidence is treated as a guess and dropped.
         * At 0.75 only exact-name (1.0), name-prefix (0.88), and exact-keyword
         * (0.80) matches pass — keyword-prefix (0.70) and substring matches
         * are suppressed. This matches Gboard's conservative behavior.
         */
        const val MIN_CONFIDENCE = 0.75

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
        if (prefs.emoji.suggestionUpdateHistory.get()) {
            EmojiHistoryHelper.markEmojiUsed(prefs, candidate.emoji)
        }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // No-op
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate) = false

    override suspend fun getListOfWords(subtype: Subtype) = emptyList<String>()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String) = 0.0

    override suspend fun destroy() {
        cachedEmojiMappings.invalidateAll()
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
