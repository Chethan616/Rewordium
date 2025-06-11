package com.example.yc_startup.keyboard.dictionary.provider

import android.content.Context
import android.util.Log
import com.example.yc_startup.keyboard.dictionary.model.PredictionCandidate
import com.example.yc_startup.keyboard.dictionary.model.PredictionSource
import com.example.yc_startup.keyboard.dictionary.model.SuggestionContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.min

class TypoCorrectionProvider : ISuggestionProvider {
    private var wordSet: Set<String> = emptySet()
    private val TAG = "TypoCorrectionProvider"

    override suspend fun initialize(context: Context) {
        // This provider shares the same dictionary as DictionaryProvider.
        // In a real app with dependency injection, this would be a shared dependency.
        // For simplicity here, we load it again.
        try {
            val inputStream = context.assets.open("words.txt")
            wordSet = BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                lines.filter { it.isNotBlank() }.map { it.trim().lowercase() }.toSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dictionary: ${e.message}", e)
        }
    }

    override fun getSuggestions(input: String, context: SuggestionContext): List<PredictionCandidate> {
        if (input.length < 3 || context.isAfterSpace) {
            return emptyList()
        }

        val lowerInput = input.lowercase()
        val maxDistance = if (input.length <= 4) 1 else 2

        return wordSet
            .filter { abs(it.length - lowerInput.length) <= maxDistance }
            .mapNotNull { candidateWord ->
                val distance = levenshteinDistance(lowerInput, candidateWord)
                if (distance > 0 && distance <= maxDistance) {
                    // Score is higher for shorter distances
                    val score = (1.0 - (distance.toDouble() / lowerInput.length)) * 100.0
                    PredictionCandidate(candidateWord, score, PredictionSource.TYPO_CORRECTION, isCompletion = true)
                } else {
                    null
                }
            }
            .sortedByDescending { it.score }
            .take(2) // Take the top 2 corrections
    }

    // Levenshtein distance function for typo checking
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) {
            for (j in 0..s2.length) {
                when {
                    i == 0 -> dp[i][j] = j
                    j == 0 -> dp[i][j] = i
                    else -> {
                        val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                        dp[i][j] = min(
                            min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + cost
                        )
                    }
                }
            }
        }
        return dp[s1.length][s2.length]
    }
}