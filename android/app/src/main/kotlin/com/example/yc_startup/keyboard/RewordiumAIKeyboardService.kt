package com.example.yc_startup.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.InputType
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.yc_startup.R
import com.example.yc_startup.keyboard.api.GroqApiClient
import com.example.yc_startup.keyboard.dictionary.AutocorrectManager
import com.example.yc_startup.keyboard.dictionary.SuggestionEngine
import com.example.yc_startup.keyboard.dictionary.model.SuggestionContext
import com.example.yc_startup.keyboard.dictionary.provider.ContractionProvider
import com.example.yc_startup.keyboard.util.KeyboardConstants
import kotlinx.coroutines.*

class RewordiumAIKeyboardService : InputMethodService() {

    // ... (All properties remain the same) ...
    internal lateinit var layoutManager: KeyboardLayoutManager
    internal var paraphraseManager: ParaphraseViewManager? = null
    internal var keyboardHeight = 0
    internal var isCapsOn = false
    internal var isCapsLock = false
    internal var isSymbolsShown = false
    internal var isEmojiKeyboardShown = false
    internal var currentEmojiCategoryIndex = 0
    private var lastShiftTime: Long = 0
    private val DOUBLE_TAP_SHIFT_TIMEOUT = 250L
    private var lastSpaceTime: Long = 0
    internal var themeColor = "#007AFF"
    internal var isDarkMode = false
    internal var isHapticFeedbackEnabled = true
    internal var isAutoCapitalizeEnabled = true
    internal var isDoubleSpacePeriodEnabled = true
    internal var isAutocorrectEnabled = true
    internal var currentSuggestions: List<String> = emptyList()
    private var pendingAutocorrection: Pair<String, String>? = null
    internal var currentParaphraseJob: Job? = null
    internal var availablePersonas = mutableListOf("Neutral", "Happy", "Sad", "Humor")
    private var currentSentenceStart: Int = -1
    private var currentEditorInfo: EditorInfo? = null
    private var currentInputTypeSupportsMultiLine: Boolean = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val deleteHandler = Handler(Looper.getMainLooper())
    private lateinit var deleteRunnable: Runnable
    private var isDeleting = false


    // ... (onCreate and other initial methods remain the same) ...
    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                KeyboardConstants.ACTION_SETTINGS_UPDATED -> {
                    Log.d(KeyboardConstants.TAG, "Received settings update broadcast.")
                    loadSettings()
                    updateSystemNavBar()
                    if (::layoutManager.isInitialized) layoutManager.applyTheme(isDarkMode, themeColor)
                }
                KeyboardConstants.ACTION_PERSONAS_UPDATED -> {
                    Log.d(KeyboardConstants.TAG, "Received persona update broadcast.")
                    loadPersonas()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        loadSettings()
        loadPersonas()
        AutocorrectManager.initialize(this)
        SuggestionEngine.initialize(this)
        val intentFilter = IntentFilter().apply {
            addAction(KeyboardConstants.ACTION_SETTINGS_UPDATED)
            addAction(KeyboardConstants.ACTION_PERSONAS_UPDATED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsUpdateReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsUpdateReceiver, intentFilter)
        }
    }

    private fun updateSystemNavBar() {
        val window: Window? = window?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    override fun onInitializeInterface() {
        super.onInitializeInterface()
        val window: Window? = window?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = Color.TRANSPARENT
            updateSystemNavBar()
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val themeResId = if (isDarkMode) R.style.KeyboardTheme_Dark else R.style.KeyboardTheme_Light
        val contextThemeWrapper = ContextThemeWrapper(this, themeResId)
        val layoutInflater = layoutInflater.cloneInContext(contextThemeWrapper)
        val rootView = layoutInflater.inflate(R.layout.ios_keyboard_layout, null)
        layoutManager = KeyboardLayoutManager(this)
        layoutManager.initialize(rootView)
        return rootView
    }
    
    // =========================================================================
    // START OF THE FINAL FIX
    // =========================================================================

    /**
     * This is the definitive fix. This method is called when the user leaves the text field
     * (e.g., switches app, goes to home screen). By hiding the window and resetting state,
     * we ensure the keyboard always starts fresh and never gets into a broken state.
     */
    override fun onFinishInput() {
        super.onFinishInput()
        // Reset internal state to default values.
        isSymbolsShown = false
        isEmojiKeyboardShown = false
        isCapsOn = false
        isCapsLock = false

        // Hide the keyboard window. This forces a clean start next time.
        hideWindow()
    }
    
    /**
     * Handles live configuration changes (like screen rotation) while the keyboard is active.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Hide the window to prevent weird artifacts during recreation
        hideWindow()
        // Reload settings and recreate the view to match the new configuration
        loadSettings()
        updateSystemNavBar()
        setInputView(onCreateInputView())
    }

    /**
     * Sets up the keyboard's internal STATE when it's about to be displayed.
     * The actual layout drawing is deferred to onWindowShown to avoid race conditions.
     */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        this.currentEditorInfo = info
        this.currentInputTypeSupportsMultiLine = (info?.inputType ?: 0 and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        paraphraseManager?.exitParaphraseMode()
        layoutManager.applyTheme(isDarkMode, themeColor)
        layoutManager.updateSuggestions(emptyList())

        if (isAutoCapitalizeEnabled) {
            val capType = info?.inputType ?: 0
            val textBefore = currentInputConnection?.getTextBeforeCursor(1, 0)
            isCapsOn = (capType and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0 && (textBefore.isNullOrEmpty() || textBefore.endsWith(" ") || textBefore.endsWith("\n"))
        } else {
            isCapsOn = false
        }
        isCapsLock = false
    }

    /**
     * This is the safest place to perform the actual layout and drawing of the keys,
     * as it guarantees the window has its final dimensions.
     */
    override fun onWindowShown() {
        super.onWindowShown()
        if (::layoutManager.isInitialized) {
            layoutManager.updateLayout()
        }
    }
    // =========================================================================
    // END OF THE FINAL FIX
    // =========================================================================

    override fun onFinishInputView(finishingInput: Boolean) {
        // This is called just before onFinishInput. Perform view-specific cleanup here.
        layoutManager.cleanup()
        stopTurboDelete()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel("Service is being destroyed")
        unregisterReceiver(settingsUpdateReceiver)
    }

    // ... All other methods (handleText, handleBackspace, etc.) remain unchanged.
    fun handleAIButton() {
        performHapticFeedback()
        val rootView = layoutManager.getRootView()
        if (rootView == null) {
            showToast("Cannot open AI rewriter.")
            return
        }
        if (paraphraseManager == null) {
            paraphraseManager = ParaphraseViewManager(this, rootView as FrameLayout)
        }
        val ic = currentInputConnection ?: return
        val textBeforeCursor = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        currentSentenceStart = findSentenceStart(textBeforeCursor)
        val currentSentence = textBeforeCursor.substring(currentSentenceStart)
        if (currentSentence.trim().length < 5) {
            showToast("Type a longer sentence to reword")
            return
        }
        if (isEmojiKeyboardShown || isSymbolsShown) {
            switchToLetters()
        }
        paraphraseManager?.show(currentSentence)
    }

    fun switchToEmoji() {
        performHapticFeedback()
        isEmojiKeyboardShown = true
        isSymbolsShown = false
        layoutManager.updateLayout()
    }

    fun handleText(text: String, fromKey: Boolean = false) {
        if (fromKey) performHapticFeedback()
        val ic = currentInputConnection ?: return
        if (text == " ") {
            val (currentWord, previousWord) = getContextWords()
            if (currentWord.isNotBlank()) {
                SuggestionEngine.learn(currentWord, previousWord)
            }
        }
        if (isCapsOn && !isCapsLock) {
            isCapsOn = false
            layoutManager.updateLetterKeys()
        }
        if (text == " ") {
            if (applyAutocorrectionOrContraction(ic)) {
                ic.commitText(" ", 1)
                updateSuggestionsFromInput()
                return
            }
            if (isDoubleSpacePeriodEnabled && System.currentTimeMillis() - lastSpaceTime < 400) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                if (isAutoCapitalizeEnabled) {
                    isCapsOn = true
                    isCapsLock = false
                    layoutManager.updateLayout()
                }
                lastSpaceTime = 0
                return
            }
            lastSpaceTime = System.currentTimeMillis()
        } else {
            lastSpaceTime = 0
            if (".?!".contains(text) && isAutoCapitalizeEnabled) {
                isCapsOn = true
                isCapsLock = false
            }
        }
        ic.commitText(text, 1)
        updateSuggestionsFromInput()
    }

    fun handleEmojiKeyPress(emoji: String) {
        addEmojiToRecents(emoji)
        handleText(emoji, fromKey = true)
    }

    fun handleBackspace() {
        performHapticFeedback()
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            ic.commitText("", 1)
        }
        updateSuggestionsFromInput()
    }

    fun toggleShift() {
        performHapticFeedback()
        val currentTime = System.currentTimeMillis()
        if (isCapsLock) {
            isCapsLock = false
            isCapsOn = false
        } else if (isCapsOn && (currentTime - lastShiftTime) < DOUBLE_TAP_SHIFT_TIMEOUT) {
            isCapsLock = true
            isCapsOn = true
        } else {
            isCapsOn = !isCapsOn
        }
        lastShiftTime = currentTime
        layoutManager.updateLetterKeys()
    }

    fun handleReturnKey() {
        performHapticFeedback()
        val ic = currentInputConnection ?: return
        val editorAction = currentEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)

        when (editorAction) {
            EditorInfo.IME_ACTION_DONE -> {
                ic.commitText("\n", 1)
            }
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT -> {
                ic.performEditorAction(editorAction)
            }
            else -> {
                if (currentInputTypeSupportsMultiLine) {
                    ic.commitText("\n", 1)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }
        }
    }

    fun onSuggestionTapped(suggestion: String) {
        performHapticFeedback()
        val ic = currentInputConnection ?: return
        val (currentWord, previousWord) = getContextWords()
        val textBeforeCursor = ic.getTextBeforeCursor(100, 0)
        val composingText = textBeforeCursor?.takeLastWhile { it.isLetterOrDigit() || it == '\'' }
        if (!composingText.isNullOrEmpty()) {
            ic.deleteSurroundingText(composingText.length, 0)
        }
        ic.commitText("$suggestion ", 1)
        SuggestionEngine.learn(suggestion, previousWord)
        Handler(Looper.getMainLooper()).post {
            updateSuggestionsFromInput()
        }
    }

    fun switchToLetters() {
        performHapticFeedback()
        isSymbolsShown = false
        isEmojiKeyboardShown = false
        layoutManager.updateLayout()
    }

    fun switchToSymbols() {
        performHapticFeedback()
        isSymbolsShown = true
        isEmojiKeyboardShown = false
        layoutManager.updateLayout()
    }

    fun handleEmojiCategorySwitch(index: Int) {
        performHapticFeedback()
        if (currentEmojiCategoryIndex != index) {
            currentEmojiCategoryIndex = index
            layoutManager.setupEmojiKeyboard()
        }
    }

    fun generateParaphraseWithPersona(text: String, persona: String) {
        currentParaphraseJob?.cancel()
        currentParaphraseJob = coroutineScope.launch {
            try {
                val paraphrases = withContext(Dispatchers.IO) { GroqApiClient.getParaphrases(text, persona) }
                if (isActive) paraphraseManager?.updateWithResults(paraphrases)
            } catch (e: Exception) {
                if (e !is CancellationException && isActive) {
                    showToast("Error getting paraphrases")
                    paraphraseManager?.exitParaphraseMode()
                }
            }
        }
    }

    fun applyParaphrase(paraphrase: String) {
        val ic = currentInputConnection ?: return
        val textBeforeCursor = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        if (currentSentenceStart >= 0 && currentSentenceStart <= textBeforeCursor.length) {
            ic.deleteSurroundingText(textBeforeCursor.length - currentSentenceStart, 0)
            ic.commitText(paraphrase, 1)
        }
        paraphraseManager?.exitParaphraseMode()
    }

    fun startTurboDelete() {
        isDeleting = true
        handleBackspace()
        deleteRunnable = object : Runnable {
            override fun run() {
                if (isDeleting) {
                    handleBackspace()
                    deleteHandler.postDelayed(this, KeyboardConstants.REPEAT_DELETE_DELAY)
                }
            }
        }
        deleteHandler.postDelayed(deleteRunnable, KeyboardConstants.INITIAL_DELETE_DELAY)
    }

    fun stopTurboDelete() {
        isDeleting = false
        if (::deleteRunnable.isInitialized) deleteHandler.removeCallbacks(deleteRunnable)
    }

    fun getRecentEmojis(): List<String> {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val emojisString = prefs.getString(KeyboardConstants.KEY_RECENT_EMOJIS, "") ?: ""
        if (emojisString.isBlank()) return emptyList()
        return emojisString.split(",")
    }

    private fun addEmojiToRecents(emoji: String) {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val recentEmojis = getRecentEmojis().toMutableList()
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        val updatedRecents = recentEmojis.take(KeyboardConstants.MAX_RECENT_EMOJIS)
        prefs.edit().putString(KeyboardConstants.KEY_RECENT_EMOJIS, updatedRecents.joinToString(",")).apply()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean(KeyboardConstants.KEY_DARK_MODE, false)
        themeColor = prefs.getString(KeyboardConstants.KEY_THEME_COLOR, "#007AFF") ?: "#007AFF"
        val hapticValue = prefs.getBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true)
        Log.d(KeyboardConstants.TAG, "Loading haptic feedback setting: $hapticValue")
        isHapticFeedbackEnabled = hapticValue
        isAutoCapitalizeEnabled = prefs.getBoolean(KeyboardConstants.KEY_AUTO_CAPITALIZE, true)
        isDoubleSpacePeriodEnabled = prefs.getBoolean(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD, true)
        isAutocorrectEnabled = prefs.getBoolean(KeyboardConstants.KEY_AUTOCORRECT, true)
        Log.d(KeyboardConstants.TAG, "Current haptic feedback state: $isHapticFeedbackEnabled")
    }

    private fun loadPersonas() {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedPersonas = prefs.getString(KeyboardConstants.KEY_PERSONAS, null)
        availablePersonas.clear()
        availablePersonas.add("Neutral")
        savedPersonas?.let {
            val personaList = it.split(",").filter { name ->
                name.isNotBlank() && name != "Neutral"
            }.distinct().take(4)
            availablePersonas.addAll(personaList)
        }
        Log.d(KeyboardConstants.TAG, "Loaded personas: ${availablePersonas.joinToString()}")
    }

    fun updateKeyboardPersonas(personaList: List<String>) {
        Log.d(KeyboardConstants.TAG, "Updating keyboard personas: ${personaList.joinToString()}")
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KeyboardConstants.KEY_PERSONAS, personaList.joinToString(","))
            .apply()
        loadPersonas()
        paraphraseManager?.updatePersonaButtons()
        Log.d(KeyboardConstants.TAG, "Keyboard personas updated successfully")
    }

    fun updateSuggestionsFromInput() {
        val (currentWord, previousWord, isAfterSpace) = getFullContext()
        if (currentWord.isBlank() && !isAfterSpace) {
            layoutManager.updateSuggestions(emptyList())
            return
        }
        val context = if (isAfterSpace) {
            SuggestionContext(currentInput = "", isAfterSpace = true, previousWord = currentWord)
        } else {
            SuggestionContext(currentInput = currentWord, isAfterSpace = false, previousWord = previousWord)
        }
        val suggestions = SuggestionEngine.getSuggestions(context)
        if (isAutocorrectEnabled && !isAfterSpace && currentWord.isNotEmpty()) {
            val (needsCorrection, correctedWord) = AutocorrectManager.checkAndCorrect(currentWord, this)
            if (needsCorrection) {
                val finalSuggestions = listOf(correctedWord) + suggestions.filter { it.equals(correctedWord, ignoreCase = true).not() }
                layoutManager.updateSuggestions(finalSuggestions.distinct())
                return
            }
        }
        layoutManager.updateSuggestions(suggestions)
    }

    private fun applyAutocorrectionOrContraction(ic: InputConnection): Boolean {
        performHapticFeedback()
        val textBeforeCursor = ic.getTextBeforeCursor(100, 0)?.toString() ?: return false
        val lastWord = textBeforeCursor.trim().split("\\s+".toRegex()).lastOrNull() ?: return false
        if (pendingAutocorrection?.first == lastWord) {
            ic.deleteSurroundingText(lastWord.length, 0)
            ic.commitText(pendingAutocorrection!!.second, 1)
            pendingAutocorrection = null
            return true
        }
        val contractionProvider = ContractionProvider()
        val contraction = contractionProvider.getSuggestions(lastWord, SuggestionContext(currentInput = lastWord, isAfterSpace = false)).firstOrNull()
        if (contraction != null) {
            ic.deleteSurroundingText(lastWord.length, 0)
            ic.commitText(contraction.word, 1)
            SuggestionEngine.learn(contraction.word, null)
            return true
        }
        return false
    }

    private fun getContextWords(): Pair<String, String?> {
        val textBeforeCursor = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val tokens = textBeforeCursor.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (textBeforeCursor.endsWith(" ")) {
            return Pair("", tokens.lastOrNull())
        }
        val currentWord = tokens.lastOrNull() ?: ""
        val previousWord = if (tokens.size > 1) tokens[tokens.size - 2] else null
        return Pair(currentWord, previousWord)
    }

    private fun getFullContext(): Triple<String, String?, Boolean> {
        val textBeforeCursor = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val isAfterSpace = textBeforeCursor.endsWith(" ") || textBeforeCursor.isEmpty()
        val tokens = textBeforeCursor.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val currentWord = if (isAfterSpace) "" else tokens.lastOrNull() ?: ""
        val previousWord = if (isAfterSpace) {
            tokens.lastOrNull()
        } else if (tokens.size > 1) {
            tokens[tokens.size - 2]
        } else {
            null
        }
        return Triple(currentWord, previousWord, isAfterSpace)
    }

    private fun findSentenceStart(text: String): Int {
        val lastTerminator = text.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (lastTerminator != -1 && lastTerminator + 2 <= text.length) lastTerminator + 2 else 0
    }

    fun getReturnKeyLabel(): String {
        return when (currentEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "return"
        }
    }

    fun performHapticFeedback() {
        Log.d(KeyboardConstants.TAG, "performHapticFeedback called. isHapticFeedbackEnabled: $isHapticFeedbackEnabled")
        if (!isHapticFeedbackEnabled) {
            Log.d(KeyboardConstants.TAG, "Haptic feedback is disabled, skipping")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rootView = layoutManager.getRootView()
                if (rootView != null) {
                    Log.d(KeyboardConstants.TAG, "Performing haptic feedback (API ${Build.VERSION.SDK_INT}+)")
                    rootView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                } else {
                    Log.w(KeyboardConstants.TAG, "Root view is null, cannot perform haptic feedback")
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null) {
                    Log.d(KeyboardConstants.TAG, "Performing haptic feedback (legacy vibrator)")
                    vibrator.vibrate(5)
                } else {
                    Log.w(KeyboardConstants.TAG, "Vibrator service not available")
                }
            }
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "Error performing haptic feedback", e)
        }
    }

    fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
}