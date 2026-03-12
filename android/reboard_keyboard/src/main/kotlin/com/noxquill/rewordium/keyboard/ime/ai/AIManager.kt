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
 * Provides text rewriting capabilities using configurable AI providers
 * (Groq, OpenAI, Claude, Gemini, or custom) with various personas
 * and writing styles.
 */
class AIManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AIManager"
        
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
    
    // Cached AI configuration - refreshed on each request
    private var cachedConfig: AIConfigProvider.AIConfig? = null
    
    // API Key - for backward compatibility, can be overridden via setApiKey()
    private var overrideApiKey: String? = null
    
    // Current selected persona
    var currentPersona: AIPersona = AIPersona.CASUAL
        private set
    
    // Custom persona prompt
    var customPersonaPrompt: String = ""
        private set
    
    /**
     * Get current AI configuration from SharedPreferences
     * Always loads fresh config to ensure latest settings are used
     */
    private fun getConfig(): AIConfigProvider.AIConfig {
        // If we have an override API key set, use default Groq with that key
        if (!overrideApiKey.isNullOrBlank()) {
            return AIConfigProvider.AIConfig(
                isAdvancedEnabled = false,
                provider = AIConfigProvider.PROVIDER_GROQ,
                apiKey = overrideApiKey!!,
                model = "llama-3.3-70b-versatile",
                maxTokens = 2048,
                customEndpoint = ""
            )
        }
        
        // Always reload from SharedPreferences to get latest settings
        val config = AIConfigProvider.reloadConfig(context)
        Log.d(TAG, "Fresh config loaded: provider=${config.provider}, model=${config.model}, hasKey=${config.hasValidApiKey()}")
        return config
    }
    
    /**
     * Reload AI configuration from SharedPreferences
     * Call this when notified of settings changes
     */
    fun reloadConfig() {
        cachedConfig = AIConfigProvider.reloadConfig(context)
        Log.d(TAG, "Config reloaded: provider=${cachedConfig?.provider}, hasKey=${cachedConfig?.hasValidApiKey()}")
    }
    
    /**
     * Set the API key for Groq API (backward compatibility)
     */
    fun setApiKey(key: String) {
        overrideApiKey = key
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
     * Check if user is logged in by reading from SharedPreferences.
     * The Flutter app syncs login state to keyboard_settings prefs.
     */
    private fun isUserLoggedIn(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
            prefs.getBoolean("user_logged_in", false)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking login status", e)
            false
        }
    }
    
    /**
     * Make an API request with the given config and prompt
     */
    private suspend fun makeApiRequest(config: AIConfigProvider.AIConfig, systemPrompt: String, userPrompt: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Handle Gemini separately as it uses a different API format
                if (config.isGemini()) {
                    return@withContext makeGeminiRequest(config, systemPrompt, userPrompt)
                }
                
                val request = GroqRequest(
                    model = config.model,
                    messages = listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = userPrompt)
                    ),
                    temperature = 0.7,
                    maxTokens = config.maxTokens
                )
                
                val jsonBody = gson.toJson(request)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                
                val httpRequestBuilder = Request.Builder()
                    .url(config.getBaseUrl())
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                
                // Add appropriate auth header based on provider
                if (config.isClaude()) {
                    httpRequestBuilder.addHeader("x-api-key", config.apiKey)
                    httpRequestBuilder.addHeader("anthropic-version", "2023-06-01")
                } else {
                    httpRequestBuilder.addHeader("Authorization", config.getAuthHeader())
                }
                
                val httpRequest = httpRequestBuilder.build()
                
                Log.d(TAG, "Making API request to ${config.provider} at ${config.getBaseUrl()}")
                
                val response = client.newCall(httpRequest).execute()
                
                if (!response.isSuccessful) {
                    val errorCode = response.code
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "API request failed: $errorCode, body: $errorBody")
                    val errorMessage = when (errorCode) {
                        401 -> "⚠️ Invalid API key"
                        429 -> "⏳ Rate limit - wait a moment"
                        403 -> "🚫 API access forbidden"
                        500, 502, 503, 504 -> "🔧 AI service unavailable"
                        else -> "❌ Error $errorCode"
                    }
                    return@withContext Result.failure(AIException(errorMessage))
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("Empty response from API"))
                }
                
                Log.d(TAG, "Response body: ${responseBody.take(500)}")
                
                val groqResponse = gson.fromJson(responseBody, GroqResponse::class.java)
                val rewrittenText = groqResponse.choices?.firstOrNull()?.message?.content
                
                if (rewrittenText.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("No content in response"))
                }
                
                Result.success(rewrittenText.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Error making API request", e)
                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "⏱️ Request timed out"
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "📶 No internet connection"
                    else -> "❌ Network error"
                }
                Result.failure(AIException(errorMessage))
            }
        }
    }
    
    /**
     * Make a request to Google Gemini API
     * Gemini uses a different request/response format
     */
    private fun makeGeminiRequest(config: AIConfigProvider.AIConfig, systemPrompt: String, userPrompt: String): Result<String> {
        try {
            // Gemini request format
            val geminiRequest = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to "$systemPrompt\n\n$userPrompt")
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.7,
                    "maxOutputTokens" to config.maxTokens,
                    "topP" to 0.95,
                    "topK" to 40
                )
            )
            
            val jsonBody = gson.toJson(geminiRequest)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            
            // Gemini uses API key as query parameter
            val url = "${config.getBaseUrl()}?key=${config.apiKey}"
            
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            Log.d(TAG, "Making Gemini API request to: ${config.getBaseUrl()}")
            
            val response = client.newCall(httpRequest).execute()
            
            if (!response.isSuccessful) {
                val errorCode = response.code
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API request failed: $errorCode, body: $errorBody")
                val errorMessage = when (errorCode) {
                    400 -> "⚠️ Invalid request: check model"
                    401, 403 -> "⚠️ Invalid Gemini API key"
                    429 -> "⏳ Gemini rate limit - wait"
                    404 -> "❓ Model not found: ${config.model}"
                    500, 502, 503, 504 -> "🔧 Gemini unavailable"
                    else -> "❌ Gemini error $errorCode"
                }
                return Result.failure(AIException(errorMessage))
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return Result.failure(AIException("Empty response from Gemini"))
            }
            
            Log.d(TAG, "Gemini response: ${responseBody.take(500)}")
            
            // Parse Gemini response format
            val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
            val content = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (content.isNullOrBlank()) {
                return Result.failure(AIException("No content in Gemini response"))
            }
            
            return Result.success(content.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error making Gemini API request", e)
            return Result.failure(AIException("Gemini request failed: ${e.message}"))
        }
    }
    
    /**
     * Rewrite text using the current persona
     */
    suspend fun rewriteText(text: String, action: AIAction = AIAction.REWRITE): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("⚠️ Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("⚠️ No API key. Go to Settings → Advanced AI"))
        }
        
        if (text.isBlank()) {
            return Result.failure(AIException("No text to rewrite"))
        }
        
        val systemPrompt = buildSystemPrompt(action)
        val userPrompt = buildUserPrompt(text, action)
        
        return makeApiRequest(config, systemPrompt, userPrompt)
    }
    
    /**
     * Rewrite text using a custom prompt with persona, task, and length
     * This is used by the 3-row AI panel
     */
    suspend fun rewriteTextWithPrompt(fullPrompt: String, action: AIAction = AIAction.REWRITE): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("⚠️ Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("⚠️ No API key. Go to Settings → Advanced AI"))
        }
        
        if (fullPrompt.isBlank()) {
            return Result.failure(AIException("No text to rewrite"))
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
        
        return makeApiRequest(config, systemPrompt, fullPrompt)
    }

    /**
     * Generate contextual continuation text that flows naturally after the existing content.
     * Used by the "Add Below" mode in AI panels.
     */
    suspend fun continueText(existingText: String, persona: String = "", task: String = "", length: String = ""): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("⚠️ Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("⚠️ No API key. Go to Settings → Advanced AI"))
        }
        
        if (existingText.isBlank()) {
            return Result.failure(AIException("No text to continue from"))
        }
        
        val systemPrompt = """You are a skilled writer who continues and extends existing text naturally. Your job is to generate NEW content that flows seamlessly after the given text.

CRITICAL RULES:
1. Return ONLY the new continuation text - no explanations, no quotes, no "Here is...", no commentary
2. DO NOT repeat, rephrase, or rewrite ANY of the original text
3. Write content that naturally follows and extends what was already written
4. Match the tone, style, and context of the existing text
5. ALWAYS respond in ENGLISH regardless of input language
6. The continuation should feel like a natural next paragraph or section
7. Write like a thoughtful human, not a template

You're adding to existing text, not replacing it."""
        
        val fullPrompt = buildString {
            if (persona.isNotBlank()) append("Writing style: $persona. ")
            if (task.isNotBlank()) append("Purpose: $task. ")
            if (length.isNotBlank()) append("Length: $length. ")
            append("\n\nExisting text (DO NOT repeat this, write what comes NEXT):\n\n")
            append(existingText)
        }
        
        return makeApiRequest(config, systemPrompt, fullPrompt)
    }

    /**
     * Generate contextual continuation using persona/action enums (for compact AiPanel)
     */
    suspend fun continueTextWithAction(existingText: String, action: AIAction = AIAction.EXPAND): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("⚠️ Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("⚠️ No API key. Go to Settings → Advanced AI"))
        }
        
        if (existingText.isBlank()) {
            return Result.failure(AIException("No text to continue from"))
        }
        
        val personaDescription = when (currentPersona) {
            AIPersona.CASUAL -> "casual and conversational"
            AIPersona.ACADEMIC -> "academic and scholarly"
            AIPersona.POETRY -> "poetic and lyrical"
            AIPersona.PROFESSIONAL -> "professional and polished"
            AIPersona.FRIENDLY -> "warm and friendly"
            AIPersona.CUSTOM -> customPersonaPrompt.ifBlank { "natural and helpful" }
        }
        
        val taskInstruction = when (action) {
            AIAction.REWRITE -> "Continue the text with more content on the same topic."
            AIAction.EXPAND -> "Elaborate and expand on the ideas presented."
            AIAction.SUMMARIZE -> "Add a brief conclusion or summary paragraph."
            AIAction.FIX_GRAMMAR -> "Continue with well-structured, grammatically perfect prose."
            AIAction.MAKE_FORMAL -> "Add a formal continuation appropriate for business or academic contexts."
            AIAction.MAKE_CASUAL -> "Continue in a relaxed, conversational way."
        }
        
        val systemPrompt = """You are a skilled $personaDescription writer. $taskInstruction

CRITICAL RULES:
1. Return ONLY the new continuation text - no explanations, no quotes, no commentary
2. DO NOT repeat or rephrase ANY of the original text
3. Write content that naturally follows what was already written
4. ALWAYS respond in ENGLISH
5. Write like a thoughtful human"""
        
        return makeApiRequest(config, systemPrompt, "Continue after this text:\n\n$existingText")
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
     * Enhance a prompt for AI chat applications.
     * Makes the prompt more detailed, specific, and effective for getting
     * better responses from AI assistants like ChatGPT, Gemini, Claude, etc.
     *
     * @param prompt The user's original prompt
     * @param aiAppName Optional name of the AI app being used (for context-aware enhancement)
     * @return Enhanced prompt text
     */
    suspend fun enhancePrompt(prompt: String, aiAppName: String? = null): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("⚠️ Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("⚠️ No API key. Go to Settings → Advanced AI"))
        }
        
        if (prompt.isBlank()) {
            return Result.failure(AIException("No prompt to enhance"))
        }
        
        val targetContext = if (!aiAppName.isNullOrBlank()) {
            "The user is typing this prompt into $aiAppName. "
        } else {
            "The user is typing this prompt into an AI chat assistant. "
        }
        
        val systemPrompt = """You are an expert prompt engineer. Your job is to enhance and improve AI prompts to get better, more detailed, and more useful responses from AI assistants.

$targetContext

CRITICAL RULES:
1. Return ONLY the enhanced prompt text - no explanations, no quotes, no "Here is...", no commentary, no meta-text
2. DO NOT answer the prompt - only IMPROVE it
3. Keep the same intent and topic as the original prompt
4. Make the prompt more specific, detailed, and well-structured
5. Add relevant context, constraints, or formatting instructions where helpful
6. If the prompt is vague, add specificity while preserving the user's intent
7. Use clear, direct language - write the prompt as if the user wrote it themselves
8. Maintain first-person perspective where appropriate
9. DO NOT add "Please" or overly polite filler - keep it natural and efficient
10. The enhanced prompt should be 1.5x to 3x the length of the original, not excessively long
11. ALWAYS respond in the same language as the original prompt

ENHANCEMENT STRATEGIES:
- Add role/persona instructions if relevant (e.g., "Act as a...")
- Specify desired output format (bullet points, steps, table, etc.)
- Add constraints (length, tone, audience)
- Include relevant context the AI might need
- Break complex requests into clear sub-tasks
- Add "think step by step" for reasoning tasks
- Specify what to include AND what to avoid"""
        
        val userPrompt = "Enhance this prompt:\n\n$prompt"
        
        return makeApiRequest(config, systemPrompt, userPrompt)
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

// Gemini API Response data classes
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

data class GeminiContent(
    val parts: List<GeminiPart>?
)

data class GeminiPart(
    val text: String?
)
