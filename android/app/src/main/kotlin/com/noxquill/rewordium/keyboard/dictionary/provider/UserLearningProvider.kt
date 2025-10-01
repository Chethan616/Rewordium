package com.noxquill.rewordium.keyboard.dictionary.provider

import android.content.Context
import android.content.SharedPreferences
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionCandidate
import com.noxquill.rewordium.keyboard.dictionary.model.PredictionSource
import com.noxquill.rewordium.keyboard.dictionary.model.SuggestionContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UserLearningProvider : ISuggestionProvider {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    // --- START OF CHANGES ---
    // A map of individual words to their usage frequency
    private var userDictionary: MutableMap<String, Int> = mutableMapOf()
    // A map of a word to a map of its next words and their frequencies
    private var userBigrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    // --- END OF CHANGES ---

    private val PREFS_NAME = "user_suggestions"
    private val KEY_USER_DICTIONARY = "user_dictionary"
    private val KEY_USER_BIGRAMS = "user_bigrams" // New key for saving bigrams

    override suspend fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadUserData()
    }

    private fun loadUserData() {
        // Load single words
        prefs.getString(KEY_USER_DICTIONARY, null)?.let { json ->
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            userDictionary = gson.fromJson(json, type)
        }
        // Load next-word pairs (bigrams)
        prefs.getString(KEY_USER_BIGRAMS, null)?.let { json ->
            val type = object : TypeToken<MutableMap<String, MutableMap<String, Int>>>() {}.type
            userBigrams = gson.fromJson(json, type)
        }
    }

    // --- UPDATED: Learn single words and next-word pairs ---
    fun learn(currentWord: String, previousWord: String?) {
        val cleanCurrent = currentWord.trim().lowercase()
        if (cleanCurrent.length < 2) return

        // Learn the individual word
        userDictionary[cleanCurrent] = (userDictionary[cleanCurrent] ?: 0) + 1

        // If there was a previous word, learn the sequence
        if (previousWord != null) {
            val cleanPrevious = previousWord.trim().lowercase()
            if (cleanPrevious.isNotEmpty()) {
                val nextWordsMap = userBigrams.getOrPut(cleanPrevious) { mutableMapOf() }
                nextWordsMap[cleanCurrent] = (nextWordsMap[cleanCurrent] ?: 0) + 1
            }
        }
        saveUserData()
    }

    private fun saveUserData() {
        prefs.edit()
            .putString(KEY_USER_DICTIONARY, gson.toJson(userDictionary))
            .putString(KEY_USER_BIGRAMS, gson.toJson(userBigrams))
            .apply()
    }

    override fun getSuggestions(input: String, context: SuggestionContext): List<PredictionCandidate> {
        val candidates = mutableListOf<PredictionCandidate>()

        if (context.isAfterSpace && context.previousWord != null) {
            // --- Gboard Feature: Suggest next word based on user history ---
            val nextWords = userBigrams[context.previousWord.lowercase()]
            if (nextWords != null) {
                val userNextWordCandidates = nextWords.entries
                    .sortedByDescending { it.value }
                    .map { (word, frequency) ->
                        PredictionCandidate(
                            word,
                            frequency.toDouble() * 20, // High score for learned sequences
                            PredictionSource.USER_LEARNED_NEXT_WORD
                        )
                    }
                candidates.addAll(userNextWordCandidates)
            }
        } else if (!context.isAfterSpace && input.isNotEmpty()) {
            // --- Suggest completions from user's dictionary ---
            val lowerInput = input.lowercase()
            val userCompletionCandidates = userDictionary.entries
                .filter { it.key.startsWith(lowerInput) }
                .sortedByDescending { it.value }
                .map { (word, frequency) ->
                    val source = if (word == lowerInput) PredictionSource.USER_LEARNED_EXACT else PredictionSource.USER_LEARNED_COMPLETION
                    PredictionCandidate(word, frequency.toDouble() * 10, source, isCompletion = true)
                }
            candidates.addAll(userCompletionCandidates)
        }
        return candidates.take(3)
    }
}