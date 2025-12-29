/*
 * Copyright (C) 2024-2025 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noxquill.rewordium.keyboard.ime.ai

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.aiManager
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.input.InputEventDispatcher
import com.noxquill.rewordium.keyboard.ime.input.LocalInputFeedbackController
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyData
import com.noxquill.rewordium.keyboard.ime.media.KeyboardLikeButton
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

// Gradient colors for selected chip outline (accent colors)
private val GradientStart = Color(0xFF6366F1) // Purple
private val GradientMiddle = Color(0xFF8B5CF6) // Violet
private val GradientEnd = Color(0xFFEC4899) // Pink

/**
 * AI Suggestion Chip data
 */
data class AiSuggestionChip(
    val emoji: String,
    val text: String,
    val action: AIAction,
    val prompt: String? = null
)

/**
 * AI Input Layout - A keyboard panel for AI writing assistance
 * Themed to match the current keyboard theme
 */
@Composable
fun AiInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val aiManager by context.aiManager()
    val scope = rememberCoroutineScope()
    
    // Get theme colors from keyboard theme
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val smartbarStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    
    // Dynamic theme colors
    val backgroundColor = windowStyle.background()
    val surfaceColor = keyStyle.background()
    val textColor = keyStyle.foreground()
    val secondaryTextColor = textColor.copy(alpha = 0.7f)
    val chipBackgroundColor = surfaceColor.copy(alpha = 0.8f)
    val chipSelectedColor = surfaceColor
    
    var isGenerating by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf<AIAction?>(null) }
    var generatedText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wasUsingAllText by remember { mutableStateOf(false) }  // Track if we should replace all text

    // 3-ROW AI CONTROL PANEL DATA
    // Row 1 - Personas (expanded)
    val personaChips = remember {
        listOf(
            "🙂 Casual",
            "🎓 Academic",
            "👔 Professional",
            "🤝 Friendly",
            "🪄 Creative",
            "🎭 Dramatic",
            "🧘 Calm",
            "💪 Motivational",
            "🤓 Nerdy",
            "😎 Cool",
            "🌸 Poetic",
            "🔬 Scientific",
            "📰 Journalistic",
            "🎬 Cinematic",
            "👶 Simple"
        )
    }
    
    // Row 2 - Tasks/Modes (expanded)
    val taskChips = remember {
        listOf(
            "✉️ Email",
            "💼 Work",
            "📚 Academic",
            "😄 Humor",
            "🤵 Polite",
            "🌐 Translate",
            "✨ Rewrite",
            "📋 Summarize",
            "📖 Expand",
            "✏️ Fix Grammar",
            "💌 Love Letter",
            "📝 Essay",
            "🎯 Pitch",
            "📢 Marketing",
            "🤝 Apology",
            "🎉 Congratulate",
            "📊 Report",
            "💡 Brainstorm",
            "🔄 Paraphrase",
            "📣 Announce",
            "🙏 Request",
            "👋 Introduction",
            "🎤 Speech",
            "📱 Social Media",
            "💬 Chat Reply"
        )
    }
    
    // Row 3 - Output Length (expanded)
    val lengthChips = remember {
        listOf(
            "⚡ Very Short",
            "📏 Short",
            "📐 Medium",
            "📜 Long",
            "📚 Very Long",
            "🎯 Precise",
            "💎 Concise",
            "📖 Detailed",
            "🔍 Elaborate",
            "✂️ Compact",
            "🌊 Flowing",
            "📌 Bullet Points"
        )
    }
    
    // Selected chip states
    var selectedPersona by remember { mutableStateOf<String?>(null) }
    var selectedTask by remember { mutableStateOf<String?>(null) }
    var selectedLength by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.imeUiHeight())
                .background(backgroundColor)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with AI branding
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ai__panel_title),
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isGenerating -> {
                            // Loading state with animated spinner
                            val infiniteTransition = rememberInfiniteTransition(label = "loading")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "rotation"
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .rotate(rotation)
                                        .border(
                                            width = 3.dp,
                                            brush = Brush.sweepGradient(
                                                listOf(
                                                    GradientStart,
                                                    GradientMiddle,
                                                    GradientEnd,
                                                    GradientStart
                                                )
                                            ),
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Generating...",
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "AI is working on your text",
                                    color = secondaryTextColor,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        generatedText != null -> {
                            // Result display - full screen with regenerate button
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            ) {
                                // Generated text with scroll
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(chipBackgroundColor.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = generatedText!!,
                                            color = textColor,
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Action buttons row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Regenerate button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(chipBackgroundColor)
                                            .clickable {
                                                // Regenerate with same settings
                                                scope.launch {
                                                    val selectedText = editorInstance.activeContent.selectedText
                                                    val hasSelection = selectedText.toString().isNotBlank()
                                                    val textToRewrite = if (hasSelection) {
                                                        selectedText.toString()
                                                    } else {
                                                        editorInstance.activeContent.text.toString()
                                                    }
                                                    
                                                    // Track whether we're using all text (so we know to select all when inserting)
                                                    wasUsingAllText = !hasSelection
                                                    
                                                    if (textToRewrite.isBlank()) {
                                                        errorMessage = context.getString(R.string.ai__error_no_text)
                                                        return@launch
                                                    }
                                                    
                                                    isGenerating = true
                                                    errorMessage = null
                                                    generatedText = null
                                                    
                                                    val persona = selectedPersona ?: ""
                                                    val task = selectedTask ?: ""
                                                    val length = selectedLength ?: ""
                                                    
                                                    val fullPrompt = buildString {
                                                        if (persona.isNotBlank()) append("Persona: $persona. ")
                                                        if (task.isNotBlank()) append("Task: $task. ")
                                                        if (length.isNotBlank()) append("Length: $length. ")
                                                        append("Rewrite this: $textToRewrite")
                                                    }
                                                    
                                                    val action = when {
                                                        task.contains("Rewrite") -> AIAction.REWRITE
                                                        task.contains("Summarize") -> AIAction.SUMMARIZE
                                                        task.contains("Expand") -> AIAction.EXPAND
                                                        task.contains("Grammar") -> AIAction.FIX_GRAMMAR
                                                        task.contains("Polite") || task.contains("Academic") -> AIAction.MAKE_FORMAL
                                                        task.contains("Humor") -> AIAction.MAKE_CASUAL
                                                        else -> AIAction.REWRITE
                                                    }
                                                    
                                                    val result = aiManager.rewriteTextWithPrompt(fullPrompt, action)
                                                    
                                                    isGenerating = false
                                                    result.fold(
                                                        onSuccess = { text -> generatedText = text },
                                                        onFailure = { e -> errorMessage = e.message ?: context.getString(R.string.ai__error_api) }
                                                    )
                                                }
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Regenerate",
                                                tint = textColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Regenerate",
                                                color = textColor,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    
                                    // New prompt button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.horizontalGradient(
                                                    listOf(GradientStart, GradientMiddle, GradientEnd)
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                generatedText = null
                                                errorMessage = null
                                                selectedPersona = null
                                                selectedTask = null
                                                selectedLength = null
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "New Prompt",
                                            color = textColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                        errorMessage != null -> {
                            // Error state
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(chipBackgroundColor)
                                        .clickable { errorMessage = null }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Try Again",
                                        color = textColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        else -> {
                            // Default state - instruction text
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Select options below",
                                    color = secondaryTextColor,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "then tap Generate to enhance your text",
                                    color = secondaryTextColor.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                
                // ---------- 3-ROW META-AI STYLE CONTROL PANEL WITH INFINITE SCROLL ----------
                // Only show when not generating and no result yet
                if (!isGenerating && generatedText == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Row 1 — Persona Selection (Infinite Scroll)
                        InfiniteScrollChipRow(
                            chips = personaChips,
                            selectedChip = selectedPersona,
                            onChipSelected = { chip ->
                                selectedPersona = if (selectedPersona == chip) null else chip
                            },
                            chipBackground = chipBackgroundColor,
                            chipSelectedBackground = chipSelectedColor,
                            textColor = textColor
                        )

                        // Row 2 — Writing Task (Infinite Scroll)
                        InfiniteScrollChipRow(
                            chips = taskChips,
                            selectedChip = selectedTask,
                            onChipSelected = { chip ->
                                selectedTask = if (selectedTask == chip) null else chip
                            },
                            chipBackground = chipBackgroundColor,
                            chipSelectedBackground = chipSelectedColor,
                            textColor = textColor
                        )

                        // Row 3 — Output Length (Infinite Scroll)
                        InfiniteScrollChipRow(
                            chips = lengthChips,
                            selectedChip = selectedLength,
                            onChipSelected = { chip ->
                                selectedLength = if (selectedLength == chip) null else chip
                            },
                            chipBackground = chipBackgroundColor,
                            chipSelectedBackground = chipSelectedColor,
                            textColor = textColor
                        )
                        
                        // Generate Button Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(GradientStart, GradientMiddle, GradientEnd)
                                        )
                                    )
                                    .clickable {
                                        scope.launch {
                                            val selectedText = editorInstance.activeContent.selectedText
                                            val hasSelection = selectedText.toString().isNotBlank()
                                            val textToRewrite = if (hasSelection) {
                                                selectedText.toString()
                                            } else {
                                                editorInstance.activeContent.text.toString()
                                            }
                                            
                                            // Track whether we're using all text (so we know to select all when inserting)
                                            wasUsingAllText = !hasSelection
                                            
                                            if (textToRewrite.isBlank()) {
                                                errorMessage = context.getString(R.string.ai__error_no_text)
                                                return@launch
                                            }
                                            
                                            isGenerating = true
                                            errorMessage = null
                                            generatedText = null
                                            
                                            // Build smart prompt with persona + task + length
                                            val persona = selectedPersona ?: ""
                                            val task = selectedTask ?: ""
                                            val length = selectedLength ?: ""
                                            
                                            val fullPrompt = buildString {
                                                if (persona.isNotBlank()) append("Persona: $persona. ")
                                                if (task.isNotBlank()) append("Task: $task. ")
                                                if (length.isNotBlank()) append("Length: $length. ")
                                                append("Rewrite this: $textToRewrite")
                                            }
                                            
                                            // Determine action based on selected task
                                            val action = when {
                                                task.contains("Rewrite") -> AIAction.REWRITE
                                                task.contains("Summarize") -> AIAction.SUMMARIZE
                                                task.contains("Expand") -> AIAction.EXPAND
                                                task.contains("Grammar") -> AIAction.FIX_GRAMMAR
                                                task.contains("Polite") || task.contains("Academic") -> AIAction.MAKE_FORMAL
                                                task.contains("Humor") -> AIAction.MAKE_CASUAL
                                                else -> AIAction.REWRITE
                                            }
                                            
                                            val result = aiManager.rewriteTextWithPrompt(fullPrompt, action)
                                            
                                            isGenerating = false
                                            result.fold(
                                                onSuccess = { text ->
                                                    generatedText = text
                                                },
                                                onFailure = { e ->
                                                    errorMessage = e.message ?: context.getString(R.string.ai__error_api)
                                                }
                                            )
                                        }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Generate",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        }
                    }
                }
                // ---------- END 3-ROW PANEL ----------
                
                // Bottom action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FlorisImeSizing.keyboardRowBaseHeight * 0.85f)
                        .background(chipBackgroundColor.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back to keyboard button
                    KeyboardLikeButton(
                        elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                        inputEventDispatcher = keyboardManager.inputEventDispatcher,
                        keyData = TextKeyData.IME_UI_MODE_TEXT,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Text(
                            text = "ABC",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    // Insert generated text button (if available)
                    if (generatedText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF4CAF50))
                                .clickable {
                                    generatedText?.let { text ->
                                        // Select all text first if we were rewriting all text,
                                        // this ensures the commitText replaces the old text
                                        if (wasUsingAllText) {
                                            editorInstance.performClipboardSelectAll()
                                        }
                                        // commitText will replace any selected text
                                        editorInstance.commitText(text)
                                        Toast.makeText(context, R.string.ai__text_replaced, Toast.LENGTH_SHORT).show()
                                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Insert",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Replace",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        // Reset/Try again button
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBackgroundColor)
                                .clickable {
                                    generatedText = null
                                    errorMessage = null
                                    selectedAction = null
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Backspace button
                    KeyboardLikeButton(
                        elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                        inputEventDispatcher = keyboardManager.inputEventDispatcher,
                        keyData = TextKeyData.DELETE,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = null,
                            tint = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Theme-aware suggestion chip with gradient outline when selected
 */
@Composable
private fun MetaAiSuggestionChip(
    emoji: String,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    chipBackground: Color,
    chipSelectedBackground: Color,
    textColor: Color
) {
    val gradientBrush = Brush.horizontalGradient(
        listOf(GradientStart, GradientMiddle, GradientEnd)
    )
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        brush = gradientBrush,
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier
                }
            )
            .background(if (isSelected) chipSelectedBackground else chipBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Infinite scroll chip row - creates circular/looping scroll effect
 * by duplicating items multiple times to simulate infinite scroll
 */
@Composable
private fun InfiniteScrollChipRow(
    chips: List<String>,
    selectedChip: String?,
    onChipSelected: (String) -> Unit,
    chipBackground: Color,
    chipSelectedBackground: Color,
    textColor: Color
) {
    // Create a large list by repeating the chips many times for infinite scroll effect
    val repeatCount = 100 // Repeat enough times for seamless scrolling
    val infiniteChips = remember(chips) {
        List(chips.size * repeatCount) { index ->
            chips[index % chips.size]
        }
    }
    
    // Start from the middle to allow scrolling in both directions
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = chips.size * (repeatCount / 2)
    )
    
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(infiniteChips.size) { index ->
            val chip = infiniteChips[index]
            MetaAiSuggestionChip(
                emoji = "",
                text = chip,
                isSelected = selectedChip == chip,
                onClick = { onChipSelected(chip) },
                chipBackground = chipBackground,
                chipSelectedBackground = chipSelectedBackground,
                textColor = textColor
            )
        }
    }
}

@Composable
private fun getPersonaLabel(persona: AIPersona): String {
    return when (persona) {
        AIPersona.CASUAL -> stringResource(R.string.ai__persona_casual)
        AIPersona.ACADEMIC -> stringResource(R.string.ai__persona_academic)
        AIPersona.POETRY -> stringResource(R.string.ai__persona_poetry)
        AIPersona.PROFESSIONAL -> stringResource(R.string.ai__persona_professional)
        AIPersona.FRIENDLY -> stringResource(R.string.ai__persona_friendly)
        AIPersona.CUSTOM -> stringResource(R.string.ai__persona_custom)
    }
}

@Composable
private fun getActionLabel(action: AIAction): String {
    return when (action) {
        AIAction.REWRITE -> stringResource(R.string.ai__action_rewrite)
        AIAction.EXPAND -> stringResource(R.string.ai__action_expand)
        AIAction.SUMMARIZE -> stringResource(R.string.ai__action_summarize)
        AIAction.FIX_GRAMMAR -> stringResource(R.string.ai__action_fix_grammar)
        AIAction.MAKE_FORMAL -> stringResource(R.string.ai__action_make_formal)
        AIAction.MAKE_CASUAL -> stringResource(R.string.ai__action_make_casual)
    }
}
