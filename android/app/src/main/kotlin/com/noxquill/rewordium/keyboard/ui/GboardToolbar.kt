package com.noxquill.rewordium.keyboard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.noxquill.rewordium.keyboard.RewordiumAIKeyboardService
import com.noxquill.rewordium.keyboard.util.KeyboardConstants

/**
 * FlorisBoard-inspired Gboard-style Toolbar
 * 
 * This class creates a professional, modern toolbar at the top of the keyboard
 * Similar to FlorisBoard's smartbar and Gboard's suggestion bar
 * 
 * Features:
 * - Clean, minimal design with iOS-inspired aesthetics
 * - Smooth animations and transitions
 * - Adaptive colors based on dark/light mode
 * - Suggestion chips with intelligent spacing
 * - Quick action buttons (undo, redo, settings)
 * - Clipboard shortcuts
 */
class GboardToolbar(
    private val context: Context,
    private val service: RewordiumAIKeyboardService
) {
    private var toolbarView: LinearLayout? = null
    private var suggestionContainer: LinearLayout? = null
    private var actionContainer: LinearLayout? = null
    
    // Suggestion views
    private val suggestionChips = mutableListOf<TextView>()
    
    // Quick action views  
    private var undoButton: TextView? = null
    private var redoButton: TextView? = null
    private var clipboardButton: TextView? = null
    
    companion object {
        private const val TAG = "GboardToolbar"
        private const val MAX_SUGGESTIONS = 3
        private const val TOOLBAR_HEIGHT_DP = 44
        private const val CHIP_PADDING_HORIZONTAL_DP = 16
        private const val CHIP_PADDING_VERTICAL_DP = 8
        private const val CHIP_MARGIN_DP = 6
        private const val CORNER_RADIUS_DP = 18f
    }
    
    /**
     * Creates the toolbar view with FlorisBoard-inspired design
     */
    fun createToolbar(isDarkMode: Boolean): View {
        Log.d(TAG, "🎨 Creating Gboard-style toolbar with FlorisBoard design")
        
        toolbarView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(TOOLBAR_HEIGHT_DP)
            )
            
            // Modern background with subtle elevation
            background = createToolbarBackground(isDarkMode)
            elevation = dpToPx(2).toFloat()
            
            setPadding(dpToPx(8))
            
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // Create suggestion container (takes most space)
        suggestionContainer = createSuggestionContainer(isDarkMode)
        toolbarView?.addView(suggestionContainer, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f
        ))
        
        // Create action container (quick actions on right)
        actionContainer = createActionContainer(isDarkMode)
        toolbarView?.addView(actionContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        
        Log.d(TAG, "✅ Gboard toolbar created successfully")
        return toolbarView!!
    }
    
    /**
     * Creates the suggestion container with chip-style suggestions
     */
    private fun createSuggestionContainer(isDarkMode: Boolean): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL
            
            // Create suggestion chips
            for (i in 0 until MAX_SUGGESTIONS) {
                val chip = createSuggestionChip(i, isDarkMode)
                suggestionChips.add(chip)
                addView(chip)
            }
        }
    }
    
    /**
     * Creates a single suggestion chip with modern design
     */
    private fun createSuggestionChip(index: Int, isDarkMode: Boolean): TextView {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(CHIP_MARGIN_DP)
            }
            
            // Styling
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setTextColor(getChipTextColor(index, isDarkMode))
            
            // Padding
            setPadding(
                dpToPx(CHIP_PADDING_HORIZONTAL_DP),
                dpToPx(CHIP_PADDING_VERTICAL_DP),
                dpToPx(CHIP_PADDING_HORIZONTAL_DP),
                dpToPx(CHIP_PADDING_VERTICAL_DP)
            )
            
            // Background
            background = createChipBackground(index, isDarkMode)
            
            // Click handling
            isClickable = true
            isFocusable = true
            
            setOnClickListener {
                handleSuggestionClick(index)
            }
            
            // Touch feedback
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start()
                    }
                }
                false
            }
            
            // Initially hidden
            visibility = View.GONE
        }
    }
    
    /**
     * Creates the action container with quick action buttons
     */
    private fun createActionContainer(isDarkMode: Boolean): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL
            
            // Clipboard button
            clipboardButton = createActionButton("📋", isDarkMode) {
                service.handleClipboardButton()
            }
            addView(clipboardButton)
            
            // Settings button
            val settingsButton = createActionButton("⚙️", isDarkMode) {
                service.handleKeyboardSettingsButton()
            }
            addView(settingsButton)
        }
    }
    
    /**
     * Creates a quick action button
     */
    private fun createActionButton(icon: String, isDarkMode: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = icon
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                marginStart = dpToPx(4)
            }
            
            gravity = Gravity.CENTER
            
            background = createActionButtonBackground(isDarkMode)
            
            isClickable = true
            isFocusable = true
            
            setOnClickListener {
                service.performHapticFeedback()
                onClick()
            }
            
            // Touch feedback
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.7f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start()
                    }
                }
                false
            }
        }
    }
    
    /**
     * Updates suggestions in the toolbar
     */
    fun updateSuggestions(suggestions: List<String>) {
        Log.d(TAG, "📝 Updating toolbar suggestions: ${suggestions.size} items")
        
        for (i in 0 until MAX_SUGGESTIONS) {
            val chip = suggestionChips.getOrNull(i)
            val suggestion = suggestions.getOrNull(i)
            
            if (chip != null) {
                if (suggestion != null && suggestion.isNotBlank()) {
                    chip.text = suggestion
                    chip.visibility = View.VISIBLE
                    
                    // Smooth fade-in animation
                    chip.alpha = 0f
                    chip.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                } else {
                    chip.visibility = View.GONE
                }
            }
        }
    }
    
    /**
     * Handles suggestion chip click
     */
    private fun handleSuggestionClick(index: Int) {
        service.performHapticFeedback()
        
        val suggestion = suggestionChips.getOrNull(index)?.text?.toString()
        if (!suggestion.isNullOrBlank()) {
            service.onSuggestionTapped(suggestion)
            Log.d(TAG, "✅ Suggestion applied: $suggestion")
        }
    }
    
    /**
     * Creates toolbar background with subtle gradient
     */
    private fun createToolbarBackground(isDarkMode: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            
            if (isDarkMode) {
                colors = intArrayOf(
                    Color.argb(255, 32, 32, 34),
                    Color.argb(255, 28, 28, 30)
                )
            } else {
                colors = intArrayOf(
                    Color.argb(255, 248, 248, 250),
                    Color.argb(255, 242, 242, 247)
                )
            }
            
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
    }
    
    /**
     * Creates chip background
     */
    private fun createChipBackground(index: Int, isDarkMode: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(CORNER_RADIUS_DP).toFloat()
            
            // First suggestion gets accent color, others get subtle background
            if (index == 0) {
                setColor(if (isDarkMode) 
                    Color.argb(255, 0, 122, 255) else 
                    Color.argb(255, 0, 122, 255)
                )
            } else {
                setColor(if (isDarkMode) 
                    Color.argb(60, 255, 255, 255) else 
                    Color.argb(80, 0, 0, 0)
                )
            }
        }
    }
    
    /**
     * Creates action button background
     */
    private fun createActionButtonBackground(isDarkMode: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isDarkMode) 
                Color.argb(40, 255, 255, 255) else 
                Color.argb(60, 0, 0, 0)
            )
        }
    }
    
    /**
     * Gets chip text color based on index and theme
     */
    private fun getChipTextColor(index: Int, isDarkMode: Boolean): Int {
        return if (index == 0) {
            // First suggestion always white for contrast
            Color.WHITE
        } else {
            if (isDarkMode) Color.WHITE else Color.BLACK
        }
    }
    
    /**
     * Converts dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Converts dp (float) to pixels
     */
    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Applies theme to the toolbar
     */
    fun applyTheme(isDarkMode: Boolean, themeColor: String) {
        Log.d(TAG, "🎨 Applying theme to Gboard toolbar")
        
        // Update toolbar background
        toolbarView?.background = createToolbarBackground(isDarkMode)
        
        // Update suggestion chips
        suggestionChips.forEachIndexed { index, chip ->
            chip.background = createChipBackground(index, isDarkMode)
            chip.setTextColor(getChipTextColor(index, isDarkMode))
        }
        
        // Update action buttons
        clipboardButton?.background = createActionButtonBackground(isDarkMode)
    }
    
    /**
     * Shows the toolbar with animation
     */
    fun show() {
        toolbarView?.visibility = View.VISIBLE
        toolbarView?.alpha = 0f
        toolbarView?.translationY = -dpToPx(TOOLBAR_HEIGHT_DP).toFloat()
        
        toolbarView?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(250)
            ?.start()
    }
    
    /**
     * Hides the toolbar with animation
     */
    fun hide() {
        toolbarView?.animate()
            ?.alpha(0f)
            ?.translationY(-dpToPx(TOOLBAR_HEIGHT_DP).toFloat())
            ?.setDuration(200)
            ?.withEndAction {
                toolbarView?.visibility = View.GONE
            }
            ?.start()
    }
    
    /**
     * Gets the toolbar view
     */
    fun getView(): View? = toolbarView
}
