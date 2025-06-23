package com.example.yc_startup.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.yc_startup.R

private const val TAG = "RewordiumAIKeyboard"
private const val PREFS_NAME = "RewordiumKeyboardPrefs"
private const val KEY_THEME_COLOR = "theme_color"
private const val KEY_DARK_MODE = "dark_mode"
private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
private const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
private const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"

/**
 * A custom iOS-styled keyboard service with AI-powered features
 * that can be customized from the Flutter app settings.
 */
class RewordiumAIKeyboardService : InputMethodService() {
    // UI components
    private var inputView: View? = null
    private lateinit var keyboardContainer: LinearLayout
    
    // Keyboard rows
    private lateinit var numberRow: LinearLayout
    private lateinit var rowQwerty: LinearLayout
    private lateinit var rowAsdf: LinearLayout
    private lateinit var rowZxcv: LinearLayout
    private lateinit var bottomRow: LinearLayout
    
    // Keyboard state
    private var isCapsOn = false
    private var isCapsLock = false
    private var isSymbolsShown = false
    private var lastSpaceTime: Long = 0
    private var lastKeyWasSpace = false
    
    // Theme settings
    private var themeColor = "#007AFF" // Default iOS blue
    private var isDarkMode = false
    private var isHapticFeedbackEnabled = true
    private var isAutoCapitalizeEnabled = true
    private var isDoubleSpacePeriodEnabled = true
    
    // Keyboard colors
    private var keyBackgroundColor = 0
    private var keyTextColor = 0
    private var specialKeyBackgroundColor = 0
    private var keyboardBackgroundColor = 0
    
    // Shared preferences for settings
    private lateinit var preferences: SharedPreferences
    
    // Modify onCreate to start memory monitoring
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RewordiumAIKeyboardService created")
        
        // Initialize preferences
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load settings
        loadSettings()
        
        // Start memory monitoring
        startMemoryMonitoring()
    }
    
    // Enhance onDestroy to clean up resources
    override fun onDestroy() {
        Log.d(TAG, "RewordiumAIKeyboardService destroyed")
        
        // Cancel any pending operations
        mainHandler.removeCallbacksAndMessages(null)
        memoryMonitorRunnable = null
        
        // Clear caches
        viewCache.clear()
        emojiViewsCache.clear()
        
        // Force garbage collection
        System.gc()
        
        Log.d(TAG, "Low memory - cleared caches")
    }
    
    // Enhance onFinishInputView
    override fun onFinishInputView(finishingInput: Boolean) {
        try {
            // Clear any pending operations specific to the input view
            mainHandler.removeCallbacksAndMessages("keyboard_ops")
            
            // Release any resources that aren't needed when keyboard is hidden
            // but don't clear everything since the keyboard might be shown again soon
            
            super.onFinishInputView(finishingInput)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onFinishInputView: ${e.message}")
            e.printStackTrace()
            super.onFinishInputView(finishingInput)
        }
    }
    
    override fun onCreateInputView(): View {
        Log.d(TAG, "Creating input view")
        inputView = LayoutInflater.from(this).inflate(R.layout.ios_keyboard_layout, null)
        keyboardContainer = inputView?.findViewById(R.id.keyboard_container) ?: throw IllegalStateException("Keyboard container not found")
        
        // Initialize keyboard rows
        setupKeyboardButtons()
        
        // Apply theme colors
        applyThemeColors()
        
        // Setup keyboard layout
        setupKeyboard()
        
        return inputView!!
    }
    
    private fun applyThemeColors() {
        // Apply background color to keyboard container
        keyboardContainer.setBackgroundColor(keyboardBackgroundColor)
        
        // Theme color will be applied to individual keys when they're created
    }
    
    /**
     * Handles text input with support for double-space for period functionality
     */
    private fun handleText(text: String, enableHaptic: Boolean = true) {
        val ic = currentInputConnection ?: return
        
        // Handle double-space for period if enabled
        if (isDoubleSpacePeriodEnabled && text == " ") {
            val currentTime = System.currentTimeMillis()
            
            // Check if this is a double-space (within 500ms of previous space)
            if (currentTime - lastSpaceTime < 500 && lastKeyWasSpace) {
                // Delete the previous space
                ic.deleteSurroundingText(1, 0)
                // Add period + space
                ic.commitText(". ", 1)
                
                // Enable caps for next character
                if (isAutoCapitalizeEnabled) {
                    isCapsOn = true
                    setupKeyboard()
                }
                
                lastKeyWasSpace = false
                lastSpaceTime = 0 // Reset the timer
                return
            }
            
            lastKeyWasSpace = true
            lastSpaceTime = currentTime
        } else {
            // Reset the space timer for any non-space character
            lastKeyWasSpace = false
            lastSpaceTime = 0
            
            // Auto-capitalize after sentence endings if enabled
            if (isAutoCapitalizeEnabled && text in listOf(".", "!", "?")) {
                isCapsOn = true
                setupKeyboard()
            }
        }
        
        // Commit the text
        ic.commitText(text, 1)
        
        // Provide haptic feedback if enabled
        if (isHapticFeedbackEnabled && enableHaptic) {
            performHapticFeedback()
        }
    }

    //latency issues
    
    /**
     * Performs haptic feedback for key presses
     */
    private fun performHapticFeedback() {
        try {
            // Try to use view haptic feedback first
            inputView?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (e: Exception) {
            // Fall back to vibrator service
            try {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing haptic feedback: ${e.message}")
            }
        }
    }
    
    private fun setupKeyboardButtons() {
        // Set up button click listeners here
        inputView?.findViewById<View>(R.id.ai_button)?.setOnClickListener {
            // Handle AI button click
            Log.d(TAG, "AI button clicked")
        }
        
        // Initialize keyboard rows
        numberRow = inputView?.findViewById(R.id.number_row) ?: return
        rowQwerty = inputView?.findViewById(R.id.row_qwerty) ?: return
        rowAsdf = inputView?.findViewById(R.id.row_asdfghjkl) ?: return
        rowZxcv = inputView?.findViewById(R.id.row_zxcvbnm) ?: return
        bottomRow = inputView?.findViewById(R.id.bottom_row) ?: return
    }
    
    /**
     * Sets up the keyboard layout based on current state
     */
    // Add debounce for keyboard setup
    private var lastKeyboardSetupTime = 0L
    private val KEYBOARD_SETUP_DEBOUNCE = 100L
    
    private fun setupKeyboard() {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastKeyboardSetupTime < KEYBOARD_SETUP_DEBOUNCE) {
            // Debounce rapid setup calls
            mainHandler.removeCallbacksAndMessages("keyboard_setup")
            mainHandler.postDelayed({
                performKeyboardSetup()
            }, KEYBOARD_SETUP_DEBOUNCE, "keyboard_setup")
        } else {
            performKeyboardSetup()
        }
        lastKeyboardSetupTime = currentTime
    }
    
    private fun performKeyboardSetup() {
        try {
            // Clear existing views
            numberRow.removeAllViews()
            rowQwerty.removeAllViews()
            rowAsdf.removeAllViews()
            rowZxcv.removeAllViews()
            bottomRow.removeAllViews()
            
            if (isSymbolsShown) {
                setupSymbolsKeyboard()
            } else {
                setupLettersKeyboard()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in keyboard setup", e)
            // Fallback to a simple setup if possible
        }
    }
    
    private fun setupLettersKeyboard() {
        // Add number row
        for (char in "1234567890") {
            addKey(numberRow, char.toString()) { handleText(char.toString()) }
        }
        
        // Add QWERTY row
        val qwertyRow = if (isCapsOn) "QWERTYUIOP" else "qwertyuiop"
        for (char in qwertyRow) {
            addKey(rowQwerty, char.toString()) { handleText(char.toString()) }
        }
        
        // Add ASDF row with padding on both sides for centered appearance
        // Add left padding
        addKey(rowAsdf, "", 0, 0.25f, false) {}
        
        val asdfRow = if (isCapsOn) "ASDFGHJKL" else "asdfghjkl"
        for (char in asdfRow) {
            addKey(rowAsdf, char.toString()) { handleText(char.toString()) }
        }
        
        // Add right padding
        addKey(rowAsdf, "", 0, 0.25f, false) {}
        
        // Add ZXCV row
        // Add shift key
        val shiftIconRes = when {
            isCapsLock -> R.drawable.ic_shift_filled
            isCapsOn -> R.drawable.ic_shift_filled
            else -> R.drawable.ic_shift
        }
        val shiftKey = addSpecialKey(rowZxcv, "", shiftIconRes, 2.0f) {
            toggleCapsLock()
        }
        
        // Apply special styling to shift key based on state
        val shiftBackground = ContextCompat.getDrawable(this, R.drawable.ios_key_shift_selector)?.mutate()
        if (shiftBackground != null) {
            shiftKey.background = shiftBackground
        }
        
        // Set state for proper background color
        if (shiftKey is Button) {
            // Use tag to track state since isActivated/isChecked might not be available
            shiftKey.tag = if (isCapsLock) 2 else if (isCapsOn) 1 else 0
            
            // Apply different background tint based on state
            val stateColor = when {
                isCapsLock -> Color.parseColor(themeColor) // Blue for caps lock
                isCapsOn -> Color.GRAY // Gray for shifted
                else -> Color.DKGRAY // Dark gray for normal
            }
            
            val drawable = shiftKey.background.mutate()
            DrawableCompat.setTint(drawable, stateColor)
            
            // Increase text size for better visibility
            shiftKey.textSize = 30f
            shiftKey.typeface = android.graphics.Typeface.DEFAULT_BOLD
            shiftKey.setPadding(0, 10, 0, 0) // Better vertical centering
        }
        
        val zxcvRow = if (isCapsOn) "ZXCVBNM" else "zxcvbnm"
        for (char in zxcvRow) {
            addKey(rowZxcv, char.toString()) { handleText(char.toString()) }
        }
        
        // Add return key
        addSpecialKey(rowZxcv, "return", null, 1.5f, true) {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
        
        // Add bottom row
        // Numbers key
        addSpecialKey(bottomRow, "123", null, 1.5f, true) {
            isSymbolsShown = true
            setupKeyboard()
        }
        
        // Globe key (language)
        addSpecialKey(bottomRow, "", R.drawable.ic_globe, 1f) {
            // Would show language options
            Log.d(TAG, "Globe key pressed")
        }
        
        // Space bar - wider than other keys with iOS styling
        val spaceKey = addKey(bottomRow, "space", 0, 5f, false, true) {
            handleText(" ")
        }
        
        // Apply iOS-style appearance to spacebar
        if (spaceKey is Button) {
            val spacebarBackground = ContextCompat.getDrawable(this, R.drawable.ios_key_space_selector)?.mutate()
            if (spacebarBackground != null) {
                spaceKey.background = spacebarBackground
            }
            spaceKey.textSize = 16f
            spaceKey.setTextColor(keyTextColor)
        }
        
        // Emoji key
        addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) {
            // Would show emoji keyboard
            Log.d(TAG, "Emoji key pressed")
        }
        
        // Return key (could be customized based on input type)
        addSpecialKey(bottomRow, "return", null, 1.5f, true) {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }
    
    private fun setupSymbolsKeyboard() {
        // Add number row
        for (char in "1234567890") {
            addKey(numberRow, char.toString()) { handleText(char.toString()) }
        }
        
        // Add symbols rows
        for (char in "-/:;()$&@\"") {
            addKey(rowQwerty, char.toString()) { handleText(char.toString()) }
        }
        
        // Add middle row with offset
        addKey(rowAsdf, "", 0, 0.5f, false) {}
        
        for (char in ".,?!\\\"") {
            addKey(rowAsdf, char.toString()) { handleText(char.toString()) }
        }
        
        // Add backspace key
        addSpecialKey(rowAsdf, "", R.drawable.ic_backspace, 1.5f) {
            handleBackspace(currentInputConnection ?: return@addSpecialKey)
        }
        
        // Add bottom symbols row
        addSpecialKey(rowZxcv, "#+=", null, 1.5f, true) {
            // Would show more symbols
            Log.d(TAG, "More symbols key pressed")
        }
        
        for (char in "[]{}<>") {
            addKey(rowZxcv, char.toString()) { handleText(char.toString()) }
        }
        
        // Add return key
        addSpecialKey(rowZxcv, "return", null, 1.5f, true) {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
        
        // Add bottom row
        // ABC key
        addSpecialKey(bottomRow, "ABC", null, 1.5f, true) {
            isSymbolsShown = false
            setupKeyboard()
        }
        
        // Globe key (language)
        addSpecialKey(bottomRow, "", R.drawable.ic_globe, 1f) {
            // Would show language options
            Log.d(TAG, "Globe key pressed")
        }
        
        // Space bar
        addKey(bottomRow, "space", 0, 5f, false, true) {
            handleText(" ", true)
        }
        
        // Emoji key
        addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) {
            // Would show emoji keyboard
            Log.d(TAG, "Emoji key pressed")
        }
        
        // Return key
        addSpecialKey(bottomRow, "return", null, 1.5f, true) {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }
    
    // Modify addKey to use view recycling
    private fun addKey(
        parent: ViewGroup,
        text: String,
        width: Int = 0,
        weight: Float = 1f,
        isSpecial: Boolean = false,
        isSpacebar: Boolean = false,
        onClick: () -> Unit
    ): Button {
        // Try to reuse a view from cache
        val cacheKey = "key_${text}_${width}_${weight}_${isSpecial}_${isSpacebar}"
        val cachedView = viewCache[cacheKey] as? Button
        
        val keyView = if (cachedView != null) {
            // Reuse cached view
            cachedView
        } else {
            // Create new view
            val newView = LayoutInflater.from(this).inflate(
                R.layout.ios_key_letter,
                parent,
                false
            ) as Button
            
            // Cache for future reuse
            viewCache[cacheKey] = newView
            newView
        }
        
        // Reset view state and configure
        keyView.text = if (text == "space") " " else text
        keyView.setBackgroundColor(keyBackgroundColor)
        keyView.setTextColor(keyTextColor)
        
        // Apply rounded corners via background drawable
        val backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.ios_key_background)?.mutate()
        if (backgroundDrawable != null) {
            DrawableCompat.setTint(backgroundDrawable, keyBackgroundColor)
            keyView.background = backgroundDrawable
        }
        
        // Configure layout params
        val params = if (width == 0) {
            LinearLayout.LayoutParams(
                0, 
                resources.getDimensionPixelSize(R.dimen.ios_key_height),
                weight
            )
        } else {
            LinearLayout.LayoutParams(
                width, 
                resources.getDimensionPixelSize(R.dimen.ios_key_height),
                weight
            )
        }.apply {
            val margin = resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        
        keyView.layoutParams = params
        keyView.setOnClickListener { 
            if (isHapticFeedbackEnabled) {
                performHapticFeedback()
            }
            onClick() 
        }
        
        parent.addView(keyView)
        return keyView
    }
    
    private fun addSpecialKey(
        parent: ViewGroup,
        text: String,
        iconResId: Int? = null,
        weight: Float = 1f,
        isTextKey: Boolean = false,
        onClick: () -> Unit
    ): View {
        val view = if (isTextKey) {
            // Text-based special key
            val keyView = LayoutInflater.from(this).inflate(
                R.layout.ios_key_special,
                parent,
                false
            ) as Button
            
            keyView.text = text
            keyView
        } else {
            // Icon-based special key
            val keyView = LayoutInflater.from(this).inflate(
                R.layout.ios_key_icon,
                parent,
                false
            ) as ImageButton
            
            if (iconResId != null) {
                keyView.setImageResource(iconResId)
                
                // Apply theme color to icon
                val drawable = keyView.drawable.mutate()
                drawable.colorFilter = PorterDuffColorFilter(
                    Color.parseColor(themeColor),
                    PorterDuff.Mode.SRC_IN
                )
            }
            keyView
        }
        
        // Apply special key background
        val backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.ios_key_special_background)?.mutate()
        if (backgroundDrawable != null) {
            DrawableCompat.setTint(backgroundDrawable, specialKeyBackgroundColor)
            view.background = backgroundDrawable
        }
        
        val params = LinearLayout.LayoutParams(
            0, 
            resources.getDimensionPixelSize(R.dimen.ios_key_height),
            weight
        ).apply {
            val margin = resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        
        view.layoutParams = params
        view.setOnClickListener { 
            if (isHapticFeedbackEnabled) {
                performHapticFeedback()
            }
            onClick() 
        }
        
        parent.addView(view)
        return view
    }
    
    private fun toggleCapsLock() {
        if (isCapsLock) {
            // If already in caps lock, turn everything off
            isCapsLock = false
            isCapsOn = false
        } else if (isCapsOn) {
            // If shift is on but not caps lock, enable caps lock
            isCapsLock = true
        } else {
            // If no shift, turn on shift
            isCapsOn = true
        }
        setupKeyboard()
    }
    
    private fun handleBackspace(ic: InputConnection) {
        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            // No selection, so delete previous character
            val beforeCursor = ic.getTextBeforeCursor(1, 0)
            if (!beforeCursor.isNullOrEmpty()) {
                ic.deleteSurroundingText(1, 0)
            }
        } else {
            // Delete the selection
            ic.commitText("", 1)
        }
        
        // Turn off caps if needed
        if (isAutoCapitalizeEnabled) {
            // Check if we're at the beginning of a sentence
            val beforeCursor = ic.getTextBeforeCursor(2, 0)
            if (beforeCursor != null && (beforeCursor.isEmpty() || 
                (beforeCursor.length >= 2 && beforeCursor[beforeCursor.length - 2] != '.' && 
                 beforeCursor[beforeCursor.length - 2] != '?' && 
                 beforeCursor[beforeCursor.length - 2] != '!'))) {
                isCapsOn = false
                setupKeyboard()
            }
        }
    }
    
    // Add at class level
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Use this scope for any coroutines
    // Example for an AI operation:
    private fun handleAIButton() {
        serviceScope.launch {
            try {
                // Show loading state
                showToast("AI processing...")
                
                // Perform AI operation on background thread
                withContext(Dispatchers.IO) {
                    // Your AI processing code
                }
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Update UI with results
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in AI processing", e)
                showToast("AI processing failed")
            }
        }
    }
    
    // Cancel all coroutines in onDestroy
    override fun onDestroy() {
        // Cancel all coroutines
        serviceScope.cancel()
        
        // Rest of onDestroy implementation
        // ...
        
        super.onDestroy()
    }
    
    /**
     * Public methods for settings updates
     */
    
    /**
     * Updates the keyboard theme color
     */
    fun updateThemeColor(colorHex: String) {
        themeColor = colorHex
        preferences.edit().putString(KEY_THEME_COLOR, colorHex).apply()
        updateColors()
        
        // Update UI if keyboard is visible
        if (inputView != null) {
            applyThemeColors()
            setupKeyboard()
        }
    }
    
    /**
     * Toggles dark mode
     */
    fun setDarkMode(enabled: Boolean) {
        isDarkMode = enabled
        preferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        updateColors()
        
        // Update UI if keyboard is visible
        if (inputView != null) {
            applyThemeColors()
            setupKeyboard()
        }
    }
    
    /**
     * Toggles haptic feedback
     */
    fun setHapticFeedback(enabled: Boolean) {
        isHapticFeedbackEnabled = enabled
        preferences.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }
    
    /**
     * Toggles auto-capitalization
     */
    fun setAutoCapitalize(enabled: Boolean) {
        isAutoCapitalizeEnabled = enabled
        preferences.edit().putBoolean(KEY_AUTO_CAPITALIZE, enabled).apply()
    }
    
    /**
     * Toggles double-space for period
     */
    fun setDoubleSpacePeriod(enabled: Boolean) {
        isDoubleSpacePeriodEnabled = enabled
        preferences.edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, enabled).apply()
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "onStartInput: ${attribute?.packageName}")
        
        // Reset caps lock based on input type
        if (isAutoCapitalizeEnabled) {
            val inputType = attribute?.inputType ?: 0
            val isCapEnabled = (inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0 ||
                               (inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0 ||
                               (inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0
            
            isCapsOn = isCapEnabled
            
            // Update keyboard if it's already shown
            if (inputView != null) {
                setupKeyboard()
            }
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput")
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentInputConnection = currentInputConnection ?: return false
        
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                handleBackspace(currentInputConnection)
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                currentInputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)
                return true
            }
            KeyEvent.KEYCODE_SPACE -> {
                handleText(" ", true)
                return true
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "RewordiumAIKeyboardService destroyed")
        inputView = null
        super.onDestroy()
    }
    
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

// Add these properties at the class level
private val viewCache = HashMap<String, View>() // Cache for recycling views
private val emojiViewsCache = HashMap<String, View>() // Cache for emoji views
private var memoryMonitorRunnable: Runnable? = null
private val mainHandler = Handler(Looper.getMainLooper())
private var lastMemoryCheck = 0L
private val MEMORY_CHECK_INTERVAL = 60000L // 1 minute

// Add this method to start memory monitoring
private fun startMemoryMonitoring() {
    memoryMonitorRunnable = object : Runnable {
        override fun run() {
            try {
                // Check if we need to clear caches
                val currentTime = SystemClock.elapsedRealtime()
                if (currentTime - lastMemoryCheck > MEMORY_CHECK_INTERVAL) {
                    lastMemoryCheck = currentTime
                    // Clear view cache if it's too large
                    if (viewCache.size > 50) {
                        viewCache.clear()
                    }
                    // Force garbage collection if memory pressure is high
                    val runtime = Runtime.getRuntime()
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                    val maxMemory = runtime.maxMemory()
                    if (usedMemory > maxMemory * 0.75) {
                        // Memory pressure is high, clear caches
                        viewCache.clear()
                        emojiViewsCache.clear()
                        System.gc()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in memory monitoring", e)
            } finally {
                // Schedule next run
                mainHandler.postDelayed(this, 30000) // Check every 30 seconds
            }
        }
    }
    mainHandler.post(memoryMonitorRunnable!!)
}

// Add efficient emoji handling with view recycling
private fun setupEmojiKeyboard() {
    // Use a RecyclerView for emoji grid if possible
    // Or implement view recycling manually:
    
    val emojiContainer = inputView?.findViewById<ViewGroup>(R.id.emoji_container) ?: return
    emojiContainer.removeAllViews()
    
    // Group emojis into pages to avoid loading all at once
    val currentEmojiPage = 0 // Track current page
    
    // Load only visible emojis
    loadEmojiPage(currentEmojiPage, emojiContainer)
}

private fun loadEmojiPage(page: Int, container: ViewGroup) {
    // Calculate emoji range for this page
    val pageSize = 20
    val startIndex = page * pageSize
    val endIndex = min((page + 1) * pageSize, emojiList.size)
    
    for (i in startIndex until endIndex) {
        val emoji = emojiList[i]
        val cacheKey = "emoji_${emoji}"
        
        // Try to reuse from cache
        val emojiView = if (emojiViewsCache.containsKey(cacheKey)) {
            emojiViewsCache[cacheKey]
        } else {
            // Create new emoji view
            val newView = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                // Configure view...
            }
            emojiViewsCache[cacheKey] = newView
            newView
        }
        
        container.addView(emojiView as View)
    }
}
}
// Add a method to clean up resources
private fun cleanupResources() {
    // Remove all callbacks
    mainHandler.removeCallbacksAndMessages(null)
    
    // Clear all view references that might cause leaks
    numberRow.removeAllViews()
    rowQwerty.removeAllViews()
    rowAsdf.removeAllViews()
    rowZxcv.removeAllViews()
    bottomRow.removeAllViews()
    
    // Clear caches
    viewCache.clear()
    emojiViewsCache.clear()
}

// Call in appropriate lifecycle methods
override fun onDestroy() {
    cleanupResources()
    super.onDestroy()
}

override fun onFinishInputView(finishingInput: Boolean) {
    if (finishingInput) {
        // Only clean up if we're actually finishing input
        cleanupResources()
    }
    super.onFinishInputView(finishingInput)
}
}

// Add these properties at the class level
private val viewCache = HashMap<String, View>() // Cache for recycling views
private val emojiViewsCache = HashMap<String, View>() // Cache for emoji views
private var memoryMonitorRunnable: Runnable? = null
private val mainHandler = Handler(Looper.getMainLooper())
private var lastMemoryCheck = 0L
private val MEMORY_CHECK_INTERVAL = 60000L // 1 minute

// Add this method to start memory monitoring
private fun startMemoryMonitoring() {
    memoryMonitorRunnable = object : Runnable {
        override fun run() {
            try {
                // Check if we need to clear caches
                val currentTime = SystemClock.elapsedRealtime()
                if (currentTime - lastMemoryCheck > MEMORY_CHECK_INTERVAL) {
                    lastMemoryCheck = currentTime
                    // Clear view cache if it's too large
                    if (viewCache.size > 50) {
                        viewCache.clear()
                    }
                    // Force garbage collection if memory pressure is high
                    val runtime = Runtime.getRuntime()
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                    val maxMemory = runtime.maxMemory()
                    if (usedMemory > maxMemory * 0.75) {
                        // Memory pressure is high, clear caches
                        viewCache.clear()
                        emojiViewsCache.clear()
                        System.gc()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in memory monitoring", e)
            } finally {
                // Schedule next run
                mainHandler.postDelayed(this, 30000) // Check every 30 seconds
            }
        }
    }
    mainHandler.post(memoryMonitorRunnable!!)
}

// Add efficient emoji handling with view recycling
private fun setupEmojiKeyboard() {
    // Use a RecyclerView for emoji grid if possible
    // Or implement view recycling manually:
    
    val emojiContainer = inputView?.findViewById<ViewGroup>(R.id.emoji_container) ?: return
    emojiContainer.removeAllViews()
    
    // Group emojis into pages to avoid loading all at once
    val currentEmojiPage = 0 // Track current page
    
    // Load only visible emojis
    loadEmojiPage(currentEmojiPage, emojiContainer)
}

private fun loadEmojiPage(page: Int, container: ViewGroup) {
    // Calculate emoji range for this page
    val pageSize = 20
    val startIndex = page * pageSize
    val endIndex = min((page + 1) * pageSize, emojiList.size)
    
    for (i in startIndex until endIndex) {
        val emoji = emojiList[i]
        val cacheKey = "emoji_${emoji}"
        
        // Try to reuse from cache
        val emojiView = if (emojiViewsCache.containsKey(cacheKey)) {
            emojiViewsCache[cacheKey]
        } else {
            // Create new emoji view
            val newView = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                // Configure view...
            }
            emojiViewsCache[cacheKey] = newView
            newView
        }
        
        container.addView(emojiView as View)
    }
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}


