package com.noxquill.rewordium.keyboard.dictionary.provider

import android.content.Context
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionCandidate
import com.noxquill.rewordium.keyboard.dictionary.model.SuggestionContext

interface ISuggestionProvider {
    /**
     * Initializes the provider, loading any necessary data.
     * This should be called once when the keyboard service starts.
     */
    suspend fun initialize(context: Context)

    /**
     * Generates a list of suggestion candidates based on the current input and context.
     * @return A list of PredictionCandidate objects.
     */
    fun getSuggestions(input: String, context: SuggestionContext): List<PredictionCandidate>
}