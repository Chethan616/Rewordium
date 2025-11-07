package com.noxquill.rewordium.keyboard.dictionary.provider

import android.content.Context
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionCandidate
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionSource
import com.noxquill.rewordium.keyboard.dictionary.model.SuggestionContext

/**
 * Predicts the next word based on the previous word.
 * This is a simple implementation; a real-world version would use a more complex n-gram model.
 */
class NextWordProvider : ISuggestionProvider {

    // A simple map of a word to its likely next words.
    private val bigramModel: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "have", "will", "think", "can"),
        "you" to listOf("are", "have", "can", "want", "need"),
        "we" to listOf("are", "can", "should", "will"),
        "it" to listOf("is", "was", "will", "has"),
        "in" to listOf("the", "a", "an", "my"),
        "of" to listOf("the", "a", "my"),
        "to" to listOf("be", "the", "a", "my"),
        "on" to listOf("the", "a", "my"),
        "for" to listOf("the", "a", "my"),
        "good" to listOf("morning", "afternoon", "evening", "night", "job", "luck"),
        "thank" to listOf("you", "you,", "god"),
        "please" to listOf("let", "me", "know", "send"),
        "let's" to listOf("go", "see", "get", "do"),
        "see" to listOf("you", "it", "the"),
        "the" to listOf("next", "first", "last", "best", "only")
    )

    override suspend fun initialize(context: Context) {
        // No heavy initialization needed for this simple model
    }

    override fun getSuggestions(input: String, context: SuggestionContext): List<PredictionCandidate> {
        // This provider only works if the user has just pressed space and there's a previous word.
        if (!context.isAfterSpace || context.previousWord == null) {
            return emptyList()
        }

        val nextWords = bigramModel[context.previousWord.lowercase()] ?: return emptyList()

        // Give a high score to these predictions, but decrease it slightly for each subsequent one.
        return nextWords.mapIndexed { index, word ->
            PredictionCandidate(word, 100.0 - index, PredictionSource.NEXT_WORD_PREDICTION)
        }
    }
}