package com.noxquill.rewordium.app.jade

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/** How a Jade skill looks in the app. */
data class JadeSkillUi(
    val id: String,
    val label: String,
    val hint: String,
    val placeholder: String,
    val icon: ImageVector,
)

val jadeSkills = listOf(
    JadeSkillUi("chat", "Chat", "Ask or tune anything", "Message Jade…", Icons.Filled.AutoAwesome),
    JadeSkillUi("rewrite", "Rewrite", "Say it more clearly", "Paste text to rewrite…", Icons.Filled.EditNote),
    JadeSkillUi("grammar", "Fix grammar", "Polish spelling & punctuation", "Paste text to proofread…", Icons.Filled.Spellcheck),
    JadeSkillUi("tone", "Change tone", "Warmer, sharper, calmer", "Paste text to re-tone…", Icons.Filled.Tune),
    JadeSkillUi("summarize", "Summarize", "Keep what matters", "Paste text to summarize…", Icons.Filled.Summarize),
    JadeSkillUi("translate", "Translate", "Carry it across languages", "Paste text to translate…", Icons.Filled.Translate),
    JadeSkillUi("humanize", "Humanize", "Lose the robotic phrasing", "Paste text to make it sound like you…", Icons.Filled.AutoFixHigh),
    JadeSkillUi("polish", "Polish document", "Tidy a longer piece", "Paste or attach a document…", Icons.Filled.Description),
    JadeSkillUi("detector", "AI check", "Look for AI-like patterns", "Paste writing to review…", Icons.AutoMirrored.Filled.FactCheck),
)

fun jadeSkill(id: String?): JadeSkillUi = jadeSkills.firstOrNull { it.id == id } ?: jadeSkills.first()
