package com.noxquill.rewordium.keyboard

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import com.noxquill.rewordium.keyboard.animation.SiriWaveDrawable
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ColorDrawable
import android.text.TextWatcher
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noxquill.rewordium.R
import com.noxquill.rewordium.keyboard.data.EmojiData
import com.noxquill.rewordium.keyboard.util.KeyboardConstants
import com.noxquill.rewordium.keyboard.gesture.SwipeGestureEngine
import com.noxquill.rewordium.keyboard.ui.GboardToolbar

class KeyboardLayoutManager(private val service: RewordiumAIKeyboardService) {
    
    companion object {
        private val BUTTER_SMOOTH_INTERPOLATOR = android.view.animation.DecelerateInterpolator()
    }

    private var rootView: View? = null
    private lateinit var keyboardSwitcher: ViewAnimator
    private val MAIN_KEYBOARD_INDEX = 0
    private val EMOJI_KEYBOARD_INDEX = 1

    private lateinit var keyboardRootContainer: SwipeInterceptorLayout
    private lateinit var mainKeyboardContainer: LinearLayout
    private lateinit var emojiKeyboardContainer: LinearLayout
    private lateinit var numberRow: LinearLayout
    private lateinit var rowQwerty: LinearLayout
    private lateinit var rowAsdf: LinearLayout
    private lateinit var rowZxcv: LinearLayout
    private lateinit var bottomRow: LinearLayout
    private lateinit var suggestionsContainer: LinearLayout
    private lateinit var regularSuggestionsContainer: LinearLayout
    private lateinit var featuresContainer: LinearLayout
    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView
    private lateinit var separator1: View
    private lateinit var separator2: View
    private lateinit var settingsIcon: ImageView
    private lateinit var aiButton: Button
    private lateinit var gifButton: ImageView
    private lateinit var stickersButton: ImageView
    private lateinit var clipboardButton: ImageView
    private lateinit var themeButton: ImageView
    private lateinit var keyboardSettingsButton: ImageView
    
    // FlorisBoard-inspired Gboard toolbar
    private var gboardToolbar: GboardToolbar? = null
    
    // Key bounds tracking for swipe gestures
    internal val keyBounds = mutableMapOf<String, KeyBounds>()
    
    // Key bounds data class
    data class KeyBounds(
        val centerX: Float,
        val centerY: Float,
        val radius: Float
    )
    
    // Method to get current key bounds
    fun getKeyBounds(): Map<String, KeyBounds> = keyBounds.toMap()

    private var emojiRecyclerView: RecyclerView? = null
    private var emojiCategoryTabsContainer: HorizontalScrollView? = null
    private var emojiBottomControlRow: LinearLayout? = null
    private var emojiAdapter: EmojiAdapter? = null

    private val letterKeyViews = mutableListOf<Button>()
    private var keyPopup: PopupWindow? = null
    private var popupTextView: TextView? = null
    private var shiftKeyView: ImageButton? = null

    private var keyTextColor: Int = 0
    private var currentEmojiCategories: List<Pair<String, List<String>>> = emptyList()
    private var mainKeyboardHeight: Int = 0

    // Caches for drawables to prevent recreating them constantly
    private var keyBackgroundDrawable: Drawable? = null
    private var specialKeyBackgroundDrawable: Drawable? = null
    private var returnKeyBackgroundDrawable: Drawable? = null
    private var spacebarBackgroundDrawable: Drawable? = null
    private var activeShiftDrawable: Drawable? = null
    
    // AI Button animation tracking
    private var currentSiriWaveDrawable: SiriWaveDrawable? = null

    fun getRootView(): View? = rootView
    fun getKeyboardSwitcher(): ViewAnimator = keyboardSwitcher
    fun getEmojiCategoryTabsContainer(): HorizontalScrollView? = emojiCategoryTabsContainer
    
    /**
     * Setup gesture engine for swipe typing on spacebar and other keys
     */
    fun setupGestureEngine(gestureEngine: SwipeGestureEngine) {
        try {
            Log.d(KeyboardConstants.TAG, "🎯 Setting up gesture engine in KeyboardLayoutManager")
            
            // Set the gesture engine in the SwipeInterceptorLayout
            keyboardRootContainer.setGestureEngine(gestureEngine, this)
            
            Log.d(KeyboardConstants.TAG, "✅ Gesture engine setup complete - spacebar navigation enabled")
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error setting up gesture engine: ${e.message}")
        }
    }

    fun initialize(root: View) {
        this.rootView = root
        keyboardRootContainer = root.findViewById(R.id.keyboard_root_container)
        keyboardSwitcher = root.findViewById(R.id.keyboard_switcher)
        mainKeyboardContainer = root.findViewById(R.id.keyboard_container)
        emojiKeyboardContainer = root.findViewById(R.id.emoji_keyboard_container)
        numberRow = root.findViewById(R.id.number_row)
        rowQwerty = root.findViewById(R.id.row_qwerty)
        rowAsdf = root.findViewById(R.id.row_asdfghjkl)
        rowZxcv = root.findViewById(R.id.row_zxcvbnm)
        bottomRow = root.findViewById(R.id.bottom_row)
        aiButton = root.findViewById(R.id.ai_button)

        suggestionsContainer = root.findViewById(R.id.suggestions_container)
        regularSuggestionsContainer = root.findViewById(R.id.regular_suggestions_container)
        featuresContainer = root.findViewById(R.id.features_container)
        suggestion1 = root.findViewById(R.id.suggestion_1)
        suggestion2 = root.findViewById(R.id.suggestion_2)
        suggestion3 = root.findViewById(R.id.suggestion_3)
        separator1 = root.findViewById(R.id.separator_1)
        separator2 = root.findViewById(R.id.separator_2)
        settingsIcon = root.findViewById(R.id.settings_icon)
        gifButton = root.findViewById(R.id.gif_button)
        stickersButton = root.findViewById(R.id.stickers_button)
        clipboardButton = root.findViewById(R.id.clipboard_button)
        themeButton = root.findViewById(R.id.theme_button)
        keyboardSettingsButton = root.findViewById(R.id.keyboard_settings_button)

        ViewCompat.setOnApplyWindowInsetsListener(keyboardRootContainer) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val keyboardLift = view.context.resources.getDimensionPixelSize(R.dimen.keyboard_bottom_margin)
            val totalBottomPadding = navBarHeight + keyboardLift
            view.updatePadding(bottom = totalBottomPadding)
            insets
        }
        mainKeyboardContainer.doOnLayout { view ->
            if (mainKeyboardHeight == 0 && view.height > 0) {
                mainKeyboardHeight = view.height
            }
        }
        setupSuggestionClicks()
        setupAIButton()
        setupSettingsIcon()
        createKeyPopup()
        
        // Initialize FlorisBoard-inspired Gboard toolbar
        initializeGboardToolbar()
        
        // Show special features by default when keyboard first loads
        updateSuggestions(emptyList())
    }
    
    private fun setupSettingsIcon() {
        // Setup Mic button
        settingsIcon.setImageResource(R.drawable.ic_mic)
        settingsIcon.setOnClickListener {
            service.performHapticFeedback()
            service.handleSettingsButton()
        }
        
        // Setup GIF button
        gifButton.setOnClickListener {
            service.performHapticFeedback()
            service.handleGifButton()
        }
        
        // Setup Stickers button
        stickersButton.setOnClickListener {
            service.performHapticFeedback()
            service.handleStickersButton()
        }
        
        // Setup Clipboard button
        clipboardButton.setOnClickListener {
            service.performHapticFeedback()
            service.handleClipboardButton()
        }
        
        // Setup Theme button
        themeButton.setOnClickListener {
            service.performHapticFeedback()
            service.handleThemeButton()
        }
        
        // Setup Keyboard Settings button
        keyboardSettingsButton.setOnClickListener {
            service.performHapticFeedback()
            service.handleKeyboardSettingsButton()
        }
    }
    
    /**
     * Initialize FlorisBoard-inspired Gboard toolbar
     */
    private fun initializeGboardToolbar() {
        try {
            Log.d(KeyboardConstants.TAG, "🎨 Initializing FlorisBoard-inspired Gboard toolbar")
            
            // Create toolbar instance
            gboardToolbar = GboardToolbar(service, service)
            
            // Toolbar is already created and will be used for suggestions
            Log.d(KeyboardConstants.TAG, "✅ Gboard toolbar initialized successfully")
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error initializing Gboard toolbar: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun updateLayout() {
        if (service.paraphraseManager?.isParaphrasingMode == true) {
            return
        }
        if (service.isEmojiKeyboardShown) {
            if (keyboardSwitcher.displayedChild != EMOJI_KEYBOARD_INDEX) {
                keyboardSwitcher.displayedChild = EMOJI_KEYBOARD_INDEX
            }
            setupEmojiKeyboard()
        } else {
            if (keyboardSwitcher.displayedChild != MAIN_KEYBOARD_INDEX) {
                keyboardSwitcher.displayedChild = MAIN_KEYBOARD_INDEX
            }
            if (service.isSymbolsShown) {
                setupSymbolsKeyboard()
            } else {
                setupLettersKeyboard()
            }
        }
    }

    fun updateSuggestions(suggestions: List<String>) {
        service.currentSuggestions = suggestions.take(3)
        val suggestionViews = listOf(suggestion1, suggestion2, suggestion3)

        // Hide suggestion bar in landscape, show in portrait
        val orientation = service.resources.configuration.orientation
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            suggestionsContainer.visibility = View.GONE
        } else {
            suggestionsContainer.visibility = View.VISIBLE
        }

        // Check if text is empty or we have no suggestions
        val currentText = service.getCurrentInputConnection()?.getTextBeforeCursor(1, 0)?.toString() ?: ""
        val isTextEmpty = currentText.isEmpty()

        // Check if we should show features or suggestions
        if (suggestions.isEmpty() || isTextEmpty) {
            // Show special features (GIF, Stickers, Clipboard, Theme) when no suggestions or text is empty
            regularSuggestionsContainer.visibility = View.GONE
            featuresContainer.visibility = View.VISIBLE
        } else {
            // Show regular suggestions when available
            regularSuggestionsContainer.visibility = View.VISIBLE
            featuresContainer.visibility = View.GONE

            // Update suggestion text views
            for (i in 0..2) {
                val suggestion = service.currentSuggestions.getOrNull(i)
                val view = suggestionViews[i]
                if (suggestion != null) {
                    view.text = suggestion
                    view.visibility = View.VISIBLE
                } else {
                    view.text = ""
                    view.visibility = View.INVISIBLE
                }
            }

            // Update separators visibility
            separator1.visibility = if (suggestion2.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            separator2.visibility = if (suggestion3.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        }
        
        // Update Gboard toolbar if initialized
        try {
            gboardToolbar?.updateSuggestions(service.currentSuggestions)
        } catch (e: Exception) {
            Log.w(KeyboardConstants.TAG, "Error updating toolbar suggestions: ${e.message}")
        }
    }

    fun setupEmojiKeyboard() {
        try {
            Log.d("EmojiKeyboard", "Setting up emoji keyboard...")
            
            if (mainKeyboardHeight > 0) {
                val params = emojiKeyboardContainer.layoutParams
                if (params.height != mainKeyboardHeight) {
                    params.height = mainKeyboardHeight
                    emojiKeyboardContainer.layoutParams = params
                }
            }
            
            // EMOJI CRASH FIX: Clean up existing views safely
            try {
                emojiKeyboardContainer.removeAllViews()
            } catch (e: Exception) {
                Log.e("EmojiKeyboard", "Error removing views: ${e.message}")
            }
            
            // EMOJI CRASH FIX: Clear references to prevent memory leaks
            emojiRecyclerView = null
            emojiCategoryTabsContainer = null
            emojiBottomControlRow = null
            emojiAdapter = null
            
            createEmojiTabs()
            createEmojiRecyclerView()
            createEmojiBottomControlRow()
            
            // EMOJI CRASH FIX: Add views with null checks
            emojiCategoryTabsContainer?.let { 
                emojiKeyboardContainer.addView(it) 
                Log.d("EmojiKeyboard", "Added emoji tabs")
            }
            emojiRecyclerView?.let { 
                emojiKeyboardContainer.addView(it) 
                Log.d("EmojiKeyboard", "Added emoji recycler view")
            }
            emojiBottomControlRow?.let { 
                emojiKeyboardContainer.addView(it) 
                Log.d("EmojiKeyboard", "Added emoji control row")
            }
            
            updateEmojiCategoryTabs()
            
            // EMOJI EMPTY LAYOUT FIX: Ensure emoji list is populated after tabs are set up
            // Use post to ensure the tabs container and categories are fully initialized
            rootView?.post {
                try {
                    Log.d("EmojiKeyboard", "Updating emoji list after tab initialization...")
                    updateEmojiList()
                    Log.d("EmojiKeyboard", "Emoji list updated successfully with ${currentEmojiCategories.size} categories")
                } catch (e: Exception) {
                    Log.e("EmojiKeyboard", "Error in delayed emoji list update: ${e.message}")
                    // Emergency fallback: try to force first category
                    try {
                        service.currentEmojiCategoryIndex = 0
                        updateEmojiList()
                    } catch (fallbackError: Exception) {
                        Log.e("EmojiKeyboard", "Emergency fallback failed: ${fallbackError.message}")
                    }
                }
            }
            
            Log.d("EmojiKeyboard", "Emoji keyboard setup completed successfully")
        } catch (e: Exception) {
            Log.e("EmojiKeyboard", "Failed to setup emoji keyboard: ${e.message}", e)
            // Try to prevent complete crash by clearing state
            emojiRecyclerView = null
            emojiCategoryTabsContainer = null
            emojiBottomControlRow = null
            emojiAdapter = null
        }
    }

    fun applyTheme(isDarkMode: Boolean, themeColorHex: String) {
        Log.d("KeyboardTheme", "🎨 APPLYING THEME: darkMode=$isDarkMode, color=$themeColorHex")
        
        val keyBackgroundColor = Color.parseColor(if (isDarkMode) "#333333" else "#FFFFFF")
        keyTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val specialKeyBackgroundColor = Color.parseColor(if (isDarkMode) "#5A5A5A" else "#D1D1D6")
        val keyboardBackgroundColor = Color.parseColor(if (isDarkMode) "#1C1C1E" else "#D1D1D6")
        
        Log.d("KeyboardTheme", "🎨 Colors calculated: keyBg=$keyBackgroundColor, specialBg=$specialKeyBackgroundColor, kbBg=$keyboardBackgroundColor")
        
        keyboardRootContainer.setBackgroundColor(keyboardBackgroundColor)
        
        // SUGGESTION BAR FIX: Make suggestion bar background match keyboard background
        try {
            suggestionsContainer.setBackgroundColor(keyboardBackgroundColor)
            Log.d("KeyboardTheme", "🎨 Updated suggestion bar background to match keyboard: $keyboardBackgroundColor")
        } catch (e: Exception) {
            Log.e("KeyboardTheme", "Failed to update suggestion bar background: ${e.message}")
        }

        val cornerRadius = dpToPx(6f)
        keyBackgroundDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(keyBackgroundColor) }
        specialKeyBackgroundDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(specialKeyBackgroundColor) }
        
        // Safe theme color parsing - fallback to default if empty or invalid
        val safeThemeColor = if (service.themeColor.isNotEmpty()) {
            try {
                Color.parseColor(service.themeColor)
            } catch (e: Exception) {
                if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
            }
        } else {
            // Default theme color when no solid color is selected (gradient mode)
            if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
        }
        
        returnKeyBackgroundDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(safeThemeColor) }
        spacebarBackgroundDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(keyBackgroundColor) }
        activeShiftDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(Color.WHITE) }

        val suggestionTextColor = if(isDarkMode) Color.WHITE else Color.BLACK
        suggestion1.setTextColor(safeThemeColor)
        suggestion2.setTextColor(suggestionTextColor)
        suggestion3.setTextColor(suggestionTextColor)
        val separatorColor = if(isDarkMode) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")
        separator1.setBackgroundColor(separatorColor)
        separator2.setBackgroundColor(separatorColor)

        // Update AI button to match return key style with theme color
        updateAIButtonTheme(themeColorHex, isDarkMode)

        Log.d("KeyboardTheme", "🎨 Starting immediate theme application to all views...")
        // FORCE IMMEDIATE APPLICATION TO ALL EXISTING VIEWS
        forceImmediateThemeApplicationToAllViews(keyBackgroundColor, specialKeyBackgroundColor, keyboardBackgroundColor, isDarkMode, themeColorHex)

        // Apply theme to Gboard toolbar
        try {
            gboardToolbar?.applyTheme(isDarkMode, themeColorHex)
        } catch (e: Exception) {
            Log.w("KeyboardTheme", "Error applying theme to toolbar: ${e.message}")
        }

        createKeyPopup()
        updateLayout()
        
        Log.d("KeyboardTheme", "✅ THEME APPLICATION COMPLETED")
    }
    
    /**
     * Apply gradient theme to the keyboard
     */
    fun applyGradientTheme(isDarkMode: Boolean, gradientId: String, gradientColors: IntArray) {
        Log.d("KeyboardTheme", "🌈 APPLYING GRADIENT THEME: darkMode=$isDarkMode, gradientId=$gradientId")
        
        val keyBackgroundColor = Color.parseColor(if (isDarkMode) "#333333" else "#FFFFFF")
        keyTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val specialKeyBackgroundColor = Color.parseColor(if (isDarkMode) "#5A5A5A" else "#D1D1D6")
        val keyboardBackgroundColor = Color.parseColor(if (isDarkMode) "#1C1C1E" else "#D1D1D6")
        
        // Set keyboard background with subtle gradient
        val keyboardGradient = GradientDrawable().apply {
            this.colors = intArrayOf(
                Color.argb(30, Color.red(gradientColors[0]), Color.green(gradientColors[0]), Color.blue(gradientColors[0])),
                Color.argb(15, Color.red(gradientColors[1]), Color.green(gradientColors[1]), Color.blue(gradientColors[1]))
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        
        val combinedBackground = LayerDrawable(arrayOf(
            ColorDrawable(keyboardBackgroundColor),
            keyboardGradient
        ))
        
        keyboardRootContainer.background = combinedBackground
        
        // SUGGESTION BAR GRADIENT FIX: Apply same gradient background to suggestion bar
        try {
            val suggestionGradient = GradientDrawable().apply {
                this.colors = intArrayOf(
                    Color.argb(35, Color.red(gradientColors[0]), Color.green(gradientColors[0]), Color.blue(gradientColors[0])),
                    Color.argb(20, Color.red(gradientColors[1]), Color.green(gradientColors[1]), Color.blue(gradientColors[1]))
                )
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.BOTTOM_TOP // Reversed to create seamless flow
            }
            
            val suggestionCombinedBackground = LayerDrawable(arrayOf(
                ColorDrawable(keyboardBackgroundColor),
                suggestionGradient
            ))
            
            suggestionsContainer.background = suggestionCombinedBackground
            Log.d("KeyboardTheme", "🌈 Updated suggestion bar with seamless gradient flow")
        } catch (e: Exception) {
            Log.e("KeyboardTheme", "Failed to update suggestion bar gradient: ${e.message}")
            // Fallback to solid color
            suggestionsContainer.setBackgroundColor(keyboardBackgroundColor)
        }

        val cornerRadius = dpToPx(6f)
        keyBackgroundDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(keyBackgroundColor) }
        specialKeyBackgroundDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(specialKeyBackgroundColor) }
        
        // Create beautiful gradient return key
        returnKeyBackgroundDrawable = GradientDrawable().apply { 
            this.cornerRadius = cornerRadius
            this.colors = gradientColors
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
        }
        
        spacebarBackgroundDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(keyBackgroundColor) }
        activeShiftDrawable = GradientDrawable().apply { this.cornerRadius = cornerRadius; setColor(Color.WHITE) }

        val suggestionTextColor = if(isDarkMode) Color.WHITE else Color.BLACK
        suggestion1.setTextColor(gradientColors[0]) // Use gradient color for first suggestion
        suggestion2.setTextColor(suggestionTextColor)
        suggestion3.setTextColor(suggestionTextColor)
        val separatorColor = if(isDarkMode) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")
        separator1.setBackgroundColor(separatorColor)
        separator2.setBackgroundColor(separatorColor)

        // Update AI button with gradient theme
        updateAIButtonGradientTheme(gradientColors, isDarkMode)

        Log.d("KeyboardTheme", "🌈 Starting immediate gradient theme application...")
        forceImmediateGradientThemeApplication(keyBackgroundColor, specialKeyBackgroundColor, keyboardBackgroundColor, isDarkMode, gradientColors)

        createKeyPopup()
        updateLayout()
        
        Log.d("KeyboardTheme", "✅ GRADIENT THEME APPLICATION COMPLETED")
    }
    
    /**
     * Force immediate theme application to all existing views without recreation
     */
    private fun forceImmediateThemeApplicationToAllViews(
        keyBackgroundColor: Int, 
        specialKeyBackgroundColor: Int, 
        keyboardBackgroundColor: Int, 
        isDarkMode: Boolean, 
        themeColorHex: String
    ) {
        try {
            // Update background immediately
            keyboardRootContainer.setBackgroundColor(keyboardBackgroundColor)
            keyboardRootContainer.invalidate()
            
            // Force update all key views immediately
            updateAllKeyViewsImmediately(keyBackgroundColor, specialKeyBackgroundColor, isDarkMode, themeColorHex)
            
            // Force update suggestion area
            val suggestionTextColor = if(isDarkMode) Color.WHITE else Color.BLACK
            val safeSuggestionColor = if (themeColorHex.isNotEmpty()) {
                try {
                    Color.parseColor(themeColorHex)
                } catch (e: Exception) {
                    if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
                }
            } else {
                if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
            }
            suggestion1.setTextColor(safeSuggestionColor)
            suggestion2.setTextColor(suggestionTextColor)
            suggestion3.setTextColor(suggestionTextColor)
            val separatorColor = if(isDarkMode) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")
            separator1.setBackgroundColor(separatorColor)
            separator2.setBackgroundColor(separatorColor)
            
            // Update AI button theme
            updateAIButtonTheme(themeColorHex, isDarkMode)
            
            // Force invalidation of suggestion area
            suggestionsContainer.invalidate()
            suggestion1.invalidate()
            suggestion2.invalidate()
            suggestion3.invalidate()
            
        } catch (e: Exception) {
            // Silent catch to prevent crashes
        }
    }
    
    /**
     * Update all key views immediately without waiting for layout changes
     */
    private fun updateAllKeyViewsImmediately(
        keyBackgroundColor: Int, 
        specialKeyBackgroundColor: Int, 
        isDarkMode: Boolean, 
        themeColorHex: String
    ) {
        try {
            val cornerRadius = dpToPx(6f)
            val newKeyBackgroundDrawable = GradientDrawable().apply { 
                this.cornerRadius = cornerRadius
                setColor(keyBackgroundColor) 
            }
            val newSpecialKeyBackgroundDrawable = GradientDrawable().apply { 
                this.cornerRadius = cornerRadius
                setColor(specialKeyBackgroundColor) 
            }
            val newReturnKeyBackgroundDrawable = GradientDrawable().apply { 
                this.cornerRadius = cornerRadius
                val safeReturnColor = if (themeColorHex.isNotEmpty()) {
                    try {
                        Color.parseColor(themeColorHex)
                    } catch (e: Exception) {
                        if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
                    }
                } else {
                    if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
                }
                setColor(safeReturnColor)
            }
            
            // Update all existing key views
            updateViewGroupKeysImmediately(mainKeyboardContainer, newKeyBackgroundDrawable, newSpecialKeyBackgroundDrawable, newReturnKeyBackgroundDrawable, isDarkMode)
            updateViewGroupKeysImmediately(emojiKeyboardContainer, newKeyBackgroundDrawable, newSpecialKeyBackgroundDrawable, newReturnKeyBackgroundDrawable, isDarkMode)
            
        } catch (e: Exception) {
            // Silent catch to prevent crashes
        }
    }
    
    /**
     * Recursively update all key views in a ViewGroup
     */
    private fun updateViewGroupKeysImmediately(
        viewGroup: ViewGroup?, 
        keyBg: GradientDrawable, 
        specialKeyBg: GradientDrawable, 
        returnKeyBg: GradientDrawable, 
        isDarkMode: Boolean
    ) {
        viewGroup?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                when {
                    child is Button -> {
                        // Update button immediately
                        child.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
                        
                        // Apply appropriate background based on button type
                        val tag = child.tag?.toString() ?: ""
                        val newBackground = when {
                            tag == "return" || tag == "enter" -> returnKeyBg.constantState?.newDrawable()?.mutate()
                            tag in listOf("shift", "backspace", "symbols", "numbers", "letters") -> specialKeyBg.constantState?.newDrawable()?.mutate()
                            else -> keyBg.constantState?.newDrawable()?.mutate()
                        }
                        
                        // CRITICAL: Force background change and immediate invalidation
                        child.background = newBackground
                        child.invalidate()
                        child.requestLayout()
                        
                        Log.d("KeyboardTheme", "Updated key '${child.text}' with tag '$tag' - immediate refresh forced")
                    }
                    child is ImageButton -> {
                        // Update ImageButton (like shift key) immediately
                        child.setColorFilter(if (isDarkMode) Color.WHITE else Color.BLACK)
                        val tag = child.tag?.toString() ?: ""
                        val newBackground = when {
                            tag in listOf("shift", "backspace", "symbols", "numbers", "letters") -> specialKeyBg.constantState?.newDrawable()?.mutate()
                            else -> keyBg.constantState?.newDrawable()?.mutate()
                        }
                        child.background = newBackground
                        child.invalidate()
                        child.requestLayout()
                        
                        Log.d("KeyboardTheme", "Updated ImageButton with tag '$tag' - immediate refresh forced")
                    }
                    child is ViewGroup -> {
                        // Recursively update child ViewGroups
                        updateViewGroupKeysImmediately(child, keyBg, specialKeyBg, returnKeyBg, isDarkMode)
                        // Force the ViewGroup itself to refresh
                        child.invalidate()
                        child.requestLayout()
                    }
                }
            }
            // Force the main container to refresh completely
            container.invalidate()
            container.requestLayout()
            
            Log.d("KeyboardTheme", "Container '${container.javaClass.simpleName}' updated - forced complete refresh")
        }
    }

    // =========================================================================
    // START OF THE FIX: The OnTouchListener is rewritten for maximum responsiveness.
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun addKey(parent: ViewGroup, text: String, weight: Float = 1f, isLetter: Boolean = false) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_letter, parent, false) as Button
        keyView.tag = text.lowercase()
        keyView.text = text
        keyView.setTextColor(keyTextColor)
        
        // Optimize view settings
        keyView.setWillNotDraw(false)
        keyView.isHapticFeedbackEnabled = true
        
        // Create and cache background drawables
        val normalBg = keyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
        val pressedBg = keyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
        pressedBg?.alpha = 150
        
        keyView.background = normalBg
        
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        
            // SWIPE TYPING FIX: Simple touch handling that allows swipe gestures
        // and handles key clicks properly without double-tap requirement
        keyView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Show visual feedback
                    v.background = pressedBg
                    val currentText = (v as Button).text.toString()
                    showKeyPopup(v, currentText)
                    false // Don't consume - let gesture system handle it
                }
                MotionEvent.ACTION_UP -> {
                    // Handle the key press here directly for single tap
                    val hitX = event.x.toInt()
                    val hitY = event.y.toInt()
                    val isInside = hitX >= 0 && hitY >= 0 && 
                                 hitX < v.width && hitY < v.height
                    
                    if (isInside) {
                        // Process the key press
                        val currentText = (v as Button).text.toString()
                        try {
                            service.queueKeyPress(currentText)
                            Log.v("KeyboardLayoutManager", "Key pressed: $currentText")
                        } catch (e: Exception) {
                            Log.e("KeyboardLayoutManager", "Error processing key: ${e.message}")
                        }
                    }
                    
                    // Reset visual state
                    v.background = normalBg
                    dismissKeyPopup()
                    false // Don't consume - let gesture system handle it too
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Reset visual state on cancel
                    v.background = normalBg
                    dismissKeyPopup()
                    false // Don't consume
                }
                else -> false // Don't consume any other events
            }
        }
        
        // Track key bounds for swipe detection using consistent coordinate system
        keyView.doOnLayout {
            // Use window coordinates for consistency with touch events
            val locationInWindow = IntArray(2)
            keyView.getLocationInWindow(locationInWindow)
            val centerX = locationInWindow[0] + keyView.width / 2f
            val centerY = locationInWindow[1] + keyView.height / 2f
            val radius = minOf(keyView.width, keyView.height) / 2f
            keyBounds[text] = KeyBounds(centerX, centerY, radius)
            Log.d("KeyboardLayoutManager", "Tracked key '$text' bounds: window coords ($centerX, $centerY) radius=$radius")
        }

        parent.addView(keyView)
        if (isLetter) letterKeyViews.add(keyView)
    }
    // =========================================================================
    // END OF THE FIX
    // =========================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun addSpecialKey(parent: ViewGroup, text: String, iconResId: Int? = null, weight: Float = 1f, onClick: () -> Unit) {
        val view: View = if (iconResId == null) {
            (LayoutInflater.from(service).inflate(R.layout.ios_key_special, parent, false) as Button).apply { 
                this.text = text
                this.setTextColor(keyTextColor)
                // Set bold text for specific keys
                if (text == "123" || text == "@" || text == "." || text == "ABC") {
                    this.setTypeface(this.typeface, android.graphics.Typeface.BOLD)
                }
            }
        } else {
            (LayoutInflater.from(service).inflate(R.layout.ios_key_icon, parent, false) as ImageButton).apply { setImageResource(iconResId); setColorFilter(keyTextColor, PorterDuff.Mode.SRC_IN) }
        }
        view.background = specialKeyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
        view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) onClick()
            true
        }
        parent.addView(view)
    }

    fun updateLetterKeys() {
        val isCaps = service.isCapsOn || service.isCapsLock
        letterKeyViews.forEach { button ->
            val originalText = button.tag as? String ?: ""
            button.text = if (isCaps) originalText.uppercase() else originalText.lowercase()
        }
        updateShiftKeyState()
    }
    
    private fun updateShiftKeyState() {
        val keyView = shiftKeyView ?: return
        val iconRes = when {
            service.isCapsLock -> R.drawable.ic_shift_caps_lock
            service.isCapsOn -> R.drawable.ic_shift_filled
            else -> R.drawable.ic_shift
        }
        keyView.setImageResource(iconRes)
        val isActive = service.isCapsLock || service.isCapsOn
        keyView.background = if (isActive) activeShiftDrawable?.constantState?.newDrawable()?.mutate() else specialKeyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
        keyView.setColorFilter(if (isActive) Color.BLACK else keyTextColor, PorterDuff.Mode.SRC_IN)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addSpacebarKey(parent: ViewGroup, weight: Float) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_special, parent, false) as Button
        keyView.text = "space"
        keyView.setTextColor(keyTextColor)
        keyView.background = spacebarBackgroundDrawable?.constantState?.newDrawable()?.mutate()
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        var startX = 0f
        var isSwiping = false
        val swipeThreshold = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
        keyView.setOnTouchListener { _, event ->
            val ic = service.currentInputConnection ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startX = event.x; isSwiping = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - startX
                    if (Math.abs(deltaX) > swipeThreshold) {
                        isSwiping = true
                        val keyCode = if (deltaX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                        startX = event.x
                        service.performHapticFeedback()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!isSwiping) service.queueKeyPress(" "); true }
                else -> false
            }
        }
        
        // Track spacebar bounds for gesture recognition (using consistent window coordinates)
        keyView.doOnLayout {
            val locationInWindow = IntArray(2)
            keyView.getLocationInWindow(locationInWindow)
            val centerX = locationInWindow[0] + keyView.width / 2f
            val centerY = locationInWindow[1] + keyView.height / 2f
            val radius = minOf(keyView.width, keyView.height) / 2f
            val bounds = KeyBounds(centerX, centerY, radius)
            keyBounds["space"] = bounds
            keyBounds[" "] = bounds // Also store with space character as key
            Log.d("KeyboardLayoutManager", "Tracked spacebar bounds: window coords ($centerX, $centerY) radius=$radius")
        }
        
        parent.addView(keyView)
    }

    private fun addReturnKey(parent: ViewGroup, weight: Float) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_special, parent, false) as Button
        keyView.text = service.getReturnKeyLabel().lowercase()
        keyView.setTextColor(Color.WHITE)
        keyView.background = returnKeyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        keyView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) service.handleReturnKey()
            true
        }
        parent.addView(keyView)
    }

    fun cleanup() {
        // Clean up AI button animation
        currentSiriWaveDrawable?.stopAnimation()
        currentSiriWaveDrawable = null
        
        // Clean up popup
        dismissKeyPopup()
    }

    private fun clearAlphabetRows() {
        numberRow.removeAllViews()
        rowQwerty.removeAllViews()
        rowAsdf.removeAllViews()
        rowZxcv.removeAllViews()
        bottomRow.removeAllViews()
        letterKeyViews.clear()
        shiftKeyView = null
    }

    private fun setupLettersKeyboard() {
        clearAlphabetRows()
        "1234567890".forEach { addKey(numberRow, it.toString()) }
        "qwertyuiop".forEach { addKey(rowQwerty, it.toString(), isLetter = true) }
        addPaddingView(rowAsdf, 0.5f)
        "asdfghjkl".forEach { addKey(rowAsdf, it.toString(), isLetter = true) }
        addPaddingView(rowAsdf, 0.5f)
        addShiftKey()
        "zxcvbnm".forEach { addKey(rowZxcv, it.toString(), isLetter = true) }
        addBackspaceKey(rowZxcv, 1.5f)
        addSpecialKey(bottomRow, "123", weight = 1.5f) { service.switchToSymbols() }
        addSpecialKey(bottomRow, "@", weight = 1f) { service.queueKeyPress("@") }
        addSpacebarKey(bottomRow, 4.0f)
        addSpecialKey(bottomRow, ".", weight = 1f) { service.queueKeyPress(".") }
        addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) { service.switchToEmoji() }
        addReturnKey(bottomRow, 1.5f)
        updateLetterKeys()
    }

    private fun setupSymbolsKeyboard() {
        clearAlphabetRows()
        
        // Always show numbers on top row
        "1234567890".forEach { addKey(numberRow, it.toString()) }
        
        if (service.isSecondSymbolPanelShown) {
            // Second symbol panel ($+=) - Currency and special characters
            "€£¥₹¢°^%#".forEach { addKey(rowQwerty, it.toString()) }
            addPaddingView(rowAsdf, 0.5f)
            "±×÷=≠<>[]".forEach { addKey(rowAsdf, it.toString()) }
            addPaddingView(rowAsdf, 0.5f)
            
            addSpecialKey(rowZxcv, "#+=", weight = 1.5f) { service.switchToFirstSymbolPanel() }
            "\\|{}`~".forEach { addKey(rowZxcv, it.toString()) }
            addBackspaceKey(rowZxcv, 1.5f)
            
        } else {
            // First symbol panel (#+=) - Common symbols  
            "-/:;()$&@\"".forEach { addKey(rowQwerty, it.toString()) }
            addPaddingView(rowAsdf, 0.5f)
            ".,?!'*%_^~".forEach { addKey(rowAsdf, it.toString()) }
            addPaddingView(rowAsdf, 0.5f)
            
            addSpecialKey(rowZxcv, "$+=", weight = 1.5f) { service.switchToSecondSymbolPanel() }
            "[]{}<>".forEach { addKey(rowZxcv, it.toString()) }
            addBackspaceKey(rowZxcv, 1.5f)
        }
        
        // Bottom row (same for both panels)
        addSpecialKey(bottomRow, "ABC", weight = 1.5f) { service.switchToLetters() }
        addSpecialKey(bottomRow, "@", weight = 1f) { service.queueKeyPress("@") }
        addSpacebarKey(bottomRow, 4.0f)
        addSpecialKey(bottomRow, ".", weight = 1f) { service.queueKeyPress(".") }
        addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) { service.switchToEmoji() }
        addReturnKey(bottomRow, 1.5f)
    }

    private fun createEmojiTabs() {
        emojiCategoryTabsContainer = HorizontalScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48))
            isHorizontalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dpToPx(16))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            clipToPadding = false
            
            // Enhanced touch handling for better sliding
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Prevent parent from intercepting scroll gestures
                        parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Ensure smooth horizontal scrolling
                        parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Re-allow parent touch events
                        parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    else -> false
                }
            }
            
            addView(LinearLayout(service).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // Add extra padding for better spacing
                setPadding(dpToPx(4), 0, dpToPx(4), 0)
            })
        }
    }
    
    /**
     * Custom RecyclerView for emoji scrolling that completely prevents parent touch interception
     */
    private inner class EmojiRecyclerView(context: Context) : RecyclerView(context) {
        override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
            // Always prevent parent from intercepting during emoji scrolling
            parent?.requestDisallowInterceptTouchEvent(true)
            Log.v(KeyboardConstants.TAG, "🎯 EmojiRecyclerView intercepting touch event")
            return super.onInterceptTouchEvent(e)
        }
        
        override fun onTouchEvent(e: MotionEvent?): Boolean {
            // Ensure parent doesn't intercept during scrolling
            parent?.requestDisallowInterceptTouchEvent(true)
            return super.onTouchEvent(e)
        }
    }

    private fun createEmojiRecyclerView() {
        try {
            emojiRecyclerView = EmojiRecyclerView(service).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    0, 
                    1f
                ).apply {
                    topMargin = dpToPx(4)
                    bottomMargin = dpToPx(4)
                    leftMargin = dpToPx(4)
                    rightMargin = dpToPx(4)
                }
                
                // Create optimized layout manager for smooth scrolling
                val gridLayoutManager = GridLayoutManager(service, KeyboardConstants.EMOJI_COLUMNS).apply {
                    orientation = RecyclerView.VERTICAL
                    // Maximum prefetch for ultra-smooth scrolling
                    initialPrefetchItemCount = KeyboardConstants.EMOJI_COLUMNS * 8
                    isItemPrefetchEnabled = true
                    
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int = 1
                        override fun isSpanIndexCacheEnabled(): Boolean = true
                    }
                }
                layoutManager = gridLayoutManager
                
                // Ultra-smooth scrolling configuration
                setHasFixedSize(true)
                isNestedScrollingEnabled = true
                
                // Increased cache sizes for butter-smooth scrolling
                setItemViewCacheSize(120) // Doubled cache size
                
                // Create a larger recycled view pool
                setRecycledViewPool(RecyclerView.RecycledViewPool().apply {
                    setMaxRecycledViews(0, 200) // Much larger pool
                })
                
                // Disable animations for maximum performance
                setItemAnimator(null)
                
                // Enhanced touch handling for perfect emoji scrolling
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            Log.d(KeyboardConstants.TAG, "🎯 Emoji RecyclerView DOWN - preventing parent interception")
                            // Aggressively prevent ANY parent from intercepting touch events
                            parent?.requestDisallowInterceptTouchEvent(true)
                            // Let parent hierarchy know we want ALL touch events
                            var currentParent = parent
                            while (currentParent != null) {
                                if (currentParent is ViewGroup) {
                                    currentParent.requestDisallowInterceptTouchEvent(true)
                                    Log.v(KeyboardConstants.TAG, "🎯 Disabled interception for ${currentParent.javaClass.simpleName}")
                                }
                                currentParent = currentParent.parent
                            }
                            false // Let RecyclerView handle the touch
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Ensure no interception during scrolling
                            parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            Log.d(KeyboardConstants.TAG, "🎯 Emoji RecyclerView UP/CANCEL - re-enabling parent interception")
                            // Re-allow parent touch interception
                            parent?.requestDisallowInterceptTouchEvent(false)
                            false
                        }
                        else -> false
                    }
                }
                
                // Perfect scrolling settings
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                
                // Optimized padding and clipping for smooth edges
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                clipToPadding = false
                clipChildren = false
                
                // Additional performance optimizations
                setWillNotDraw(false)
                isDrawingCacheEnabled = false
            }
        } catch (e: Exception) {
            android.util.Log.e("EmojiRecyclerView", "Error creating optimized RecyclerView: ${e.message}")
            // Create a basic fallback RecyclerView
            emojiRecyclerView = RecyclerView(service).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                layoutManager = GridLayoutManager(service, KeyboardConstants.EMOJI_COLUMNS)
                setHasFixedSize(true)
                isNestedScrollingEnabled = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createEmojiBottomControlRow() {
        val keyHeight = service.resources.getDimensionPixelSize(R.dimen.ios_key_height)
        emojiBottomControlRow = LinearLayout(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyHeight)
            orientation = LinearLayout.HORIZONTAL
        }
        val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
        val abcButton = Button(service).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f).apply { setMargins(margin, 0, margin, 0) }
            text = "ABC"; setTextColor(keyTextColor); background = specialKeyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
            setOnClickListener { service.switchToLetters() }
        }
        val spacebar = Button(service).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 5.0f).apply { setMargins(margin, 0, margin, 0) }
            text = "space"; setTextColor(keyTextColor); background = spacebarBackgroundDrawable?.constantState?.newDrawable()?.mutate()
            setOnClickListener { service.queueKeyPress(" ") }
        }
        val backspaceKey = ImageButton(service).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f).apply { setMargins(margin, 0, margin, 0) }
            setImageResource(R.drawable.ic_backspace); setColorFilter(keyTextColor, PorterDuff.Mode.SRC_IN); background = specialKeyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { service.startTurboDelete(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { service.stopTurboDelete(); true }
                    else -> false
                }
            }
        }
        emojiBottomControlRow?.addView(abcButton)
        emojiBottomControlRow?.addView(spacebar)
        emojiBottomControlRow?.addView(backspaceKey)
    }
    
    private fun updateEmojiCategoryTabs() {
        try {
            Log.d("EmojiKeyboard", "🏷️ Updating emoji category tabs...")
            val tabsContainer = emojiCategoryTabsContainer ?: return
            val tabsLayout = tabsContainer.getChildAt(0) as? LinearLayout ?: return
            tabsLayout.removeAllViews()
            
            // EMOJI EMPTY LAYOUT FIX: Ensure categories are properly initialized
            val recentEmojis = service.getRecentEmojis()
            val allCategories = mutableListOf<Pair<String, List<String>>>()
            if (recentEmojis.isNotEmpty()) {
                allCategories.add("Recents" to recentEmojis)
                Log.d("EmojiKeyboard", "📌 Added ${recentEmojis.size} recent emojis")
            }
            allCategories.addAll(EmojiData.emojiCategories)
            this.currentEmojiCategories = allCategories
            
            Log.d("EmojiKeyboard", "📦 Initialized ${allCategories.size} emoji categories")
            
            // Ensure current index is valid
            if (service.currentEmojiCategoryIndex >= allCategories.size) {
                service.currentEmojiCategoryIndex = 0
                Log.d("EmojiKeyboard", "🔄 Reset emoji category index to 0")
            }
        
        currentEmojiCategories.forEachIndexed { index, (name, _) ->
            // Better category icons that match the content
            val iconResId = when(name) {
                "Recents" -> R.drawable.ic_recent
                "Emoticons" -> R.drawable.ic_emoji_emoticons  // 😀 category
                "People & Body" -> R.drawable.ic_emoji_people  // 👤 category
                "Animals & Nature" -> R.drawable.ic_emoji_animals  // 🐶 category
                "Food & Drink" -> R.drawable.ic_emoji_food  // 🍎 category  
                "Travel & Places" -> R.drawable.ic_emoji_transportation  // 🚗 category
                "Activities" -> R.drawable.ic_emoji_activities  // ⚽ category
                "Objects" -> R.drawable.ic_emoji_objects_new  // 💻 category
                "Symbols" -> R.drawable.ic_emoji_symbols_new  // ❤️ category
                "Flags" -> R.drawable.ic_emoji_flags  // 🇺🇸 category
                else -> R.drawable.ic_emoji_symbols_new
            }
            
            val tabButton = ImageButton(service, null, android.R.attr.borderlessButtonStyle).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(56), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    leftMargin = dpToPx(4)
                    rightMargin = dpToPx(4)
                }
                setImageResource(iconResId)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                
                // Enhanced ripple effect background
                background = createRippleDrawable(
                    cornerRadius = dpToPx(16f),
                    isSelected = (service.currentEmojiCategoryIndex == index)
                )
                
                // Professional icon tinting with better contrast
                val tintColor = if (service.currentEmojiCategoryIndex == index) {
                    Color.WHITE // White on selected (themed) background
                } else {
                    if (service.isDarkMode) Color.parseColor("#E0E0E0") else Color.parseColor("#666666")
                }
                imageTintList = ColorStateList.valueOf(tintColor)
                
                // Enhanced touch feedback
                isSelected = (service.currentEmojiCategoryIndex == index)
                isClickable = true
                isFocusable = true
                
                setOnClickListener { 
                    service.performHapticFeedback()
                    service.handleEmojiCategorySwitch(index)
                    
                    // Auto-scroll to center the selected tab
                    post {
                        val tabsContainer = emojiCategoryTabsContainer
                        if (tabsContainer != null) {
                            val scrollX = this.left - (tabsContainer.width - this.width) / 2
                            tabsContainer.smoothScrollTo(maxOf(0, scrollX), 0)
                        }
                    }
                }
            }
            tabsLayout.addView(tabButton)
        }
        
        Log.d("EmojiKeyboard", "✅ Successfully created ${currentEmojiCategories.size} emoji category tabs")
        
        } catch (e: Exception) {
            Log.e("EmojiKeyboard", "❌ Error updating emoji category tabs: ${e.message}", e)
            // Emergency fallback: ensure we have at least basic emoji categories
            try {
                if (currentEmojiCategories.isEmpty()) {
                    this.currentEmojiCategories = EmojiData.emojiCategories
                    service.currentEmojiCategoryIndex = 0
                    Log.d("EmojiKeyboard", "🔄 Applied emergency fallback categories")
                }
            } catch (fallbackError: Exception) {
                Log.e("EmojiKeyboard", "💥 Emergency fallback failed: ${fallbackError.message}")
            }
        }
    }

    private fun updateEmojiList() {
        try {
            Log.d("EmojiKeyboard", "📋 Updating emoji list...")
            
            // Ensure we're on the main thread
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { updateEmojiList() }
                return
            }
            
            // EMOJI EMPTY LAYOUT FIX: Ensure categories exist before processing
            if (currentEmojiCategories.isEmpty()) {
                Log.w("EmojiKeyboard", "⚠️  No emoji categories available, initializing defaults...")
                this.currentEmojiCategories = EmojiData.emojiCategories
                service.currentEmojiCategoryIndex = 0
            }
            
            // Validate current category index
            if (service.currentEmojiCategoryIndex >= currentEmojiCategories.size || service.currentEmojiCategoryIndex < 0) {
                Log.w("EmojiKeyboard", "⚠️  Invalid category index ${service.currentEmojiCategoryIndex}, resetting to 0")
                service.currentEmojiCategoryIndex = 0
            }
            
            val categoryInfo = currentEmojiCategories.getOrNull(service.currentEmojiCategoryIndex)
            val emojis = categoryInfo?.second ?: emptyList()
            val categoryName = categoryInfo?.first ?: "Unknown"
            
            Log.d("EmojiKeyboard", "📦 Category '$categoryName' has ${emojis.size} emojis")
            
            // Only update if we have a RecyclerView
            emojiRecyclerView?.let { recyclerView ->
                if (emojis.isNotEmpty()) {
                    emojiAdapter = EmojiAdapter(emojis) { emoji -> 
                        try {
                            service.handleEmojiKeyPress(emoji)
                        } catch (e: Exception) {
                            android.util.Log.e("EmojiList", "Emoji press error: ${e.message}")
                        }
                    }
                    recyclerView.adapter = emojiAdapter
                    emojiAdapter?.notifyDataSetChanged()
                    Log.d("EmojiKeyboard", "✅ Successfully loaded ${emojis.size} emojis into RecyclerView")
                } else {
                    Log.w("EmojiKeyboard", "⚠️  No emojis in category '$categoryName', setting empty adapter")
                    // Set empty adapter if no emojis
                    recyclerView.adapter = EmojiAdapter(emptyList()) { }
                }
            } ?: Log.e("EmojiKeyboard", "❌ RecyclerView is null, cannot update emoji list")
            
        } catch (e: Exception) {
            android.util.Log.e("EmojiList", "Update emoji list error: ${e.message}")
            // Fallback: try to show the first category
            try {
                Log.d("EmojiKeyboard", "🔄 Attempting fallback emoji list update...")
                service.currentEmojiCategoryIndex = 0
                val fallbackEmojis = if (currentEmojiCategories.isNotEmpty()) {
                    currentEmojiCategories[0].second
                } else {
                    EmojiData.emojiCategories.firstOrNull()?.second ?: emptyList()
                }
                emojiRecyclerView?.adapter = EmojiAdapter(fallbackEmojis) { emoji -> 
                    try {
                        service.handleEmojiKeyPress(emoji)
                    } catch (e2: Exception) {
                        android.util.Log.e("EmojiList", "Fallback emoji press error: ${e2.message}")
                    }
                }
                Log.d("EmojiKeyboard", "✅ Fallback loaded ${fallbackEmojis.size} emojis")
            } catch (fallbackError: Exception) {
                android.util.Log.e("EmojiList", "Fallback update error: ${fallbackError.message}")
            }
        }
    }
    
    private fun addPaddingView(parent: ViewGroup, weight: Float) {
        parent.addView(View(service).apply { layoutParams = LinearLayout.LayoutParams(0, 1, weight) })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addShiftKey() {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_icon, rowZxcv, false) as ImageButton
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        keyView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) service.toggleShift()
            true
        }
        rowZxcv.addView(keyView)
        shiftKeyView = keyView
        updateShiftKeyState()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBackspaceKey(parent: ViewGroup, weight: Float) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_icon, parent, false) as ImageButton
        keyView.setImageResource(R.drawable.ic_backspace)
        keyView.setColorFilter(keyTextColor, PorterDuff.Mode.SRC_IN)
        keyView.background = specialKeyBackgroundDrawable?.constantState?.newDrawable()?.mutate()
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        keyView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { service.startTurboDelete(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { service.stopTurboDelete(); true }
                else -> false
            }
        }
        parent.addView(keyView)
    }

    private fun createKeyPopup() {
        val inflater = service.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.key_popup, null)
        popupTextView = popupView.findViewById(R.id.popup_text)
        val popupBackgroundResId = if (service.isDarkMode) R.drawable.key_popup_background_dark else R.drawable.key_popup_background
        popupView.background = ContextCompat.getDrawable(service, popupBackgroundResId)
        popupTextView?.setTextColor(keyTextColor)
        val popupSize = dpToPx(54)
        keyPopup = PopupWindow(popupView, popupSize, popupSize, false).apply {
            animationStyle = 0
            isClippingEnabled = false
        }
    }

    private fun showKeyPopup(anchor: View, text: String) {
        if (keyPopup == null || popupTextView == null) createKeyPopup()
        popupTextView?.text = text
        keyPopup?.let { popup ->
            if (!anchor.isAttachedToWindow) return
            val anchorLocation = IntArray(2)
            anchor.getLocationInWindow(anchorLocation)
            val x = anchorLocation[0] + (anchor.width - popup.width) / 2
            val y = anchorLocation[1] - popup.height - dpToPx(4)
            if (!popup.isShowing) popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            else popup.update(x, y, -1, -1)
        }
    }

    private fun dismissKeyPopup() = keyPopup?.dismiss()
    private fun dpToPx(dp: Int): Int = (dp * service.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, service.resources.displayMetrics)

    /**
     * Create a professional ripple drawable for tab buttons
     */
    private fun createRippleDrawable(cornerRadius: Float, isSelected: Boolean): Drawable {
        val backgroundColor = if (isSelected) {
            // EMOJI CATEGORY TABS FIX: Handle empty theme color (gradient mode)
            val themeColor = if (service.themeColor.isNotEmpty()) {
                try {
                    Color.parseColor(service.themeColor)
                } catch (e: Exception) {
                    Log.w("EmojiKeyboard", "Invalid theme color '${service.themeColor}', using default")
                    // Default theme color
                    if (service.isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
                }
            } else {
                // When gradient is active, use a default accent color
                if (service.isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
            }
            themeColor
        } else {
            Color.TRANSPARENT
        }
        
        val backgroundDrawable = GradientDrawable().apply {
            this.cornerRadius = cornerRadius
            setColor(backgroundColor)
        }
        
        // Create ripple effect for touch feedback
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val rippleColor = if (isSelected) {
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF"))
            } else {
                ColorStateList.valueOf(Color.parseColor(if (service.isDarkMode) "#40FFFFFF" else "#40000000"))
            }
            android.graphics.drawable.RippleDrawable(rippleColor, backgroundDrawable, null)
        } else {
            backgroundDrawable
        }
    }

    /**
     * Update AI button theme to match return key styling with Siri-like animation
     */
    private fun updateAIButtonTheme(themeColorHex: String, isDarkMode: Boolean) {
        try {
            // Clean up previous animation
            currentSiriWaveDrawable?.stopAnimation()
            
            // Parse theme color with fallback
            val themeColor = if (themeColorHex.isNotEmpty()) {
                try {
                    Color.parseColor(themeColorHex)
                } catch (e: Exception) {
                    if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
                }
            } else {
                if (isDarkMode) Color.parseColor("#007AFF") else Color.parseColor("#007AFF")
            }
            
            // Create accent color (slightly different for wave variation)
            val accentColor = adjustColorBrightness(themeColor, 1.2f)
            
            // Create Siri-like wave drawable
            currentSiriWaveDrawable = SiriWaveDrawable(themeColor, accentColor)
            
            // Apply the animated background
            aiButton.background = currentSiriWaveDrawable
            
            // Set text color to white for visibility on animated background
            aiButton.setTextColor(Color.WHITE)
            
            // Force invalidation
            aiButton.invalidate()
            
            Log.d("KeyboardTheme", "✅ AI Button theme updated with Siri wave animation: $themeColorHex")
        } catch (e: Exception) {
            Log.e("KeyboardTheme", "❌ Error updating AI button theme: ${e.message}")
        }
    }
    
    /**
     * Adjust color brightness for gradient effect
     */
    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
    
    /**
     * Update AI button with gradient theme using Siri-like animation
     */
    private fun updateAIButtonGradientTheme(gradientColors: IntArray, isDarkMode: Boolean) {
        try {
            // Clean up previous animation
            currentSiriWaveDrawable?.stopAnimation()
            
            // Use the primary gradient color as base
            val baseColor = gradientColors.firstOrNull() ?: Color.parseColor("#007AFF")
            val accentColor = gradientColors.getOrNull(1) ?: adjustColorBrightness(baseColor, 1.2f)
            
            // Create Siri-like wave drawable with gradient colors
            currentSiriWaveDrawable = SiriWaveDrawable(baseColor, accentColor)
            
            aiButton.background = currentSiriWaveDrawable
            aiButton.setTextColor(Color.WHITE)
            aiButton.invalidate()
            
            Log.d("KeyboardTheme", "✅ AI Button gradient theme updated with Siri wave animation")
        } catch (e: Exception) {
            Log.e("KeyboardTheme", "❌ Error updating AI button gradient theme: ${e.message}")
        }
    }
    
    /**
     * Force immediate gradient theme application to all existing views
     */
    private fun forceImmediateGradientThemeApplication(
        keyBackgroundColor: Int, 
        specialKeyBackgroundColor: Int, 
        keyboardBackgroundColor: Int, 
        isDarkMode: Boolean, 
        gradientColors: IntArray
    ) {
        try {
            // Update all key views immediately with gradient accents
            updateAllKeyViewsImmediatelyWithGradient(keyBackgroundColor, specialKeyBackgroundColor, isDarkMode, gradientColors)
            
            // SUGGESTION BAR GRADIENT FIX: Apply gradient background to suggestion bar
            try {
                val suggestionGradient = GradientDrawable().apply {
                    this.colors = intArrayOf(
                        Color.argb(35, Color.red(gradientColors[0]), Color.green(gradientColors[0]), Color.blue(gradientColors[0])),
                        Color.argb(20, Color.red(gradientColors[1]), Color.green(gradientColors[1]), Color.blue(gradientColors[1]))
                    )
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.BOTTOM_TOP // Reversed for seamless flow
                }
                
                val suggestionCombinedBackground = LayerDrawable(arrayOf(
                    ColorDrawable(keyboardBackgroundColor),
                    suggestionGradient
                ))
                
                suggestionsContainer.background = suggestionCombinedBackground
            } catch (e: Exception) {
                // Fallback to solid color
                suggestionsContainer.setBackgroundColor(keyboardBackgroundColor)
            }
            
            // Force update suggestion area with gradient colors
            val suggestionTextColor = if(isDarkMode) Color.WHITE else Color.BLACK
            suggestion1.setTextColor(gradientColors[0])
            suggestion2.setTextColor(suggestionTextColor)
            suggestion3.setTextColor(suggestionTextColor)
            val separatorColor = if(isDarkMode) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")
            separator1.setBackgroundColor(separatorColor)
            separator2.setBackgroundColor(separatorColor)
            
            // Update AI button gradient theme
            updateAIButtonGradientTheme(gradientColors, isDarkMode)
            
            // Force invalidation of suggestion area
            suggestionsContainer.invalidate()
            suggestion1.invalidate()
            suggestion2.invalidate()
            suggestion3.invalidate()
            
        } catch (e: Exception) {
            // Silent catch to prevent crashes
        }
    }
    
    /**
     * Update all key views immediately with gradient theme
     */
    private fun updateAllKeyViewsImmediatelyWithGradient(
        keyBackgroundColor: Int, 
        specialKeyBackgroundColor: Int, 
        isDarkMode: Boolean, 
        gradientColors: IntArray
    ) {
        try {
            val cornerRadius = dpToPx(6f)
            val newKeyBackgroundDrawable = GradientDrawable().apply { 
                this.cornerRadius = cornerRadius
                setColor(keyBackgroundColor) 
            }
            val newSpecialKeyBackgroundDrawable = GradientDrawable().apply { 
                this.cornerRadius = cornerRadius
                setColor(specialKeyBackgroundColor) 
            }
            val newReturnKeyBackgroundDrawable = GradientDrawable().apply { 
                this.cornerRadius = cornerRadius
                this.colors = gradientColors
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TL_BR
            }
            
            // Update drawables for immediate application
            keyBackgroundDrawable = newKeyBackgroundDrawable
            specialKeyBackgroundDrawable = newSpecialKeyBackgroundDrawable
            returnKeyBackgroundDrawable = newReturnKeyBackgroundDrawable
            
            // Apply to all visible keys immediately
            applyGradientToAllKeys(newKeyBackgroundDrawable, newSpecialKeyBackgroundDrawable, newReturnKeyBackgroundDrawable, isDarkMode)
            
        } catch (e: Exception) {
            // Silent catch
        }
    }
    
    /**
     * Apply gradient theme to all keys immediately
     */
    private fun applyGradientToAllKeys(
        keyDrawable: GradientDrawable,
        specialKeyDrawable: GradientDrawable,
        returnKeyDrawable: GradientDrawable,
        isDarkMode: Boolean
    ) {
        try {
            val textColor = if (isDarkMode) Color.WHITE else Color.BLACK
            
            // Apply to all existing views in keyboard
            applyGradientThemeToViewGroup(keyboardRootContainer, keyDrawable, specialKeyDrawable, returnKeyDrawable, textColor)
            
        } catch (e: Exception) {
            // Silent catch
        }
    }
    
    /**
     * Recursively apply gradient theme to view groups
     */
    private fun applyGradientThemeToViewGroup(
        viewGroup: ViewGroup,
        keyDrawable: GradientDrawable,
        specialKeyDrawable: GradientDrawable,
        returnKeyDrawable: GradientDrawable,
        textColor: Int
    ) {
        try {
            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i)
                when (child) {
                    is ViewGroup -> {
                        applyGradientThemeToViewGroup(child, keyDrawable, specialKeyDrawable, returnKeyDrawable, textColor)
                    }
                    is TextView -> {
                        // Apply gradient theme based on key type
                        val tag = child.tag as? String
                        when {
                            tag == "return_key" || tag == "enter_key" -> {
                                child.background = returnKeyDrawable
                                child.setTextColor(Color.WHITE)
                            }
                            tag?.contains("special") == true -> {
                                child.background = specialKeyDrawable
                                child.setTextColor(textColor)
                            }
                            tag?.contains("letter") == true || tag?.contains("number") == true -> {
                                child.background = keyDrawable
                                child.setTextColor(textColor)
                            }
                        }
                        child.invalidate()
                    }
                }
            }
        } catch (e: Exception) {
            // Silent catch
        }
    }

    private fun setupAIButton() {
        // Regular tap - existing AI functionality
        aiButton.setOnClickListener { service.handleAIButton() }
        
        // Long press - show AI cards in the response area
        aiButton.setOnLongClickListener {
            service.performHapticFeedback()
            service.showAICardsInResponseArea()
            true
        }
    }

    private fun setupSuggestionClicks() {
        val listener = View.OnClickListener { view ->
            val suggestion = (view as TextView).text.toString()
            if (suggestion.isNotEmpty()) service.onSuggestionTapped(suggestion)
        }
        suggestion1.setOnClickListener(listener)
        suggestion2.setOnClickListener(listener)
        suggestion3.setOnClickListener(listener)
    }
    
    /**
     * Hide emoji keyboard and return to main keyboard
     */
    fun hideEmojiKeyboard() {
        service.isEmojiKeyboardShown = false
        keyboardSwitcher.displayedChild = MAIN_KEYBOARD_INDEX
        updateLayout()
    }
    
    /**
     * Apply landscape-specific optimizations for better key layout
     */
    fun applyLandscapeOptimizations() {
        try {
            // Get all key rows and optimize their dimensions
            val keyboardView = getRootView()
            if (keyboardView is ViewGroup) {
                optimizeKeyRowsForLandscape(keyboardView)
            }
        } catch (e: Exception) {
            Log.w(KeyboardConstants.TAG, "Error applying landscape optimizations: ${e.message}")
        }
    }
    
    /**
     * Optimize key rows for landscape mode
     */
    private fun optimizeKeyRowsForLandscape(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            when (child) {
                is LinearLayout -> {
                    // This is likely a key row
                    val layoutParams = child.layoutParams
                    if (layoutParams is LinearLayout.LayoutParams) {
                        // Much smaller key row height for landscape
                        val smallerHeight = (service.landscapeKeyHeight * service.resources.displayMetrics.density).toInt()
                        layoutParams.height = smallerHeight
                        child.layoutParams = layoutParams
                        
                        // Minimal margins between rows
                        layoutParams.topMargin = 1
                        layoutParams.bottomMargin = 1
                    }
                    
                    // Optimize individual keys in this row
                    optimizeKeysInRow(child)
                }
                is ViewGroup -> {
                    // Recursively optimize child view groups
                    optimizeKeyRowsForLandscape(child)
                }
            }
        }
    }
    
    /**
     * Optimize individual keys in a row for landscape
     */
    private fun optimizeKeysInRow(keyRow: LinearLayout) {
        for (i in 0 until keyRow.childCount) {
            val key = keyRow.getChildAt(i)
            if (key is Button || key is ImageButton || key is TextView) {
                val layoutParams = key.layoutParams
                if (layoutParams is LinearLayout.LayoutParams) {
                    // Make keys more compact with minimal margins
                    layoutParams.leftMargin = 1
                    layoutParams.rightMargin = 1
                    layoutParams.topMargin = 0
                    layoutParams.bottomMargin = 0
                    key.layoutParams = layoutParams
                }
                
                // Reduce text size and padding for better fit
                if (key is TextView) {
                    key.textSize = key.textSize * 0.85f // Smaller text
                    key.setPadding(
                        (key.paddingLeft * 0.7f).toInt(),
                        (key.paddingTop * 0.5f).toInt(),
                        (key.paddingRight * 0.7f).toInt(),
                        (key.paddingBottom * 0.5f).toInt()
                    )
                }
                
                if (key is Button) {
                    key.textSize = key.textSize * 0.85f // Smaller text
                    key.setPadding(
                        (key.paddingLeft * 0.7f).toInt(),
                        (key.paddingTop * 0.5f).toInt(),
                        (key.paddingRight * 0.7f).toInt(),
                        (key.paddingBottom * 0.5f).toInt()
                    )
                }
            }
        }
    }
    
    /**
     * Professional key detection for glide typing
     * Get the key character at specific coordinates relative to the keyboard switcher view
     */
    fun getKeyAt(x: Float, y: Float): String? {
        try {
            Log.v("KeyDetection", "🎯 Looking for key at switcher coords($x, $y)")
            
            // Check all key rows for hits in the current keyboard
            val currentKeyboard = when {
                service.isSymbolsShown -> mainKeyboardContainer  // Symbols are part of main keyboard
                service.isEmojiKeyboardShown -> emojiKeyboardContainer
                else -> mainKeyboardContainer  // Letters keyboard is also main keyboard
            }
            
            return findKeyInLayout(currentKeyboard, x, y)
            
        } catch (e: Exception) {
            Log.e("KeyDetection", "❌ Error detecting key: ${e.message}")
            return null
        }
    }
    
    /**
     * Recursively find key in layout hierarchy using relative coordinates
     */
    private fun findKeyInLayout(layout: View?, x: Float, y: Float): String? {
        if (layout == null) return null
        
        try {
            if (layout is ViewGroup) {
                // Check all children
                for (i in 0 until layout.childCount) {
                    val child = layout.getChildAt(i)
                    val result = findKeyInLayout(child, x, y)
                    if (result != null) return result
                }
            } else if (layout is Button || layout is TextView) {
                // Check if point is within this key's bounds
                val keyboardSwitcher = getKeyboardSwitcher()
                val switcherLocation = IntArray(2)
                val keyLocation = IntArray(2)
                
                keyboardSwitcher.getLocationInWindow(switcherLocation)
                layout.getLocationInWindow(keyLocation)
                
                // Calculate key bounds relative to keyboard switcher
                val relativeLeft = keyLocation[0] - switcherLocation[0]
                val relativeTop = keyLocation[1] - switcherLocation[1]
                val relativeRight = relativeLeft + layout.width
                val relativeBottom = relativeTop + layout.height
                
                if (x >= relativeLeft && x <= relativeRight && y >= relativeTop && y <= relativeBottom) {
                    val keyText = when (layout) {
                        is Button -> layout.text?.toString()
                        is TextView -> layout.text?.toString()
                        else -> null
                    }
                    
                    if (!keyText.isNullOrEmpty() && keyText != "space") {
                        Log.d("KeyDetection", "✅ Found key '$keyText' at ($x, $y) - bounds: ($relativeLeft, $relativeTop, $relativeRight, $relativeBottom)")
                        return keyText.uppercase()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("KeyDetection", "Warning in key detection: ${e.message}")
        }
        
        return null
    }
}