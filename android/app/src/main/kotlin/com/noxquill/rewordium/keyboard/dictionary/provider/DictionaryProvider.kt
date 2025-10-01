package com.noxquill.rewordium.keyboard.dictionary.provider

import android.content.Context
import android.util.Log
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionCandidate
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionSource
import com.noxquill.rewordium.keyboard.dictionary.model.SuggestionContext
import java.io.BufferedReader
import java.io.InputStreamReader

class DictionaryProvider : ISuggestionProvider {
    private var wordSet: Set<String> = emptySet()
    private val TAG = "DictionaryProvider"

    override suspend fun initialize(context: Context) {
        try {
            val inputStream = context.assets.open("words.txt")
            wordSet = BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                lines.filter { it.isNotBlank() }.map { it.trim().lowercase() }.toSet()
            }
            Log.d(TAG, "Initialized with ${wordSet.size} words.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dictionary: ${e.message}", e)
            wordSet = emptySet()
        }
    }

    override fun getSuggestions(input: String, context: SuggestionContext): List<PredictionCandidate> {
        if (input.isEmpty() || context.isAfterSpace) {
            return emptyList()
        }

        val lowerInput = input.lowercase()
        val candidates = mutableListOf<PredictionCandidate>()

        // 1. Exact Match
        if (wordSet.contains(lowerInput)) {
            candidates.add(
                PredictionCandidate(lowerInput, 100.0, PredictionSource.EXACT_MATCH)
            )
        }

        // 2. Prefix Completions
        val completions = wordSet.filter {
            it.startsWith(lowerInput) && it.length > lowerInput.length
        }.take(10) // Limit to avoid performance issues

        completions.forEach { completion ->
            val score = lowerInput.length.toDouble() / completion.length * 100.0
            candidates.add(
                PredictionCandidate(completion, score, PredictionSource.PREFIX_COMPLETION, isCompletion = true)
            )
        }

        return candidates
    }
}