package com.noxquill.rewordium.service

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * Dynamic Retrofit client that supports multiple AI providers
 * Uses the base URL from AIConfigProvider to route requests to the correct endpoint
 */
object DynamicRetrofitClient {
    private const val TAG = "DynamicRetrofitClient"
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val retrofitCache = mutableMapOf<String, DynamicApiService>()
    
    /**
     * Get an API service instance for the given base URL
     * Caches instances to avoid creating new ones for the same URL
     */
    fun getService(baseUrl: String): DynamicApiService {
        return retrofitCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DynamicApiService::class.java)
        }
    }
    
    /**
     * Make a completion request to the AI provider
     * Automatically routes to the correct endpoint based on config
     */
    suspend fun makeCompletionRequest(
        config: AIConfigProvider.AIConfig,
        prompt: String
    ): Response<GroqResponse> {
        val service = getService(config.getBaseUrl())
        
        Log.d(TAG, "Making request to provider: ${config.provider}, model: ${config.getEffectiveModel()}")
        
        return when (config.provider.lowercase()) {
            AIConfigProvider.PROVIDER_GEMINI -> {
                // Gemini uses a completely different API format
                Log.d(TAG, "Using Gemini API format")
                makeGeminiRequest(config, prompt, service)
            }
            AIConfigProvider.PROVIDER_GROQ, 
            AIConfigProvider.PROVIDER_OPENAI,
            AIConfigProvider.PROVIDER_CUSTOM -> {
                // OpenAI-compatible API format
                val effectiveModel = config.getEffectiveModel()
                val request = GroqRequest(
                    model = effectiveModel,
                    messages = listOf(Message("user", prompt)),
                    maxTokens = 512,
                    reasoningEffort = null,
                )
                service.postCompletion(
                    url = config.getEndpointPath(),
                    authorization = config.getAuthHeader(),
                    request = request
                )
            }
            AIConfigProvider.PROVIDER_CLAUDE, AIConfigProvider.PROVIDER_ANTHROPIC -> {
                // Claude uses a different format, but we'll adapt
                val request = GroqRequest(
                    model = config.getEffectiveModel(),
                    messages = listOf(Message("user", prompt)),
                    maxTokens = 512,
                )
                service.postClaudeCompletion(
                    url = config.getEndpointPath(),
                    apiKey = config.apiKey,
                    contentType = "application/json",
                    anthropicVersion = "2023-06-01",
                    request = request
                )
            }
            else -> {
                // Default to OpenAI-compatible
                val effectiveModel = config.getEffectiveModel()
                val request = GroqRequest(
                    model = effectiveModel,
                    messages = listOf(Message("user", prompt)),
                    maxTokens = 512,
                    reasoningEffort = null,
                )
                service.postCompletion(
                    url = config.getEndpointPath(),
                    authorization = config.getAuthHeader(),
                    request = request
                )
            }
        }
    }
    
    /**
     * Make a request to Gemini API with proper format
     * Gemini uses: contents[{parts[{text}]}] format and API key as query param
     */
    private suspend fun makeGeminiRequest(
        config: AIConfigProvider.AIConfig,
        prompt: String,
        service: DynamicApiService
    ): Response<GroqResponse> {
        val geminiRequest = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )
        
        // Gemini endpoint path already includes the model
        val endpointPath = config.getEndpointPath()
        Log.d(TAG, "Gemini endpoint: $endpointPath, has key: ${config.apiKey.isNotBlank()}")
        
        val geminiResponse = service.postGeminiCompletion(
            url = endpointPath,
            apiKey = config.apiKey,
            request = geminiRequest
        )
        
        // Convert Gemini response to GroqResponse format for unified handling
        return if (geminiResponse.isSuccessful && geminiResponse.body() != null) {
            val geminiBody = geminiResponse.body()!!
            Log.d(TAG, "Gemini response successful, candidates: ${geminiBody.candidates?.size ?: 0}")
            
            val textContent = geminiBody.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text ?: ""
            
            Log.d(TAG, "Extracted text length: ${textContent.length}")
            
            // Convert to GroqResponse format
            val groqResponse = GroqResponse(
                choices = listOf(
                    Choice(
                        message = Message(role = "assistant", content = textContent)
                    )
                )
            )
            Response.success(groqResponse)
        } else {
            val errorBody = geminiResponse.errorBody()?.string()
            Log.e(TAG, "Gemini API error: code=${geminiResponse.code()}, error=$errorBody")
            
            // Return error response with appropriate code
            Response.error(
                geminiResponse.code(),
                geminiResponse.errorBody() ?: okhttp3.ResponseBody.create(null, "Unknown error")
            )
        }
    }
}

// Gemini request/response data classes
data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val promptFeedback: GeminiPromptFeedback?
)

data class GeminiCandidate(
    val content: GeminiContentResponse?,
    val finishReason: String?
)

data class GeminiContentResponse(
    val parts: List<GeminiPart>?,
    val role: String?
)

data class GeminiPromptFeedback(
    val safetyRatings: List<Any>?
)

/**
 * Dynamic API service that can hit any endpoint
 */
interface DynamicApiService {
    @POST
    suspend fun postCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: GroqRequest
    ): Response<GroqResponse>
    
    @POST
    suspend fun postClaudeCompletion(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Header("Content-Type") contentType: String,
        @Header("anthropic-version") anthropicVersion: String,
        @Body request: GroqRequest
    ): Response<GroqResponse>
    
    @POST
    suspend fun postGeminiCompletion(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
