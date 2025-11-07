package com.noxquill.rewordium.keyboard.dictionary.provider

import android.content.Context
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionCandidate
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionSource
import com.noxquill.rewordium.keyboard.dictionary.model.SuggestionContext

class ContractionProvider : ISuggestionProvider {
    private val contractions = mapOf(
        "im" to "I'm", "ill" to "I'll", "id" to "I'd", "ive" to "I've",
        "dont" to "don't", "cant" to "can't", "wont" to "won't",
        "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't",
        "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
        "youre" to "you're", "youll" to "you'll", "youd" to "you'd",
        "hes" to "he's", "shes" to "she's", "its" to "it's",
        "theyre" to "they're", "theyll" to "they'll", "theyd" to "they'd",
        "weve" to "we've", "well" to "we'll", "wed" to "we'd",
        "whats" to "what's", "theres" to "there's", "lets" to "let's"
    )

    override suspend fun initialize(context: Context) {
        // No initialization needed for this provider
    }

    override fun getSuggestions(input: String, context: SuggestionContext): List<PredictionCandidate> {
        if (context.isAfterSpace) return emptyList()

        val expansion = contractions[input.lowercase()]
        return if (expansion != null) {
            listOf(PredictionCandidate(expansion, 100.0, PredictionSource.CONTRACTION, isCompletion = true))
        } else {
            emptyList()
        }
    }
}