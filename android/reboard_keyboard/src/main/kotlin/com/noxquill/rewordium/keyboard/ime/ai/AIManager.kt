/*
 * Copyright (C) 2024-2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.noxquill.rewordium.keyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AI Writing Assistant Manager for keyboard integration.
 * 
 * Provides text rewriting capabilities using the Groq API with various personas
 * and writing styles.
 */
class AIManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AIManager"
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b-versatile"
        
        // Default timeout values
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 60L
        private const val WRITE_TIMEOUT = 30L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // API Key - auto-initialized from BuildConfig
    private var apiKey: String? = BuildConfig.GROQ_API_KEY.takeIf { it.isNotBlank() }
    
    // Current selected persona
    var currentPersona: AIPersona = AIPersona.CASUAL
        private set
    
    // Custom persona prompt
    var customPersonaPrompt: String = ""
        private set
    
    /**
     * Set the API key for Groq API
     */
    fun setApiKey(key: String) {
        apiKey = key
    }
    
    /**
     * Set the current AI persona
     */
    fun setPersona(persona: AIPersona) {
        currentPersona = persona
    }
    
    /**
     * Set custom persona prompt
     */
    fun setCustomPrompt(prompt: String) {
        customPersonaPrompt = prompt
    }
    
    /**
     * Rewrite text using the current persona
     */
    suspend fun rewriteText(text: String, action: AIAction = AIAction.REWRITE): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val key = apiKey
                if (key.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("API key not set"))
                }
                
                if (text.isBlank()) {
                    return@withContext Result.failure(AIException("No text to rewrite"))
                }
                
                val systemPrompt = buildSystemPrompt(action)
                val userPrompt = buildUserPrompt(text, action)
                
                val request = GroqRequest(
                    model = MODEL,
                    messages = listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = userPrompt)
                    ),
                    temperature = 0.7,
                    maxTokens = 2048
                )
                
                val jsonBody = gson.toJson(request)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                
                val httpRequest = Request.Builder()
                    .url(BASE_URL)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(httpRequest).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed: ${response.code}")
                    return@withContext Result.failure(AIException("API request failed: ${response.code}"))
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("Empty response from API"))
                }
                
                val groqResponse = gson.fromJson(responseBody, GroqResponse::class.java)
                val rewrittenText = groqResponse.choices?.firstOrNull()?.message?.content
                
                if (rewrittenText.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("No content in response"))
                }
                
                Result.success(rewrittenText.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Error rewriting text", e)
                Result.failure(AIException("Failed to rewrite text: ${e.message}"))
            }
        }
    }
    
    /**
     * Rewrite text using a custom prompt with persona, task, and length
     * This is used by the 3-row AI panel
     */
    suspend fun rewriteTextWithPrompt(fullPrompt: String, action: AIAction = AIAction.REWRITE): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val key = apiKey
                if (key.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("API key not set"))
                }
                
                if (fullPrompt.isBlank()) {
                    return@withContext Result.failure(AIException("No text to rewrite"))
                }
                
                val systemPrompt = """You are a skilled human writer helping someone communicate better. Your goal is to sound completely natural and human-like.

CRITICAL RULES:
1. Return ONLY the final text - no explanations, no quotes, no "Here is...", no commentary
2. Match the original language exactly (Spanish input = Spanish output, etc.)
3. Sound like a real person wrote it - use natural phrasing, contractions, and flow
4. Preserve the speaker's intent and meaning precisely
5. Avoid robotic or corporate-sounding language
6. Follow the user's specific persona, task, and length instructions exactly

Think of yourself as a professional editor refining someone's message while keeping their authentic voice."""
                
                val request = GroqRequest(
                    model = MODEL,
                    messages = listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = fullPrompt)
                    ),
                    temperature = 0.7,
                    maxTokens = 2048
                )
                
                val jsonBody = gson.toJson(request)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                
                val httpRequest = Request.Builder()
                    .url(BASE_URL)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(httpRequest).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed: ${response.code}")
                    return@withContext Result.failure(AIException("API request failed: ${response.code}"))
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("Empty response from API"))
                }
                
                val groqResponse = gson.fromJson(responseBody, GroqResponse::class.java)
                val rewrittenText = groqResponse.choices?.firstOrNull()?.message?.content
                
                if (rewrittenText.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("No content in response"))
                }
                
                Result.success(rewrittenText.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Error rewriting text with prompt", e)
                Result.failure(AIException("Failed to rewrite text: ${e.message}"))
            }
        }
    }
    
    private fun buildSystemPrompt(action: AIAction): String {
        val personaDescription = when (currentPersona) {
            AIPersona.CASUAL -> "Write like you're texting a friend - relaxed, natural, maybe throw in some contractions. Keep it real and easy to read."
            AIPersona.ACADEMIC -> "Write with scholarly precision and intellectual depth. Use proper terminology and maintain a thoughtful, well-reasoned tone."
            AIPersona.POETRY -> "Let your words flow with rhythm and beauty. Use vivid imagery, metaphors, and expressive language that stirs emotions."
            AIPersona.PROFESSIONAL -> "Write with clarity and polish, suitable for business contexts. Be direct, confident, and respectful."
            AIPersona.FRIENDLY -> "Write with warmth and genuine care. Be encouraging, supportive, and make the reader feel valued."
            AIPersona.CUSTOM -> customPersonaPrompt.ifBlank { "Write naturally and helpfully, matching the tone of the original." }
        }
        
        val actionInstruction = when (action) {
            AIAction.REWRITE -> "Rephrase this while keeping the same meaning. Make it flow naturally."
            AIAction.EXPAND -> "Build on this text with more detail and depth, but keep it interesting."
            AIAction.SUMMARIZE -> "Capture the essence in fewer words without losing what matters."
            AIAction.FIX_GRAMMAR -> "Fix any grammar, spelling, or punctuation issues. Keep everything else the same."
            AIAction.MAKE_FORMAL -> "Make this sound more polished and professional while keeping the meaning."
            AIAction.MAKE_CASUAL -> "Make this sound more relaxed and conversational, like talking to a friend."
        }
        
        return """$personaDescription

$actionInstruction

CRITICAL: Return ONLY the final text. No quotes, no "Here is...", no explanations.
Match the original language. Sound like a real person wrote it."""
    }
    
    private fun buildUserPrompt(text: String, action: AIAction): String {
        return text
    }
    
    /**
     * Get a random creative persona for inspiration
     */
    fun getRandomCreativePersona(): String {
        return randomPersonas.random()
    }
    
    private val randomPersonas = listOf(
        "a witty pirate captain who speaks in nautical metaphors",
        "a sophisticated British detective with keen observation skills",
        "a cheerful Disney character who sees magic in everything",
        "a wise ancient philosopher pondering life's mysteries",
        "a sassy valley girl with attitude and style",
        "a dramatic Shakespearean actor with poetic flair",
        "a curious scientist explaining everything with wonder",
        "a friendly neighborhood superhero giving encouragement",
        "a zen master speaking in calm, mindful phrases",
        "a passionate Italian chef describing life like cooking",
        "a tech-savvy millennial using modern slang and references",
        "a mystical fortune teller revealing hidden meanings",
        "a quirky professor who loves fun facts and trivia",
        "a motivational life coach spreading positivity",
        "a rebellious teenager with a unique perspective on life"
    )
}

/**
 * AI Personas for text rewriting
 */
enum class AIPersona {
    CASUAL,
    ACADEMIC,
    POETRY,
    PROFESSIONAL,
    FRIENDLY,
    CUSTOM
}

/**
 * AI Actions for text transformation
 */
enum class AIAction {
    REWRITE,
    EXPAND,
    SUMMARIZE,
    FIX_GRAMMAR,
    MAKE_FORMAL,
    MAKE_CASUAL
}

/**
 * Exception class for AI-related errors
 */
class AIException(message: String) : Exception(message)

// Groq API Request/Response data classes
data class GroqRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048
)

data class Message(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: Message?
)
