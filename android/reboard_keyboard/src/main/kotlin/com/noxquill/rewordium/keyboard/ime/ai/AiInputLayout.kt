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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.noxquill.rewordium.keyboard.ime.input.LocalInputFeedbackController
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.media.KeyboardLikeButton
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * AI generation mode: Rewrite (replace), Append (continue/append), or Enhance (prompt enhancer)
 */
enum class AiMode { CONTEXT, REWRITE, APPEND, ENHANCE }

/**
 * AI Input Layout — full-screen keyboard panel for writing assistance.
 * Uses system keyboard theme colors for a native, professional appearance.
 * 3-row chip selector (persona / task / length) + mode toggle + generate button.
 * Automatically switches to "Prompt Enhancer" mode when in AI apps.
 */
@Composable
fun AiInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val aiManager by context.aiManager()
    val inputFeedbackController = LocalInputFeedbackController.current
    val scope = rememberCoroutineScope()

    // ── AI app detection for prompt enhancer ──
    val currentPackageName = editorInstance.activeInfo.packageName
    val isInAiApp = remember(currentPackageName) { PromptEnhancerDetector.isAiApp(currentPackageName) }
    val aiAppName = remember(currentPackageName) { PromptEnhancerDetector.getAiAppName(currentPackageName) }

    // ── System keyboard theme colors ──
    val windowStyle   = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val keyStyle      = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val smartbarStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)

    val bgColor        = windowStyle.background()
    val surfaceColor   = keyStyle.background()
    val onSurface      = keyStyle.foreground()
    val primary        = smartbarStyle.foreground().takeIf { it.alpha > 0f } ?: onSurface
    val onPrimary      = bgColor
    val outline        = onSurface.copy(alpha = 0.12f)
    val surfaceVariant = surfaceColor.copy(alpha = 0.55f)
    val onSurfaceVar   = onSurface.copy(alpha = 0.65f)
    val errorColor     = Color(0xFFBA1A1A)

    // ── State ──
    var isGenerating    by remember { mutableStateOf(false) }
    var generatedText   by remember { mutableStateOf<String?>(null) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var wasUsingAllText by remember { mutableStateOf(false) }
    var aiMode          by remember { mutableStateOf(if (isInAiApp) AiMode.ENHANCE else AiMode.CONTEXT) }
    var selectedPersona by remember { mutableStateOf<String?>(null) }
    var selectedTask    by remember { mutableStateOf<String?>(null) }
    var selectedLength  by remember { mutableStateOf<String?>(null) }

    // Auto-switch to ENHANCE mode when AI app is detected
    LaunchedEffect(isInAiApp) {
        if (isInAiApp && aiMode != AiMode.ENHANCE) {
            aiMode = AiMode.ENHANCE
        } else if (!isInAiApp && aiMode == AiMode.ENHANCE) {
            aiMode = AiMode.CONTEXT
        }
    }

    val showIdleControls = !isGenerating && generatedText == null
    val showAdvancedChips = showIdleControls && (aiMode == AiMode.REWRITE || aiMode == AiMode.APPEND)
    val collapseIdleContentArea = showAdvancedChips && errorMessage == null

    // ── Chip data — clean labels, no emojis, professional ──
    val personaChips = remember { listOf(
        "Casual", "Academic", "Professional", "Friendly",
        "Creative", "Dramatic", "Calm", "Motivational",
        "Technical", "Poetic", "Journalistic", "Simple"
    ) }
    val taskChips = remember { listOf(
        "Email", "Work", "Academic", "Humor", "Polite",
        "Translate", "Rewrite", "Summarize", "Expand",
        "Fix Grammar", "Essay", "Pitch",
        "Marketing", "Apology", "Congratulate", "Report",
        "Brainstorm", "Paraphrase", "Announce", "Request",
        "Introduction", "Speech", "Social Media", "Chat Reply"
    ) }
    val lengthChips = remember { listOf(
        "Very Short", "Short", "Medium", "Long",
        "Detailed", "Concise", "Elaborate", "Bullet Points"
    ) }

    // ── Generate logic ──
    fun doGenerate() {
        scope.launch {
            val sel = editorInstance.activeContent.selectedText
            val hasSel = sel.toString().isNotBlank()
            val text = if (hasSel) sel.toString() else editorInstance.activeContent.text.toString()
            wasUsingAllText = !hasSel
            if (text.isBlank()) { errorMessage = context.getString(R.string.ai__error_no_text); return@launch }

            isGenerating = true; errorMessage = null; generatedText = null
            val persona = selectedPersona ?: ""
            val task = selectedTask ?: ""
            val length = selectedLength ?: ""

            val result = when (aiMode) {
                AiMode.ENHANCE -> {
                    // Prompt Enhancer mode — enhance the prompt for AI apps
                    aiManager.enhancePrompt(text, aiAppName)
                }
                AiMode.CONTEXT -> {
                    // Keeps tone/slang while cleaning grammar and clarity.
                    aiManager.contextPolishText(text)
                }
                AiMode.APPEND -> {
                    aiManager.continueText(text, persona, task, length)
                }
                AiMode.REWRITE -> {
                    val action = when {
                        task.contains("Summarize", ignoreCase = true) -> AIAction.SUMMARIZE
                        task.contains("Expand", ignoreCase = true) -> AIAction.EXPAND
                        task.contains("Grammar", ignoreCase = true) -> AIAction.FIX_GRAMMAR
                        task.contains("Polite", ignoreCase = true) || task.contains("Academic", ignoreCase = true) -> AIAction.MAKE_FORMAL
                        task.contains("Humor", ignoreCase = true) || task.contains("Casual", ignoreCase = true) -> AIAction.MAKE_CASUAL
                        else -> AIAction.REWRITE
                    }
                    val fullPrompt = buildString {
                        appendLine("STYLE: ${if (persona.isNotBlank()) persona else "Keep original voice"}")
                        appendLine("INTENT: ${if (task.isNotBlank()) task else "Rewrite for clarity"}")
                        appendLine("LENGTH: ${if (length.isNotBlank()) length else "Match source length"}")
                        appendLine("ACTION: ${action.name}")
                        appendLine()
                        appendLine("SOURCE_TEXT:")
                        append(text)
                    }
                    aiManager.rewriteTextWithPrompt(fullPrompt, action)
                }
            }

            isGenerating = false
            result.fold(
                onSuccess = { generatedText = it },
                onFailure = { e -> errorMessage = e.message ?: context.getString(R.string.ai__error_api) }
            )
        }
    }

    fun applyGeneratedTextAndExit() {
        val text = generatedText ?: return
        try {
            when (aiMode) {
                AiMode.ENHANCE, AiMode.REWRITE, AiMode.CONTEXT -> {
                    if (wasUsingAllText) editorInstance.performClipboardSelectAll()
                    editorInstance.commitText(text)
                    val toastRes = if (aiMode == AiMode.ENHANCE) {
                        R.string.ai__prompt_enhancer_replaced
                    } else {
                        R.string.ai__text_replaced
                    }
                    Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
                }
                AiMode.APPEND -> {
                    val currentText = editorInstance.activeContent.text.toString()
                    if (currentText.isNotEmpty()) {
                        val endPos = currentText.length
                        editorInstance.setSelection(endPos, endPos)
                        editorInstance.commitText("\n\n$text")
                    } else {
                        editorInstance.commitText(text)
                    }
                    Toast.makeText(context, R.string.ai__text_inserted, Toast.LENGTH_SHORT).show()
                }
            }
            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
        } catch (_: Exception) {
            Toast.makeText(context, R.string.ai__error_api, Toast.LENGTH_SHORT).show()
        }
    }

    // Keep AI panel height stable and tall enough for all chip rows without scrolling.
    val aiPanelHeight = FlorisImeSizing.keyboardRowBaseHeight * 5 + FlorisImeSizing.smartbarHeight

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(aiPanelHeight)
                .background(bgColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ════════ Header ════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (isInAiApp) stringResource(R.string.ai__prompt_enhancer_title)
                            else stringResource(R.string.ai__panel_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface,
                            letterSpacing = 0.3.sp
                        )
                        if (isInAiApp) {
                            Surface(
                                color = primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    aiAppName ?: "AI",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primary
                                )
                            }
                        }
                    }
                    // Selection indicator
                    val activeFilters = listOfNotNull(selectedPersona, selectedTask, selectedLength)
                    if (activeFilters.isNotEmpty()) {
                        Text(
                            activeFilters.joinToString(" \u00B7 "),
                            fontSize = 10.sp,
                            color = onSurfaceVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }
                }

                // ════════ Mode segmented toggle ════════
                Surface(
                    color = surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val modeList = if (isInAiApp) {
                        listOf(
                            AiMode.ENHANCE to R.string.ai__mode_enhance,
                            AiMode.REWRITE to R.string.ai__mode_rewrite,
                            AiMode.APPEND to R.string.ai__mode_append
                        )
                    } else {
                        listOf(
                            AiMode.CONTEXT to R.string.ai__mode_context,
                            AiMode.REWRITE to R.string.ai__mode_rewrite,
                            AiMode.APPEND to R.string.ai__mode_append
                        )
                    }
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        modeList.forEach { (mode, labelRes) ->
                            val selected = aiMode == mode
                            val bg by animateColorAsState(
                                if (selected) primary else Color.Transparent, tween(200), label = "bg"
                            )
                            val fg by animateColorAsState(
                                if (selected) onPrimary else onSurfaceVar, tween(200), label = "fg"
                            )
                            Surface(
                                color = bg,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        inputFeedbackController.keyPress()
                                        aiMode = mode
                                        generatedText = null
                                        errorMessage = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        when (mode) {
                                            AiMode.CONTEXT -> Icons.Default.AutoFixHigh
                                            AiMode.REWRITE, AiMode.ENHANCE -> Icons.Default.Edit
                                            AiMode.APPEND -> Icons.Default.ArrowDownward
                                        },
                                        null, tint = fg, modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(labelRes), color = fg, fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                    )
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ════════ Content area with animated transitions ════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (collapseIdleContentArea) {
                                Modifier.height(0.dp)
                            } else {
                                Modifier.weight(1f)
                            }
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val contentKey = when {
                        isGenerating -> "loading"
                        generatedText != null -> "result"
                        errorMessage != null -> "error"
                        else -> "idle"
                    }
                    AnimatedContent(
                        targetState = contentKey,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "ai_content"
                    ) { state ->
                        when (state) {
                            "loading" -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth(0.5f)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp)),
                                        color = primary,
                                        trackColor = outline
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        when (aiMode) {
                                            AiMode.ENHANCE -> "Enhancing\u2026"
                                            AiMode.CONTEXT -> "Polishing\u2026"
                                            else -> "Processing\u2026"
                                        },
                                        color = onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        when (aiMode) {
                                            AiMode.ENHANCE -> "Making your prompt more effective"
                                            AiMode.CONTEXT -> "Keeping your vibe while fixing grammar"
                                            else -> "Enhancing your text"
                                        },
                                        color = onSurfaceVar,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            "result" -> {
                                val generated = generatedText
                                if (generated != null) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.7f)),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    generated,
                                                    color = onSurface,
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "Result not available",
                                            color = onSurfaceVar,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                            "error" -> {
                                val message = errorMessage
                                if (message != null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            message,
                                            color = errorColor,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                inputFeedbackController.keyPress()
                                                errorMessage = null
                                            },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = surfaceVariant, contentColor = onSurface
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Try Again", fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "Something went wrong",
                                            color = errorColor,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            else -> {
                                when (aiMode) {
                                    AiMode.ENHANCE,
                                    AiMode.CONTEXT -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                when (aiMode) {
                                                    AiMode.ENHANCE -> "Write your prompt in the text field"
                                                    AiMode.CONTEXT -> stringResource(R.string.ai__context_mode_hint)
                                                    else -> ""
                                                },
                                                color = onSurfaceVar,
                                                fontSize = 14.sp,
                                            )
                                            Spacer(Modifier.height(0.dp))
                                            Text(
                                                when (aiMode) {
                                                    AiMode.ENHANCE -> "then tap Enhance to make it better"
                                                    AiMode.CONTEXT -> "It auto-preserves slang and tone"
                                                    else -> ""
                                                },
                                                color = onSurfaceVar.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                    AiMode.REWRITE,
                                    AiMode.APPEND -> {
                                        Spacer(Modifier.height(0.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ════════ 3-Row chip selector (only when idle and NOT in enhance mode) ════════
                if (showIdleControls) {
                    val chipAreaMaxHeight = if (showAdvancedChips) {
                        FlorisImeSizing.keyboardRowBaseHeight * 2.2f
                    } else {
                        FlorisImeSizing.keyboardRowBaseHeight * 1.1f
                    }
                    val chipAreaModifier = if (showAdvancedChips) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.heightIn(max = chipAreaMaxHeight)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(chipAreaModifier)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (showAdvancedChips) {
                                SmartChipSection(
                                    title = "Style",
                                    chips = personaChips,
                                    selectedChip = selectedPersona,
                                    onChipSelected = { selectedPersona = if (selectedPersona == it) null else it },
                                    primary = primary,
                                    surfaceVariant = surfaceVariant,
                                    onSurface = onSurface,
                                    outline = outline
                                )
                                SmartChipSection(
                                    title = "Intent",
                                    chips = taskChips,
                                    selectedChip = selectedTask,
                                    onChipSelected = { selectedTask = if (selectedTask == it) null else it },
                                    primary = primary,
                                    surfaceVariant = surfaceVariant,
                                    onSurface = onSurface,
                                    outline = outline
                                )
                                SmartChipSection(
                                    title = "Length",
                                    chips = lengthChips,
                                    selectedChip = selectedLength,
                                    onChipSelected = { selectedLength = if (selectedLength == it) null else it },
                                    primary = primary,
                                    surfaceVariant = surfaceVariant,
                                    onSurface = onSurface,
                                    outline = outline
                                )
                            }
                        }
                    }
                }

                // ════════ Material action row (ABC | primary action | Backspace) ════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FlorisImeSizing.keyboardRowBaseHeight)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyboardLikeButton(
                        inputEventDispatcher = keyboardManager.inputEventDispatcher,
                        keyData = TextKeyData.IME_UI_MODE_TEXT,
                        elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.84f),
                    ) {
                        Text(
                            "ABC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    if (generatedText != null) {
                        Row(
                            modifier = Modifier
                                .weight(2.8f)
                                .fillMaxHeight(0.84f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    inputFeedbackController.keyPress()
                                    applyGeneratedTextAndExit()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = primary,
                                    contentColor = onPrimary,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            ) {
                                Icon(
                                    when (aiMode) {
                                        AiMode.ENHANCE, AiMode.REWRITE, AiMode.CONTEXT -> Icons.Default.Check
                                        AiMode.APPEND -> Icons.Default.ArrowDownward
                                    },
                                    null,
                                    modifier = Modifier.size(19.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when (aiMode) {
                                        AiMode.ENHANCE -> "Apply"
                                        AiMode.CONTEXT -> "Apply"
                                        AiMode.REWRITE -> "Replace"
                                        AiMode.APPEND -> "Insert"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    inputFeedbackController.keyPress()
                                    generatedText = null
                                    errorMessage = null
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = surfaceColor,
                                    contentColor = onSurface,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(44.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                inputFeedbackController.keyPress()
                                doGenerate()
                            },
                            enabled = !isGenerating,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = primary,
                                contentColor = onPrimary,
                                disabledContainerColor = primary.copy(alpha = 0.45f),
                                disabledContentColor = onPrimary.copy(alpha = 0.65f),
                            ),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(2.8f)
                                .fillMaxHeight(0.84f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                when (aiMode) {
                                    AiMode.ENHANCE, AiMode.REWRITE -> Icons.Default.Edit
                                    AiMode.APPEND -> Icons.Default.ArrowDownward
                                    AiMode.CONTEXT -> Icons.Default.AutoFixHigh
                                },
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (aiMode) {
                                    AiMode.ENHANCE -> stringResource(R.string.ai__prompt_enhancer_action)
                                    AiMode.CONTEXT -> stringResource(R.string.ai__context_mode_action)
                                    else -> "Generate"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    KeyboardLikeButton(
                        inputEventDispatcher = keyboardManager.inputEventDispatcher,
                        keyData = TextKeyData.DELETE,
                        elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.84f),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Backspace, null, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// System-styled chip row
// ──────────────────────────────────────────────

@Composable
private fun SmartChipSection(
    title: String,
    chips: List<String>,
    selectedChip: String?,
    onChipSelected: (String) -> Unit,
    primary: Color,
    surfaceVariant: Color,
    onSurface: Color,
    outline: Color,
) {
    Surface(
        color = surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = onSurface.copy(alpha = 0.82f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (!selectedChip.isNullOrBlank()) {
                    Surface(
                        color = primary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            selectedChip,
                            color = primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 120.dp)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            SystemChipRow(
                chips = chips,
                selectedChip = selectedChip,
                onChipSelected = onChipSelected,
                primary = primary,
                surfaceVariant = surfaceVariant,
                onSurface = onSurface,
                outline = outline,
            )
        }
    }
}

@Composable
private fun SystemChipRow(
    chips: List<String>,
    selectedChip: String?,
    onChipSelected: (String) -> Unit,
    primary: Color,
    surfaceVariant: Color,
    onSurface: Color,
    outline: Color
) {
    val inputFeedbackController = LocalInputFeedbackController.current

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        items(chips.size) { index ->
            val chip = chips[index]
            val isSelected = selectedChip == chip
            FilterChip(
                selected = isSelected,
                onClick = {
                    inputFeedbackController.keyPress()
                    onChipSelected(chip)
                },
                label = {
                    Text(
                        chip,
                        fontSize = 11.sp,
                        maxLines = 1,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = surfaceVariant,
                    labelColor = onSurface,
                    selectedContainerColor = primary.copy(alpha = 0.18f),
                    selectedLabelColor = primary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderWidth = 0.dp,
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(34.dp)
                    .widthIn(min = 74.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────

@Composable
private fun getPersonaLabel(persona: AIPersona): String = when (persona) {
    AIPersona.CASUAL       -> stringResource(R.string.ai__persona_casual)
    AIPersona.ACADEMIC     -> stringResource(R.string.ai__persona_academic)
    AIPersona.POETRY       -> stringResource(R.string.ai__persona_poetry)
    AIPersona.PROFESSIONAL -> stringResource(R.string.ai__persona_professional)
    AIPersona.FRIENDLY     -> stringResource(R.string.ai__persona_friendly)
    AIPersona.CUSTOM       -> stringResource(R.string.ai__persona_custom)
}

@Composable
private fun getActionLabel(action: AIAction): String = when (action) {
    AIAction.REWRITE     -> stringResource(R.string.ai__action_rewrite)
    AIAction.EXPAND      -> stringResource(R.string.ai__action_expand)
    AIAction.SUMMARIZE   -> stringResource(R.string.ai__action_summarize)
    AIAction.FIX_GRAMMAR -> stringResource(R.string.ai__action_fix_grammar)
    AIAction.MAKE_FORMAL -> stringResource(R.string.ai__action_make_formal)
    AIAction.MAKE_CASUAL -> stringResource(R.string.ai__action_make_casual)
}
