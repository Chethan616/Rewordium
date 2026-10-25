package com.noxquill.rewordium.data

import android.content.Context
import com.noxquill.rewordium.service.AIConfigProvider
import com.noxquill.rewordium.service.DynamicRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WritingRepository {
    private const val MAX_CHARS = 8000

    suspend fun generate(
        context: Context,
        mode: String,
        text: String,
        tone: String = "Clear",
        targetLanguage: String = "English",
    ): String = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Add some text first." }
        require(text.length <= MAX_CHARS) { "Keep each request under $MAX_CHARS characters." }
        val config = AIConfigProvider.getConfig(context)
        require(config.hasValidApiKey()) {
            "Add an API key in Settings → AI preferences, or set the app’s default Groq key in the build environment."
        }
        val prompt = when (mode) {
            "grammar" -> "Proofread the text for grammar, spelling, and punctuation. Preserve its meaning and voice. Return only the corrected text.\n\n$text"
            "summarizer" -> "Summarize the text in a concise, useful way. Keep key facts and decisions.\n\n$text"
            "translator" -> "Translate the text into $targetLanguage. Preserve tone, meaning, and formatting. Return only the translation.\n\n$text"
            "tone" -> "Rewrite the text in a $tone tone. Keep the same meaning and avoid adding facts. Return only the revised text.\n\n$text"
            "detector" -> "Assess the writing below for patterns commonly associated with AI-generated prose. Be careful not to claim certainty. Give a short estimate, explain the strongest signals, and remind the reader this is not proof of authorship.\n\n$text"
            "document" -> "Improve this document for clarity, flow, and correctness while preserving its facts and structure.\n\n$text"
            else -> "Rewrite the text so it reads naturally and clearly while keeping the original meaning. Return only the revised text.\n\n$text"
        }
        val response = DynamicRetrofitClient.makeCompletionRequest(config, prompt)
        if (!response.isSuccessful) {
            throw IllegalStateException("The writing service returned ${response.code()}. Try again in a moment.")
        }
        response.body()?.choices?.firstOrNull()?.message?.content?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("The writing service returned an empty response. Please try again.")
    }

    suspend fun chat(context: Context, prompt: String, conversation: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            require(prompt.isNotBlank()) { "Write a message for Jade first." }
            val config = AIConfigProvider.getConfig(context)
            require(config.hasValidApiKey()) {
                "Add an API key in Settings → AI preferences, or set the app’s default Groq key in the build environment."
            }
            val contextText = conversation.takeLast(6).joinToString("\n") { (role, message) -> "$role: $message" }
            val fullPrompt = buildString {
                append("You are Jade, Rewordium’s warm, thoughtful writing companion. Be concise, kind, and practical. Help with writing, phrasing, grammar, tone, and ideas.\n")
                if (contextText.isNotBlank()) append("Recent conversation:\n$contextText\n\n")
                append("User: ").append(prompt)
            }
            val response = DynamicRetrofitClient.makeCompletionRequest(config, fullPrompt)
            if (!response.isSuccessful) throw IllegalStateException("Jade could not reply (${response.code()}). Try again.")
            response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                ?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Jade returned an empty response. Please try again.")
        }
}
