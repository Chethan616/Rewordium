package com.example.yc_startup.keyboard.api

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface GroqApiService {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun getParaphrase(@Header("Authorization") authHeader: String, @Body request: GroqRequest): GroqResponse

}

@Keep
data class GroqRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val n: Int
)

@Keep
data class RequestMessage(
    val role: String,
    val content: String
)

@Keep
data class GroqResponse(
    val choices: List<Choice>,
    val id: String,
    val model: String,
    val usage: Usage? = null
)

@Keep
data class Choice(
    val message: Message,
    val finish_reason: String,
    val index: Int
)

@Keep
data class Message(
    val content: String,
    val role: String
)

@Keep
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)