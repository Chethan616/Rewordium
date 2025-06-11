package com.example.yc_startup.keyboard

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yc_startup.R

class EmojiAdapter(
    private val emojis: List<String>,
    private val onEmojiClicked: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                parent.context.resources.getDimensionPixelSize(R.dimen.emoji_key_height)
            )
            gravity = Gravity.CENTER
            textSize = 28f
        }
        return EmojiViewHolder(textView)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        holder.textView.text = emoji
        holder.textView.setOnClickListener {
            onEmojiClicked(emoji)
        }
    }

    override fun getItemCount() = emojis.size
}