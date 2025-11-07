package com.noxquill.rewordium.keyboard.dictionary.model

data class SuggestionContext(
    val currentInput: String,
    val isAfterSpace: Boolean, // True if the last character typed was a space
    val previousWord: String? = null // The word just before the space
)

data class PredictionCandidate(
    val word: String,
    val score: Double,
    val source: PredictionSource,
    val isCompletion: Boolean = false
)

enum class PredictionSource(val weight: Double) {
    // Top tier: High-confidence actions
    CONTRACTION(1.6),
    TYPO_CORRECTION(1.5),

    // High tier: User has explicitly typed this before
    USER_LEARNED_NEXT_WORD(1.4),
    USER_LEARNED_EXACT(1.3),
    USER_LEARNED_COMPLETION(1.2),

    // Mid tier: Standard dictionary behavior
    NEXT_WORD_PREDICTION(1.1),
    PREFIX_COMPLETION(1.0),

    // Low tier: A basic fallback
    EXACT_MATCH(0.9)
}