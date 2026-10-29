package com.noxquill.rewordium.data.jade

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeSpec
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.noxquill.rewordium.keyboard.ime.ai.AIConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** A previous turn, as the model sees it. */
data class JadeTurn(val fromUser: Boolean, val text: String)

class JadeException(message: String, val rateLimited: Boolean = false, val retryable: Boolean = false) : Exception(message)

/**
 * Talks to the configured AI provider for Jade. With OpenAI-compatible
 * providers (the bundled Groq key included) Jade gets function calling and
 * can work with every keyboard and app setting through [JadeTools]; with
 * Anthropic or Gemini keys she chats without tools.
 */
class JadeEngine(context: Context) {
    private val appContext = context.applicationContext
    private val tools = JadeTools(appContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Whether the current provider supports Jade's settings tools. */
    fun canUseTools(): Boolean = AIConfigProvider.getConfig(appContext).let { !it.isClaude() && !it.isGemini() }

    fun usesOwnApiKey(): Boolean = AIConfigProvider.getConfig(appContext).isAdvancedEnabled

    /** A full conversational reply, using tools as needed. */
    suspend fun reply(userName: String?, history: List<JadeTurn>, message: String, host: JadeToolHost): String = withContext(Dispatchers.IO) {
        val config = readyConfig()
        val toolsOn = !config.isClaude() && !config.isGemini()
        val system = systemPrompt(userName, toolsOn)
        if (!toolsOn) return@withContext plainCompletion(config, system, history.takeLast(MAX_HISTORY) + JadeTurn(true, message), 1200)

        val messages = JsonArray().apply {
            add(msg("system", system))
            history.takeLast(MAX_HISTORY).forEach { add(msg(if (it.fromUser) "user" else "assistant", it.text)) }
            add(msg("user", message))
        }
        val models = modelsFor(config).toMutableList()
        repeat(MAX_TOOL_ROUNDS) {
            val answer = completeWithFallback(config, models, messages, withTools = true)
            // Some providers send "tool_calls": null rather than leaving it out.
            val toolCalls = answer.get("tool_calls")?.takeIf { it.isJsonArray }?.asJsonArray?.takeIf { it.size() > 0 }
            if (toolCalls == null) {
                return@withContext clean(answer.get("content").asText())
                    ?: throw JadeException("Jade went quiet there. Try asking again.", retryable = true)
            }
            messages.add(JsonObject().apply {
                addProperty("role", "assistant")
                addProperty("content", answer.get("content").asText().orEmpty())
                add("tool_calls", toolCalls)
            })
            for (call in toolCalls) {
                val obj = call.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val function = obj.get("function")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val name = function.get("name").asText().orEmpty()
                val result = tools.execute(name, function.get("arguments").asText(), host)
                messages.add(JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", obj.get("id").asText().orEmpty())
                    addProperty("content", result.take(3_500))
                })
            }
        }
        // Out of tool rounds: ask for a final answer in words.
        val final = completeWithFallback(config, models, messages, withTools = false)
        clean(final.get("content").asText()) ?: "All set! Let me know if you'd like anything else changed."
    }

    /** One writing skill run on [text]. */
    suspend fun runSkill(skill: String, text: String, options: SkillOptions): String = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Add some text for Jade to work with." }
        if (text.length > MAX_INPUT) throw JadeException("Keep it under ${"%,d".format(MAX_INPUT)} characters for one request.")
        val config = readyConfig()
        val (system, user) = JadeSkills.prompts(skill, text, options)
        if (config.isClaude() || config.isGemini()) return@withContext plainCompletion(config, system, listOf(JadeTurn(true, user)), 1600)
        val messages = JsonArray().apply {
            add(msg("system", system))
            add(msg("user", user))
        }
        val answer = completeWithFallback(config, modelsFor(config).toMutableList(), messages, withTools = false, maxTokens = 1600)
        clean(answer.get("content").asText()) ?: throw JadeException("Jade didn't return anything usable. Try again.", retryable = true)
    }

    /**
     * A keyboard theme for [description] ("cozy autumn café", "my football club"): Jade picks the palette,
     * shape, typeface and mascot. The result is cleaned and made readable before anyone installs it.
     */
    suspend fun designTheme(description: String): KeyboardThemeSpec = withContext(Dispatchers.IO) {
        require(description.isNotBlank()) { "Describe the look you want." }
        val config = readyConfig()
        val user = description.take(300)
        val raw = if (config.isClaude() || config.isGemini()) {
            plainCompletion(config, THEME_PROMPT, listOf(JadeTurn(true, user)), 500)
        } else {
            val messages = JsonArray().apply {
                add(msg("system", THEME_PROMPT))
                add(msg("user", user))
            }
            completeWithFallback(config, modelsFor(config).toMutableList(), messages, withTools = false, maxTokens = 500).get("content").asText().orEmpty()
        }
        val jsonText = raw.substringAfter('{', "").substringBeforeLast('}', "")
        val spec = KeyboardThemeSpec.fromJson("{$jsonText}")
            ?: throw JadeException("Jade's theme didn't come out right. Try again.", retryable = true)
        spec.readable().copy(author = "Jade", style = spec.style.ifBlank { "jade" })
    }

    private fun readyConfig(): AIConfigProvider.AIConfig {
        val config = AIConfigProvider.reloadConfig(appContext)
        if (!config.hasValidApiKey()) throw JadeException("No AI key is set up. Add one in Settings → AI preferences.")
        return config
    }

    private fun systemPrompt(userName: String?, toolsOn: Boolean): String = buildString {
        append("You are Jade, the cheerful, warm writing buddy inside Rewordium, an Android app with the ReBoard keyboard and an accessibility writing assistant.")
        userName?.takeIf { it.isNotBlank() }?.let { append(" You're talking with $it.") }
        append("\n\nWriting: help rewrite, fix grammar, adjust tone, summarize, translate, brainstorm and draft replies. Keep the writer's voice. Be concise, with short paragraphs or bullets. When you give rewritten text, give the text itself without preamble unless asked.")
        if (toolsOn) {
            append(
                "\n\nSettings: you can read and change EVERY ReBoard keyboard setting (layout, height, themes, sounds, vibration, glide typing, " +
                    "autocorrect, suggestions, emoji, clipboard, gestures, smartbar, and hidden developer options) and the Rewordium app's own " +
                    "settings (keys starting with app__: light/dark mode, colour palette, the user's avatar, touch feedback, keyboard AI tools, writing persona).\n" +
                    "- Use find_settings to get exact keys and allowed values before changing anything; never guess keys.\n" +
                    "- Then change_settings (several at once is fine). Respect each type, option list and range. For 'bigger/smaller/more/less', step from the current value sensibly.\n" +
                    "- The app shows an undo card after every change, so don't ask 'are you sure' for ordinary changes: do it, then confirm in one friendly sentence.\n" +
                    "- Advanced settings automatically ask the user for approval.\n" +
                    "- Keyboard languages (add, remove, switch, see what's available) go through manage_keyboard_languages.\n" +
                    "- For things a setting can't do (editing theme colours, dictionary words, backups, stickers) use open_settings_screen.\n" +
                    "- Only say something changed if the tool result says it worked.",
            )
        } else {
            append("\n\nWith the AI provider currently chosen you can't change settings; if asked, say settings control works with the default Rewordium AI or an OpenAI-compatible provider.")
        }
        append("\n\nTalk like a kind friend. Never mention tool names, setting keys or JSON. A light emoji now and then is welcome.")
    }

    private fun modelsFor(config: AIConfigProvider.AIConfig): List<String> {
        val model = config.model.trim()
        val isGroq = config.provider.equals(AIConfigProvider.PROVIDER_GROQ, ignoreCase = true)
        val isOpenAi = config.provider.equals(AIConfigProvider.PROVIDER_OPENAI, ignoreCase = true)
        val primary = when {
            isOpenAi && (model.isBlank() || model == DEFAULT_GROQ_MODEL) -> DEFAULT_OPENAI_MODEL
            model.isBlank() -> DEFAULT_GROQ_MODEL
            else -> model
        }
        return if (!config.isAdvancedEnabled && isGroq) listOf(primary) + GROQ_FALLBACKS.filter { it != primary } else listOf(primary)
    }

    /**
     * Sends one request, moving down the model list when the bundled key is
     * throttled. A model that worked stays first for the rest of the turn.
     */
    private suspend fun completeWithFallback(
        config: AIConfigProvider.AIConfig,
        models: MutableList<String>,
        messages: JsonArray,
        withTools: Boolean,
        maxTokens: Int = 1200,
    ): JsonObject {
        var last: JadeException? = null
        for (model in models.toList()) {
            var attempt = 0
            while (true) {
                try {
                    val result = openAiCompletion(config, model, messages, withTools, maxTokens)
                    if (models.first() != model) { models.remove(model); models.add(0, model) }
                    return result
                } catch (e: JadeException) {
                    last = e
                    if (e.retryable && attempt == 0) { attempt++; delay(800); continue }
                    break
                }
            }
            if (last?.rateLimited != true && last?.retryable != true) break
        }
        throw last ?: JadeException("Jade couldn't reply just now. Please try again.")
    }

    private fun openAiCompletion(
        config: AIConfigProvider.AIConfig,
        model: String,
        messages: JsonArray,
        withTools: Boolean,
        maxTokens: Int,
    ): JsonObject {
        val isOpenAi = config.provider.equals(AIConfigProvider.PROVIDER_OPENAI, ignoreCase = true)
        val reasoningModel = isOpenAi && Regex("^(o\\d|gpt-5)").containsMatchIn(model)
        val body = JsonObject().apply {
            addProperty("model", model)
            add("messages", messages)
            if (withTools) {
                add("tools", tools.schemas)
                addProperty("tool_choice", "auto")
            }
            if (reasoningModel) {
                addProperty("max_completion_tokens", maxTokens)
                addProperty("reasoning_effort", if (model.startsWith("gpt-5")) "minimal" else "low")
            } else {
                addProperty("max_tokens", maxTokens)
                addProperty("temperature", 0.6)
                // gpt-oss reasons before answering; "low" keeps that short so the answer isn't cut off.
                if (model.contains("gpt-oss")) addProperty("reasoning_effort", "low")
            }
        }
        val request = Request.Builder()
            .url(config.getBaseUrl())
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val json = send(request, config, model)
        return json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")
            ?: throw JadeException("Jade didn't return anything usable. Try again.", retryable = true)
    }

    /** Anthropic and Gemini keys: a plain text conversation. */
    private fun plainCompletion(config: AIConfigProvider.AIConfig, system: String, turns: List<JadeTurn>, maxTokens: Int): String {
        val model = config.model.trim()
        val json = if (config.isClaude()) {
            val resolved = if (model.isBlank() || !model.startsWith("claude")) DEFAULT_CLAUDE_MODEL else model
            val body = JsonObject().apply {
                addProperty("model", resolved)
                addProperty("max_tokens", maxTokens)
                addProperty("system", system)
                add("messages", JsonArray().apply { mergedTurns(turns).forEach { (user, text) -> add(msg(if (user) "user" else "assistant", text)) } })
            }
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody(JSON))
                .build()
            send(request, config, resolved).getAsJsonArray("content")
                ?.mapNotNull { it.asJsonObject.takeIf { block -> block.get("type").asText() == "text" }?.get("text").asText() }
                ?.joinToString("")
        } else {
            val resolved = if (model.isBlank() || !model.startsWith("gemini")) DEFAULT_GEMINI_MODEL else model
            val body = JsonObject().apply {
                add("systemInstruction", JsonObject().apply { add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", system) }) }) })
                add("contents", JsonArray().apply {
                    mergedTurns(turns).forEach { (user, text) ->
                        add(JsonObject().apply {
                            addProperty("role", if (user) "user" else "model")
                            add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", text) }) })
                        })
                    }
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("maxOutputTokens", maxTokens)
                    if (resolved.contains("2.5-flash")) add("thinkingConfig", JsonObject().apply { addProperty("thinkingBudget", 0) })
                })
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$resolved:generateContent")
                .addHeader("x-goog-api-key", config.apiKey)
                .post(body.toString().toRequestBody(JSON))
                .build()
            send(request, config, resolved).getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                ?.mapNotNull { part -> part.asJsonObject.takeIf { it.get("thought")?.asBoolean != true }?.get("text").asText() }
                ?.joinToString("")
        }
        return clean(json) ?: throw JadeException("Jade didn't return anything usable. Try again.", retryable = true)
    }

    /** Providers that want strictly alternating roles get consecutive turns merged. */
    private fun mergedTurns(turns: List<JadeTurn>): List<Pair<Boolean, String>> {
        val merged = mutableListOf<Pair<Boolean, String>>()
        turns.dropWhile { !it.fromUser }.forEach { turn ->
            val last = merged.lastOrNull()
            if (last != null && last.first == turn.fromUser) merged[merged.lastIndex] = last.first to last.second + "\n\n" + turn.text
            else merged += turn.fromUser to turn.text
        }
        return merged
    }

    private fun send(request: Request, config: AIConfigProvider.AIConfig, model: String): JsonObject {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "${config.provider}/$model failed: ${response.code} ${body.take(300)}")
                    throw httpError(response.code, config, model, body)
                }
                return runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
                    throw JadeException("Jade got a garbled answer. Try again.", retryable = true)
                }
            }
        } catch (e: JadeException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw JadeException("Jade took too long to answer. Try again.", retryable = true)
        } catch (e: UnknownHostException) {
            throw JadeException("You're offline. Check your connection and try again.")
        } catch (e: IOException) {
            throw JadeException("Network hiccup. Check your connection and try again.", retryable = true)
        }
    }

    private fun httpError(code: Int, config: AIConfigProvider.AIConfig, model: String, body: String): JadeException = when (code) {
        400 -> if (body.contains("tool", ignoreCase = true)) {
            JadeException("Jade tripped over a settings step. Try asking in different words.", retryable = true)
        } else JadeException("The AI couldn't process that request.")
        401, 403 -> if (config.isAdvancedEnabled) JadeException("Your API key was rejected. Check it in Settings → AI preferences.")
        else JadeException("The AI service rejected the request. Please update Rewordium.")
        404 -> JadeException("The AI model \"$model\" wasn't found. Check Settings → AI preferences.")
        408 -> JadeException("Jade took too long to answer. Try again.", retryable = true)
        413 -> JadeException("That's too long for one request. Try a shorter piece.")
        429 -> JadeException("Jade is a little busy right now. Try again in a minute.", rateLimited = true)
        in 500..599 -> JadeException("The AI service is having trouble. Try again shortly.", retryable = true)
        else -> JadeException("The AI couldn't process that (error $code).")
    }

    private fun clean(raw: String?): String? = raw
        ?.replace(Regex("(?s)<think>.*?</think>"), "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun msg(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }

    private fun JsonElement?.asText(): String? = this?.takeIf { it.isJsonPrimitive }?.asString

    companion object {
        private val THEME_PROMPT = """
            You design keyboard themes for a phone keyboard. Reply with ONE JSON object and nothing else:
            {"name": short catchy name (max 3 words),
             "night": true for a dark theme, false for light,
             "background": "#RRGGBB" behind the keys,
             "keys": "#RRGGBB" letter keys,
             "letters": "#RRGGBB" text on the keys (must contrast strongly with keys),
             "accent": "#RRGGBB" enter key and highlights (vivid),
             "special": "#RRGGBB" shift/delete/symbol keys (between keys and accent),
             "shape": one of SQUARE, ROUNDED, SOFT, PILL, BUBBLE, LEAF, CUT, TICKET,
             "pressed_shape": one of the same shapes (what a key morphs into when pressed),
             "font": one of SYSTEM, ROUNDED (playful), SOFT (friendly), MONO (techy), SERIF (elegant),
             "bold": true or false,
             "outline": true or false,
             "shadow": 0 to 4,
             "mascot": one of clover, flower, triangle, square, blob, ghost, circle, drop, star, droid, mech, alien, hexagon, cat, cloud, pill, pebble, puddle, or null for none,
             "style": one of playful, expressive, professional, clean}
            Match the mood the user describes. Keep it readable: letters vs keys contrast at least 4.5:1.
        """.trimIndent()

        private const val TAG = "JadeEngine"
        private const val MAX_TOOL_ROUNDS = 6
        private const val MAX_HISTORY = 12
        const val MAX_INPUT = 8_000
        private const val DEFAULT_GROQ_MODEL = "openai/gpt-oss-120b"
        private val GROQ_FALLBACKS = listOf("openai/gpt-oss-20b", "qwen/qwen3.8-27b")
        private const val DEFAULT_OPENAI_MODEL = "gpt-4.1-mini"
        private const val DEFAULT_CLAUDE_MODEL = "claude-haiku-4-5"
        private const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        private val JSON = "application/json".toMediaType()
    }
}
