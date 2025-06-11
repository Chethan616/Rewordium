package com.example.yc_startup.keyboard.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GroqApiClient {
    private const val TAG = "GroqApiClient"
    private const val BASE_URL = "https://api.groq.com/openai/v1/"
    private const val API_KEY = "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" // Replace with your key
    private const val MODEL = "llama3-8b-8192"
    
    private val service: GroqApiService
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        service = retrofit.create(GroqApiService::class.java)
    }
    
    suspend fun getParaphrases(text: String, persona: String = "Neutral"): List<String> = withContext(Dispatchers.IO) {
        try {
            if (text.trim().length < 5) {
                Log.d(TAG, "Text too short for paraphrasing")
                return@withContext emptyList<String>()
            }
            
            val systemMessage = when (persona) {
                "Happy" -> "You are a cheerful assistant that provides optimistic and upbeat paraphrases."
                "Sad" -> "You are a melancholic assistant that provides somber and reflective paraphrases."
                "Humor" -> "You are a witty assistant that provides humorous and playful paraphrases."
                "Formal" -> "You are a professional assistant that provides business-like and sophisticated paraphrases."
                "Casual" -> "You are a relaxed assistant that provides conversational and informal paraphrases."
                else -> "You are a helpful assistant that provides clear, concise paraphrases."
            }
            
            val styleInstruction = when (persona) {
                "Happy" -> "Make each paraphrase sound cheerful, optimistic, and upbeat."
                "Sad" -> "Make each paraphrase sound melancholic, somber, and reflective."
                "Humor" -> "Make each paraphrase sound witty, funny, and humorous."
                "Formal" -> "Make each paraphrase sound professional, polished, and business-like."
                "Casual" -> "Make each paraphrase sound relaxed, conversational, and informal."
                else -> "Each paraphrase should be significantly different in wording but maintain the same meaning."
            }
            
            val prompt = """
                Provide 3 different paraphrases of the following text.
                $styleInstruction
                Return ONLY the 3 paraphrases, each on a new line, with no additional text, numbering, or explanation.
                
                Text: "$text"
            """.trimIndent()

            val request = GroqRequest(
                model = MODEL,
                messages = listOf(
                    RequestMessage(role = "system", content = systemMessage),
                    RequestMessage(role = "user", content = prompt)
                ),
                temperature = 0.7,
                max_tokens = 1000,
                n = 1
            )

            val responseBody = service.getParaphrase("Bearer $API_KEY", request)
            val content = responseBody.choices?.firstOrNull()?.message?.content ?: ""

            val paraphrases = content
                .split("\n")
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .take(3)

            Log.d(TAG, "Received ${paraphrases.size} paraphrases")

            if (paraphrases.isEmpty()) {
                Log.w(TAG, "No paraphrases found in response: $content")
            }

            return@withContext paraphrases
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API call", e)
            return@withContext emptyList<String>()
        }
    }
}