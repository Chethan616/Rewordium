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
    }

    override val providerId = ProviderId

    private val appContext by context.appContext()
    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

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
        if (composingWord.isBlank()) return emptyList()

        return wordData.withLock { data ->
            data.asSequence()
                .filter { (candidate, _) -> candidate.startsWith(composingWord) && candidate != composingWord }
                .sortedByDescending { (_, score) -> score }
                .take(maxCandidateCount)
                .map { (candidate, score) ->
                    WordSuggestionCandidate(
                        text = candidate,
                        confidence = (score / 255.0).coerceIn(0.0, 1.0),
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                }
                .toList()
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
        val accepted = candidate.text.toString().trim().lowercase()
        if (accepted.isBlank()) return
        wordData.withLock { data ->
            val current = data.getOrDefault(accepted, 0)
            data[accepted] = (current + 1).coerceAtMost(255)
        }
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
