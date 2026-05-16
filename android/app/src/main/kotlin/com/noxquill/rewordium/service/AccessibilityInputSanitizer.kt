package com.noxquill.rewordium.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Filters accessibility "text" that is really hint/placeholder copy (common in Meta apps).
 */
object AccessibilityInputSanitizer {

    private val META_APP_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.facebook.lite",
        "com.facebook.mlite",
        "com.fb.mlite",
        "com.instagram.lite",
    )

    private val INVISIBLE_CHARS = Regex("[\\u200B-\\u200D\\uFEFF\\u200E\\u200F\\u00A0]")

    private val KNOWN_PLACEHOLDER_PHRASES = listOf(
        "message",
        "messages",
        "type a message",
        "type a message...",
        "type message",
        "write a message",
        "enter message",
        "say something",
        "ask meta ai",
        "ask meta",
        "meta ai",
        "search",
        "search...",
        "write something",
        "add a comment",
        "reply",
        "send message",
        "type here",
        "compose message",
        "start typing",
        "what's on your mind",
        "create a post",
        "write a caption",
    )

    fun isMetaMessagingApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.lowercase()
        return META_APP_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }
    }

    fun isInputField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val className = node.className?.toString().orEmpty()
        return className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInputEditText", ignoreCase = true) ||
            className.contains("AutoCompleteTextView", ignoreCase = true) ||
            className.contains("MultiAutoCompleteTextView", ignoreCase = true) ||
            node.isEditable
    }

    fun sanitizeFieldText(node: AccessibilityNodeInfo?, rawText: String?): String {
        val text = rawText?.replace(INVISIBLE_CHARS, "")?.trim().orEmpty()
        if (text.isEmpty()) return ""

        if (isPlaceholderText(node, text)) return ""
        return text
    }

    fun isPlaceholderText(node: AccessibilityNodeInfo?, text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return true

        val lower = normalized.lowercase()

        val hint = node?.hintText?.toString()?.trim()
        if (!hint.isNullOrBlank()) {
            val hintLower = hint.lowercase()
            if (lower == hintLower) return true
            if (lower.contains(hintLower) && normalized.length <= hint.length + 12) return true
            if (hintLower.contains(lower) && lower.length >= 4 && lower.length <= 24) return true
        }

        val contentDesc = node?.contentDescription?.toString()?.trim()
        if (!contentDesc.isNullOrBlank()) {
            val descLower = contentDesc.lowercase()
            if (lower == descLower) return true
        }

        if (KNOWN_PLACEHOLDER_PHRASES.any { phrase -> lower == phrase }) return true

        val packageName = node?.packageName?.toString()
        if (isMetaMessagingApp(packageName)) {
            if (KNOWN_PLACEHOLDER_PHRASES.any { phrase ->
                    lower == phrase ||
                        (lower.contains(phrase) && lower.length <= phrase.length + 16)
                }) {
                return true
            }
            if (lower.contains("meta ai") || lower.contains("ask meta")) return true
        }

        if (isInputField(node) && lower.length <= 32) {
            if (lower == "message" || lower.endsWith(" message")) return true
            if (lower.startsWith("type ") || lower.startsWith("enter ") || lower.startsWith("ask ")) {
                return true
            }
        }

        return false
    }

    fun shouldSkipTextForScreenCapture(node: AccessibilityNodeInfo): Boolean {
        return isInputField(node)
    }
}
