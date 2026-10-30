/*
 * Copyright (C) 2024-2026 The ReBoard Contributors
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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * The AI engine behind the keyboard's writing tools and the accessibility
 * overlay: builds the prompt for an [AiTool], talks to the configured
 * provider (the bundled Groq key, or the user's own OpenAI / Anthropic /
 * Gemini / OpenAI-compatible key) and charges one credit per successful
 * request for free users.
 */
class AIManager(private val context: Context) {
    companion object {
        private const val TAG = "AIManager"

        /** Longer input is refused with a clear message instead of being cut off. */
        const val MAX_INPUT_CHARS = 8000

        private const val DEFAULT_GROQ_MODEL = "openai/gpt-oss-120b"

        /**
         * Each Groq model has its own rate limit, so when the bundled key is
         * throttled on the main model the request moves down this list.
         */
        private val GROQ_FALLBACK_MODELS = listOf("openai/gpt-oss-20b", "qwen/qwen3.8-27b")

        private const val DEFAULT_OPENAI_MODEL = "gpt-4.1-mini"
        private const val DEFAULT_CLAUDE_MODEL = "claude-haiku-4-5"
        private const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

        private val JSON = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** Kept for callers that react to the settings broadcast; the config is re-read on every request. */
    fun reloadConfig() {
        AIConfigProvider.reloadConfig(context)
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Runs [tool] on [text] and returns the single best result. */
    suspend fun runTool(tool: AiTool, text: String, options: AiOptions = AiOptions()): Result<String> {
        return runToolVariants(tool, text, options, count = 1).map { it.first() }
    }

    /** Runs [tool] and returns up to [count] alternative results from one request. */
    suspend fun runToolVariants(
        tool: AiTool,
        text: String,
        options: AiOptions = AiOptions(),
        count: Int = 3,
    ): Result<List<String>> {
        val config = checkReady().getOrElse { return Result.failure(it) }
        val hasConversation = tool == AiTool.REPLY && !options.conversation.isNullOrBlank()
        if (text.isBlank() && !hasConversation) {
            return Result.failure(AIException("Type or select some text first", AIException.Kind.NO_TEXT))
        }
        if (text.length > MAX_INPUT_CHARS) {
            return Result.failure(
                AIException(
                    "This text is too long for AI (${text.length} characters). Select up to $MAX_INPUT_CHARS characters.",
                    AIException.Kind.TOO_LONG,
                ),
            )
        }
        val spec = AiPrompts.build(tool, text, options, count)
        val raw = request(config, spec).getOrElse { return Result.failure(it) }
        val results = if (count > 1) {
            AiOutput.cleanVariants(raw, text)
        } else {
            listOfNotNull(AiOutput.clean(raw, text))
        }
        if (results.isEmpty()) {
            return Result.failure(AIException("The AI didn't return anything usable. Try again.", AIException.Kind.EMPTY))
        }
        return Result.success(results.take(count.coerceAtLeast(1)))
    }

    /** Free-form generation for short creative text (sticker captions and similar). */
    suspend fun generate(instruction: String, maxTokens: Int = 300): Result<String> {
        val config = checkReady().getOrElse { return Result.failure(it) }
        val spec = AiPrompts.Spec(
            system = "You write short text for the user. Output only the text they asked for: no quotes, labels, " +
                "preamble or explanation.",
            user = instruction,
            temperature = 0.9,
            maxTokens = maxTokens,
        )
        val raw = request(config, spec).getOrElse { return Result.failure(it) }
        return AiOutput.clean(raw, "")?.let { Result.success(it) }
            ?: Result.failure(AIException("The AI didn't return anything usable. Try again.", AIException.Kind.EMPTY))
    }

    // ── Account & credits ────────────────────────────────────────────────────

    /** Whether the user pays with credits (bundled key) or uses their own API key. */
    fun usesOwnApiKey(): Boolean = AIConfigProvider.getConfig(context).isAdvancedEnabled

    private fun checkReady(): Result<AIConfigProvider.AIConfig> {
        val config = AIConfigProvider.reloadConfig(context)
        // Only the built-in AI (its allowance is kept per user on the server) needs a session; your own key doesn't.
        if (!config.isAdvancedEnabled && !isUserLoggedIn()) {
            return Result.failure(AIException(SIGN_IN_FOR_AI, AIException.Kind.LOGIN))
        }
        if (!config.hasValidApiKey()) {
            return Result.failure(AIException("No API key set. Add one in Settings → Advanced AI.", AIException.Kind.NO_KEY))
        }
        return Result.success(config)
    }

    /**
     * Check if user is logged in by reading from SharedPreferences.
     * The Rewordium app syncs login state to keyboard_settings prefs.
     */
    private fun isUserLoggedIn(): Boolean {
        return try {
            context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE).getBoolean("user_logged_in", false)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking login status", e)
            false
        }
    }

    private fun isProUser(): Boolean {
        return try {
            context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE).getBoolean("user_is_pro", false)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking pro status", e)
            false
        }
    }

    private fun getCachedCredits(): Int {
        return try {
            context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE).getInt("user_credits", 0)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cached credits", e)
            0
        }
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
        statusIntent.setPackage(context.packageName)
        context.sendBroadcast(statusIntent)
    }

    private suspend fun verifyCreditAvailabilityForRequest(config: AIConfigProvider.AIConfig): Result<Unit> {
        if (config.isAdvancedEnabled || isProUser()) {
            return Result.success(Unit)
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(AIException(SIGN_IN_FOR_AI, AIException.Kind.LOGIN))
        }

        return withContext(Dispatchers.IO) {
            try {
                val userSnap = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (!userSnap.exists()) {
                    return@withContext Result.failure(AIException("Unable to verify credits", AIException.Kind.OTHER))
                }

                val remoteIsPro = userSnap.getBoolean("isPro") ?: false
                val remoteCredits = (userSnap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
                updateLocalCreditState(isLoggedIn = true, isPro = remoteIsPro, credits = remoteCredits)

                if (remoteIsPro || remoteCredits > 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(AIException(outOfAiUses(), AIException.Kind.CREDITS))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Credit availability sync failed, using cached keyboard state", e)
                if (getCachedCredits() > 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(AIException(outOfAiUses(), AIException.Kind.CREDITS))
                }
            }
        }
    }

    /**
     * Charges one credit after a successful request. A failure here (usually
     * the network dropping after the answer arrived) doesn't take the result
     * away from the user; the next availability check syncs the balance.
     */
    private suspend fun consumeCreditAfterSuccess(config: AIConfigProvider.AIConfig) {
        if (config.isAdvancedEnabled || isProUser()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        withContext(Dispatchers.IO) {
            try {
                val userRef = FirebaseFirestore.getInstance().collection("users").document(uid)
                val (isPro, remaining) = FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val userSnap = transaction.get(userRef)
                    if (!userSnap.exists()) return@runTransaction false to 0
                    if (userSnap.getBoolean("isPro") == true) return@runTransaction true to 0
                    val currentCredits = (userSnap.getLong("credits") ?: 0L).toInt()
                    if (currentCredits <= 0) return@runTransaction false to 0
                    val updatedCredits = currentCredits - 1
                    transaction.update(
                        userRef,
                        mapOf(
                            "credits" to updatedCredits,
                            "lastUpdated" to FieldValue.serverTimestamp(),
                        ),
                    )
                    false to updatedCredits
                }.await()
                updateLocalCreditState(isLoggedIn = true, isPro = isPro, credits = remaining)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to consume credit", e)
            }
        }
    }

    // ── Transport ────────────────────────────────────────────────────────────

    private suspend fun request(config: AIConfigProvider.AIConfig, spec: AiPrompts.Spec): Result<String> {
        verifyCreditAvailabilityForRequest(config).onFailure { return Result.failure(it) }
        val result = withContext(Dispatchers.IO) { executeWithFallback(config, spec) }
        if (result.isSuccess) consumeCreditAfterSuccess(config)
        return result
    }

    private fun executeWithFallback(config: AIConfigProvider.AIConfig, spec: AiPrompts.Spec): Result<String> {
        val models = buildList {
            add(modelFor(config))
            if (!config.isAdvancedEnabled && config.provider.equals(AIConfigProvider.PROVIDER_GROQ, ignoreCase = true)) {
                addAll(GROQ_FALLBACK_MODELS.filter { it != first() })
            }
        }
        var last: Result<String> = Result.failure(AIException("AI request failed", AIException.Kind.OTHER))
        for (model in models) {
            last = executeWithRetry(config, model, spec)
            if (last.isSuccess) return last
            val kind = (last.exceptionOrNull() as? AIException)?.kind
            // Only capacity problems are worth another model; a bad key or a
            // bad request fails the same way everywhere.
            if (kind != AIException.Kind.RATE_LIMIT && kind != AIException.Kind.SERVICE && kind != AIException.Kind.EMPTY) break
        }
        return last
    }

    private fun executeWithRetry(config: AIConfigProvider.AIConfig, model: String, spec: AiPrompts.Spec): Result<String> {
        val first = execute(config, model, spec)
        val kind = (first.exceptionOrNull() as? AIException)?.kind
        if (kind == AIException.Kind.NETWORK || kind == AIException.Kind.SERVICE) {
            Thread.sleep(700)
            return execute(config, model, spec)
        }
        return first
    }

    private fun execute(config: AIConfigProvider.AIConfig, model: String, spec: AiPrompts.Spec): Result<String> {
        return try {
            when {
                config.isClaude() -> executeAnthropic(config, model, spec)
                config.isGemini() -> executeGemini(config, model, spec)
                else -> executeOpenAiCompatible(config, model, spec)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(AIException("The AI took too long to answer. Try again.", AIException.Kind.NETWORK))
        } catch (e: UnknownHostException) {
            Result.failure(AIException("No internet connection", AIException.Kind.NETWORK))
        } catch (e: IOException) {
            Log.w(TAG, "AI request I/O error", e)
            Result.failure(AIException("Network error. Check your connection and try again.", AIException.Kind.NETWORK))
        } catch (e: Exception) {
            Log.e(TAG, "AI request failed", e)
            Result.failure(AIException("Something went wrong with the AI request", AIException.Kind.OTHER))
        }
    }

    private fun executeOpenAiCompatible(config: AIConfigProvider.AIConfig, model: String, spec: AiPrompts.Spec): Result<String> {
        val isOpenAi = config.provider.equals(AIConfigProvider.PROVIDER_OPENAI, ignoreCase = true)
        val openAiReasoning = isOpenAi && Regex("^(o\\d|gpt-5)").containsMatchIn(model)
        val body = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to spec.system),
                mapOf("role" to "user", "content" to spec.user),
            )))
            if (openAiReasoning) {
                // o-series / GPT-5 reject max_tokens and custom temperatures.
                addProperty("max_completion_tokens", spec.maxTokens)
                addProperty("reasoning_effort", if (model.startsWith("gpt-5")) "minimal" else "low")
            } else {
                addProperty("max_tokens", spec.maxTokens)
                addProperty("temperature", spec.temperature)
                // gpt-oss thinks before answering; "low" keeps that to a few
                // tokens so the answer is fast and never cut off.
                if (model.contains("gpt-oss")) addProperty("reasoning_effort", "low")
            }
        }
        val request = Request.Builder()
            .url(config.getBaseUrl())
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return send(request, config, model) { json ->
            json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")
                ?.takeIf { it.isJsonPrimitive }?.asString
        }
    }

    private fun executeAnthropic(config: AIConfigProvider.AIConfig, model: String, spec: AiPrompts.Spec): Result<String> {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", spec.maxTokens)
            addProperty("system", spec.system)
            addProperty("temperature", spec.temperature.coerceAtMost(1.0))
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "user", "content" to spec.user))))
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return send(request, config, model) { json ->
            json.getAsJsonArray("content")
                ?.mapNotNull { block ->
                    val obj = block.asJsonObject
                    if (obj.get("type")?.asString == "text") obj.get("text")?.asString else null
                }
                ?.joinToString("")
        }
    }

    private fun executeGemini(config: AIConfigProvider.AIConfig, model: String, spec: AiPrompts.Spec): Result<String> {
        val generationConfig = JsonObject().apply {
            addProperty("temperature", spec.temperature)
            addProperty("maxOutputTokens", spec.maxTokens)
            // 2.5 Flash models think by default and count it against the
            // output budget; writing tools don't need it.
            if (model.contains("2.5-flash")) {
                add("thinkingConfig", JsonObject().apply { addProperty("thinkingBudget", 0) })
            }
        }
        val body = JsonObject().apply {
            add("systemInstruction", gson.toJsonTree(mapOf("parts" to listOf(mapOf("text" to spec.system)))))
            add("contents", gson.toJsonTree(listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to spec.user))))))
            add("generationConfig", generationConfig)
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", config.apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return send(request, config, model) { json ->
            json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                ?.mapNotNull { part ->
                    val obj = part.asJsonObject
                    if (obj.get("thought")?.asBoolean == true) null else obj.get("text")?.asString
                }
                ?.joinToString("")
        }
    }

    private fun send(
        request: Request,
        config: AIConfigProvider.AIConfig,
        model: String,
        extract: (JsonObject) -> String?,
    ): Result<String> {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "${config.provider}/$model failed: ${response.code} ${body.take(300)}")
                return Result.failure(httpError(response.code, config, model))
            }
            val content = runCatching { extract(JsonParser.parseString(body).asJsonObject) }.getOrNull()
            if (content.isNullOrBlank()) {
                Log.w(TAG, "${config.provider}/$model returned no content: ${body.take(300)}")
                return Result.failure(AIException("The AI didn't return anything usable. Try again.", AIException.Kind.EMPTY))
            }
            return Result.success(content)
        }
    }

    private fun httpError(code: Int, config: AIConfigProvider.AIConfig, model: String): AIException = when (code) {
        401, 403 -> if (config.isAdvancedEnabled) {
            AIException("Your API key was rejected. Check it in Settings → Advanced AI.", AIException.Kind.NO_KEY)
        } else {
            AIException("The AI service rejected the request. Please update Rewordium.", AIException.Kind.OTHER)
        }
        404 -> AIException("AI model \"$model\" wasn't found. Check Settings → Advanced AI.", AIException.Kind.OTHER)
        408 -> AIException("The AI took too long to answer. Try again.", AIException.Kind.NETWORK)
        413 -> AIException("This text is too long for the AI. Select a shorter part.", AIException.Kind.TOO_LONG)
        429 -> AIException("The AI is busy right now. Try again in a minute.", AIException.Kind.RATE_LIMIT)
        in 500..599 -> AIException("The AI service is having trouble. Try again shortly.", AIException.Kind.SERVICE)
        else -> AIException("The AI couldn't process this text (error $code).", AIException.Kind.OTHER)
    }

    /** The configured model, or a sensible default when it doesn't belong to the provider. */
    private fun modelFor(config: AIConfigProvider.AIConfig): String {
        val model = config.model.trim()
        val unset = model.isBlank() || model == DEFAULT_GROQ_MODEL
        return when {
            config.isClaude() -> if (unset || !model.startsWith("claude")) DEFAULT_CLAUDE_MODEL else model
            config.isGemini() -> if (unset || !model.startsWith("gemini")) DEFAULT_GEMINI_MODEL else model
            config.provider.equals(AIConfigProvider.PROVIDER_OPENAI, ignoreCase = true) -> if (unset) DEFAULT_OPENAI_MODEL else model
            model.isBlank() -> DEFAULT_GROQ_MODEL
            else -> model
        }
    }
}

/** An AI failure with a user-facing [message] and a [kind] the UI can act on. */
class AIException(message: String, val kind: Kind = Kind.OTHER) : Exception(message) {
    enum class Kind { LOGIN, CREDITS, NO_KEY, NO_TEXT, TOO_LONG, RATE_LIMIT, NETWORK, SERVICE, EMPTY, OTHER }
}

private const val SIGN_IN_FOR_AI = "Open Rewordium to use the built-in AI (or add your own AI key). Typing works as normal."

/** The message when the built-in AI allowance is used up, matching what this release offers. */
internal fun outOfAiUses(): String =
    if (com.noxquill.rewordium.keyboard.app.ReleaseFlags.subscriptionsEnabled) "You're out of AI credits. Upgrade to keep going."
    else "You've used all ${com.noxquill.rewordium.keyboard.app.ReleaseFlags.FREE_AI_USES} included AI uses. Typing works as normal; add your own AI key in Rewordium to keep using AI."
