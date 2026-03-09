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

import android.util.Log

/**
 * Detects whether the user is currently typing in an AI chat application.
 * When detected, the keyboard can offer "Prompt Enhancer" mode instead of
 * regular rewrite, helping users craft better prompts for AI assistants.
 */
object PromptEnhancerDetector {

    private const val TAG = "PromptEnhancerDetector"

    /**
     * Known AI app package names.
     * Covers major AI chat and assistant applications.
     */
    private val AI_APP_PACKAGES = setOf(
        // OpenAI / ChatGPT
        "com.openai.chatgpt",
        
        // Google Gemini / Bard
        "com.google.android.apps.bard",
        "com.google.android.apps.gemini",
        
        // Anthropic / Claude
        "com.anthropic.claude",
        
        // Meta AI
        // "com.facebook.orca",              // Messenger (has Meta AI)
        // "com.instagram.android",          // Instagram (has Meta AI)
        // "com.whatsapp",                   // WhatsApp (has Meta AI)
        // "com.facebook.katana",            // Facebook (has Meta AI)
        
        // Microsoft Copilot / Bing Chat
        "com.microsoft.copilot",
        "com.microsoft.bing",
        
        // Perplexity AI
        "ai.perplexity.app.android",
        
        // Character.AI
        "ai.character.app",
        
        // Jasper AI
        "com.jasper.chat",
        
        // Poe by Quora
        "com.quora.poe",
        
        // Replika
        "ai.replika.app",
        
        // DeepSeek
        "com.deepseek.chat",
        
        // Grok (xAI / Twitter)
        "com.twitter.android",
        
        // HuggingChat
        "co.huggingface.chat",
        
        // Pi AI
        "ai.inflection.pi",
        
        // Mistral
        "ai.mistral.chat",
        
        // You.com
        "com.you.app",
        
        // Cohere
        "com.cohere.chat",
        
        // Phind
        "com.phind.search",
        
        // Notion AI
        "notion.id",
        
        // Writesonic / Chatsonic
        "com.writesonic.app",
        
        // Copy.ai
        "com.copyai.app",
        
        // Samsung Bixby (AI features)
        "com.samsung.android.bixby.agent",
        
        // Brave Leo AI
        "com.brave.browser",
    )

    /**
     * Additional partial package name patterns for AI apps.
     * Used for fuzzy matching when exact package names aren't known.
     */
    private val AI_PACKAGE_PATTERNS = listOf(
        "chatgpt",
        "gemini",
        "claude",
        "copilot",
        "perplexity",
        "deepseek",
        "mistral",
        "huggingface",
        "openai",
        "anthropic",
    )

    /**
     * Check if the given package name belongs to a known AI application.
     *
     * @param packageName The package name of the currently focused app
     * @return true if the app is a known AI application
     */
    fun isAiApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        
        val pkg = packageName.lowercase()
        
        // Exact match first
        if (pkg in AI_APP_PACKAGES) {
            Log.d(TAG, "AI app detected (exact): $pkg")
            return true
        }
        
        // Fuzzy match for AI-related package names
        if (AI_PACKAGE_PATTERNS.any { pattern -> pkg.contains(pattern) }) {
            Log.d(TAG, "AI app detected (pattern): $pkg")
            return true
        }
        
        return false
    }

    /**
     * Get a user-friendly name for the detected AI app.
     *
     * @param packageName The package name of the AI app
     * @return Display name of the AI app, or null if not recognized
     */
    fun getAiAppName(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        
        return when (packageName.lowercase()) {
            "com.openai.chatgpt" -> "ChatGPT"
            "com.google.android.apps.bard", "com.google.android.apps.gemini" -> "Gemini"
            "com.anthropic.claude" -> "Claude"
            "com.microsoft.copilot" -> "Copilot"
            "com.microsoft.bing" -> "Bing Chat"
            "ai.perplexity.app.android" -> "Perplexity"
            "ai.character.app" -> "Character.AI"
            "com.quora.poe" -> "Poe"
            "com.deepseek.chat" -> "DeepSeek"
            "com.twitter.android" -> "Grok"
            "co.huggingface.chat" -> "HuggingChat"
            "ai.inflection.pi" -> "Pi"
            "ai.mistral.chat" -> "Mistral"
            "com.facebook.orca" -> "Meta AI"
            "com.instagram.android" -> "Meta AI"
            "com.whatsapp" -> "Meta AI"
            "com.facebook.katana" -> "Meta AI"
            "ai.replika.app" -> "Replika"
            "com.brave.browser" -> "Brave Leo"
            "notion.id" -> "Notion AI"
            else -> {
                // Try pattern matching for display name
                val pkg = packageName.lowercase()
                when {
                    pkg.contains("chatgpt") || pkg.contains("openai") -> "ChatGPT"
                    pkg.contains("gemini") -> "Gemini"
                    pkg.contains("claude") || pkg.contains("anthropic") -> "Claude"
                    pkg.contains("copilot") -> "Copilot"
                    pkg.contains("perplexity") -> "Perplexity"
                    pkg.contains("deepseek") -> "DeepSeek"
                    pkg.contains("mistral") -> "Mistral"
                    pkg.contains("huggingface") -> "HuggingChat"
                    else -> "AI App"
                }
            }
        }
    }
}
