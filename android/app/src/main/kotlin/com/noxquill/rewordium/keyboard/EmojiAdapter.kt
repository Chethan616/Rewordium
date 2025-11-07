package com.noxquill.rewordium.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.noxquill.rewordium.R
import com.noxquill.rewordium.keyboard.ui.UltraPremiumGlassEffects

class EmojiAdapter(
    private val emojis: List<String>,
    private val onEmojiClicked: (String) -> Unit,
    private val isDarkMode: Boolean = true
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                parent.context.resources.getDimensionPixelSize(R.dimen.emoji_key_height)
            )
            gravity = Gravity.CENTER
            textSize = 26f // Slightly larger for better visibility
            
            // ✨ EMOJI GLASS BUTTON (Enhancement #6) ✨
            background = UltraPremiumGlassEffects.createGlassEmojiButton(parent.context, isDarkMode)
            
            // Optimized padding
            val padding = 8
            setPadding(padding, padding, padding, padding)
            
            // Disable drawing cache for better scrolling
            isDrawingCacheEnabled = false
            setWillNotDraw(false)
            
            // Enable hardware acceleration
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
        return EmojiViewHolder(textView)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        try {
            if (position < 0 || position >= emojis.size) {
                return
            }
            
            val emoji = emojis[position]
            holder.textView.text = emoji
            
            // Simple click handler without animations for maximum scrolling performance
            holder.textView.setOnClickListener {
                try {
                    onEmojiClicked(emoji)
                } catch (e: Exception) {
                    android.util.Log.e("EmojiAdapter", "Click handler error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EmojiAdapter", "Bind error: ${e.message}")
            holder.textView.text = ""
        }
    }

    override fun getItemCount(): Int = try {
        emojis.size
    } catch (e: Exception) {
        android.util.Log.e("EmojiAdapter", "Get item count error: ${e.message}")
        0
    }
}