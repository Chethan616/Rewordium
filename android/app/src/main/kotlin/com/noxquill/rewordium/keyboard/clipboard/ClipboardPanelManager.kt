package com.noxquill.rewordium.keyboard.clipboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noxquill.rewordium.R
import com.noxquill.rewordium.keyboard.RewordiumAIKeyboardService
import com.noxquill.rewordium.keyboard.util.KeyboardConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the clipboard panel interface
 */
class ClipboardPanelManager(
    private val service: RewordiumAIKeyboardService,
    private val rootView: FrameLayout
) {
    private var clipboardPanelView: View? = null
    private var clipboardManager: ClipboardManager = service.provideClipboardHistoryManager()
    private var clipboardAdapter: ClipboardAdapter? = null
    private var isShowing = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var closeButton: TextView  // Changed from ImageButton to TextView
    private lateinit var emptyView: LinearLayout
    private lateinit var clearButton: TextView
    private lateinit var filterAllButton: TextView
    private lateinit var filterFavoritesButton: TextView
    
    // Current filter state
    private var showingOnlyFavorites = false
    
    /**
     * Shows the clipboard panel
     */
    fun show() {
        Log.d(KeyboardConstants.TAG, "📋 ClipboardPanelManager.show() called - isShowing: $isShowing")
        
        if (isShowing) {
            Log.d(KeyboardConstants.TAG, "📋 Panel already showing, ignoring show() call")
            return
        }
        
        try {
            // Initialize if needed
            if (clipboardPanelView == null) {
                Log.d(KeyboardConstants.TAG, "📋 Initializing clipboard panel for first time")
                initializeClipboardPanel()
            }
            
            // Make sure we have the latest clipboard items
            updateClipboardList()
            
            // Animate showing the panel
            clipboardPanelView?.let { panel ->
                Log.d(KeyboardConstants.TAG, "📋 Showing clipboard panel with animation")
                
                panel.visibility = View.VISIBLE
                panel.alpha = 0f  // Start transparent
                panel.translationY = 0f  // Keep in position
                
                // Fade in animation for overlay effect
                ObjectAnimator.ofFloat(panel, "alpha", 1f).apply {
                    duration = 250
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            Log.d(KeyboardConstants.TAG, "📋 Clipboard panel fade-in animation completed")
                        }
                    })
                    start()
                }
                
                isShowing = true
                Log.d(KeyboardConstants.TAG, "📋 Clipboard panel is now showing")
            } ?: run {
                Log.e(KeyboardConstants.TAG, "❌ clipboardPanelView is null after initialization!")
            }
            
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error showing clipboard panel: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Hides the clipboard panel
     */
    fun hide() {
        if (!isShowing) return
        
        clipboardPanelView?.let { panel ->
            // Fade out animation for overlay effect
            ObjectAnimator.ofFloat(panel, "alpha", 0f).apply {
                duration = 250
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        panel.visibility = View.GONE
                    }
                })
                start()
            }
            
            isShowing = false
        }
    }
    
    /**
     * Toggle showing/hiding the clipboard panel
     */
    fun toggle() {
        if (isShowing) hide() else show()
    }
    
    /**
     * Initialize the modern clipboard panel UI with settings-style design
     */
    private fun initializeClipboardPanel() {
        Log.d(KeyboardConstants.TAG, "📋 Starting modern clipboard panel initialization")
        
        try {
            // Create main container with modern design
            clipboardPanelView = FrameLayout(service).apply {
                setBackgroundColor(android.graphics.Color.argb(200, 0, 0, 0)) // Semi-transparent overlay
                setPadding(16, 16, 16, 16)
            }
            
            // Create content container with settings-style design
            val contentContainer = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                background = createModernCardBackground()
                setPadding(0, 0, 0, 0)
                elevation = 12f
                
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (service.resources.displayMetrics.heightPixels * 0.6).toInt() // 60% of screen height
                ).apply {
                    gravity = android.view.Gravity.CENTER
                    leftMargin = 24
                    rightMargin = 24
                }
            }
            
            // Create header with close button
            val headerContainer = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 20, 16)
                setBackgroundColor(if (service.isDarkMode) 
                    android.graphics.Color.argb(255, 28, 28, 30) else 
                    android.graphics.Color.argb(255, 248, 248, 250)
                )
            }
            
            // Header title
            val headerTitle = TextView(service).apply {
                text = "Clipboard History"
                textSize = 20f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                setTextColor(if (service.isDarkMode) 
                    android.graphics.Color.WHITE else 
                    android.graphics.Color.argb(255, 40, 40, 40)
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            // Close button
            closeButton = TextView(service).apply {
                text = "×"
                textSize = 22f
                setPadding(14, 10, 14, 10)
                background = createCloseButtonBackground()
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                
                layoutParams = LinearLayout.LayoutParams(
                    44,
                    44
                )
                
                setOnClickListener { hide() }
            }
            
            headerContainer.addView(headerTitle)
            headerContainer.addView(closeButton)
            contentContainer.addView(headerContainer)
            
            // Create filter buttons container
            val filterContainer = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 12, 24, 12)
                setBackgroundColor(if (service.isDarkMode) 
                    android.graphics.Color.argb(255, 44, 44, 46) else 
                    android.graphics.Color.argb(255, 242, 242, 247)
                )
            }
            
            filterAllButton = TextView(service).apply {
                text = "All"
                textSize = 14f
                setPadding(16, 8, 16, 8)
                background = createFilterButtonBackground(true)
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { rightMargin = 8 }
                setOnClickListener { filterItems(false) }
            }
            
            filterFavoritesButton = TextView(service).apply {
                text = "Favorites"
                textSize = 14f
                setPadding(16, 8, 16, 8)
                background = createFilterButtonBackground(false)
                setTextColor(if (service.isDarkMode) 
                    android.graphics.Color.WHITE else 
                    android.graphics.Color.argb(255, 60, 60, 60)
                )
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { leftMargin = 8 }
                setOnClickListener { filterItems(true) }
            }
            
            filterContainer.addView(filterAllButton)
            filterContainer.addView(filterFavoritesButton)
            contentContainer.addView(filterContainer)
            
            // Create scrollable RecyclerView container
            val scrollContainer = FrameLayout(service).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f // Take remaining space
                )
                setPadding(16, 8, 16, 8)
            }
            
            // Create RecyclerView with scroll
            recyclerView = RecyclerView(service).apply {
                layoutManager = LinearLayoutManager(service)
                clipboardAdapter = ClipboardAdapter(
                    mutableListOf(),
                    onItemClick = { onClipboardItemSelected(it) },
                    onFavoriteClick = { toggleItemFavorite(it) },
                    onDeleteClick = { deleteClipboardItem(it) }
                )
                adapter = clipboardAdapter
                
                // Enable scrolling
                isNestedScrollingEnabled = true
                
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            
            // Empty view
            emptyView = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(32, 32, 32, 32)
                
                val emptyIcon = TextView(service).apply {
                    text = "CLIPBOARD"
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setPadding(16, 8, 16, 8)
                    background = createEmptyStateBadge()
                    setTextColor(android.graphics.Color.WHITE)
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 16 }
                }
                
                val emptyText = TextView(service).apply {
                    text = "No clipboard items yet"
                    textSize = 16f
                    setTextColor(if (service.isDarkMode) 
                        android.graphics.Color.argb(150, 255, 255, 255) else 
                        android.graphics.Color.argb(150, 60, 60, 60)
                    )
                    gravity = android.view.Gravity.CENTER
                }
                
                addView(emptyIcon)
                addView(emptyText)
                
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                visibility = View.GONE
            }
            
            scrollContainer.addView(recyclerView)
            scrollContainer.addView(emptyView)
            contentContainer.addView(scrollContainer)
            
            // Create footer with clear button
            val footerContainer = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 12, 24, 20)
                setBackgroundColor(if (service.isDarkMode) 
                    android.graphics.Color.argb(255, 28, 28, 30) else 
                    android.graphics.Color.argb(255, 248, 248, 250)
                )
            }
            
            clearButton = TextView(service).apply {
                text = "× Clear All"
                textSize = 16f
                setPadding(20, 12, 20, 12)
                background = createClearButtonBackground()
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                setOnClickListener { clearNonFavoriteItems() }
            }
            
            footerContainer.addView(clearButton)
            contentContainer.addView(footerContainer)
            
            // Add content to main container
            (clipboardPanelView as FrameLayout).addView(contentContainer)
            
            // Add to root view
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            rootView.addView(clipboardPanelView, layoutParams)
            clipboardPanelView?.visibility = View.GONE
            
            // Update initial filter button states
            updateFilterButtons()
            
            Log.d(KeyboardConstants.TAG, "📋 Modern clipboard panel initialized successfully")
            
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error initializing modern clipboard panel: ${e.message}")
            throw e
        }
    }
    
    /**
     * Creates modern card background for main container
     */
    private fun createModernCardBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(if (service.isDarkMode) 
                android.graphics.Color.argb(255, 44, 44, 46) else 
                android.graphics.Color.WHITE
            )
            setStroke(2, if (service.isDarkMode) 
                android.graphics.Color.argb(100, 255, 255, 255) else 
                android.graphics.Color.argb(100, 0, 0, 0)
            )
        }
    }
    
    /**
     * Creates close button background
     */
    private fun createCloseButtonBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.argb(255, 255, 59, 48)) // Red color
        }
    }
    
    /**
     * Creates filter button background
     */
    private fun createFilterButtonBackground(isActive: Boolean): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 16f
            if (isActive) {
                setColor(android.graphics.Color.argb(255, 0, 122, 255)) // Blue
            } else {
                setColor(if (service.isDarkMode) 
                    android.graphics.Color.argb(100, 255, 255, 255) else 
                    android.graphics.Color.argb(100, 60, 60, 60)
                )
            }
        }
    }
    
    /**
     * Creates clear button background with red color
     */
    private fun createClearButtonBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(android.graphics.Color.argb(255, 255, 59, 48)) // Red color like the close button
        }
    }
    
    /**
     * Creates empty state badge background
     */
    private fun createEmptyStateBadge(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(if (service.isDarkMode) 
                android.graphics.Color.argb(255, 99, 102, 241) else 
                android.graphics.Color.argb(255, 59, 130, 246)
            )
        }
    }
    
    /**
     * Update the clipboard items list with production-ready data
     */
    private fun updateClipboardList() {
        clipboardAdapter?.let { adapter ->
            val items = if (showingOnlyFavorites) {
                clipboardManager.getFavoriteItems()
            } else {
                clipboardManager.getAllItems()
            }
            
            Log.d(KeyboardConstants.TAG, "📋 Updating clipboard list - Found ${items.size} items (showingOnlyFavorites: $showingOnlyFavorites)")
            
            // Filter out temporary trial texts for production use
            val filteredItems = items.filter { item ->
                !item.text.contains("trial", ignoreCase = true) &&
                !item.text.contains("test", ignoreCase = true) &&
                !item.text.contains("sample", ignoreCase = true) &&
                item.text.trim().isNotEmpty()
            }
            
            updateClipboardListInternal(filteredItems)
        }
    }
    
    /**
     * Internal method to update the list with given items
     */
    private fun updateClipboardListInternal(items: List<ClipboardItem>? = null) {
        clipboardAdapter?.let { adapter ->
            val clipboardItems = items ?: if (showingOnlyFavorites) {
                clipboardManager.getFavoriteItems()
            } else {
                clipboardManager.getAllItems()
            }
            
            Log.d(KeyboardConstants.TAG, "📋 Updating adapter with ${clipboardItems.size} items")
            
            // Show empty view if needed
            emptyView.visibility = if (clipboardItems.isEmpty()) View.VISIBLE else View.GONE
            
            // Update the adapter
            adapter.updateItems(clipboardItems.toMutableList())
        }
    }
    
    /**
     * Add a new text item to the clipboard
     */
    fun addClipboardItem(text: String) {
        coroutineScope.launch {
            val newItem = clipboardManager.addItem(text)
            updateClipboardList()
        }
    }
    
    /**
     * Refresh the clipboard list (called when system clipboard changes)
     */
    fun refreshClipboardList() {
        if (clipboardPanelView?.visibility == View.VISIBLE) {
            Log.d(KeyboardConstants.TAG, "📋 Refreshing clipboard list due to new system clipboard item")
            updateClipboardList()
        }
    }
    
    /**
     * Get the internal clipboard manager for monitoring purposes
     */
    fun getClipboardManager(): ClipboardManager = clipboardManager
    
    /**
     * When a clipboard item is selected, insert its text
     */
    private fun onClipboardItemSelected(item: ClipboardItem) {
        val ic = service.currentInputConnection
        if (ic != null) {
            try {
                // Insert the clipboard text
                ic.commitText(item.text, 1)
                
                // Hide the panel
                hide()
                
                // Perform haptic feedback
                service.performHapticFeedback()
                
                Log.d(KeyboardConstants.TAG, "📋 Clipboard item inserted: ${item.text.take(20)}...")
            } catch (e: Exception) {
                Log.e(KeyboardConstants.TAG, "❌ Error inserting clipboard item: ${e.message}")
            }
        }
    }
    
    /**
     * Toggle favorite status for a clipboard item
     */
    private fun toggleItemFavorite(item: ClipboardItem) {
        coroutineScope.launch {
            val newStatus = clipboardManager.toggleFavorite(item.id)
            withContext(Dispatchers.Main) {
                // Update UI
                service.performHapticFeedback()
                clipboardAdapter?.updateFavoriteStatus(item)
                
                // If in favorites mode and item was unfavorited, it should disappear
                if (showingOnlyFavorites && !newStatus) {
                    updateClipboardList()
                }
            }
        }
    }
    
    /**
     * Delete a clipboard item
     */
    private fun deleteClipboardItem(item: ClipboardItem) {
        coroutineScope.launch {
            val success = clipboardManager.deleteItem(item.id)
            if (success) {
                withContext(Dispatchers.Main) {
                    service.performHapticFeedback()
                    clipboardAdapter?.removeItem(item)
                    
                    // Show empty view if needed
                    val items = if (showingOnlyFavorites) {
                        clipboardManager.getFavoriteItems()
                    } else {
                        clipboardManager.getAllItems()
                    }
                    emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
    
    /**
     * Clear all non-favorite clipboard items
     */
    private fun clearNonFavoriteItems() {
        coroutineScope.launch {
            val count = clipboardManager.clearNonFavorites()
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    service.performHapticFeedback()
                    service.showToast("Cleared $count clipboard items")
                    updateClipboardList()
                }
            }
        }
    }
    
    /**
     * Filter items by favorite status
     */
    private fun filterItems(onlyFavorites: Boolean) {
        if (showingOnlyFavorites != onlyFavorites) {
            showingOnlyFavorites = onlyFavorites
            service.performHapticFeedback()
            updateFilterButtons()
            updateClipboardList()
        }
    }
    
    /**
     * Update filter button styles based on current selection
     */
    private fun updateFilterButtons() {
        // Update "All" button
        filterAllButton.background = createFilterButtonBackground(!showingOnlyFavorites)
        filterAllButton.setTextColor(if (!showingOnlyFavorites) 
            android.graphics.Color.WHITE else 
            if (service.isDarkMode) 
                android.graphics.Color.WHITE else 
                android.graphics.Color.argb(255, 60, 60, 60)
        )
        
        // Update "Favorites" button  
        filterFavoritesButton.background = createFilterButtonBackground(showingOnlyFavorites)
        filterFavoritesButton.setTextColor(if (showingOnlyFavorites) 
            android.graphics.Color.WHITE else 
            if (service.isDarkMode) 
                android.graphics.Color.WHITE else 
                android.graphics.Color.argb(255, 60, 60, 60)
        )
    }
}
