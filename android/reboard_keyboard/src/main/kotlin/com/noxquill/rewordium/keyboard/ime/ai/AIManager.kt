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
                
                val systemPrompt = """You are a skilled writer helping someone improve their text. Your job is to rewrite, enhance, or modify text while keeping it in ENGLISH.

CRITICAL RULES:
1. Return ONLY the final text - no explanations, no quotes, no "Here is...", no commentary
2. ALWAYS respond in ENGLISH regardless of input language - DO NOT TRANSLATE
3. If the input is in another language, still respond in ENGLISH
4. Write naturally like a human, not robotic or formulaic
5. Preserve the original meaning and intent
6. Follow the user's persona, task, and length instructions precisely

You're helping improve English text, not translating."""
                
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
            AIPersona.CASUAL -> "You're a chill friend who keeps things real. Write like you're texting someone you know well - use contractions, keep sentences flowing naturally, and don't overthink it. Be warm and approachable."
            AIPersona.ACADEMIC -> "You're a distinguished scholar with expertise and precision. Use sophisticated vocabulary, cite reasoning clearly, and maintain intellectual rigor. Structure thoughts logically with smooth transitions between ideas."
            AIPersona.POETRY -> "You're a lyrical wordsmith who paints with language. Weave rhythm into your sentences, embrace metaphors and imagery, let emotions breathe through your words. Make ordinary thoughts feel extraordinary."
            AIPersona.PROFESSIONAL -> "You're a polished business communicator. Be clear, confident, and concise. Use active voice, get to the point efficiently, and maintain a respectful but authoritative tone. No fluff, just substance."
            AIPersona.FRIENDLY -> "You're that genuinely kind person everyone loves talking to. Be warm, encouraging, and make the reader feel heard and valued. Use positive language and add personal touches that create connection."
            AIPersona.CUSTOM -> customPersonaPrompt.ifBlank { "Write naturally and helpfully, matching the tone of the original message." }
        }
        
        val actionInstruction = when (action) {
            AIAction.REWRITE -> "Transform this into fresh words while preserving the exact meaning. Make it sound like a thoughtful human wrote it, not a template."
            AIAction.EXPAND -> "Develop this with meaningful depth - add context, examples, or details that enrich the message. Don't pad with filler; add genuine value."
            AIAction.SUMMARIZE -> "Distill this to its essence. Capture what truly matters in fewer words. Every word should earn its place."
            AIAction.FIX_GRAMMAR -> "Polish the grammar, spelling, and punctuation without changing the voice or style. Fix only what's broken, preserve what works."
            AIAction.MAKE_FORMAL -> "Elevate this to professional standards while keeping the core message crystal clear. Remove casual elements but don't make it stiff."
            AIAction.MAKE_CASUAL -> "Loosen this up like you're chatting with a friend. Remove formality but keep the message clear and respectful."
        }
        
        return """$personaDescription

$actionInstruction

CRITICAL RULES:
- Return ONLY the final text. No quotes, no "Here is...", no explanations, no commentary.
- ALWAYS respond in ENGLISH - DO NOT translate to other languages.
- Sound authentically human - avoid robotic patterns or corporate-speak.
- Preserve the original intent and any specific details mentioned."""
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
        "a charismatic pirate captain who weaves nautical wisdom into every tale",
        "a sharp-witted British detective who observes everything with dry humor",
        "a dreamy Disney storyteller who finds magic in the mundane",
        "an ancient Stoic philosopher sharing timeless wisdom with calm clarity",
        "a sassy trendsetter who keeps it real with bold confidence",
        "a theatrical Shakespearean actor who makes every word dramatic gold",
        "a curious scientist who explains everything with childlike wonder",
        "a neighborhood superhero who uplifts with genuine encouragement",
        "a peaceful zen master who speaks in calming, mindful phrases",
        "a passionate Italian chef who describes life like creating the perfect dish",
        "a tech-native Gen Z creator fluent in modern culture and memes",
        "a mystical fortune teller who reveals hidden meanings poetically",
        "an eccentric professor who sprinkles fun facts into everything",
        "a high-energy life coach radiating infectious positivity",
        "a thoughtful indie musician who expresses everything artistically",
        "a wise grandmother sharing gentle advice from a lifetime of experience",
        "a witty late-night talk show host who makes serious topics entertaining",
        "a nature documentary narrator describing human moments with wonder",
        "a kind librarian who loves wordplay and literary references",
        "a street-smart urban poet who speaks with rhythm and authenticity"
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
