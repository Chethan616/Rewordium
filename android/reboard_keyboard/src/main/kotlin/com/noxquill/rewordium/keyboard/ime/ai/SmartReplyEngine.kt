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
import com.noxquill.rewordium.keyboard.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Generates context-aware smart reply chips for messaging apps.
 *
 * Usage:
 *   1. Call [isMessagingApp] with the current editor's packageName.
 *   2. If true, call [getSuggestions] with the last 500 chars of text before
 *      the cursor; it returns up to 3 short reply strings.
 *   3. Display the replies as tappable chips above the suggestion bar.
 *   4. On chip tap, call [EditorInstance.commitText] with the chosen reply.
 *
 * Replies are cached for 60 s keyed by the last 100 chars of context,
 * so fast successive calls for the same conversation thread are free.
 *
 * Gated by [BuildConfig.ENABLE_SMART_REPLIES]. When the flag is false,
 * [getSuggestions] returns an empty list immediately.
 */
class SmartReplyEngine(private val context: Context) {

    companion object {
        private const val TAG = "SmartReplyEngine"
        private const val CACHE_TTL_MS = 60_000L
        private const val MAX_REPLIES = 3

        private val MESSAGING_PACKAGES = setOf(
            // WhatsApp
            "com.whatsapp",
            "com.whatsapp.w4b",
            // Meta Messenger
            "com.facebook.orca",
            "com.facebook.mlite",
            // Instagram DMs
            "com.instagram.android",
            // Telegram
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            // Signal
            "org.thoughtcrime.securesms",
            // Google Messages
            "com.google.android.apps.messaging",
            "com.google.android.talk",
            // Samsung Messages
            "com.samsung.android.messaging",
            // Twitter / X DMs
            "com.twitter.android",
            "com.x.android",
            // Snapchat
            "com.snapchat.android",
            // Discord
            "com.discord",
            // Slack
            "com.Slack",
            // Teams
            "com.microsoft.teams",
            // iMessage via Beeper / BlueBubbles
            "com.beeper.android",
            "com.bluebubbles.bluebubbles",
            // Viber
            "com.viber.voip",
            // Line
            "jp.naver.line.android",
            // WeChat
            "com.tencent.mm",
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // Simple time-based LRU cache keyed by last-100-chars of context.
    private data class CacheEntry(val replies: List<String>, val expiresAt: Long)
    private val cache = LinkedHashMap<String, CacheEntry>(16, 0.75f, true)

    /**
     * Returns true when the given package is a known messaging application.
     * When [BuildConfig.ENABLE_SMART_REPLIES] is false, always returns false
     * so the feature doesn't activate at all.
     */
    fun isMessagingApp(packageName: String?): Boolean {
        if (!BuildConfig.ENABLE_SMART_REPLIES) return false
        return packageName != null && MESSAGING_PACKAGES.contains(packageName)
    }

    /**
     * Generates up to [MAX_REPLIES] short reply suggestions for the given
     * conversation context. Returns empty list on error or when flag is off.
     *
     * @param contextText Last ~500 chars of text before the cursor. Treated as
     *                    the conversation history to reply to.
     */
    suspend fun getSuggestions(contextText: String): List<String> {
        if (!BuildConfig.ENABLE_SMART_REPLIES) return emptyList()
        if (contextText.isBlank()) return emptyList()

        val cacheKey = contextText.takeLast(100)
        val now = System.currentTimeMillis()

        // Return cached result if still valid.
        synchronized(cache) {
            val entry = cache[cacheKey]
            if (entry != null && entry.expiresAt > now) {
                Log.d(TAG, "Cache hit for smart replies")
                return entry.replies
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val config = AIConfigProvider.reloadConfig(context)
                if (!config.hasValidApiKey()) return@withContext emptyList()

                val systemPrompt = """You are a messaging assistant. Generate exactly $MAX_REPLIES short, natural reply suggestions for the conversation below.

Rules:
1. Return ONLY a JSON array of $MAX_REPLIES strings: ["reply1","reply2","reply3"]
2. Each reply must be 1-10 words, conversational, and contextually appropriate.
3. Vary tone: one casual, one engaging, one neutral.
4. No labels, no markdown, no explanation — raw JSON array only.
5. Replies must be in the same language as the conversation.""".trimIndent()

                val userPrompt = "Conversation:\n${contextText.takeLast(500)}"

                val request = SmartReplyRequest(
                    model = "qwen/qwen3-32b",
                    messages = listOf(
                        SmartReplyMessage("system", systemPrompt),
                        SmartReplyMessage("user", userPrompt),
                    ),
                    temperature = 0.7,
                    maxTokens = 80,
                )
                val body = gson.toJson(request).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(config.getBaseUrl())
                    .addHeader("Authorization", config.getAuthHeader())
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Smart reply API failed: ${response.code}")
                    return@withContext emptyList()
                }

                val raw = response.body?.string() ?: return@withContext emptyList()
                val apiResponse = gson.fromJson(raw, SmartReplyApiResponse::class.java)
                val content = apiResponse.choices?.firstOrNull()?.message?.content
                    ?.replace(Regex("<think>[\\s\\S]*?(?:</think>|$)", RegexOption.IGNORE_CASE), "")
                    ?.trim() ?: return@withContext emptyList()

                // Parse JSON array from content
                val replies = parseRepliesFromContent(content)
                if (replies.isEmpty()) return@withContext emptyList()

                // Cache the result
                synchronized(cache) {
                    if (cache.size >= 32) {
                        cache.keys.firstOrNull()?.let { cache.remove(it) }
                    }
                    cache[cacheKey] = CacheEntry(replies, now + CACHE_TTL_MS)
                }

                Log.d(TAG, "Smart replies generated: $replies")
                replies
            } catch (e: Exception) {
                Log.w(TAG, "Smart reply generation failed", e)
                emptyList()
            }
        }
    }

    /** Clear cached replies (e.g., when switching apps). */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun parseRepliesFromContent(content: String): List<String> {
        return try {
            // Find the JSON array in the response (model may add extra text)
            val arrayStart = content.indexOf('[')
            val arrayEnd = content.lastIndexOf(']')
            if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
                return emptyList()
            }
            val jsonArray = content.substring(arrayStart, arrayEnd + 1)
            @Suppress("UNCHECKED_CAST")
            val list = gson.fromJson(jsonArray, List::class.java) as? List<String>
            list?.take(MAX_REPLIES)?.filter { it.isNotBlank() } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse smart replies JSON", e)
            emptyList()
        }
    }
}

// Minimal data classes for smart reply API calls
private data class SmartReplyRequest(
    val model: String,
    val messages: List<SmartReplyMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 80,
)

private data class SmartReplyMessage(val role: String, val content: String)

private data class SmartReplyApiResponse(
    val choices: List<SmartReplyChoice>?,
)

private data class SmartReplyChoice(val message: SmartReplyMessage?)
