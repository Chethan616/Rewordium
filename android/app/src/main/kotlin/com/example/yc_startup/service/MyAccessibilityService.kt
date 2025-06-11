package com.example.yc_startup.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class MyAccessibilityService : AccessibilityService(), BubbleListener, ResultWindowListener {

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val EVALUATION_DELAY_MS = 150L
        private val readingAppWhitelist = setOf("com.google.android.apps.docs", "com.microsoft.office.word", "com.microsoft.office.officehubrow", "com.adobe.reader", "cn.wps.moffice_eng", "com.android.chrome", "org.mozilla.firefox")
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val bubbleManager: BubbleManager by lazy { BubbleManager(this, this) }
    private val resultWindowManager: ResultWindowManager by lazy { ResultWindowManager(this, this) }
    private val groqRepository: GroqRepository by lazy { GroqRepository() }
    private val handler = Handler(Looper.getMainLooper())
    private val evaluationRunnable = Runnable { evaluateCurrentState() }

    private var lastAction: MenuAction? = null
    private var lastText: String? = null
    private var lastTargetLanguage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handler.removeCallbacks(evaluationRunnable)
        handler.postDelayed(evaluationRunnable, EVALUATION_DELAY_MS)
    }

    private fun evaluateCurrentState() {
        val rootNode = rootInActiveWindow ?: run { bubbleManager.updateVisibility(false); return }
        val activePackageName = rootNode.packageName?.toString()
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        try {
            val isInReadingApp = activePackageName in readingAppWhitelist
            val isFocusEditable = focusedNode?.isEditable == true
            val shouldShow = isInReadingApp || isFocusEditable
            bubbleManager.updateVisibility(shouldShow)
        } finally {
            rootNode.recycle()
            focusedNode?.recycle()
        }
    }

    override fun onPerformAction(action: MenuAction) {
        try {
            val textToProcess = getTextForAction()
            if (textToProcess.isNullOrBlank()) {
                Log.w(TAG, "No text found for action.")
                return
            }

            this.lastAction = action
            this.lastText = textToProcess
            this.lastTargetLanguage = null

            if (action == MenuAction.Translate) {
                val languages = listOf("English", "Spanish", "French", "German", "Japanese", "Chinese", "Hindi", "Russian", "Portuguese", "Arabic", "Korean", "Italian")
                resultWindowManager.showLanguageSelection(languages) { selectedLanguage ->
                    this.lastTargetLanguage = selectedLanguage
                    resultWindowManager.showLoading()
                    performApiCall(action, textToProcess, selectedLanguage)
                }
            } else {
                resultWindowManager.showLoading()
                performApiCall(action, textToProcess)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "CRITICAL ERROR in onPerformAction", t)
            // --- THIS IS THE CORRECTED BLOCK ---
            // Instead of calling a non-existent `show()`, we call `updateContent` which
            // ensures the window is visible and displays our error message.
            resultWindowManager.updateContent("Critical Error", "An unexpected error occurred.\n\n${t.message}")
            // --- END OF CORRECTION ---
        }
    }

    override fun onRefreshClicked() {
        Log.d(TAG, "Refresh clicked!")
        val action = lastAction
        val text = lastText
        if (action == null || text == null) {
            Log.w(TAG, "No last action to refresh.")
            return
        }
        resultWindowManager.showLoading()
        performApiCall(action, text, lastTargetLanguage)
    }

    private fun performApiCall(action: MenuAction, text: String, targetLanguage: String? = null) {
        serviceScope.launch {
            val (prompt, title) = createPromptForAction(action, text, targetLanguage)
            val result = withContext(Dispatchers.IO) { groqRepository.getCompletion(prompt) }
            when (result) {
                is AiResult.Success -> resultWindowManager.updateContent(title, result.text)
                is AiResult.Error -> {
                    Log.e(TAG, "AI Error: ${result.message}")
                    resultWindowManager.updateContent("Error", result.message)
                }
            }
        }
    }

    private fun getTextForAction(): String? {
        var rootNode: AccessibilityNodeInfo? = null
        var focusedNode: AccessibilityNodeInfo? = null
        try {
            rootNode = rootInActiveWindow ?: return null
            focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode?.isEditable == true) {
                return focusedNode.text?.toString()
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val originalClipboard: ClipData? = clipboard.primaryClip
            val success = rootNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
            val selectedText = if (success) clipboard.primaryClip?.getItemAt(0)?.text?.toString() else null
            if (originalClipboard != null) {
                clipboard.setPrimaryClip(originalClipboard)
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            return selectedText
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get text due to a critical error", t)
            return null
        } finally {
            focusedNode?.recycle()
            rootNode?.recycle()
        }
    }

    private fun createPromptForAction(action: MenuAction, text: String, language: String? = null): Pair<String, String> {
        return when (action) {
            MenuAction.Translate -> Pair("Translate the following text to ${language ?: "Spanish"}. Return only the translated text, without any additional explanations or introductions: \"$text\"", "Translation to ${language ?: "Spanish"}")
            MenuAction.Summarize -> Pair("Summarize the following text concisely. Focus on the main points and keep it brief: \"$text\"", "Summary")
            MenuAction.GrammarCheck -> Pair("Correct any grammatical errors in the following text. Return only the corrected version of the text, without any explanations: \"$text\"", "Grammar Check")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
        handler.removeCallbacks(evaluationRunnable)
        bubbleManager.cleanUp()
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service Destroyed")
        handler.removeCallbacks(evaluationRunnable)
        bubbleManager.cleanUp()
        serviceScope.cancel()
    }
}