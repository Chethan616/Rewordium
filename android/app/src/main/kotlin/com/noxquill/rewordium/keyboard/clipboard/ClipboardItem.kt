package com.noxquill.rewordium.keyboard.clipboard

import java.util.Date

/**
 * Data model for a clipboard item
 */
data class ClipboardItem(
    val id: String, // Unique identifier for the item
    val text: String, // The clipboard text content
    val timestamp: Date, // When this item was copied
    var isFavorite: Boolean = false // Whether this item is starred/favorited
) {
    companion object {
        fun generateId(): String {
            return System.currentTimeMillis().toString() + "_" + (0..999).random()
        }
    }
}
