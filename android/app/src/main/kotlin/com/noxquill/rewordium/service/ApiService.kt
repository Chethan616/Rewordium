package com.noxquill.rewordium.service

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// --- Data classes to model the JSON request and response ---

data class GroqRequest(
    val model: String,
    val messages: List<Message>,
    @com.google.gson.annotations.SerializedName("max_tokens")
    val maxTokens: Int = 512,
    val temperature: Double = 0.85,
    @com.google.gson.annotations.SerializedName("top_p")
    val topP: Double = 0.95,
    @com.google.gson.annotations.SerializedName("reasoning_effort")
    val reasoningEffort: String? = null,
)

data class Message(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)


// --- The Retrofit API Interface ---

interface ApiService {
    @POST("openai/v1/chat/completions")
    suspend fun getGroqCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: GroqRequest
    ): Response<GroqResponse>
}