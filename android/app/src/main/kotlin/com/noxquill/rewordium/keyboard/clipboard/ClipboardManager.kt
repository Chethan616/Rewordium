package com.noxquill.rewordium.keyboard.clipboard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noxquill.rewordium.keyboard.util.KeyboardConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.LinkedList

/**
 * Manages clipboard history with persistent storage for favorites
 */
class ClipboardManager(private val context: Context) {
    
    private val gson = Gson()
    private val clipboardItems = LinkedList<ClipboardItem>() // Use LinkedList for efficient insertions at the beginning
    private val maxRegularItems = 20 // Maximum number of regular clipboard items to keep
    private val maxTotalItems = 100 // Maximum total items (including favorites) to prevent memory issues
    
    // SharedPreferences for persistent storage
    private val prefs: SharedPreferences = context.getSharedPreferences(
        KeyboardConstants.PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    
    init {
        // Load favorite items when initialized
        loadFavoriteItems()
    }
    
    /**
     * Add a new item to clipboard history
     */
    suspend fun addItem(text: String): ClipboardItem = withContext(Dispatchers.IO) {
        Log.d(KeyboardConstants.TAG, "📋 ClipboardManager.addItem() called with text: '${text.take(50)}...'")
        
        // Don't add empty or whitespace-only text
        if (text.trim().isEmpty()) {
            Log.w(KeyboardConstants.TAG, "📋 Ignoring empty clipboard text")
            // Return a new empty item instead of null
            return@withContext ClipboardItem(
                id = ClipboardItem.generateId(),
                text = text,
                timestamp = Date()
            )
        }
        
        // Check if identical text already exists
        val existingItem = clipboardItems.firstOrNull { it.text == text }
        if (existingItem != null) {
            Log.d(KeyboardConstants.TAG, "📋 Moving existing item to top: '${text.take(20)}...'")
            // Move to top of list if it exists
            clipboardItems.remove(existingItem)
            clipboardItems.addFirst(existingItem)
            return@withContext existingItem
        }
        
        // Create new item
        val newItem = ClipboardItem(
            id = ClipboardItem.generateId(),
            text = text,
            timestamp = Date()
        )
        
        Log.d(KeyboardConstants.TAG, "📋 Adding new clipboard item: '${text.take(20)}...' - Total items: ${clipboardItems.size + 1}")
        clipboardItems.addFirst(newItem)
        
        // Memory management - keep list size under control
        cleanupClipboardItems()
        
        Log.d(KeyboardConstants.TAG, "📋 Clipboard item added successfully - Current total: ${clipboardItems.size}")
        
        // Return the newly added item
        return@withContext newItem
    }
    
    /**
     * Get all clipboard items
     */
    fun getAllItems(): List<ClipboardItem> {
        Log.d(KeyboardConstants.TAG, "📋 ClipboardManager.getAllItems() called - returning ${clipboardItems.size} items")
        return clipboardItems
    }
    
    /**
     * Get only favorite items
     */
    fun getFavoriteItems(): List<ClipboardItem> {
        val favorites = clipboardItems.filter { it.isFavorite }
        Log.d(KeyboardConstants.TAG, "📋 ClipboardManager.getFavoriteItems() called - returning ${favorites.size} favorites")
        return favorites
    }
    
    /**
     * Toggle favorite status of an item
     */
    suspend fun toggleFavorite(itemId: String): Boolean = withContext(Dispatchers.IO) {
        val item = clipboardItems.find { it.id == itemId } ?: return@withContext false
        
        // Toggle favorite status
        item.isFavorite = !item.isFavorite
        
        // Save favorites to persistent storage
        saveFavoriteItems()
        
        return@withContext item.isFavorite
    }
    
    /**
     * Delete a clipboard item
     */
    suspend fun deleteItem(itemId: String): Boolean = withContext(Dispatchers.IO) {
        val initialSize = clipboardItems.size
        clipboardItems.removeIf { it.id == itemId }
        
        // If we removed a favorite item, update persistent storage
        if (clipboardItems.size < initialSize) {
            saveFavoriteItems()
            return@withContext true
        }
        
        return@withContext false
    }
    
    /**
     * Delete all non-favorite items
     */
    suspend fun clearNonFavorites(): Int = withContext(Dispatchers.IO) {
        val initialSize = clipboardItems.size
        clipboardItems.removeIf { !it.isFavorite }
        return@withContext initialSize - clipboardItems.size
    }
    
    /**
     * Load favorite items from persistent storage
     */
    private fun loadFavoriteItems() {
        try {
            val favoriteItemsJson = prefs.getString(KeyboardConstants.KEY_CLIPBOARD_FAVORITES, null) ?: return
            
            val type = object : TypeToken<List<ClipboardItem>>() {}.type
            val favoriteItems: List<ClipboardItem> = gson.fromJson(favoriteItemsJson, type)
            
            clipboardItems.addAll(favoriteItems)
            Log.d(KeyboardConstants.TAG, "📋 Loaded ${favoriteItems.size} favorite clipboard items")
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error loading favorite clipboard items: ${e.message}")
        }
    }
    
    /**
     * Save favorite items to persistent storage
     */
    private fun saveFavoriteItems() {
        try {
            val favoriteItems = clipboardItems.filter { it.isFavorite }
            val favoriteItemsJson = gson.toJson(favoriteItems)
            
            prefs.edit()
                .putString(KeyboardConstants.KEY_CLIPBOARD_FAVORITES, favoriteItemsJson)
                .apply()
            
            Log.d(KeyboardConstants.TAG, "📋 Saved ${favoriteItems.size} favorite clipboard items")
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error saving favorite clipboard items: ${e.message}")
        }
    }
    
    /**
     * Clean up clipboard items to prevent memory issues
     */
    private fun cleanupClipboardItems() {
        // First, ensure we don't exceed the maximum total items
        if (clipboardItems.size > maxTotalItems) {
            // If we have more than maxTotalItems, keep all favorites but trim the rest
            val favorites = clipboardItems.filter { it.isFavorite }
            val nonFavorites = clipboardItems.filter { !it.isFavorite }
                .take(maxTotalItems - favorites.size)
            
            clipboardItems.clear()
            clipboardItems.addAll(favorites)
            clipboardItems.addAll(nonFavorites)
            
            // Re-sort by recency (favorites are still in the list)
            clipboardItems.sortByDescending { it.timestamp }
        }
        
        // Then, limit the number of non-favorite items
        val nonFavoriteCount = clipboardItems.count { !it.isFavorite }
        if (nonFavoriteCount > maxRegularItems) {
            // We have too many non-favorite items, remove the oldest ones
            val itemsToRemove = clipboardItems
                .filter { !it.isFavorite }
                .sortedBy { it.timestamp }
                .take(nonFavoriteCount - maxRegularItems)
            
            clipboardItems.removeAll(itemsToRemove)
        }
    }
    
    /**
     * Clean old clipboard items (24 hour memory management)
     * Removes non-favorite items older than 24 hours
     */
    fun cleanOldItems() {
        try {
            val oneDayAgo = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000) // 24 hours ago
            
            // Remove non-favorite items older than 24 hours
            val itemsToRemove = clipboardItems.filter { item ->
                !item.isFavorite && item.timestamp.before(oneDayAgo)
            }
            
            if (itemsToRemove.isNotEmpty()) {
                clipboardItems.removeAll(itemsToRemove)
                Log.d(KeyboardConstants.TAG, "📋 Cleaned ${itemsToRemove.size} old clipboard items")
            }
            
            // Also save favorites after cleanup
            saveFavoriteItems()
            
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error cleaning old clipboard items: ${e.message}")
        }
    }
    
    /**
     * Remove a clipboard item by ID
     */
    fun removeItem(itemId: String): Boolean {
        return try {
            val itemToRemove = clipboardItems.find { it.id == itemId }
            if (itemToRemove != null) {
                clipboardItems.remove(itemToRemove)
                
                // Also remove from persistent storage if it was a favorite
                if (itemToRemove.isFavorite) {
                    saveFavoriteItems()
                }
                
                Log.d(KeyboardConstants.TAG, "📋 Removed clipboard item: ${itemId}")
                true
            } else {
                Log.w(KeyboardConstants.TAG, "📋 Item not found for removal: ${itemId}")
                false
            }
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "❌ Error removing clipboard item: ${e.message}")
            false
        }
    }
}
