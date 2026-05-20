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
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.noxquill.rewordium.keyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.tasks.await
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
        /**
         * Post-process AI response to block identity/trivial answers.
         * Also strips qwen3's chain-of-thought <think>...</think> blocks.
         * Returns null if the response should be suppressed.
         */
        private fun filterUnwantedResponses(response: String): String? {
            // Strip chain-of-thought <think>...</think> blocks first
            val cleaned = response.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()
            val lower = cleaned.lowercase()
            val identityPatterns = listOf(
                "i am an ai", "as an ai", "i am a language model", "i am artificial intelligence",
                "i am an artificial intelligence", "i am a machine", "i am not human", "as a language model"
            )
            val trivialPatterns = listOf(
                "2+2=4", "the answer is 4", "the answer is four", "as an ai, i cannot", "i do not have feelings"
            )
            if (identityPatterns.any { lower.contains(it) }) return null
            if (trivialPatterns.any { lower.contains(it) }) return null
            // Block generic meta openers
            if (lower.startsWith("as an ai")) return null
            // Block empty or nonsense
            if (lower.isBlank() || lower == "null" || lower == "n/a") return null
            return cleaned
        }
    
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
                model = "qwen/qwen3-32b",
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

    private fun isProUser(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
            prefs.getBoolean("user_is_pro", false)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking pro status", e)
            false
        }
    }

    private fun getCachedCredits(): Int {
        return try {
            val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
            prefs.getInt("user_credits", 0)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cached credits", e)
            0
        }
    }

    private fun isUsingExternalApi(config: AIConfigProvider.AIConfig): Boolean {
        // Advanced mode means the user is using their own API configuration.
        return config.isAdvancedEnabled
    }

    private fun updateLocalCreditState(
        isLoggedIn: Boolean = true,
        isPro: Boolean,
        credits: Int,
    ) {
        val normalizedCredits = credits.coerceAtLeast(0)

        val keyboardPrefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        keyboardPrefs.edit()
            .putBoolean("user_logged_in", isLoggedIn)
            .putBoolean("user_is_pro", isPro)
            .putInt("user_credits", normalizedCredits)
            .apply()

        val accessibilityPrefs = context.getSharedPreferences("rewordium_user_status", Context.MODE_PRIVATE)
        accessibilityPrefs.edit()
            .putBoolean("is_logged_in_user", isLoggedIn)
            .putBoolean("is_pro_user", isPro)
            .putInt("user_credits", normalizedCredits)
            .apply()

        val statusIntent = Intent("com.noxquill.rewordium.ACCESSIBILITY_USER_STATUS_UPDATED")
        statusIntent.putExtra("isLoggedIn", isLoggedIn)
        statusIntent.putExtra("isPro", isPro)
        statusIntent.putExtra("credits", normalizedCredits)
        statusIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(statusIntent)
    }

    private suspend fun verifyCreditAvailabilityForRequest(config: AIConfigProvider.AIConfig): Result<Unit> {
        if (isUsingExternalApi(config) || isProUser()) {
            return Result.success(Unit)
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(AIException("Please log in to use AI features"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val userSnap = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (!userSnap.exists()) {
                    return@withContext Result.failure(AIException("Unable to verify credits"))
                }

                val remoteIsPro = userSnap.getBoolean("isPro") ?: false
                val remoteCredits = (userSnap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
                updateLocalCreditState(isLoggedIn = true, isPro = remoteIsPro, credits = remoteCredits)

                if (remoteIsPro || remoteCredits > 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(AIException("Out of credits. Please upgrade to continue."))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Credit availability sync failed, using cached keyboard state", e)
                if (getCachedCredits() > 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(AIException("Out of credits. Please upgrade to continue."))
                }
            }
        }
    }

    private suspend fun consumeCreditAfterSuccess(config: AIConfigProvider.AIConfig): Result<Unit> {
        if (isUsingExternalApi(config) || isProUser()) {
            return Result.success(Unit)
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(AIException("Please log in to use AI features"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val userRef = FirebaseFirestore.getInstance().collection("users").document(uid)

                val consumeResult = FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val userSnap = transaction.get(userRef)

                    if (!userSnap.exists()) {
                        return@runTransaction Triple(false, false, 0)
                    }

                    val remoteIsPro = userSnap.getBoolean("isPro") ?: false
                    if (remoteIsPro) {
                        return@runTransaction Triple(true, true, 0)
                    }

                    val currentCredits = (userSnap.getLong("credits") ?: 0L).toInt()
                    if (currentCredits <= 0) {
                        return@runTransaction Triple(false, false, 0)
                    }

                    val updatedCredits = currentCredits - 1
                    transaction.update(
                        userRef,
                        mapOf(
                            "credits" to updatedCredits,
                            "lastUpdated" to FieldValue.serverTimestamp(),
                        ),
                    )

                    Triple(true, false, updatedCredits)
                }.await()

                val actionAllowed = consumeResult.first
                val remoteIsPro = consumeResult.second
                val remainingCredits = consumeResult.third

                updateLocalCreditState(
                    isLoggedIn = true,
                    isPro = remoteIsPro,
                    credits = remainingCredits,
                )

                if (actionAllowed) {
                    Result.success(Unit)
                } else {
                    Result.failure(AIException("Out of credits. Please upgrade to continue."))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to consume keyboard credit", e)
                Result.failure(AIException("Unable to consume credit. Please try again."))
            }
        }
    }
    
    /**
     * Make an API request with the given config and prompt
     */
    private suspend fun makeApiRequest(config: AIConfigProvider.AIConfig, systemPrompt: String, userPrompt: String, overrideMaxTokens: Int? = null): Result<String> {
        val creditAvailability = verifyCreditAvailabilityForRequest(config)
        if (creditAvailability.isFailure) {
            return Result.failure(
                creditAvailability.exceptionOrNull()
                    ?: AIException("Out of credits. Please upgrade to continue."),
            )
        }

        val requestResult = withContext(Dispatchers.IO) {
            try {
                // Handle Gemini separately as it uses a different API format.
                if (config.isGemini()) {
                    return@withContext makeGeminiRequest(config, systemPrompt, userPrompt, overrideMaxTokens)
                }

                val request = GroqRequest(
                    model = config.model,
                    messages = listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = userPrompt)
                    ),
                    temperature = 0.9,
                    topP = 0.95,
                    presencePenalty = 0.3,
                    maxTokens = overrideMaxTokens ?: config.maxTokens
                )
                val jsonBody = gson.toJson(request)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val httpRequestBuilder = Request.Builder()
                    .url(config.getBaseUrl())
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)

                // Add appropriate auth header based on provider.
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
                        401 -> "Invalid API key"
                        429 -> "Rate limit — try again later"
                        403 -> "API access forbidden"
                        500, 502, 503, 504 -> "AI service unavailable"
                        else -> "Error $errorCode"
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
                val filtered = rewrittenText?.let { filterUnwantedResponses(it) }
                if (filtered.isNullOrBlank()) {
                    return@withContext Result.failure(AIException("No usable content in response"))
                }

                Result.success(filtered)
            } catch (e: Exception) {
                Log.e(TAG, "Error making API request", e)
                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Request timed out"
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "No internet connection"
                    else -> "Network error"
                }
                Result.failure(AIException(errorMessage))
            }
        }

        if (requestResult.isFailure) {
            return requestResult
        }

        val consumeResult = consumeCreditAfterSuccess(config)
        if (consumeResult.isFailure) {
            return Result.failure(
                consumeResult.exceptionOrNull()
                    ?: AIException("Out of credits. Please upgrade to continue."),
            )
        }

        return requestResult
    }
    
    /**
     * Make a request to Google Gemini API
     * Gemini uses a different request/response format
     */
    private fun makeGeminiRequest(config: AIConfigProvider.AIConfig, systemPrompt: String, userPrompt: String, overrideMaxTokens: Int? = null): Result<String> {
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
                    "maxOutputTokens" to (overrideMaxTokens ?: config.maxTokens),
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
                    400 -> "Invalid request: check model"
                    401, 403 -> "Invalid Gemini API key"
                    429 -> "Gemini rate limit — try later"
                    404 -> "Model not found: ${config.model}"
                    500, 502, 503, 504 -> "Gemini unavailable"
                    else -> "Gemini error $errorCode"
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
            val filtered = content?.let { filterUnwantedResponses(it) }
            if (filtered.isNullOrBlank()) {
                return Result.failure(AIException("No usable content in Gemini response"))
            }
            return Result.success(filtered)
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
            return Result.failure(AIException("Please log in to use AI features"))
        }

        val config = getConfig()

        // Route to fast model for quick actions, standard model for full rewrites
        val routedConfig = if (!config.isAdvancedEnabled) {
            val routedModel = when (action) {
                AIAction.FIX_GRAMMAR -> "qwen/qwen3-32b"
                AIAction.REWRITE, AIAction.MAKE_FORMAL, AIAction.MAKE_CASUAL -> "qwen/qwen3-32b"
                else -> "qwen/qwen3-32b"
            }
            config.copy(model = routedModel)
        } else config
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("No API key. Go to Settings → Advanced AI"))
        }
        
        if (text.isBlank()) {
            return Result.failure(AIException("No text to rewrite"))
        }
        
        val systemPrompt = buildSystemPrompt(action)
        val userPrompt = buildUserPrompt(text, action)

        return makeApiRequest(routedConfig, systemPrompt, userPrompt, overrideMaxTokens = 300)
    }
    
    /**
     * Rewrite text using a custom prompt with persona, task, and length
     * This is used by the 3-row AI panel
     */
    suspend fun rewriteTextWithPrompt(fullPrompt: String, action: AIAction = AIAction.REWRITE): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("No API key. Go to Settings → Advanced AI"))
        }
        
        if (fullPrompt.isBlank()) {
            return Result.failure(AIException("No text to rewrite"))
        }

        val actionGuidance = when (action) {
            AIAction.REWRITE -> "Rewrite for clarity while preserving meaning."
            AIAction.EXPAND -> "Expand with useful detail, context, or examples without changing core intent."
            AIAction.SUMMARIZE -> "Summarize to the essential points while preserving key facts."
            AIAction.FIX_GRAMMAR -> "Fix grammar, spelling, and punctuation with minimal voice changes."
            AIAction.MAKE_FORMAL -> "Make the output professional and formal without sounding stiff."
            AIAction.MAKE_CASUAL -> "Make the output natural and conversational while staying respectful."
        }
        
        val systemPrompt = """<system_instructions>
You are an expert mobile writing assistant.
You will receive a structured request containing STYLE, INTENT, LENGTH, ACTION, and SOURCE_TEXT.
Apply all relevant instructions to SOURCE_TEXT and return the final transformed text.
</system_instructions>

<action_priority>
$actionGuidance
</action_priority>

<constraints>
1. Return ONLY the final transformed text. No labels, no quotes, no markdown wrappers, no commentary.
2. Preserve names, facts, numbers, dates, links, and logical intent unless instructions explicitly ask to change them.
3. Keep the same language as SOURCE_TEXT.
4. Keep the original formatting blocks if useful.
5. If instructions conflict, prioritize preserving user intent and factual accuracy.
</constraints>"""
        
        return makeApiRequest(config, systemPrompt, fullPrompt, overrideMaxTokens = 700)
    }

    /**
     * Generate contextual continuation text that flows naturally after the existing content.
     * Used by the "Add Below" mode in AI panels.
     */
    suspend fun continueText(existingText: String, persona: String = "", task: String = "", length: String = ""): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("No API key. Go to Settings → Advanced AI"))
        }
        
        if (existingText.isBlank()) {
            return Result.failure(AIException("No text to continue from"))
        }
        
        val systemPrompt = """You are an expert continuation writer.

You will receive STYLE, INTENT, LENGTH, and EXISTING_TEXT.
Write only the next part that should come after EXISTING_TEXT.

CRITICAL RULES:
1. Return ONLY new continuation text. No labels, no quotes, no commentary.
2. Do NOT repeat, paraphrase, or summarize EXISTING_TEXT.
3. Keep the same language as EXISTING_TEXT unless INTENT explicitly requests translation.
4. Match tone, pacing, and point of view from EXISTING_TEXT.
5. Keep continuity of entities, tense, and facts.
6. Respect STYLE, INTENT, and LENGTH instructions when provided.
7. Continue naturally from the final idea in EXISTING_TEXT.
8. Avoid boilerplate openers like "Sure" or "Here is"."""
        
        val fullPrompt = buildString {
            appendLine("STYLE: ${if (persona.isNotBlank()) persona else "Match original voice"}")
            appendLine("INTENT: ${if (task.isNotBlank()) task else "Natural continuation"}")
            appendLine("LENGTH: ${if (length.isNotBlank()) length else "Match source pacing"}")
            appendLine()
            appendLine("EXISTING_TEXT:")
            append(existingText)
        }
        
        return makeApiRequest(config, systemPrompt, fullPrompt)
    }

    /**
     * Generate contextual continuation using persona/action enums (for compact AiPanel)
     */
    suspend fun continueTextWithAction(existingText: String, action: AIAction = AIAction.EXPAND): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("No API key. Go to Settings → Advanced AI"))
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
        
        val systemPrompt = """<system_instructions>
You are a skilled $personaDescription writer. $taskInstruction
</system_instructions>

<constraints>
1. Return ONLY the new continuation text - no explanations, no quotes, no commentary.
2. DO NOT repeat or rephrase ANY of the original text.
3. Write content that naturally follows what was already written.
4. Keep the same language as the original text unless the task explicitly asks translation.
5. Maintain continuity in tone, tense, entities, and facts.
6. Write like a thoughtful human.
</constraints>"""
        
        val userPrompt = buildString {
            appendLine("<task>")
            appendLine(taskInstruction)
            appendLine("</task>")
            appendLine()
            appendLine("<existing_text>")
            append(existingText)
            appendLine("</existing_text>")
        }

        return makeApiRequest(config, systemPrompt, userPrompt, overrideMaxTokens = 512)
    }

    /**
     * Context-aware polish mode.
     * Fixes grammar/clarity while preserving slang, tone, and the user's original voice.
     */
    suspend fun contextPolishText(text: String): Result<String> {
        if (!isUserLoggedIn()) {
            return Result.failure(AIException("Please log in to use AI features"))
        }

        val config = getConfig()

        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("No API key. Go to Settings → Advanced AI"))
        }

        if (text.isBlank()) {
            return Result.failure(AIException("No text to improve"))
        }

        val systemPrompt = """<system_instructions>
You are a contextual writing editor. Your job is to improve grammar and readability while strictly preserving the writer's identity.
</system_instructions>

<constraints>
1. Return ONLY the edited text. No explanations, no quotes, no markdown.
2. Keep the same language as the input.
3. Preserve slang, colloquial phrases, abbreviations, and casual style unless they make the text unclear.
4. Keep sentence order and structure as close as possible to the original.
5. Fix grammar, punctuation, and obvious typos with minimal edits.
6. Do NOT over-formalize. Keep the exact vibe and personality.
7. Do NOT expand or summarize. Keep roughly the same length.
8. If a phrase is intentionally informal but understandable, keep it.
</constraints>"""

        val userPrompt = "<input_text>\n$text\n</input_text>"

        return makeApiRequest(config, systemPrompt, userPrompt, overrideMaxTokens = 256)
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
        return """<system_instructions>
$personaDescription
</system_instructions>

<directive>
$actionInstruction
</directive>

<constraints>
1. Return ONLY the final text. No quotes, no "Here is...", no explanations, no commentary.
2. NEVER reveal you are an AI, language model, or machine. NEVER answer questions about your identity.
3. NEVER answer trivial math or fact queries (e.g., "2+2", "who are you").
4. Keep the same language as the input text — do NOT translate unless explicitly asked.
5. Sound authentically human — avoid robotic patterns or corporate-speak.
6. Preserve the original intent and any specific details mentioned.
</constraints>"""
    }
    
    private fun buildUserPrompt(text: String, action: AIAction): String {
        return "<input_text>\n$text\n</input_text>"
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
            return Result.failure(AIException("Please log in to use AI features"))
        }

        val config = getConfig()
        
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("No API key. Go to Settings → Advanced AI"))
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
     * Stream a rewrite via SSE. Emits tokens one at a time as they arrive.
     * Falls back to the non-streaming path when ENABLE_STREAMING_AI is off.
     */
    suspend fun rewriteStreaming(
        text: String,
        action: AIAction = AIAction.REWRITE,
    ): Flow<String> {
        if (!isUserLoggedIn()) throw AIException("Please log in to use AI features")
        val config = getConfig()
        if (!config.hasValidApiKey()) throw AIException("No API key. Go to Settings → Advanced AI")
        if (text.isBlank()) throw AIException("No text to rewrite")

        verifyCreditAvailabilityForRequest(config).getOrElse { throw it }

        return if (BuildConfig.ENABLE_STREAMING_AI && !config.isGemini()) {
            val systemPrompt = buildSystemPrompt(action)
            val userPrompt = buildUserPrompt(text, action)
            makeApiRequestStreaming(config, systemPrompt, userPrompt, overrideMaxTokens = 300)
                .onCompletion { cause -> if (cause == null) consumeCreditAfterSuccess(config) }
        } else {
            // Fallback: emit the full response as a single token
            val result = makeApiRequest(config, buildSystemPrompt(action), buildUserPrompt(text, action), overrideMaxTokens = 300)
            channelFlow { send(result.getOrElse { throw it }) }
        }
    }

    /**
     * Stream a rewrite-with-prompt via SSE (used by 3-row AI panel).
     */
    suspend fun rewriteTextWithPromptStreaming(
        fullPrompt: String,
        action: AIAction = AIAction.REWRITE,
    ): Flow<String> {
        if (!isUserLoggedIn()) throw AIException("Please log in to use AI features")
        val config = getConfig()
        if (!config.hasValidApiKey()) throw AIException("No API key. Go to Settings → Advanced AI")
        if (fullPrompt.isBlank()) throw AIException("No text to rewrite")

        verifyCreditAvailabilityForRequest(config).getOrElse { throw it }

        val actionGuidance = when (action) {
            AIAction.REWRITE -> "Rewrite for clarity while preserving meaning."
            AIAction.EXPAND -> "Expand with useful detail, context, or examples without changing core intent."
            AIAction.SUMMARIZE -> "Summarize to the essential points while preserving key facts."
            AIAction.FIX_GRAMMAR -> "Fix grammar, spelling, and punctuation with minimal voice changes."
            AIAction.MAKE_FORMAL -> "Make the output professional and formal without sounding stiff."
            AIAction.MAKE_CASUAL -> "Make the output natural and conversational while staying respectful."
        }
        val systemPrompt = """<system_instructions>
You are an expert mobile writing assistant.
Apply all relevant instructions to SOURCE_TEXT and return the final transformed text.
</system_instructions>
<action_priority>$actionGuidance</action_priority>
<constraints>
1. Return ONLY the final transformed text. No labels, no quotes, no markdown, no commentary.
2. Keep the same language as SOURCE_TEXT.
</constraints>"""

        return if (BuildConfig.ENABLE_STREAMING_AI && !config.isGemini()) {
            makeApiRequestStreaming(config, systemPrompt, fullPrompt, overrideMaxTokens = 700)
                .onCompletion { cause -> if (cause == null) consumeCreditAfterSuccess(config) }
        } else {
            val result = makeApiRequest(config, systemPrompt, fullPrompt, overrideMaxTokens = 700)
            channelFlow { send(result.getOrElse { throw it }) }
        }
    }

    /**
     * Core SSE streaming implementation. Reads chunked `data:` lines from an
     * OpenAI-compatible streaming endpoint and emits content tokens via channelFlow.
     */
    private fun makeApiRequestStreaming(
        config: AIConfigProvider.AIConfig,
        systemPrompt: String,
        userPrompt: String,
        overrideMaxTokens: Int? = null,
    ): Flow<String> = channelFlow {
        val ch = this // capture ProducerScope before switching dispatcher
        val request = GroqRequest(
            model = config.model,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userPrompt),
            ),
            temperature = 0.9,
            topP = 0.95,
            presencePenalty = 0.3,
            maxTokens = overrideMaxTokens ?: config.maxTokens,
            stream = true,
        )
        val jsonBody = gson.toJson(request)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val httpRequestBuilder = Request.Builder()
            .url(config.getBaseUrl())
            .addHeader("Content-Type", "application/json")
            .post(requestBody)

        if (config.isClaude()) {
            httpRequestBuilder.addHeader("x-api-key", config.apiKey)
            httpRequestBuilder.addHeader("anthropic-version", "2023-06-01")
        } else {
            httpRequestBuilder.addHeader("Authorization", config.getAuthHeader())
        }

        withContext(Dispatchers.IO) {
            val response = client.newCall(httpRequestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val code = response.code
                val body = response.body?.string() ?: ""
                Log.e(TAG, "Streaming request failed: $code body=$body")
                val msg = when (code) {
                    401 -> "Invalid API key"
                    429 -> "Rate limit — try again later"
                    403 -> "API access forbidden"
                    500, 502, 503, 504 -> "AI service unavailable"
                    else -> "Streaming error $code"
                }
                throw AIException(msg)
            }

            val source = response.body?.source() ?: throw AIException("Empty streaming body")
            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, GroqStreamChunk::class.java)
                        val token = chunk?.choices?.firstOrNull()?.delta?.content
                        if (!token.isNullOrEmpty()) {
                            ch.send(token)
                        }
                    } catch (_: Exception) {
                        // malformed SSE chunk — skip silently
                    }
                }
            } finally {
                source.close()
            }
        }
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
    val temperature: Double = 0.9,
    @SerializedName("top_p")
    val topP: Double = 0.95,
    @SerializedName("presence_penalty")
    val presencePenalty: Double = 0.3,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048,
    val stream: Boolean = false,
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

// Streaming SSE response data classes (OpenAI-compatible chunked format)
data class GroqStreamChunk(
    val choices: List<GroqStreamChoice>?
)

data class GroqStreamChoice(
    val delta: GroqStreamDelta?,
    @SerializedName("finish_reason") val finishReason: String?,
)

data class GroqStreamDelta(
    val content: String?,
)

