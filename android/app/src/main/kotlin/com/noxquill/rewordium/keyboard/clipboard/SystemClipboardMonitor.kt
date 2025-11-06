package com.noxquill.rewordium.keyboard.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.noxquill.rewordium.keyboard.util.KeyboardConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Monitors system clipboard and automatically adds new items to clipboard history
 */
class SystemClipboardMonitor(
    private val context: Context,
    private val clipboardManager: com.noxquill.rewordium.keyboard.clipboard.ClipboardManager,
    private val coroutineScope: CoroutineScope
) {
    
    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastClipboardText = ""
    private var isMonitoring = false
    
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(KeyboardConstants.TAG, "📋 System clipboard changed - checking for new content")
        checkClipboard()
    }
    
    /**
     * Start monitoring system clipboard
     */
    fun startMonitoring() {
        if (!isMonitoring) {
            try {
                systemClipboard.addPrimaryClipChangedListener(clipboardListener)
                isMonitoring = true
                
                // Check current clipboard content immediately
                checkClipboard()
                
                Log.d(KeyboardConstants.TAG, "📋 Started clipboard monitoring - listener added successfully")
            } catch (e: Exception) {
                Log.e(KeyboardConstants.TAG, "❌ Error starting clipboard monitoring: ${e.message}")
                isMonitoring = false
            }
        } else {
            Log.d(KeyboardConstants.TAG, "📋 Clipboard monitoring already active")
        }
    }
    
    /**
     * Stop monitoring system clipboard
     */
    fun stopMonitoring() {
        if (isMonitoring) {
            systemClipboard.removePrimaryClipChangedListener(clipboardListener)
            isMonitoring = false
            
            Log.d(KeyboardConstants.TAG, "📋 Stopped clipboard monitoring")
        }
    }
    
    /**
     * Check current clipboard content and add to history if new
     */
    private fun checkClipboard() {
        try {
            val clip = systemClipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString() ?: return
                
                // Only add if it's different from the last item and not empty
                if (clipText.isNotBlank() && clipText != lastClipboardText) {
                    lastClipboardText = clipText
                    
                    Log.d(KeyboardConstants.TAG, "📋 New clipboard text detected: '${clipText.take(50)}...'")
                    
                    // Add to clipboard history in background
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val newItem = clipboardManager.addItem(clipText)
                            if (newItem != null) {
                                Log.d(KeyboardConstants.TAG, "📋 Added clipboard item to manager: ${clipText.take(30)}...")

                                withContext(Dispatchers.Main) {
                                    notifyClipboardChanged()
                                }
                            } else {
                                Log.d(KeyboardConstants.TAG, "📋 Skipping clipboard add - duplicate or recently deleted: ${clipText.take(30)}...")
                            }
                        } catch (e: Exception) {
                            Log.e(KeyboardConstants.TAG, "❌ Error adding clipboard item: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error checking clipboard: ${e.message}")
        }
    }
    
    /**
     * Notify that clipboard has changed so UI can refresh
     */
    private fun notifyClipboardChanged() {
        try {
            // Get the keyboard service to refresh clipboard panel if it's open
            val keyboardService = context as? com.noxquill.rewordium.keyboard.RewordiumAIKeyboardService
            keyboardService?.refreshClipboardPanel()
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error notifying clipboard change: ${e.message}")
        }
    }
}
