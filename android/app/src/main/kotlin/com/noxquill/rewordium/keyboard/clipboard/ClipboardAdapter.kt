package com.noxquill.rewordium.keyboard.clipboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.noxquill.rewordium.R
import java.text.SimpleDateFormat
import java.util.Locale

class ClipboardAdapter(
    private var items: MutableList<ClipboardItem>,
    private val onItemClick: (ClipboardItem) -> Unit,
    private val onFavoriteClick: (ClipboardItem) -> Unit,
    private val onDeleteClick: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textContent: TextView = view.findViewById(R.id.clipboard_text)
        val timestamp: TextView = view.findViewById(R.id.clipboard_timestamp)
        val charCount: TextView = view.findViewById(R.id.clipboard_char_count)
        val favoriteButton: ImageButton = view.findViewById(R.id.btn_favorite)
        val deleteButton: ImageButton = view.findViewById(R.id.btn_delete)
        val pasteButton: ImageButton = view.findViewById(R.id.btn_paste)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.clipboard_item_glass, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // Set clipboard text content (limit to preview if too long)
        val maxPreviewLength = 120
        val displayText = if (item.text.length > maxPreviewLength) {
            item.text.substring(0, maxPreviewLength) + "..."
        } else {
            item.text
        }
        holder.textContent.text = displayText
        
        // Set character count
        holder.charCount.text = "${item.text.length} chars"
        
        // Set timestamp with relative time format
        val timeAgo = getTimeAgo(item.timestamp.time)
        holder.timestamp.text = timeAgo
        
        // Set favorite status
        holder.favoriteButton.setImageResource(
            if (item.isFavorite) R.drawable.ic_star_filled
            else R.drawable.ic_star_outline
        )
        
        // Set click listeners
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.pasteButton.setOnClickListener { onItemClick(item) } // Paste on button click
        holder.favoriteButton.setOnClickListener { onFavoriteClick(item) }
        holder.deleteButton.setOnClickListener { onDeleteClick(item) }
    }
    
    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            diff < 604800000 -> "${diff / 86400000}d ago"
            else -> dateFormat.format(timestamp)
        }
    }

    override fun getItemCount() = items.size
    
    fun updateItems(newItems: List<ClipboardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
    
    fun updateFavoriteStatus(item: ClipboardItem) {
        val position = items.indexOfFirst { it.id == item.id }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }
    
    fun removeItem(item: ClipboardItem) {
        val position = items.indexOfFirst { it.id == item.id }
        if (position >= 0) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
