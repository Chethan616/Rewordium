package com.example.yc_startup.service

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// --- Data Models for Groq API ---

@Keep
data class GroqRequest(
    val messages: List<Message>,
    val model: String
)

@Keep
data class Message(
    val role: String,
    val content: String
)

@Keep
data class GroqResponse(
    val id: String?,
    val choices: List<Choice>?,
    @SerializedName("created") val createdAt: Long?,
    val model: String?,
    @SerializedName("system_fingerprint") val systemFingerprint: String?,
    val usage: Usage?
)

@Keep
data class Choice(
    val index: Int?,
    val message: Message?,
    @SerializedName("finish_reason") val finishReason: String?
)

@Keep
data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)

// --- Retrofit Service Interface ---

interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") token: String,
        @Body request: GroqRequest
    ): GroqResponse // Return GroqResponse directly
}