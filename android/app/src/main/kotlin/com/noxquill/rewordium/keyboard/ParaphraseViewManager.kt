package com.noxquill.rewordium.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.view.ContextThemeWrapper
import com.noxquill.rewordium.R
import kotlinx.coroutines.*

class ParaphraseViewManager(private val service: RewordiumAIKeyboardService, private val rootView: FrameLayout) {
    var isParaphrasingMode = false
        private set

    private val mainKeyboardView: View = rootView.findViewById(R.id.keyboard_root_container)

    private val paraphraseContainer: FrameLayout
    private var currentPersona = "Neutral"
    private var currentOriginalText: String = ""
    
    // UI elements that need theme updates
    private var headerBackButton: TextView? = null
    private var headerTitleView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var loadingText: TextView? = null
    private val personaButtons = mutableListOf<TextView>()
    private val paraphraseCards = mutableListOf<LinearLayout>()

    init {
        paraphraseContainer = FrameLayout(service).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT // Use MATCH_PARENT to fill the whole area
            )
            visibility = View.GONE
        }

        // Set background with gradient support AFTER paraphraseContainer is initialized
        applyAIPanelBackground()

        // =========================================================================
        // START OF THE FIX: Use a WindowInsetsListener to dynamically add padding
        // that matches the navigation bar height.
        // =========================================================================
        ViewCompat.setOnApplyWindowInsetsListener(paraphraseContainer) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            // Apply the bottom inset as padding to push the content up
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBarInsets.bottom)
            // Return the insets so that child views can also consume them if needed
            insets
        }
        // =========================================================================
        // END OF THE FIX
        // =========================================================================

        rootView.addView(paraphraseContainer)
    }
    
    /**
     * Public method to update theme when gradient or color changes
     * This method should be called from RewordiumAIKeyboardService when theme changes
     */
    fun updateTheme() {
        // Update the background first
        applyAIPanelBackground()
        
        // Update all UI elements with the new theme colors
        val newThemeColor = getThemeColorForAIPanel()
        
        // Update header back button
        headerBackButton?.setTextColor(newThemeColor)
        
        // Update header title with theme color (not just dark/light mode colors)
        headerTitleView?.setTextColor(newThemeColor)
        
        // Update loading indicator
        loadingProgressBar?.indeterminateTintList = ColorStateList.valueOf(newThemeColor)
        loadingText?.setTextColor(if (service.isDarkMode) Color.LTGRAY else Color.DKGRAY)
        
        // Update persona buttons
        personaButtons.forEach { button ->
            val persona = button.text.toString()
            val isSelected = persona == currentPersona
            button.setTextColor(if (isSelected) Color.WHITE else newThemeColor)
            button.background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(if (isSelected) newThemeColor else Color.TRANSPARENT)
                setStroke(1, newThemeColor)
            }
        }
        
        // Update paraphrase cards
        paraphraseCards.forEach { card ->
            // Update the card background based on dark/light mode
            val normalDrawable = GradientDrawable().apply {
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.WHITE)
                cornerRadius = dpToPx(12f)
            }
            val pressedDrawable = GradientDrawable().apply {
                setColor(if (service.isDarkMode) Color.parseColor("#3A3A3C") else Color.parseColor("#F0F0F0"))
                cornerRadius = dpToPx(12f)
            }
            card.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
                addState(intArrayOf(), normalDrawable)
            }
            
            // Update accent indicator (first child) with gradient/color consistency
            val accentIndicator = card.getChildAt(0) as? View
            accentIndicator?.let { indicator ->
                val prefs = service.getSharedPreferences(com.noxquill.rewordium.keyboard.util.KeyboardConstants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val currentGradient = prefs.getString(com.noxquill.rewordium.keyboard.util.KeyboardConstants.KEY_GRADIENT_THEME, "") ?: ""
                
                indicator.background = if (currentGradient.isNotEmpty()) {
                    // Create gradient accent indicator
                    val gradientColors = getGradientColorsForPanel(currentGradient)
                    GradientDrawable().apply {
                        colors = gradientColors
                        gradientType = GradientDrawable.LINEAR_GRADIENT
                        orientation = GradientDrawable.Orientation.TOP_BOTTOM
                        cornerRadii = floatArrayOf(dpToPx(12f), dpToPx(12f), 0f, 0f, 0f, 0f, dpToPx(12f), dpToPx(12f))
                    }
                } else {
                    // Use solid color accent indicator
                    GradientDrawable().apply {
                        setColor(newThemeColor)
                        cornerRadii = floatArrayOf(dpToPx(12f), dpToPx(12f), 0f, 0f, 0f, 0f, dpToPx(12f), dpToPx(12f))
                    }
                }
            }
            
            // Update text color in card
            (card.getChildAt(1) as? TextView)?.setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
        }
    }
    
    /**
     * Apply background with gradient support to AI panel
     */
    private fun applyAIPanelBackground() {
        val prefs = service.getSharedPreferences(com.noxquill.rewordium.keyboard.util.KeyboardConstants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val currentGradient = prefs.getString(com.noxquill.rewordium.keyboard.util.KeyboardConstants.KEY_GRADIENT_THEME, "") ?: ""
        
        if (currentGradient.isNotEmpty()) {
            // Apply gradient background to AI panel
            val gradientColors = getGradientColorsForPanel(currentGradient)
            val keyboardBackgroundColor = if (service.isDarkMode) Color.parseColor("#1C1C1E") else Color.parseColor("#D1D1D6")
            
            // Create subtle gradient overlay
            val gradientOverlay = android.graphics.drawable.GradientDrawable().apply {
                colors = intArrayOf(
                    Color.argb(40, Color.red(gradientColors[0]), Color.green(gradientColors[0]), Color.blue(gradientColors[0])),
                    Color.argb(20, Color.red(gradientColors[1]), Color.green(gradientColors[1]), Color.blue(gradientColors[1]))
                )
                gradientType = android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
            }
            
            val combinedBackground = android.graphics.drawable.LayerDrawable(arrayOf(
                android.graphics.drawable.ColorDrawable(keyboardBackgroundColor),
                gradientOverlay
            ))
            
            paraphraseContainer.background = combinedBackground
        } else {
            // Apply solid background
            paraphraseContainer.setBackgroundColor(if (service.isDarkMode) Color.parseColor("#1C1C1E") else Color.parseColor("#D1D1D6"))
        }
    }
    
    /**
     * Get gradient colors for AI panel background
     */
    private fun getGradientColorsForPanel(gradientId: String): IntArray {
        return when (gradientId) {
            "gradient_ocean", "gradient_royal" -> intArrayOf(Color.parseColor("#667eea"), Color.parseColor("#764ba2"))
            "gradient_sunset" -> intArrayOf(Color.parseColor("#f093fb"), Color.parseColor("#f5576c"))
            "gradient_forest" -> intArrayOf(Color.parseColor("#4facfe"), Color.parseColor("#00f2fe"))
            "gradient_fire" -> intArrayOf(Color.parseColor("#ff9a9e"), Color.parseColor("#fecfef"))
            "gradient_midnight" -> intArrayOf(Color.parseColor("#2c3e50"), Color.parseColor("#3498db"))
            "gradient_emerald" -> intArrayOf(Color.parseColor("#11998e"), Color.parseColor("#38ef7d"))
            "gradient_gold" -> intArrayOf(Color.parseColor("#f7971e"), Color.parseColor("#ffd200"))
            "gradient_pink" -> intArrayOf(Color.parseColor("#ffecd2"), Color.parseColor("#fcb69f"))
            "gradient_space" -> intArrayOf(Color.parseColor("#434343"), Color.parseColor("#000000"))
            "gradient_aurora" -> intArrayOf(Color.parseColor("#a8edea"), Color.parseColor("#fed6e3"))
            "gradient_peach" -> intArrayOf(Color.parseColor("#d299c2"), Color.parseColor("#fef9d7"))
            "gradient_mint" -> intArrayOf(Color.parseColor("#89f7fe"), Color.parseColor("#66a6ff"))
            "gradient_cherry" -> intArrayOf(Color.parseColor("#ffecd2"), Color.parseColor("#fcb69f"))
            "gradient_cosmic" -> intArrayOf(Color.parseColor("#fc466b"), Color.parseColor("#3f5efb"))
            else -> intArrayOf(Color.parseColor("#667eea"), Color.parseColor("#764ba2")) // Default
        }
    }

    // --- All other methods are unchanged ---

    fun show(originalText: String) {
        if (isParaphrasingMode) return
        isParaphrasingMode = true
        currentPersona = "Neutral"
        currentOriginalText = originalText

        mainKeyboardView.visibility = View.GONE
        
        // Refresh background to match current theme
        applyAIPanelBackground()
        
        paraphraseContainer.visibility = View.VISIBLE

        paraphraseContainer.removeAllViews()
        buildParaphraseShell()
        // It seems there's a typo here, it should probably be `currentPersona`
        service.generateParaphraseWithPersona(originalText, currentPersona)
    }

    fun exitParaphraseMode() {
        if (!isParaphrasingMode) return
        isParaphrasingMode = false
        service.currentParaphraseJob?.cancel()

        paraphraseContainer.visibility = View.GONE
        mainKeyboardView.visibility = View.VISIBLE

        service.layoutManager.updateLayout()
    }

    private fun buildParaphraseShell() {
        val shell = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL

            // The container now handles the full height, so the shell can wrap its content
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // The background color is now set on the parent container
        }
        shell.addView(createHeaderBar())
        shell.addView(createPersonaSelector())

        val contentArea = LinearLayout(service).apply {
            tag = "contentArea"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        contentArea.addView(createLoadingIndicator())
        shell.addView(contentArea)
        paraphraseContainer.addView(shell)
    }

    fun updateWithResults(paraphrases: List<String>) {
        val contentArea = paraphraseContainer.findViewWithTag<LinearLayout>("contentArea") ?: return
        contentArea.removeAllViews()

        if (paraphrases.isEmpty()) {
            service.showToast("Failed to generate paraphrases.")
            exitParaphraseMode()
            return
        }
        val scrollView = ScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
        }
        val optionsContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        val personaColor = getPersonaColor(currentPersona)
        paraphrases.take(3).forEachIndexed { index, text ->
            val card = createParaphraseCard(text, personaColor)
            card.setOnClickListener {
                service.applyParaphrase(text)
            }
            optionsContainer.addView(card)
            if (index < paraphrases.size - 1 && index < 2) {
                optionsContainer.addView(View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8)
                    )
                })
            }
        }
        scrollView.addView(optionsContainer)
        contentArea.addView(scrollView)
    }

    private fun refreshForNewPersona() {
        // Clear UI element references for new content
        personaButtons.clear()
        paraphraseCards.clear()
        headerBackButton = null
        headerTitleView = null
        loadingProgressBar = null
        loadingText = null
        
        paraphraseContainer.removeAllViews()
        buildParaphraseShell()
        service.generateParaphraseWithPersona(currentOriginalText, currentPersona)
    }

    fun updatePersonaButtons() {
        // Only update if we're in paraphrase mode
        if (!isParaphrasingMode) return

        // If the current persona is no longer in the available personas, reset to "Neutral"
        if (currentPersona != "Neutral" && !service.availablePersonas.contains(currentPersona)) {
            currentPersona = "Neutral"
            // Regenerate with the default persona
            refreshForNewPersona()
        } else {
            // Just rebuild the persona selector with the updated list
            val personaSelector = paraphraseContainer.findViewWithTag<View>("personaSelector")
            personaSelector?.let { view ->
                val parent = view.parent as? ViewGroup
                parent?.let { safeParent ->
                    val index = safeParent.indexOfChild(view)
                    if (index >= 0) {
                        safeParent.removeViewAt(index)
                        safeParent.addView(createPersonaSelector(), index)
                    }
                }
            }
        }
    }

    private fun createHeaderBar(): View {
        return LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44))
            headerBackButton = TextView(service).apply {
                text = "❮ Back"
                
                // Safe theme color parsing with gradient support
                val backButtonColor = getThemeColorForAIPanel()
                setTextColor(backButtonColor)
                
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
                setOnClickListener { exitParaphraseMode() }
            }
            addView(headerBackButton)
            headerTitleView = TextView(service).apply {
                text = "Rewordium AI"
                
                // Apply theme color or gradient effect to title
                val prefs = service.getSharedPreferences(com.noxquill.rewordium.keyboard.util.KeyboardConstants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val currentGradient = prefs.getString(com.noxquill.rewordium.keyboard.util.KeyboardConstants.KEY_GRADIENT_THEME, "") ?: ""
                
                if (currentGradient.isNotEmpty()) {
                    // For gradient themes, use the primary gradient color
                    val themeColor = getThemeColorForAIPanel()
                    setTextColor(themeColor)
                } else {
                    // For solid color themes, use the theme color
                    val themeColor = getThemeColorForAIPanel()
                    setTextColor(themeColor)
                }
                
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(headerTitleView)
            addView(View(service).apply { layoutParams = LinearLayout.LayoutParams(headerBackButton!!.paddingLeft + headerBackButton!!.paddingRight, 1) })
        }
    }

    private fun createPersonaSelector(): View {
        val selector = HorizontalScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isHorizontalScrollBarEnabled = false
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            tag = "personaSelector"
        }
        val buttonContainer = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        // Clear previous persona buttons
        personaButtons.clear()
        
        service.availablePersonas.forEach { persona ->
            val button = TextView(service).apply {
                text = persona
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                val isSelected = persona == currentPersona
                val themeColor = getThemeColorForAIPanel()
                setTextColor(if (isSelected) Color.WHITE else themeColor)
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16f)
                    setColor(if (isSelected) themeColor else Color.TRANSPARENT)
                    setStroke(1, themeColor)
                }
                setOnClickListener {
                    if (currentPersona != persona) {
                        currentPersona = persona
                        refreshForNewPersona()
                    }
                }
            }
            
            // Store reference for theme updates
            personaButtons.add(button)
            
            buttonContainer.addView(button)
            buttonContainer.addView(View(service).apply { layoutParams = LinearLayout.LayoutParams(dpToPx(8), 1) })
        }
        selector.addView(buttonContainer)
        return selector
    }

    private fun createLoadingIndicator(): View {
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            loadingProgressBar = ProgressBar(service).apply {
                indeterminateTintList = ColorStateList.valueOf(getThemeColorForAIPanel())
            }
            addView(loadingProgressBar)
            loadingText = TextView(service).apply {
                text = "Thinking..."
                setTextColor(if (service.isDarkMode) Color.LTGRAY else Color.DKGRAY)
                textSize = 15f
                setPadding(0, dpToPx(16), 0, 0)
            }
            addView(loadingText)
        }
    }

    private fun createParaphraseCard(text: String, color: Int): View {
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val normalDrawable = GradientDrawable().apply {
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.WHITE)
                cornerRadius = dpToPx(12f)
            }
            val pressedDrawable = GradientDrawable().apply {
                setColor(if (service.isDarkMode) Color.parseColor("#3A3A3C") else Color.parseColor("#F0F0F0"))
                cornerRadius = dpToPx(12f)
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
                addState(intArrayOf(), normalDrawable)
            }
        }
        val accentIndicator = View(service).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(4), ViewGroup.LayoutParams.MATCH_PARENT)
            
            // Check if gradient theme is active for accent indicator
            val prefs = service.getSharedPreferences(com.noxquill.rewordium.keyboard.util.KeyboardConstants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val currentGradient = prefs.getString(com.noxquill.rewordium.keyboard.util.KeyboardConstants.KEY_GRADIENT_THEME, "") ?: ""
            
            background = if (currentGradient.isNotEmpty()) {
                // Create gradient accent indicator
                val gradientColors = getGradientColorsForPanel(currentGradient)
                GradientDrawable().apply {
                    colors = gradientColors
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    cornerRadii = floatArrayOf(dpToPx(12f), dpToPx(12f), 0f, 0f, 0f, 0f, dpToPx(12f), dpToPx(12f))
                }
            } else {
                // Use solid color accent indicator
                GradientDrawable().apply {
                    setColor(color)
                    cornerRadii = floatArrayOf(dpToPx(12f), dpToPx(12f), 0f, 0f, 0f, 0f, dpToPx(12f), dpToPx(12f))
                }
            }
        }
        card.addView(accentIndicator)
        card.addView(TextView(service).apply {
            this.text = text
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
            textSize = 16f
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        })
        
        // Store reference for theme updates
        paraphraseCards.add(card)
        
        return card
    }
    
    /**
     * Get theme color for AI panel with gradient support
     */
    private fun getThemeColorForAIPanel(): Int {
        // Check if gradient theme is active
        val prefs = service.getSharedPreferences(com.noxquill.rewordium.keyboard.util.KeyboardConstants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val currentGradient = prefs.getString(com.noxquill.rewordium.keyboard.util.KeyboardConstants.KEY_GRADIENT_THEME, "") ?: ""
        
        if (currentGradient.isNotEmpty()) {
            // Return primary color from the active gradient
            return when (currentGradient) {
                "gradient_ocean", "gradient_royal" -> Color.parseColor("#667eea")
                "gradient_sunset" -> Color.parseColor("#f093fb")
                "gradient_forest" -> Color.parseColor("#4facfe")
                "gradient_fire" -> Color.parseColor("#ff9a9e")
                "gradient_midnight" -> Color.parseColor("#2c3e50")
                "gradient_emerald" -> Color.parseColor("#11998e")
                "gradient_gold" -> Color.parseColor("#f7971e")
                "gradient_pink" -> Color.parseColor("#ffecd2")
                "gradient_space" -> Color.parseColor("#434343")
                "gradient_aurora" -> Color.parseColor("#a8edea")
                "gradient_peach" -> Color.parseColor("#d299c2")
                "gradient_mint" -> Color.parseColor("#89f7fe")
                "gradient_cherry" -> Color.parseColor("#ffecd2")
                "gradient_cosmic" -> Color.parseColor("#fc466b")
                else -> Color.parseColor("#007AFF") // Default blue
            }
        } else {
            // Use solid color theme with safety check
            return if (service.themeColor.isNotEmpty()) {
                try {
                    Color.parseColor(service.themeColor)
                } catch (e: Exception) {
                    Color.parseColor("#007AFF") // Default blue fallback
                }
            } else {
                Color.parseColor("#007AFF") // Default blue when no theme
            }
        }
    }

    private fun getPersonaColor(persona: String): Int {
        val colorHex = when (persona) {
            "Happy" -> "#34C759"
            "Sad" -> "#FF9500"
            "Humor" -> "#FF2D55"
            "Formal" -> "#5856D6"
            "Casual" -> "#FF3B30"
            else -> {
                // Safe fallback for service theme color
                if (service.themeColor.isNotEmpty()) {
                    service.themeColor
                } else {
                    "#007AFF" // Default blue
                }
            }
        }
        return try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            Color.parseColor("#007AFF") // Safe fallback
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * service.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, service.resources.displayMetrics)

    // =========================================================================
    // AI CARDS LOTTIE IMPLEMENTATION
    // =========================================================================
    
    fun showAICards() {
    // Always reset paraphrasing mode and visibility to ensure navigation works
    isParaphrasingMode = true
    currentPersona = "Neutral"
    currentOriginalText = ""

    // Hide main keyboard, show AI panel
    mainKeyboardView.visibility = View.GONE
    paraphraseContainer.visibility = View.VISIBLE

    // Refresh background to match current theme
    applyAIPanelBackground()

    // Remove any previous views and show AI cards
    paraphraseContainer.removeAllViews()
    buildAICardsShell()
    }

    private fun buildAICardsShell() {
        val shell = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        shell.addView(createAICardsHeaderBar())
        shell.addView(createAICardsContent())
        paraphraseContainer.addView(shell)
    }

    private fun createAICardsHeaderBar(): View {
        val headerBar = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(if (service.isDarkMode) Color.parseColor("#1C1C1E") else Color.WHITE)
                setStroke(dpToPx(1), if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA"))
            }
            elevation = dpToPx(2).toFloat()
        }

        val backButton = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(10), dpToPx(6))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7"))
            }
            isClickable = true
            isFocusable = true
            
            val backIcon = TextView(service).apply {
                text = "←"
                textSize = 16f
                setTextColor(getThemeColorForAIPanel())
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            addView(backIcon)
            
            val backText = TextView(service).apply {
                text = "Back"
                textSize = 14f
                setTextColor(getThemeColorForAIPanel())
                setPadding(dpToPx(6), 0, 0, 0)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            addView(backText)
            
            setOnClickListener { 
                service.performHapticFeedback()
                // Add smooth exit animation
                animate()
                    .alpha(0.7f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(150)
                    .withEndAction {
                        exitParaphraseMode()
                    }
                    .start()
            }
        }

        val title = TextView(service).apply {
            text = "✨ AI Assistant"
            textSize = 20f
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.parseColor("#1D1D1F"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        // Settings/more button
        val moreButton = TextView(service).apply {
            text = "⋯"
            textSize = 20f
            setTextColor(getThemeColorForAIPanel())
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(20f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7"))
            }
            isClickable = true
            isFocusable = true
            
            setOnClickListener {
                service.performHapticFeedback()
                // Could expand for future settings
                service.showToast("More options coming soon!")
            }
        }

        headerBar.addView(backButton)
        headerBar.addView(title)
        headerBar.addView(moreButton)

        return headerBar
    }

    private fun createAICardsContent(): View {
        // Create ScrollView for smooth scrolling - Compact version
        val scrollView = ScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dpToPx(4), 0, dpToPx(8))
        }
        
        val contentArea = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }

        // First row of cards with Lottie animations
        val firstRow = createAICardsRow(
            listOf(
                AICardData("kb_email.lottie", "Email", "Professional emails", "#007AFF") { showEmailDialog() },
                AICardData("kb_translate.lottie", "Translate", "Any language", "#34C759") { showTranslateDialog() }
            )
        )

        // Second row of cards  
        val secondRow = createAICardsRow(
            listOf(
                AICardData("kb_summary.lottie", "Summarize", "Key points", "#FF9500") { showSummarizeDialog() },
                AICardData("kb_poetry.lottie", "Poetry", "Creative writing", "#AF52DE") { showPoetryDialog() }
            )
        )
        
        // Third row of cards
        val thirdRow = createAICardsRow(
            listOf(
                AICardData("kb_creative.lottie", "Creative", "Imaginative ideas", "#5AC8FA") { showCreativeDialog() },
                AICardData("kb_smart_ai.lottie", "Smart AI", "Human-like responses", "#FF3B30") { showSmartAIDialog() }
            )
        )

        contentArea.addView(firstRow)
        contentArea.addView(secondRow)
        contentArea.addView(thirdRow)
        
        scrollView.addView(contentArea)
        return scrollView
    }

    private fun createAICardsRow(cards: List<AICardData>): LinearLayout {
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(4), 0, dpToPx(4))
            }
            weightSum = cards.size.toFloat()
        }

        cards.forEachIndexed { index, cardData ->
            val card = createAICard(cardData)
            row.addView(card)
            
            // Add compact spacing between cards
            if (index < cards.size - 1) {
                val spacer = View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(8), 1)
                }
                row.addView(spacer)
            }
        }

        return row
    }

    private fun createAICard(cardData: AICardData): ViewGroup {
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(85), // Compact height for professional look
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))
            }
            setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))

            // Modern card design with gradient and shadow
            background = createModernCardBackground(cardData.color)
            elevation = dpToPx(3).toFloat()

            isClickable = true
            isFocusable = true

            setOnClickListener {
                // Smooth press animation
                animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .alpha(0.9f)
                    .setDuration(120)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(120)
                            .withEndAction {
                                service.performHapticFeedback()
                                cardData.action.invoke()
                            }
                            .start()
                    }
                    .start()
            }
        }

        // Compact Lottie animation icon
        val iconView = createLottieIcon(cardData).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(32),
                dpToPx(32)
            ).apply {
                bottomMargin = dpToPx(4)
            }
        }
        card.addView(iconView)

        // Compact card title
        val titleView = TextView(service).apply {
            text = cardData.title
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(2)
            }
        }
        card.addView(titleView)

        // Compact card description
        val descriptionView = TextView(service).apply {
            text = cardData.description
            textSize = 9f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            maxLines = 1
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        card.addView(descriptionView)

        return card
    }
    
    private fun createLottieIcon(cardData: AICardData): View {
        return try {
            // Create a themed context to prevent AppCompat errors
            val themedContext = ContextThemeWrapper(service, android.R.style.Theme_Material_Light)
            LottieAnimationView(themedContext).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(32),
                    dpToPx(32),
                    Gravity.CENTER
                )
                
                try {
                    // Load Lottie animation from assets/kb_assets folder
                    setAnimation("kb_assets/${cardData.icon}")
                    
                    // Set failure listener to catch parsing errors
                    setFailureListener { throwable ->
                        android.util.Log.e("LottieIcon", "Failed to load ${cardData.icon}: ${throwable.message}")
                        // Don't crash, just show fallback
                        visibility = View.GONE
                    }
                    
                    repeatCount = LottieDrawable.INFINITE
                    repeatMode = LottieDrawable.RESTART
                    playAnimation()
                    
                    // Set animation speed
                    speed = 0.8f // Slightly slower for elegance
                    
                    // Add scaling effect for visual appeal
                    scaleX = 1.0f
                    scaleY = 1.0f
                    
                    // Add subtle floating animation - optimized for compact design
                    val floatAnimation = android.view.animation.TranslateAnimation(
                        0f, 0f, 0f, -2f
                    ).apply {
                        duration = 3000
                        repeatMode = android.view.animation.Animation.REVERSE
                        repeatCount = android.view.animation.Animation.INFINITE
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    }
                    startAnimation(floatAnimation)
                    
                } catch (e: Exception) {
                    android.util.Log.e("LottieIcon", "Error setting up Lottie animation", e)
                    // If Lottie animation fails, show a fallback icon
                    visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LottieIcon", "Failed to create LottieAnimationView", e)
            // Ultimate fallback - create a beautiful icon with emoji
            createFallbackIcon(cardData)
        }
    }
    
    private fun createFallbackIcon(cardData: AICardData): TextView {
        return TextView(service).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(32),
                dpToPx(32),
                Gravity.CENTER
            )
            gravity = Gravity.CENTER
            textSize = 16f
            text = when(cardData.icon) {
                "kb_email.lottie" -> "✉️"
                "kb_translate.lottie" -> "🌐"
                "kb_summary.lottie" -> "📝"
                "kb_poetry.lottie" -> "🎭"
                "kb_creative.lottie" -> "🎨"
                "kb_smart_ai.lottie" -> "🧠"
                else -> "✨"
            }
            setTextColor(Color.WHITE)
            
            // Beautiful circular background
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#40FFFFFF")) // Semi-transparent white
                setStroke(dpToPx(2), Color.WHITE)
            }
            
            // Add floating animation
            val floatAnimation = android.view.animation.TranslateAnimation(
                0f, 0f, 0f, -4f
            ).apply {
                duration = 2500
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            startAnimation(floatAnimation)
        }
    }
    
    private fun createAnimatedIcon(cardData: AICardData): TextView {
        val emojiMap = mapOf(
            "gmail.json" to "✉️",
            "translator.json" to "🌐", 
            "summarizer.json" to "📝",
            "pencil.json" to "📜",
            "paraphraser.json" to "📈",
            "grammar.json" to "✨"
        )
        
        return TextView(service).apply {
            text = emojiMap[cardData.icon] ?: "🤖"
            textSize = 28f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48),
                Gravity.CENTER
            )
            
            // Add beautiful floating animation with rotation
            val animationSet = android.view.animation.AnimationSet(true).apply {
                // Floating effect
                val floatAnimation = android.view.animation.TranslateAnimation(
                    0f, 0f, 0f, -8f
                ).apply {
                    duration = 2500
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = android.view.animation.Animation.INFINITE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                
                // Gentle rotation
                val rotateAnimation = android.view.animation.RotateAnimation(
                    -5f, 5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 3000
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = android.view.animation.Animation.INFINITE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                
                // Subtle scale pulse
                val scaleAnimation = android.view.animation.ScaleAnimation(
                    1.0f, 1.05f, 1.0f, 1.05f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 2000
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = android.view.animation.Animation.INFINITE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                
                addAnimation(floatAnimation)
                addAnimation(rotateAnimation)
                addAnimation(scaleAnimation)
            }
            
            startAnimation(animationSet)
        }
    }

    private fun createModernCardBackground(color: String): GradientDrawable {
        val primaryColor = Color.parseColor(color)
        
        // Create a sophisticated gradient with the primary color
        val lightColor = adjustColorBrightness(primaryColor, 1.2f)
        val mediumColor = primaryColor
        val darkColor = adjustColorBrightness(primaryColor, 0.8f)
        
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16f)
            
            // Beautiful gradient from light to dark
            colors = intArrayOf(lightColor, mediumColor, darkColor)
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            
            // Subtle border for definition
            setStroke(dpToPx(1), adjustColorBrightness(primaryColor, 1.3f))
        }
    }

    private fun createTransparentGradientCard(color: String): android.graphics.drawable.LayerDrawable {
        val primaryColor = android.graphics.Color.parseColor(color)
        val transparentColor = android.graphics.Color.argb(30, android.graphics.Color.red(primaryColor), android.graphics.Color.green(primaryColor), android.graphics.Color.blue(primaryColor))
        
        // Transparent background with slight tint
        val backgroundShape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat() // Smaller corner radius for smaller cards
            setColor(android.graphics.Color.argb(15, 255, 255, 255)) // Semi-transparent white
        }
        
        // Beautiful gradient border
        val borderShape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat() // Smaller corner radius
            
            // Create gradient colors for the border
            val lightColor = android.graphics.Color.argb(120, android.graphics.Color.red(primaryColor), android.graphics.Color.green(primaryColor), android.graphics.Color.blue(primaryColor))
            val mediumColor = android.graphics.Color.argb(80, android.graphics.Color.red(primaryColor), android.graphics.Color.green(primaryColor), android.graphics.Color.blue(primaryColor))
            val darkColor = android.graphics.Color.argb(150, android.graphics.Color.red(primaryColor), android.graphics.Color.green(primaryColor), android.graphics.Color.blue(primaryColor))
            
            colors = intArrayOf(lightColor, darkColor, mediumColor, lightColor)
            gradientType = android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            setStroke(dpToPx(1), primaryColor) // Thinner stroke
        }
        
        return android.graphics.drawable.LayerDrawable(arrayOf(backgroundShape, borderShape))
    }

    private fun createProfessionalCardBackground(color: String): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#FEFEFE"))
            cornerRadius = 16f
            setStroke(1, android.graphics.Color.parseColor("#E5E5EA"))
        }
    }

    private fun createIconGradientOutline(color: String): android.graphics.drawable.GradientDrawable {
        val primaryColor = android.graphics.Color.parseColor(color)
        val lightColor = lightenColor(primaryColor, 0.9f)
        
        return android.graphics.drawable.GradientDrawable().apply {
            colors = intArrayOf(lightColor, primaryColor, lightColor)
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
            cornerRadius = 28f
            setStroke(2, primaryColor)
        }
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val alpha = android.graphics.Color.alpha(color)
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        
        val newRed = (red + (255 - red) * factor).toInt().coerceAtMost(255)
        val newGreen = (green + (255 - green) * factor).toInt().coerceAtMost(255)
        val newBlue = (blue + (255 - blue) * factor).toInt().coerceAtMost(255)
        
        return android.graphics.Color.argb(alpha, newRed, newGreen, newBlue)
    }

    // Data class for AI card configuration
    data class AICardData(
        val icon: String, // Lottie animation file name
        val title: String,
        val description: String,
        val color: String,
        val action: () -> Unit
    )
    
    data class SelectionOption(
        val displayText: String,
        val value: String,
        val color: String
    )

    // AI Card Dialog Methods - Context-aware with professional selection UI
    private fun showEmailDialog() {
        // Get current text from input field
        val inputConnection = service.currentInputConnection
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        val textAfterCursor = inputConnection?.getTextAfterCursor(200, 0)?.toString() ?: ""
        
        val contextText = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            (textBeforeCursor + textAfterCursor).trim()
        }
        
        if (contextText.isEmpty()) {
            service.showToast("Please enter some text first")
            return
        }
        
        // Clear existing content and show modern email interface
        paraphraseContainer.removeAllViews()
        
        // Create main container with vibrant background
        val mainContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = createTransparentBackground()
        }

        // Add header with back button and title
        val headerContainer = createDialogHeader("✉️ Email Assistant", "Choose your email style") {
            showAICards() // Go back to AI cards
        }
        mainContainer.addView(headerContainer)

        // Create modern options container
        val optionsContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.WHITE)
                setStroke(2, if (service.isDarkMode) Color.parseColor("#404040") else Color.parseColor("#E0E0E0"))
            }
        }

        // Email style options with vibrant colors
        val emailOptions = listOf(
            SelectionOption("✉️ Formal", "Write a formal, professional email", "#4A90E2"),
            SelectionOption("💼 Business", "Write a business-style email", "#5BC0DE"), 
            SelectionOption("📝 Casual", "Write a casual, friendly email", "#5CB85C"),
            SelectionOption("🎓 Academic", "Write an academic-style email", "#F0AD4E"),
            SelectionOption("📋 Request", "Write a polite request email", "#D9534F"),
            SelectionOption("💡 Follow-up", "Write a follow-up email", "#9B59B6")
        )

        // Create modern option buttons in grid layout
        var currentRow: LinearLayout? = null
        emailOptions.forEachIndexed { index, option ->
            if (index % 2 == 0) {
                // Create new row
                currentRow = LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 0, 0, dpToPx(12))
                }
                optionsContainer.addView(currentRow)
            }
            
            // Create vibrant option button
            val optionButton = createModernOptionButton(option) {
                val prompt = "${option.value} based on this content: $contextText"
                currentPersona = "Email"
                currentOriginalText = prompt
                
                // Setup paraphrase mode first
                mainKeyboardView.visibility = View.GONE
                applyAIPanelBackground()
                paraphraseContainer.visibility = View.VISIBLE
                paraphraseContainer.removeAllViews()
                buildParaphraseShell()
                
                // Then make the API call
                service.generateParaphraseWithPersona(prompt, currentPersona)
            }
            
            currentRow?.addView(optionButton)
            
            // Add spacing between buttons
            if (index % 2 == 0 && index < emailOptions.size - 1) {
                val spacer = View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(12), 1)
                }
                currentRow?.addView(spacer)
            }
        }

        mainContainer.addView(optionsContainer)
        
        // Add to container and show
        paraphraseContainer.addView(mainContainer)
        paraphraseContainer.visibility = View.VISIBLE
        mainKeyboardView.visibility = View.GONE
    }

    private fun showTranslateDialog() {
        // Get current text from input field
        val inputConnection = service.currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        
        val textToTranslate = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            textBeforeCursor.trim().split("\\s+".toRegex()).takeLast(20).joinToString(" ")
        }
        
        if (textToTranslate.isEmpty()) {
            service.showToast("Please enter some text first")
            return
        }
        
        // Clear existing content and show modern translate interface
        paraphraseContainer.removeAllViews()
        
        // Create main container with vibrant background
        val mainContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = createTransparentBackground()
        }

        // Add header with back button and title
        val headerContainer = createDialogHeader("🌍 Translator", "Select target language") {
            showAICards() // Go back to AI cards
        }
        mainContainer.addView(headerContainer)

        // Create scrollable container for language options
        val scrollView = ScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val optionsContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.WHITE)
                setStroke(2, if (service.isDarkMode) Color.parseColor("#404040") else Color.parseColor("#E0E0E0"))
            }
        }

        // Language options with vibrant colors
        val languageOptions = listOf(
            SelectionOption("🇪🇸 Spanish", "Translate to Spanish", "#FF6B6B"),
            SelectionOption("🇫🇷 French", "Translate to French", "#4ECDC4"),
            SelectionOption("🇩🇪 German", "Translate to German", "#45B7D1"),
            SelectionOption("🇮🇹 Italian", "Translate to Italian", "#96CEB4"),
            SelectionOption("🇵🇹 Portuguese", "Translate to Portuguese", "#FFEAA7"),
            SelectionOption("🇷🇺 Russian", "Translate to Russian", "#DDA0DD"),
            SelectionOption("🇨🇳 Chinese", "Translate to Chinese", "#FF7675"),
            SelectionOption("🇯🇵 Japanese", "Translate to Japanese", "#FD79A8"),
            SelectionOption("🇰🇷 Korean", "Translate to Korean", "#FDCB6E"),
            SelectionOption("🇸🇦 Arabic", "Translate to Arabic", "#6C5CE7"),
            SelectionOption("🇮🇳 Hindi", "Translate to Hindi", "#A29BFE"),
            SelectionOption("🇳🇱 Dutch", "Translate to Dutch", "#74B9FF")
        )

        // Create modern option buttons in grid layout
        var currentRow: LinearLayout? = null
        languageOptions.forEachIndexed { index, option ->
            if (index % 2 == 0) {
                // Create new row
                currentRow = LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 0, 0, dpToPx(12))
                }
                optionsContainer.addView(currentRow)
            }
            
            // Create vibrant option button
            val optionButton = createModernOptionButton(option) {
                val prompt = "${option.value}: $textToTranslate"
                currentPersona = "Translator"
                currentOriginalText = prompt
                
                // Setup paraphrase mode first
                mainKeyboardView.visibility = View.GONE
                applyAIPanelBackground()
                paraphraseContainer.visibility = View.VISIBLE
                paraphraseContainer.removeAllViews()
                buildParaphraseShell()
                
                // Then make the API call
                service.generateParaphraseWithPersona(prompt, currentPersona)
            }
            
            currentRow?.addView(optionButton)
            
            // Add spacing between buttons
            if (index % 2 == 0 && index < languageOptions.size - 1) {
                val spacer = View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(12), 1)
                }
                currentRow?.addView(spacer)
            }
        }

        scrollView.addView(optionsContainer)
        mainContainer.addView(scrollView)
        
        // Add to container and show
        paraphraseContainer.addView(mainContainer)
        paraphraseContainer.visibility = View.VISIBLE
        mainKeyboardView.visibility = View.GONE
    }
    
    private fun showSummarizeDialog() {
        // Get current text from input field
        val inputConnection = service.currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        val textAfterCursor = inputConnection?.getTextAfterCursor(500, 0)?.toString() ?: ""
        
        val textToSummarize = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            (textBeforeCursor + textAfterCursor).trim()
        }
        
        if (textToSummarize.isEmpty()) {
            service.showToast("Please enter some text first")
            return
        }
        
        // Directly process summarization
        val prompt = if (textToSummarize.length > 50) {
            "Summarize this text into key points: $textToSummarize"
        } else {
            "Provide 3 key points about: $textToSummarize"
        }
        
        // Start AI generation and show in paraphrase view
        currentPersona = "Summarizer"
        currentOriginalText = prompt
        
        // Setup paraphrase mode first
        mainKeyboardView.visibility = View.GONE
        applyAIPanelBackground()
        paraphraseContainer.visibility = View.VISIBLE
        paraphraseContainer.removeAllViews()
        buildParaphraseShell()
        
        // Then make the API call
        service.generateParaphraseWithPersona(prompt, currentPersona)
    }

    private fun showPoetryDialog() {
        // Get current text from input field for theme
        val inputConnection = service.currentInputConnection
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(500, 0)?.toString() ?: ""
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        
        val poetryTheme = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            textBeforeCursor.trim()
        }
        
        if (poetryTheme.isEmpty()) {
            service.showToast("Please enter some text first")
            return
        }
        
        // Clear existing content and show modern poetry interface
        paraphraseContainer.removeAllViews()
        
        // Create main container with vibrant background
        val mainContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = createTransparentBackground()
        }

        // Add header with back button and title
        val headerContainer = createDialogHeader("🎭 Poetry Studio", "Choose your poetic style") {
            showAICards() // Go back to AI cards
        }
        mainContainer.addView(headerContainer)

        // Create options container
        val optionsContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.WHITE)
                setStroke(2, if (service.isDarkMode) Color.parseColor("#404040") else Color.parseColor("#E0E0E0"))
            }
        }

        // Poetry style options with vibrant colors
        val poetryOptions = listOf(
            SelectionOption("🌹 Romantic", "Write a romantic poem", "#E91E63"),
            SelectionOption("🌿 Nature", "Write a nature-inspired poem", "#4CAF50"),
            SelectionOption("✨ Inspirational", "Write an inspirational poem", "#FF9800"),
            SelectionOption("🎭 Dramatic", "Write a dramatic poem", "#9C27B0"),
            SelectionOption("😄 Humorous", "Write a humorous poem", "#FFC107"),
            SelectionOption("🌙 Mystical", "Write a mystical poem", "#3F51B5"),
            SelectionOption("💭 Reflective", "Write a reflective poem", "#607D8B"),
            SelectionOption("🔥 Passionate", "Write a passionate poem", "#F44336")
        )

        // Create modern option buttons in grid layout
        var currentRow: LinearLayout? = null
        poetryOptions.forEachIndexed { index, option ->
            if (index % 2 == 0) {
                // Create new row
                currentRow = LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 0, 0, dpToPx(12))
                }
                optionsContainer.addView(currentRow)
            }
            
            // Create vibrant option button
            val optionButton = createModernOptionButton(option) {
                val prompt = "${option.value} about: $poetryTheme"
                currentPersona = "Poet"
                currentOriginalText = prompt
                
                // Setup paraphrase mode first
                mainKeyboardView.visibility = View.GONE
                applyAIPanelBackground()
                paraphraseContainer.visibility = View.VISIBLE
                paraphraseContainer.removeAllViews()
                buildParaphraseShell()
                
                // Then make the API call
                service.generateParaphraseWithPersona(prompt, currentPersona)
            }
            
            currentRow?.addView(optionButton)
            
            // Add spacing between buttons
            if (index % 2 == 0 && index < poetryOptions.size - 1) {
                val spacer = View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(12), 1)
                }
                currentRow?.addView(spacer)
            }
        }

        mainContainer.addView(optionsContainer)
        
        // Add to container and show
        paraphraseContainer.addView(mainContainer)
        paraphraseContainer.visibility = View.VISIBLE
        mainKeyboardView.visibility = View.GONE
    }
    
    private fun showCreativeDialog() {
        // Get current text from input field
        val inputConnection = service.currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        
        val textToProcess = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            textBeforeCursor.trim()
        }
        
        if (textToProcess.isEmpty()) {
            service.showToast("Please enter some text first")
            return
        }
        
        // Show creativity level slider similar to Smart AI
        showCreativitySlider(textToProcess)
    }
    
    private fun showCreativitySlider(textToProcess: String) {
        // Clear existing content and show creativity slider interface
        paraphraseContainer.removeAllViews()
        
        // Create main container with nice background
        val mainContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = createTransparentBackground()
        }

        // Add header with back button and title
        val headerContainer = createDialogHeader("🎨 Creative Writing", "Adjust creativity level") {
            showAICards() // Go back to AI cards
        }
        mainContainer.addView(headerContainer)

        // Create slider container
        val sliderContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.WHITE)
            }
        }

        // Slider title
        val sliderTitle = TextView(service).apply {
            text = "Creativity Level"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
        }
        sliderContainer.addView(sliderTitle)

        // Create custom slider with labels
        val sliderLayout = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        // Conservative label
        val conservativeLabel = TextView(service).apply {
            text = "📝 Practical"
            textSize = 14f
            setTextColor(if (service.isDarkMode) Color.parseColor("#8E8E93") else Color.parseColor("#6D6D70"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dpToPx(12) }
        }
        sliderLayout.addView(conservativeLabel)

        // Slider (using SeekBar)
        val slider = android.widget.SeekBar(service).apply {
            max = 100
            progress = 50 // Default to middle
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        sliderLayout.addView(slider)

        // Creative label
        val creativeLabel = TextView(service).apply {
            text = "🚀 Wild"
            textSize = 14f
            setTextColor(if (service.isDarkMode) Color.parseColor("#8E8E93") else Color.parseColor("#6D6D70"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dpToPx(12) }
        }
        sliderLayout.addView(creativeLabel)

        sliderContainer.addView(sliderLayout)

        // Current creativity indicator
        val creativityIndicator = TextView(service).apply {
            text = "Balanced Creativity"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#5AC8FA"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        sliderContainer.addView(creativityIndicator)

        // Update creativity indicator based on slider position
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                creativityIndicator.text = when {
                    progress < 25 -> "Practical & Grounded"
                    progress < 50 -> "Moderately Creative"
                    progress < 75 -> "Highly Creative"
                    else -> "Wildly Imaginative"
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        mainContainer.addView(sliderContainer)

        // Generate button
        val generateButton = TextView(service).apply {
            text = "🎨 Generate Creative Response"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(24) }
            
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f)
                setColor(Color.parseColor("#5AC8FA"))
            }
            
            setOnClickListener {
                val creativity = slider.progress
                val prompt = createCreativePrompt(textToProcess, creativity)
                currentPersona = "Creative"
                currentOriginalText = prompt
                
                // Setup paraphrase mode first
                mainKeyboardView.visibility = View.GONE
                applyAIPanelBackground()
                paraphraseContainer.visibility = View.VISIBLE
                paraphraseContainer.removeAllViews()
                buildParaphraseShell()
                
                // Then make the API call
                service.generateParaphraseWithPersona(prompt, currentPersona)
            }
        }
        mainContainer.addView(generateButton)

        paraphraseContainer.addView(mainContainer)
        
        // Show the paraphrase mode
        isParaphrasingMode = true
        mainKeyboardView.visibility = View.GONE
        paraphraseContainer.visibility = View.VISIBLE
    }

    private fun createCreativePrompt(text: String, creativity: Int): String {
        return when {
            creativity < 25 -> {
                "Rewrite this text in a practical, straightforward manner with useful information: $text"
            }
            creativity < 50 -> {
                "Rewrite this text with moderate creativity, adding some interesting elements while staying grounded: $text"
            }
            creativity < 75 -> {
                "Rewrite this text with high creativity, using vivid language, metaphors, and engaging storytelling: $text"
            }
            else -> {
                "Rewrite this text with wild creativity and imagination, using bold metaphors, unexpected comparisons, and extraordinary storytelling: $text"
            }
        }
    }
    
    private fun showExpandDialog() {
        // Get current text from input field to expand
        val inputConnection = service.currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        
        val textToExpand = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            textBeforeCursor.trim().split("\\s+".toRegex()).takeLast(15).joinToString(" ")
        }
        
        if (textToExpand.isEmpty()) {
            service.showToast("Please enter some text first")
            return
        }
        
        // Directly process expansion
        val prompt = if (textToExpand.length > 10) {
            "Expand and elaborate on this idea with more details: $textToExpand"
        } else {
            "Expand and elaborate on this idea: $textToExpand"
        }
        
        // Start AI generation and show in paraphrase view
        currentPersona = "Expander"
        currentOriginalText = prompt
        
        // Setup paraphrase mode first
        mainKeyboardView.visibility = View.GONE
        applyAIPanelBackground()
        paraphraseContainer.visibility = View.VISIBLE
        paraphraseContainer.removeAllViews()
        buildParaphraseShell()
        
        // Then make the API call
        service.generateParaphraseWithPersona(prompt, currentPersona)
    }

    private fun showImproveDialog() {
        // Get current text from input field to improve
        val inputConnection = service.currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        
        val textToImprove = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            textBeforeCursor.trim()
        }
        
        if (textToImprove.isEmpty()) {
            service.showToast("Please enter some text first")
            return
        }
        
        // Directly process improvement
        val prompt = if (textToImprove.length > 10) {
            "Improve and rewrite this text to make it more professional and clear: $textToImprove"
        } else {
            "Improve and enhance this text: $textToImprove"
        }
        
        // Start AI generation and show in paraphrase view
        currentPersona = "Improver"
        currentOriginalText = prompt
        
        // Setup paraphrase mode first
        mainKeyboardView.visibility = View.GONE
        applyAIPanelBackground()
        paraphraseContainer.visibility = View.VISIBLE
        paraphraseContainer.removeAllViews()
        buildParaphraseShell()
        
        // Then make the API call
        service.generateParaphraseWithPersona(prompt, currentPersona)
    }
    
    // Scrollable selection grid for languages
    private fun showScrollableSelectionGrid(title: String, subtitle: String, options: List<SelectionOption>, onSelect: (String) -> Unit) {
        // Clear existing content
        paraphraseContainer.removeAllViews()
        
        // Create main container
        val mainContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = createTransparentBackground()
        }

        // Add header with back button
        val headerContainer = createDialogHeader(title, subtitle) {
            showAICards() // Go back to AI cards
        }
        mainContainer.addView(headerContainer)

        // Create scrollable content
        val scrollView = ScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val gridContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add options in pairs for better layout
        for (i in options.indices step 2) {
            val rowContainer = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(12) }
                weightSum = 2f
            }

            // First option
            val option1 = options[i]
            val button1 = createOptionButton(option1) {
                onSelect(option1.value)
            }
            rowContainer.addView(button1)

            // Second option (if exists)
            if (i + 1 < options.size) {
                val option2 = options[i + 1]
                val button2 = createOptionButton(option2) {
                    onSelect(option2.value)
                }
                rowContainer.addView(button2)
            } else {
                // Add empty space if odd number of options
                val emptySpace = View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { leftMargin = dpToPx(6) }
                }
                rowContainer.addView(emptySpace)
            }

            gridContainer.addView(rowContainer)
        }

        scrollView.addView(gridContainer)
        mainContainer.addView(scrollView)
        paraphraseContainer.addView(mainContainer)
        
        // Show the selection interface
        isParaphrasingMode = true
        mainKeyboardView.visibility = View.GONE
        paraphraseContainer.visibility = View.VISIBLE
    }
    
    // Helper function to create option buttons for scrollable grid
    private fun createOptionButton(option: SelectionOption, onSelect: () -> Unit): TextView {
        return TextView(service).apply {
            text = option.displayText
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { 
                rightMargin = dpToPx(6)
                leftMargin = dpToPx(6)
            }
            
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f)
                setColor(Color.parseColor(option.color))
            }
            
            setOnClickListener { onSelect() }
        }
    }

    private fun showSmartAIDialog() {
        // Clear existing content and show Smart AI interface with human-AI slider
        paraphraseContainer.removeAllViews()
        
        // Create main container with nice background
        val mainContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = createTransparentBackground()
        }

        // Add header with back button and title
        val headerContainer = createDialogHeader("🧠 Smart AI", "Adjust response style") {
            showAICards() // Go back to AI cards
        }
        mainContainer.addView(headerContainer)

        // Get current text context
        val inputConnection = service.currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)?.toString() ?: ""
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        
        val textToProcess = if (selectedText.isNotEmpty()) {
            selectedText
        } else {
            textBeforeCursor.trim().takeLast(200) // Take last 200 chars for context
        }

        // Create slider container
        val sliderContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7"))
            }
        }

        // Slider title
        val sliderTitle = TextView(service).apply {
            text = "Response Style"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
        }
        sliderContainer.addView(sliderTitle)

        // Create custom slider with labels
        val sliderLayout = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        // Human label
        val humanLabel = TextView(service).apply {
            text = "👤 Human"
            textSize = 14f
            setTextColor(if (service.isDarkMode) Color.parseColor("#8E8E93") else Color.parseColor("#6D6D70"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dpToPx(12) }
        }
        sliderLayout.addView(humanLabel)

        // Slider (using SeekBar)
        val slider = android.widget.SeekBar(service).apply {
            max = 100
            progress = 50 // Default to middle
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        sliderLayout.addView(slider)

        // AI label
        val aiLabel = TextView(service).apply {
            text = "🤖 AI"
            textSize = 14f
            setTextColor(if (service.isDarkMode) Color.parseColor("#8E8E93") else Color.parseColor("#6D6D70"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dpToPx(12) }
        }
        sliderLayout.addView(aiLabel)

        sliderContainer.addView(sliderLayout)

        // Current style indicator
        val styleIndicator = TextView(service).apply {
            text = "Balanced Style"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF3B30"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        sliderContainer.addView(styleIndicator)

        // Update style indicator based on slider position
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                styleIndicator.text = when {
                    progress < 25 -> "Very Human-like"
                    progress < 50 -> "Human-leaning"
                    progress < 75 -> "Balanced Style"
                    else -> "AI-powered"
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        mainContainer.addView(sliderContainer)

        // Generate button
        val generateButton = TextView(service).apply {
            text = "✨ Generate Smart Response"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(24) }
            
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f)
                setColor(Color.parseColor("#FF3B30"))
            }
            
            setOnClickListener {
                val humanness = slider.progress
                val prompt = createSmartAIPrompt(textToProcess, humanness)
                currentPersona = "Smart AI"
                currentOriginalText = prompt
                
                // Setup paraphrase mode first
                mainKeyboardView.visibility = View.GONE
                applyAIPanelBackground()
                paraphraseContainer.visibility = View.VISIBLE
                paraphraseContainer.removeAllViews()
                buildParaphraseShell()
                
                // Then make the API call
                service.generateParaphraseWithPersona(prompt, currentPersona)
            }
        }
        mainContainer.addView(generateButton)

        paraphraseContainer.addView(mainContainer)
        
        // Show the paraphrase mode
        isParaphrasingMode = true
        mainKeyboardView.visibility = View.GONE
        paraphraseContainer.visibility = View.VISIBLE
    }

    private fun createSmartAIPrompt(text: String, humanness: Int): String {
        val baseText = if (text.isNotEmpty() && text.length > 10) {
            text
        } else {
            "Hello, I hope you're having a great day!"
        }

        return when {
            humanness < 25 -> {
                "Rewrite this text to sound very natural, casual, and human-like with personal touches, contractions, and conversational tone: $baseText"
            }
            humanness < 50 -> {
                "Rewrite this text to sound natural and human while maintaining good structure: $baseText"
            }
            humanness < 75 -> {
                "Rewrite this text with a balanced tone that's both professional and personable: $baseText"
            }
            else -> {
                "Rewrite this text to be highly polished, professional, and structured while maintaining clarity: $baseText"
            }
        }
    }

    // Helper method to create consistent dialog headers with back buttons
    private fun createDialogHeader(title: String, subtitle: String, onBackClick: () -> Unit): LinearLayout {
        val headerContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dpToPx(20))
        }
        
        // Back button row
        val backButtonRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
        }
        
        val backButton = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(12), dpToPx(6))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(20f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7"))
            }
            isClickable = true
            isFocusable = true
            
            val backIcon = TextView(service).apply {
                text = "←"
                textSize = 16f
                setTextColor(getThemeColorForAIPanel())
                gravity = Gravity.CENTER
            }
            addView(backIcon)
            
            val backText = TextView(service).apply {
                text = "Back"
                textSize = 14f
                setTextColor(getThemeColorForAIPanel())
                setPadding(dpToPx(6), 0, 0, 0)
            }
            addView(backText)
            
            setOnClickListener { 
                service.performHapticFeedback()
                onBackClick()
            }
        }
        
        backButtonRow.addView(backButton)
        // Add spacer to push back button to left
        backButtonRow.addView(View(service).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        
        headerContainer.addView(backButtonRow)
        
        // Title
        val titleView = TextView(service).apply {
            text = title
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.parseColor("#1C1C1E"))
            gravity = Gravity.CENTER
        }
        headerContainer.addView(titleView)
        
        // Subtitle
        val subtitleView = TextView(service).apply {
            text = subtitle
            textSize = 14f
            setTextColor(if (service.isDarkMode) Color.parseColor("#8E8E93") else Color.parseColor("#6D6D70"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, 0)
        }
        headerContainer.addView(subtitleView)
        
        return headerContainer
    }

    // Professional selection grid interface
    private fun showSelectionGrid(title: String, subtitle: String, options: List<SelectionOption>, onSelect: (String) -> Unit) {
        // Clear existing content and show selection interface
        paraphraseContainer.removeAllViews()
        
        // Create main container
        val mainContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = createTransparentBackground()
        }
        
        // Header section with back button
        val headerContainer = createDialogHeader(title, subtitle) {
            showAICards() // Go back to AI cards
        }
        mainContainer.addView(headerContainer)
        
        // Selection grid
        val gridContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Create rows of 2 options each for professional layout
        var currentRow: LinearLayout? = null
        options.forEachIndexed { index, option ->
            if (index % 2 == 0) {
                // Create new row
                currentRow = LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(12))
                }
                gridContainer.addView(currentRow)
            }
            
            // Create option card
            val optionCard = createSelectionCard(option) {
                onSelect(option.value)
            }
            
            // Add to current row
            currentRow?.addView(optionCard)
            
            // Add spacing between cards in the same row
            if (index % 2 == 0 && index < options.size - 1) {
                val spacer = View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(12), 1)
                }
                currentRow?.addView(spacer)
            }
        }
        
        mainContainer.addView(gridContainer)
        
        // Add to container and show
        paraphraseContainer.addView(mainContainer)
        paraphraseContainer.visibility = View.VISIBLE
        mainKeyboardView.visibility = View.GONE
    }
    
    // Create modern vibrant option button similar to Creative/Smart AI styling
    private fun createModernOptionButton(option: SelectionOption, onClick: () -> Unit): View {
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            
            // Create vibrant gradient background like Creative/Smart AI cards
            val baseColor = Color.parseColor(option.color)
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                
                // Create beautiful gradient
                val lightColor = adjustColorBrightness(baseColor, 1.1f)
                val darkColor = adjustColorBrightness(baseColor, 0.9f)
                colors = intArrayOf(lightColor, baseColor, darkColor)
                orientation = GradientDrawable.Orientation.TL_BR
                
                // Add glowing border effect
                setStroke(dpToPx(2), adjustColorBrightness(baseColor, 1.2f))
            }
            
            isClickable = true
            isFocusable = true
            
            // Add dynamic press effects
            val pressedBg = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(adjustColorBrightness(baseColor, 0.8f))
                setStroke(dpToPx(3), adjustColorBrightness(baseColor, 1.3f))
            }
            
            val normalBg = background
            val stateListDrawable = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
                addState(intArrayOf(), normalBg)
            }
            background = stateListDrawable
            
            // Option text with improved styling
            val textView = TextView(service).apply {
                text = option.displayText
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                setShadowLayer(2f, 1f, 1f, Color.argb(80, 0, 0, 0)) // Text shadow for depth
            }
            addView(textView)
            
            setOnClickListener { 
                // Enhanced selection animation
                animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(150)
                    .withEndAction {
                        animate()
                            .scaleX(1.05f)
                            .scaleY(1.05f)
                            .setDuration(100)
                            .withEndAction {
                                animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(100)
                                    .withEndAction {
                                        onClick()
                                    }
                            }
                    }
            }
        }
    }

    // Create individual selection card
    private fun createSelectionCard(option: SelectionOption, onClick: () -> Unit): View {
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(20), dpToPx(16), dpToPx(20))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            
            // Create professional card background with subtle gradient
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                
                // Parse the color and create subtle gradient
                val baseColor = Color.parseColor(option.color)
                val lightColor = adjustColorBrightness(baseColor, 0.95f)
                val darkColor = adjustColorBrightness(baseColor, 0.85f)
                
                colors = intArrayOf(lightColor, darkColor)
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                
                // Add subtle border
                setStroke(2, adjustColorBrightness(baseColor, 0.7f))
            }
            
            isClickable = true
            isFocusable = true
            
            // Add press effect
            val pressedBg = GradientDrawable().apply {
                cornerRadius = dpToPx(16f)
                setColor(adjustColorBrightness(Color.parseColor(option.color), 0.8f))
                setStroke(3, Color.parseColor(option.color))
            }
            
            val normalBg = background
            val stateListDrawable = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
                addState(intArrayOf(), normalBg)
            }
            background = stateListDrawable
            
            // Option text
            val textView = TextView(service).apply {
                text = option.displayText
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
            }
            addView(textView)
            
            setOnClickListener { 
                // Add selection animation
                animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                        onClick()
                    }
                }
            }
        }
    }
    
    // Helper method to adjust color brightness
    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val red = ((Color.red(color) * factor).toInt()).coerceIn(0, 255)
        val green = ((Color.green(color) * factor).toInt()).coerceIn(0, 255)
        val blue = ((Color.blue(color) * factor).toInt()).coerceIn(0, 255)
        return Color.rgb(red, green, blue)
    }
    
    // Create professional button
    private fun createProfessionalButton(text: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(service).apply {
            this.text = text
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(16), 0, 0)
            }
            
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12f)
                setColor(color)
            }
            
            isClickable = true
            isFocusable = true
            
            setOnClickListener { onClick() }
        }
    }
    
    // Inline input method for AI card user inputs
    private fun showInlineInput(title: String, placeholder: String, onGenerate: (String) -> Unit) {
        // Clear existing content and show input interface
        paraphraseContainer.removeAllViews()
        
        // Create input interface
        val inputContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            
            // Add simple background
            background = createTransparentBackground()
        }
        
        // Title
        val titleView = TextView(service).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
            setPadding(0, 0, 0, dpToPx(8))
        }
        inputContainer.addView(titleView)
        
        // Input field
        val editText = EditText(service).apply {
            hint = placeholder
            textSize = 14f
            maxLines = 2
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
            setHintTextColor(if (service.isDarkMode) Color.LTGRAY else Color.GRAY)
            
            // Style the input field
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8f)
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.parseColor("#F5F5F5"))
                setStroke(2, if (service.isDarkMode) Color.parseColor("#404040") else Color.parseColor("#E0E0E0"))
            }
        }
        inputContainer.addView(editText)
        
        // Buttons container
        val buttonsContainer = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12), 0, 0)
        }
        
        // Cancel button
        val cancelButton = createStyledButton("Cancel", Color.GRAY) {
            // Hide input and show AI cards again
            showAICards()
        }
        buttonsContainer.addView(cancelButton)
        
        // Add space between buttons
        buttonsContainer.addView(View(service).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8), 1)
        })
        
        // Generate button
        val generateButton = createStyledButton("Generate", getThemeColorForAIPanel()) {
            val input = editText.text.toString().trim()
            if (input.isNotEmpty()) {
                onGenerate(input)
            } else {
                // Show default action if no input
                onGenerate("general")
            }
        }
        buttonsContainer.addView(generateButton)
        
        inputContainer.addView(buttonsContainer)
        paraphraseContainer.addView(inputContainer)
        
        // Show the paraphrase container
        paraphraseContainer.visibility = View.VISIBLE
        mainKeyboardView.visibility = View.GONE
        
        // Focus the input field
        editText.requestFocus()
    }
    
    // Helper method to create styled buttons
    private fun createStyledButton(text: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(service).apply {
            this.text = text
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6f)
                setColor(color)
            }
            
            isClickable = true
            isFocusable = true
            
            setOnClickListener { onClick() }
        }
    }
    
    // Helper method to create transparent background
    private fun createTransparentBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(12f)
            setColor(if (service.isDarkMode) Color.parseColor("#CC2C2C2E") else Color.parseColor("#CCF5F5F5"))
            setStroke(2, if (service.isDarkMode) Color.parseColor("#404040") else Color.parseColor("#E0E0E0"))
        }
    }
}