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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.aiManager
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.media.KeyboardLikeButton
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * AI generation mode: Rewrite (replace), Append (continue/append), or Enhance (prompt enhancer)
 */
enum class AiMode { REWRITE, APPEND, ENHANCE }

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
    var aiMode          by remember { mutableStateOf(if (isInAiApp) AiMode.ENHANCE else AiMode.REWRITE) }
    var selectedPersona by remember { mutableStateOf<String?>(null) }
    var selectedTask    by remember { mutableStateOf<String?>(null) }
    var selectedLength  by remember { mutableStateOf<String?>(null) }

    // Auto-switch to ENHANCE mode when AI app is detected
    LaunchedEffect(isInAiApp) {
        if (isInAiApp && aiMode != AiMode.ENHANCE) {
            aiMode = AiMode.ENHANCE
        }
    }

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
                AiMode.APPEND -> {
                    aiManager.continueText(text, persona, task, length)
                }
                AiMode.REWRITE -> {
                    val fullPrompt = buildString {
                        if (persona.isNotBlank()) append("Persona: $persona. ")
                        if (task.isNotBlank()) append("Task: $task. ")
                        if (length.isNotBlank()) append("Length: $length. ")
                        append("Rewrite this: $text")
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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.imeUiHeight())
                .background(bgColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ════════ Header ════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    // Prompt Enhancer badge when in AI app
                    if (isInAiApp) {
                        Surface(
                            color = primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "• ${aiAppName ?: "AI"}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = primary
                            )
                        }
                    }
                }

                // ════════ Mode segmented toggle ════════
                Surface(
                    color = surfaceVariant.copy(alpha = 0.5f),
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
                            AiMode.REWRITE to R.string.ai__mode_rewrite,
                            AiMode.APPEND to R.string.ai__mode_append
                        )
                    }
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        modeList.forEach { (mode, labelRes) ->
                            val selected = aiMode == mode
                            val bg by animateColorAsState(
                                if (selected) primary else Color.Transparent, tween(220), label = "bg"
                            )
                            val fg by animateColorAsState(
                                if (selected) onPrimary else onSurfaceVar, tween(220), label = "fg"
                            )
                            Surface(
                                color = bg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { aiMode = mode; generatedText = null; errorMessage = null }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        if (mode == AiMode.REWRITE) Icons.Default.Edit else Icons.Default.VerticalAlignBottom,
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

                // ════════ Content area ════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isGenerating -> {
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
                                    if (aiMode == AiMode.ENHANCE) "Enhancing…" else "Processing…",
                                    color = onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (aiMode == AiMode.ENHANCE) "Making your prompt more effective" else "Enhancing your text",
                                    color = onSurfaceVar,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        generatedText != null -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                            ) {
                                // Result card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, outline),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            generatedText!!,
                                            color = onSurface,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                // Action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { doGenerate() },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = surfaceVariant,
                                            contentColor = onSurface
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Regenerate",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            generatedText = null; errorMessage = null
                                            selectedPersona = null; selectedTask = null; selectedLength = null
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = onSurface),
                                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "New Prompt",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                        errorMessage != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    errorMessage!!,
                                    color = errorColor,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                FilledTonalButton(
                                    onClick = { errorMessage = null },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = surfaceVariant, contentColor = onSurface
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Try Again", fontSize = 13.sp)
                                }
                            }
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (aiMode == AiMode.ENHANCE) "Write your prompt in the text field"
                                    else "Select options below",
                                    color = onSurfaceVar,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (aiMode == AiMode.ENHANCE) "then tap Enhance to make it better"
                                    else "then tap Generate to enhance your text",
                                    color = onSurfaceVar.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ════════ 3-Row chip selector (only when idle and NOT in enhance mode) ════════
                if (!isGenerating && generatedText == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Only show chip rows for Rewrite/Append modes
                        if (aiMode != AiMode.ENHANCE) {
                            // Row 1 — Persona
                            SystemChipRow(
                                chips = personaChips,
                                selectedChip = selectedPersona,
                                onChipSelected = { selectedPersona = if (selectedPersona == it) null else it },
                                primary = primary,
                                surfaceVariant = surfaceVariant,
                                onSurface = onSurface,
                                onSurfaceVar = onSurfaceVar,
                                outline = outline
                            )
                            // Row 2 — Task
                            SystemChipRow(
                                chips = taskChips,
                                selectedChip = selectedTask,
                                onChipSelected = { selectedTask = if (selectedTask == it) null else it },
                                primary = primary,
                                surfaceVariant = surfaceVariant,
                                onSurface = onSurface,
                                onSurfaceVar = onSurfaceVar,
                                outline = outline
                            )
                            // Row 3 — Length
                            SystemChipRow(
                                chips = lengthChips,
                                selectedChip = selectedLength,
                                onChipSelected = { selectedLength = if (selectedLength == it) null else it },
                                primary = primary,
                                surfaceVariant = surfaceVariant,
                                onSurface = onSurface,
                                onSurfaceVar = onSurfaceVar,
                                outline = outline
                            )
                        }

                        // Generate CTA
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilledTonalButton(
                                onClick = { doGenerate() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = primary,
                                    contentColor = onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (aiMode == AiMode.ENHANCE) stringResource(R.string.ai__prompt_enhancer_action) else "Generate",
                                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // ════════ Bottom action bar ════════
                Surface(
                    color = surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FlorisImeSizing.keyboardRowBaseHeight)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ABC button
                        KeyboardLikeButton(
                            elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                            inputEventDispatcher = keyboardManager.inputEventDispatcher,
                            keyData = TextKeyData.IME_UI_MODE_TEXT,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            Text(
                                "ABC",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        if (generatedText != null) {
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    generatedText?.let { text ->
                                        when (aiMode) {
                                            AiMode.ENHANCE, AiMode.REWRITE -> {
                                                if (wasUsingAllText) editorInstance.performClipboardSelectAll()
                                                editorInstance.commitText(text)
                                                val toastRes = if (aiMode == AiMode.ENHANCE) R.string.ai__prompt_enhancer_replaced else R.string.ai__text_replaced
                                                Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
                                            }
                                            AiMode.APPEND -> {
                                                val currentText = editorInstance.activeContent.text
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
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = primary,
                                    contentColor = onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(2.5f).fillMaxHeight(0.75f)
                            ) {
                                Icon(
                                    when (aiMode) {
                                        AiMode.ENHANCE, AiMode.REWRITE -> Icons.Default.Check
                                        AiMode.APPEND -> Icons.Default.VerticalAlignBottom
                                    },
                                    null, modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when (aiMode) {
                                        AiMode.ENHANCE -> "Apply"
                                        AiMode.REWRITE -> "Replace"
                                        AiMode.APPEND -> "Insert"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Spacer(Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = { generatedText = null; errorMessage = null },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = surfaceColor.copy(alpha = 0.8f),
                                    contentColor = onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).fillMaxHeight(0.75f)
                            ) {
                                Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(15.dp))
                            }
                        } else {
                            Spacer(Modifier.weight(3.5f))
                        }

                        Spacer(Modifier.width(8.dp))

                        // Backspace
                        KeyboardLikeButton(
                            elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                            inputEventDispatcher = keyboardManager.inputEventDispatcher,
                            keyData = TextKeyData.DELETE,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, null)
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// System-styled infinite scroll chip row
// ──────────────────────────────────────────────

@Composable
private fun SystemChipRow(
    chips: List<String>,
    selectedChip: String?,
    onChipSelected: (String) -> Unit,
    primary: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onSurfaceVar: Color,
    outline: Color
) {
    val repeatCount = 100
    val infiniteChips = remember(chips) {
        List(chips.size * repeatCount) { chips[it % chips.size] }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = chips.size * (repeatCount / 2))

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(infiniteChips.size) { index ->
            val chip = infiniteChips[index]
            val isSelected = selectedChip == chip
            FilterChip(
                selected = isSelected,
                onClick = { onChipSelected(chip) },
                label = {
                    Text(
                        chip,
                        fontSize = 12.sp,
                        maxLines = 1,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = onSurfaceVar,
                    selectedContainerColor = primary.copy(alpha = 0.12f),
                    selectedLabelColor = primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderWidth = 0.8.dp,
                    borderColor = outline,
                    selectedBorderColor = primary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(32.dp)
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
