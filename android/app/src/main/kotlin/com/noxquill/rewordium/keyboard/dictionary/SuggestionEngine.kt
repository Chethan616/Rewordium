package com.noxquill.rewordium.keyboard.dictionary

import android.content.Context
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionCandidate
import com.noxquill.rewordium.keyboard.dictionary.model.SuggestionContext
import com.noxquill.rewordium.keyboard.dictionary.provider.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SuggestionEngine {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false

    private val userLearningProvider = UserLearningProvider()

    // Providers for when the user is currently typing a word
    private val wordCompletionProviders: List<ISuggestionProvider> = listOf(
        DictionaryProvider(),
        ContractionProvider(),
        TypoCorrectionProvider(),
        userLearningProvider
    )

    // Providers for when the user has just hit space
    private val nextWordProviders: List<ISuggestionProvider> = listOf(
        NextWordProvider(),
        userLearningProvider // ADDED: User's learned next-words are a high-quality source
    )

    fun initialize(context: Context) {
        if (isInitialized) return
        scope.launch {
            (wordCompletionProviders + nextWordProviders).distinct().forEach { it.initialize(context) }
            isInitialized = true
        }
    }

    fun getSuggestions(context: SuggestionContext): List<String> {
        if (!isInitialized) return emptyList()

        val allCandidates = mutableListOf<PredictionCandidate>()
        val providersToRun = if (context.isAfterSpace) nextWordProviders else wordCompletionProviders

        providersToRun.forEach { provider ->
            allCandidates.addAll(provider.getSuggestions(context.currentInput, context))
        }

        return rankAndFilter(allCandidates)
    }

    // UPDATED: Now accepts previousWord for learning context
    fun learn(currentWord: String, previousWord: String?) {
        if (!isInitialized) return
        userLearningProvider.learn(currentWord, previousWord)
    }

    private fun rankAndFilter(candidates: List<PredictionCandidate>): List<String> {
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .groupBy { it.word.lowercase() }
            .map { (_, group) ->
                group.maxByOrNull { it.score * it.source.weight }!!
            }
            .sortedByDescending { it.score * it.source.weight }
            .map { it.word }
            .distinct()
            .take(3)
    }
}