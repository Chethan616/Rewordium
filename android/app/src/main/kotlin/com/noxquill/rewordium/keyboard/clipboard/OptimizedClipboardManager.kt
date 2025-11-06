package com.noxquill.rewordium.keyboard.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.noxquill.rewordium.keyboard.util.KeyboardConstants
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Optimized clipboard manager with debouncing and smart duplicate detection
 * Inspired by FlorisBoard's efficient clipboard handling
 */
class OptimizedClipboardManager(
    private val context: Context,
    private val maxItems: Int = 20
) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipboardItems = ConcurrentLinkedQueue<ClipboardItem>()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Debouncing
    private var debounceJob: Job? = null
    private val debounceDelay = 300L
    
    // Duplicate detection with fuzzy matching
    private var lastClipboardText: String? = null
    private val similarityThreshold = 0.95f
    
    data class ClipboardItem(
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isPinned: Boolean = false
    )
    
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardChanged()
    }
    
    fun start() {
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        Log.d(KeyboardConstants.TAG, "📋 Clipboard monitor started")
    }
    
    fun stop() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        scope.cancel()
        debounceJob?.cancel()
        Log.d(KeyboardConstants.TAG, "📋 Clipboard monitor stopped")
    }
    
    private fun onClipboardChanged() {
        // Debounce rapid clipboard changes
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceDelay)
            processClipboardChange()
        }
    }
    
    private suspend fun processClipboardChange() = withContext(Dispatchers.Main) {
        val primaryClip = clipboardManager.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            val text = primaryClip.getItemAt(0).text?.toString()
            
            if (text != null && text.isNotBlank()) {
                // Smart duplicate detection
                if (shouldAddItem(text)) {
                    addItem(text)
                }
            }
        }
    }
    
    /**
     * Check if item should be added (duplicate detection)
     */
    private fun shouldAddItem(newText: String): Boolean {
        // Quick check: exact match with last item
        if (newText == lastClipboardText) {
            return false
        }
        
        // Check against recent items with fuzzy matching
        val recentItems = clipboardItems.take(5)
        for (item in recentItems) {
            if (calculateSimilarity(newText, item.text) > similarityThreshold) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Calculate similarity between two strings (Levenshtein distance based)
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0f
        
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance) / longer.length.toFloat()
    }
    
    /**
     * Optimized Levenshtein distance calculation
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        
        for (i in 0..s2.length) {
            costs[i] = i
        }
        
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            
            for (j in 1..s2.length) {
                val cj = minOf(
                    1 + minOf(costs[j], costs[j - 1]),
                    if (s1[i - 1] == s2[j - 1]) nw else nw + 1
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        
        return costs[s2.length]
    }
    
    /**
     * Add new item to clipboard history
     */
    private fun addItem(text: String) {
        val item = ClipboardItem(text = text)
        clipboardItems.add(item)
        lastClipboardText = text
        
        // Enforce limit (keep pinned items)
        while (clipboardItems.size > maxItems) {
            val oldest = clipboardItems.firstOrNull { !it.isPinned }
            if (oldest != null) {
                clipboardItems.remove(oldest)
            } else {
                break // All items are pinned
            }
        }
        
        Log.d(KeyboardConstants.TAG, "📋 Added clipboard item: ${text.take(30)}... (total: ${clipboardItems.size})")
    }
    
    /**
     * Get all clipboard items (newest first)
     */
    fun getItems(): List<ClipboardItem> {
        return clipboardItems.reversed()
    }
    
    /**
     * Pin/unpin item
     */
    fun togglePin(text: String) {
        clipboardItems.forEach { item ->
            if (item.text == text) {
                val newItem = item.copy(isPinned = !item.isPinned)
                clipboardItems.remove(item)
                clipboardItems.add(newItem)
                return
            }
        }
    }
    
    /**
     * Remove specific item
     */
    fun removeItem(text: String) {
        clipboardItems.removeIf { it.text == text }
    }
    
    /**
     * Clear all non-pinned items
     */
    fun clearHistory() {
        clipboardItems.removeIf { !it.isPinned }
        lastClipboardText = null
        Log.d(KeyboardConstants.TAG, "📋 Clipboard history cleared")
    }
    
    /**
     * Paste item to clipboard
     */
    fun pasteItem(text: String) {
        val clip = android.content.ClipData.newPlainText("text", text)
        clipboardManager.setPrimaryClip(clip)
    }
}
