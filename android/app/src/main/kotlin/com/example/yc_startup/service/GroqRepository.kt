package com.example.yc_startup.service

import com.example.yc_startup.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// A sealed class to represent the result of our API call in a safe way.
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val message: String) : AiResult()
}

class GroqRepository {

    private val apiService: GroqApiService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(GroqApiService::class.java)
    }

    suspend fun getCompletion(prompt: String): AiResult {
        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isNullOrEmpty()) {
            return AiResult.Error("Groq API key is missing from local.properties.")
        }

        return try {
            val responseBody = apiService.getCompletion(
                token = "Bearer $apiKey",
                request = GroqRequest(
                    messages = listOf(Message(role = "user", content = prompt)),
                    model = "llama3-8b-8192"
                )
            )

            val resultText = responseBody.choices?.firstOrNull()?.message?.content?.trim()
            if (resultText != null) {
                AiResult.Success(resultText)
            } else {
                AiResult.Error("AI returned an empty response.")
            }
        } catch (e: Exception) {
            AiResult.Error("Network Exception: ${e.message}")
        }
    }
}