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
import com.noxquill.rewordium.keyboard.appContext
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.editor.EditorContent
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingProvider
import com.noxquill.rewordium.keyboard.ime.nlp.SpellingResult
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionProvider
import com.noxquill.rewordium.keyboard.ime.nlp.WordSuggestionCandidate
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
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

    override suspend fun create() {
        // No-op
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        wordData.withLock { data ->
            if (data.isEmpty()) {
                val rawData = appContext.assets.readText("ime/dict/data.json")
                val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                data.putAll(jsonData)
            }
        }
        // Load bigram dictionary
        bigramData.withLock { data ->
            if (data.isEmpty()) {
                try {
                    val rawBigrams = appContext.assets.readText("ime/dict/bigrams.json")
                    val jsonBigrams = Json.decodeFromString(bigramDataSerializer, rawBigrams)
                    data.putAll(jsonBigrams)
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

        val suggestions = wordData.withLock { data ->
            if (data.containsKey(query)) {
                return@withLock emptyList()
            }
            data.asSequence()
                .filter { (candidate, _) -> candidate.startsWith(query.take(2)) && candidate != query }
                .sortedByDescending { (_, score) -> score }
                .map { (candidate, _) -> candidate }
                .take(maxSuggestionCount)
                .toList()
        }

        return if (suggestions.isEmpty() && wordData.withLock { it.containsKey(query) }) {
            SpellingResult.validWord()
        } else if (suggestions.isNotEmpty()) {
            SpellingResult.typo(suggestions.toTypedArray())
        } else {
            SpellingResult.typo(emptyArray())
        }
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

    override suspend fun destroy() {
        // No-op
    }
}
