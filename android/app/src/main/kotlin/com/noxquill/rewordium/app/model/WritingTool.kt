package com.noxquill.rewordium.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

data class WritingTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val actionLabel: String,
)

val writingTools = listOf(
    WritingTool("paraphraser", "Rewrite", "Find a clearer way to say it", Icons.Filled.EditNote, "Rewrite"),
    WritingTool("grammar", "Grammar", "Polish spelling and punctuation", Icons.Filled.Spellcheck, "Check writing"),
    WritingTool("summarizer", "Summarize", "Keep the important parts", Icons.Filled.Summarize, "Summarize"),
    WritingTool("translator", "Translate", "Carry your meaning across languages", Icons.Filled.Translate, "Translate"),
    WritingTool("tone", "Tone editor", "Make it warmer, sharper, or calmer", Icons.Filled.Tune, "Adjust tone"),
    WritingTool("detector", "AI writing check", "Look for patterns with care", Icons.Filled.FactCheck, "Review writing"),
    WritingTool("document", "Document polish", "Improve a longer piece of writing", Icons.Filled.Description, "Polish document"),
    WritingTool("humanizer", "Humanizer", "Keep your voice; lose the robotic phrasing", Icons.Filled.AutoFixHigh, "Make it sound like you"),
    WritingTool("jade", "Ask Jade", "Think through words together", Icons.Filled.AutoAwesome, "Ask Jade"),
)

fun toolFor(id: String): WritingTool = writingTools.firstOrNull { it.id == id } ?: writingTools.first()
