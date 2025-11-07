package com.noxquill.rewordium.service

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// --- Data classes to model the JSON request and response ---

data class GroqRequest(
    val model: String,
    val messages: List<Message>
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