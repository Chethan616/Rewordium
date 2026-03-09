package com.noxquill.rewordium.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.noxquill.rewordium.BuildConfig

/**
 * Provides AI configuration from SharedPreferences
 * This allows the accessibility service to use the AI settings configured in the Flutter app
 */
object AIConfigProvider {
    private const val TAG = "AIConfigProvider"
    private const val PREFS_NAME = "ai_settings"
    
    // SharedPreferences keys (must match MainActivity.kt)
    private const val KEY_ADVANCED_AI_ENABLED = "advanced_ai_enabled"
    private const val KEY_AI_PROVIDER = "ai_provider"
    private const val KEY_AI_API_KEY = "ai_api_key"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_AI_MAX_TOKENS = "ai_max_tokens"
    private const val KEY_AI_CUSTOM_ENDPOINT = "ai_custom_endpoint"
    
    // Provider constants
    const val PROVIDER_GROQ = "groq"
    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_GEMINI = "gemini"
    const val PROVIDER_CLAUDE = "claude"
    const val PROVIDER_CUSTOM = "custom"
    
    // Default values
    private const val DEFAULT_MODEL = "llama-3.1-8b-instant"
    private const val DEFAULT_MAX_TOKENS = 1024
    
    data class AIConfig(
        val isAdvancedEnabled: Boolean,
        val provider: String,
        val apiKey: String,
        val model: String,
        val maxTokens: Int,
        val customEndpoint: String
    ) {
        /**
         * Get the base URL for the current provider
         */
        fun getBaseUrl(): String {
            return when (provider.lowercase()) {
                PROVIDER_GROQ -> "https://api.groq.com/"
                PROVIDER_OPENAI -> "https://api.openai.com/"
                PROVIDER_GEMINI -> "https://generativelanguage.googleapis.com/"
                PROVIDER_CLAUDE -> "https://api.anthropic.com/"
                PROVIDER_CUSTOM -> {
                    if (customEndpoint.isNotBlank()) {
                        // Ensure the endpoint ends with /
                        if (customEndpoint.endsWith("/")) customEndpoint else "$customEndpoint/"
                    } else {
                        "https://api.groq.com/" // Fallback to Groq
                    }
                }
                else -> "https://api.groq.com/"
            }
        }
        
        /**
         * Get the API endpoint path for the current provider
         */
        fun getEndpointPath(): String {
            return when (provider.lowercase()) {
                PROVIDER_GROQ -> "openai/v1/chat/completions"
                PROVIDER_OPENAI -> "v1/chat/completions"
                PROVIDER_GEMINI -> "v1beta/models/$model:generateContent"
                PROVIDER_CLAUDE -> "v1/messages"
                PROVIDER_CUSTOM -> "v1/chat/completions" // Assume OpenAI-compatible
                else -> "openai/v1/chat/completions"
            }
        }
        
        /**
         * Get the Authorization header value
         */
        fun getAuthHeader(): String {
            return when (provider.lowercase()) {
                PROVIDER_GEMINI -> apiKey // Gemini uses key as query param, but we'll handle it differently
                PROVIDER_CLAUDE -> apiKey // Claude uses x-api-key header
                else -> "Bearer $apiKey"
            }
        }
        
        /**
         * Check if we have a valid API key
         */
        fun hasValidApiKey(): Boolean {
            return apiKey.isNotBlank()
        }
        
        /**
         * Get the model to use, with fallback
         */
        fun getEffectiveModel(): String {
            return if (model.isNotBlank()) model else DEFAULT_MODEL
        }
    }
    
    /**
     * Load AI configuration from SharedPreferences
     * Falls back to BuildConfig values when advanced AI is disabled
     */
    fun getConfig(context: Context): AIConfig {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            val isAdvancedEnabled = prefs.getBoolean(KEY_ADVANCED_AI_ENABLED, false)
            
            if (isAdvancedEnabled) {
                // Use advanced settings from Flutter
                val provider = prefs.getString(KEY_AI_PROVIDER, PROVIDER_GROQ) ?: PROVIDER_GROQ
                val apiKey = prefs.getString(KEY_AI_API_KEY, "") ?: ""
                val model = prefs.getString(KEY_AI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
                val maxTokens = prefs.getInt(KEY_AI_MAX_TOKENS, DEFAULT_MAX_TOKENS)
                val customEndpoint = prefs.getString(KEY_AI_CUSTOM_ENDPOINT, "") ?: ""
                
                Log.d(TAG, "Using advanced AI config: provider=$provider, model=$model, hasKey=${apiKey.isNotBlank()}")
                
                return AIConfig(
                    isAdvancedEnabled = true,
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    maxTokens = maxTokens,
                    customEndpoint = customEndpoint
                )
            } else {
                // Use default Groq with BuildConfig API key
                val defaultApiKey = BuildConfig.GROQ_API_KEY.takeIf { it.isNotBlank() } ?: ""
                
                Log.d(TAG, "Using default Groq config, hasKey=${defaultApiKey.isNotBlank()}")
                
                return AIConfig(
                    isAdvancedEnabled = false,
                    provider = PROVIDER_GROQ,
                    apiKey = defaultApiKey,
                    model = DEFAULT_MODEL,
                    maxTokens = DEFAULT_MAX_TOKENS,
                    customEndpoint = ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI config, using defaults", e)
            return AIConfig(
                isAdvancedEnabled = false,
                provider = PROVIDER_GROQ,
                apiKey = BuildConfig.GROQ_API_KEY.takeIf { it.isNotBlank() } ?: "",
                model = DEFAULT_MODEL,
                maxTokens = DEFAULT_MAX_TOKENS,
                customEndpoint = ""
            )
        }
    }
    
    /**
     * Reload configuration and log the change
     */
    fun reloadConfig(context: Context): AIConfig {
        Log.d(TAG, "Reloading AI configuration...")
        return getConfig(context)
    }
}
