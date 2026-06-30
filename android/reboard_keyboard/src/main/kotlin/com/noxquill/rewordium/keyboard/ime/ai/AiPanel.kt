/*
 * Copyright (C) 2024-2026 The ReBoard Contributors
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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.aiManager
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * AiPanel — compact AI assistant overlay anchored to the keyboard surface.
 *
 * Phase 2 redesign (May 2026) reorganizes the panel around the *action* rather
 * than packing every chip into a single fixed body. The flow per mode:
 *
 *  ┌────────────────────────────────────────┐
 *  │ AI assist                          ✕    │  header
 *  │ ▌Rewrite  Append  Enhance  Context     │  segmented mode switcher
 *  │                                          │
 *  │ Source: 32 chars selected                │  source-text hint
 *  │ Style:  [Casual●] [Pro] [Academic] [+]  │  single chip row (orthogonal
 *  │                                          │  "quick preset" chips replace
 *  │                                          │  the old persona+action stack)
 *  │ ┌────────────────────────────────────┐  │
 *  │ │ Generated text shows here.          │  │  Result card, scrolls up to
 *  │ │ Up to 6 lines visible at once,      │  │  6 lines so the user can
 *  │ │ keeps scrolling beyond that.        │  │  actually read the result
 *  │ │                                      │  │  before committing.
 *  │ └────────────────────────────────────┘  │
 *  │ [Discard]            [Replace text →]   │  Cancel + primary CTA, with
 *  └────────────────────────────────────────┘  per-mode label text.
 *
 * Each AI mode (REWRITE, APPEND, ENHANCE, CONTEXT) gets its own per-mode CTA
 * label and apply behavior so the user always knows what tapping the button
 * will do. The persona+action chip layering from the original design is
 * collapsed into a single "quick preset" chip row — the underlying AIAction
 * + AIPersona enums still drive the actual API call.
 *
 * Theme: every color comes from rememberSnyggThemeQuery so the panel
 * follows the active keyboard skin (light/dark/AMOLED/custom).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiPanel(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val aiManager by context.aiManager()

    // ── App detection (prompt enhancer hot-path) ────────────────────────
    val currentPackageName = editorInstance.activeInfo.packageName
    val isInAiApp = remember(currentPackageName) { PromptEnhancerDetector.isAiApp(currentPackageName) }
    val aiAppName = remember(currentPackageName) { PromptEnhancerDetector.getAiAppName(currentPackageName) }

    // ── Snygg-driven palette ────────────────────────────────────────────
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val smartbarStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    val bgColor = windowStyle.background()
    val surfaceColor = smartbarStyle.background()
    val onSurface = keyStyle.foreground()
    val primary = smartbarStyle.foreground().takeIf { it.alpha > 0f } ?: onSurface
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    val outline = onSurface.copy(alpha = 0.14f)
    val surfaceVariant = surfaceColor.copy(alpha = 0.55f)
    val onSurfaceVar = onSurface.copy(alpha = 0.70f)
    val errorColor = Color(0xFFBA1A1A)

    // ── State ───────────────────────────────────────────────────────────
    var aiMode by remember(isInAiApp) {
        mutableStateOf(if (isInAiApp) AiMode.ENHANCE else AiMode.REWRITE)
    }
    var selectedPersona by remember { mutableStateOf(AIPersona.CASUAL) }
    var selectedAction by remember { mutableStateOf(AIAction.REWRITE) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedText by remember { mutableStateOf<String?>(null) }
    // Typewriter buffer — accumulates streaming tokens before they become `generatedText`
    var streamingBuffer by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wasUsingAllText by remember { mutableStateOf(false) }
    var sourceTextLen by remember { mutableStateOf(0) }
    var sourceWordCount by remember { mutableStateOf(0) }
    var sourceWasSelection by remember { mutableStateOf(false) }
    var showApiKeyToast by remember { mutableStateOf(false) }
    // Remembers the last-used source + mode/action so Regenerate can replay them
    var lastSource by remember { mutableStateOf("") }
    var lastMode by remember { mutableStateOf(AiMode.REWRITE) }
    var lastAction by remember { mutableStateOf(AIAction.REWRITE) }
    var lastPersona by remember { mutableStateOf(AIPersona.CASUAL) }
    // Language picker — only active when selectedAction == TRANSLATE
    var selectedLanguage by remember { mutableStateOf("Spanish") }

    // Keep mode aligned with whether we're in an AI app — but let user
    // override (so if they explicitly flip to Rewrite inside ChatGPT, we
    // honor that intent).
    LaunchedEffect(isInAiApp) {
        if (isInAiApp && aiMode == AiMode.REWRITE) {
            aiMode = AiMode.ENHANCE
        }
    }

    // ── API-key toast → bounce to settings ──────────────────────────────
    LaunchedEffect(showApiKeyToast) {
        if (showApiKeyToast) {
            Toast.makeText(
                context,
                "No API key configured. Go to Settings → Advanced AI.",
                Toast.LENGTH_LONG,
            ).show()
            runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            }
            showApiKeyToast = false
        }
    }

    val isVisible = keyboardManager.activeState.isAiPanelVisible
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight * 5),
            color = bgColor,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ─── Header ───────────────────────────────────────────
                PanelHeader(
                    title = if (isInAiApp) stringResource(R.string.ai__prompt_enhancer_title)
                    else stringResource(R.string.ai__panel_title),
                    badgeLabel = aiAppName.takeIf { isInAiApp && it != null },
                    fg = onSurface,
                    fgMuted = onSurfaceVar,
                    accent = primary,
                    onClose = {
                        keyboardManager.activeState.isAiPanelVisible = false
                        onDismiss()
                    },
                )

                // ─── Mode segmented control ──────────────────────────
                ModeSegmented(
                    selected = aiMode,
                    modes = remember(isInAiApp) {
                        if (isInAiApp) listOf(AiMode.ENHANCE, AiMode.REWRITE, AiMode.APPEND)
                        else listOf(AiMode.REWRITE, AiMode.APPEND, AiMode.CONTEXT)
                    },
                    bg = surfaceVariant,
                    fgMuted = onSurfaceVar,
                    accent = primary,
                    onAccent = onPrimary,
                    onSelect = { aiMode = it; generatedText = null; errorMessage = null },
                )

                // ─── Source-text hint ────────────────────────────────
                SourceHint(
                    selectionLen = sourceTextLen,
                    wordCount = sourceWordCount,
                    wasSelection = sourceWasSelection,
                    fgMuted = onSurfaceVar,
                )

                // ─── Style / preset chips ────────────────────────────
                StyleChipRow(
                    selectedPersona = selectedPersona,
                    onPersonaSelected = {
                        selectedPersona = it
                        aiManager.setPersona(it)
                    },
                    selectedAction = selectedAction,
                    onActionSelected = {
                        selectedAction = it
                        generatedText = null
                        errorMessage = null
                    },
                    fgMuted = onSurfaceVar,
                    accent = primary,
                    outline = outline,
                )

                // ─── Language picker (only shown when Translate is selected) ───
                if (selectedAction == AIAction.TRANSLATE) {
                    LanguageChipRow(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { selectedLanguage = it },
                        accent = primary,
                        fgMuted = onSurfaceVar,
                        outline = outline,
                    )
                }

                // ─── Result / loading / placeholder ──────────────────
                ResultArea(
                    isGenerating = isGenerating,
                    isStreaming = isStreaming,
                    streamingText = streamingBuffer,
                    text = generatedText,
                    error = errorMessage,
                    fg = onSurface,
                    fgMuted = onSurfaceVar,
                    surface = surfaceVariant,
                    accent = primary,
                    errorColor = errorColor,
                    onCopy = { text ->
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AI result", text))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                // ─── Footer: cancel + primary CTA ────────────────────
                FooterButtons(
                    aiMode = aiMode,
                    hasResult = generatedText != null,
                    isGenerating = isGenerating,
                    fg = onSurface,
                    fgMuted = onSurfaceVar,
                    accent = primary,
                    onAccent = onPrimary,
                    outline = outline,
                    onDiscard = {
                        generatedText = null
                        streamingBuffer = ""
                        errorMessage = null
                    },
                    onRegenerate = {
                        // Re-run the exact same request that produced the last result.
                        if (lastSource.isNotBlank()) {
                            generatedText = null
                            streamingBuffer = ""
                            errorMessage = null
                            scope.launch {
                                isGenerating = true
                                isStreaming = false
                                aiManager.setPersona(lastPersona)
                                val result = when (lastMode) {
                                    AiMode.ENHANCE -> aiManager.enhancePrompt(lastSource, aiAppName)
                                    AiMode.APPEND -> aiManager.continueTextWithAction(lastSource, lastAction)
                                    AiMode.REWRITE -> aiManager.rewriteText(lastSource, lastAction)
                                    AiMode.CONTEXT -> aiManager.contextPolishText(lastSource)
                                }
                                isGenerating = false
                                result.fold(
                                    onSuccess = { generatedText = it },
                                    onFailure = { e ->
                                        val msg = e.message ?: context.getString(R.string.ai__error_api)
                                        if (msg.contains("No API key", ignoreCase = true)) {
                                            showApiKeyToast = true
                                        } else {
                                            errorMessage = msg
                                        }
                                    },
                                )
                            }
                        }
                    },
                    onPrimaryAction = {
                        val text = generatedText
                        if (text == null) {
                            // Start generation. Read source from the editor.
                            scope.launch {
                                val sel = editorInstance.activeContent.selectedText.toString()
                                val hasSel = sel.isNotBlank()
                                val source = if (hasSel) sel
                                else editorInstance.activeContent.text.toString()
                                wasUsingAllText = !hasSel
                                sourceWasSelection = hasSel
                                sourceTextLen = source.length
                                sourceWordCount = source.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                                // Snapshot for regenerate
                                lastSource = source
                                lastMode = aiMode
                                lastAction = selectedAction
                                lastPersona = selectedPersona
                                // also snapshot language for regenerate
                                val snapshotLanguage = selectedLanguage
                                if (source.isBlank()) {
                                    errorMessage = context.getString(R.string.ai__error_no_text)
                                    return@launch
                                }
                                isGenerating = true
                                isStreaming = false
                                streamingBuffer = ""
                                errorMessage = null
                                // ── Streaming typewriter path ──────────────────────────
                                try {
                                    val flow = when {
                                        aiMode == AiMode.REWRITE && selectedAction == AIAction.TRANSLATE -> null // translate is batch-only
                                        aiMode == AiMode.REWRITE -> aiManager.rewriteStreaming(source, selectedAction)
                                        else -> null // ENHANCE, APPEND, CONTEXT go to batch
                                    }
                                    if (flow != null) {
                                        isGenerating = false
                                        isStreaming = true
                                        val buf = StringBuilder()
                                        flow.collect { token ->
                                            buf.append(token)
                                            streamingBuffer = buf.toString()
                                        }
                                        isStreaming = false
                                        generatedText = buf.toString().ifBlank { null }
                                        streamingBuffer = ""
                                        return@launch
                                    }
                                } catch (_: Exception) {
                                    // streaming not available — fall through to batch
                                    isStreaming = false
                                    streamingBuffer = ""
                                    isGenerating = true
                                }
                                // ── Batch (non-streaming) fallback ────────────────────
                                val result = when (aiMode) {
                                    AiMode.ENHANCE -> aiManager.enhancePrompt(source, aiAppName)
                                    AiMode.APPEND -> aiManager.continueTextWithAction(source, selectedAction)
                                    AiMode.REWRITE -> if (selectedAction == AIAction.TRANSLATE) {
                                        aiManager.translateText(source, snapshotLanguage)
                                    } else {
                                        aiManager.rewriteText(source, selectedAction)
                                    }
                                    AiMode.CONTEXT -> aiManager.contextPolishText(source)
                                }
                                isGenerating = false
                                result.fold(
                                    onSuccess = { generatedText = it },
                                    onFailure = { e ->
                                        val msg = e.message ?: context.getString(R.string.ai__error_api)
                                        if (msg.contains("No API key", ignoreCase = true)) {
                                            showApiKeyToast = true
                                            errorMessage = null
                                        } else {
                                            errorMessage = msg
                                        }
                                    },
                                )
                            }
                        } else {
                            // Commit the generated text per-mode.
                            runCatching {
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
                                keyboardManager.activeState.isAiPanelVisible = false
                            }.onFailure {
                                Toast.makeText(context, R.string.ai__error_api, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────

@Composable
private fun PanelHeader(
    title: String,
    badgeLabel: String?,
    fg: Color,
    fgMuted: Color,
    accent: Color,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                letterSpacing = 0.3.sp,
            )
            if (badgeLabel != null) {
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "• $badgeLabel",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                    )
                }
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Default.Close, "Close", tint = fgMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ModeSegmented(
    selected: AiMode,
    modes: List<AiMode>,
    bg: Color,
    fgMuted: Color,
    accent: Color,
    onAccent: Color,
    onSelect: (AiMode) -> Unit,
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            modes.forEach { mode ->
                val isSelected = selected == mode
                val segBg by animateColorAsState(
                    if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                    tween(220),
                    label = "modeBg",
                )
                val segFg by animateColorAsState(
                    if (isSelected) accent else fgMuted,
                    tween(220),
                    label = "modeFg",
                )
                Surface(
                    color = segBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(mode) },
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painter = mode.icon(), contentDescription = null, tint = segFg, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            mode.label(),
                            color = segFg,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHint(
    selectionLen: Int,
    wordCount: Int,
    wasSelection: Boolean,
    fgMuted: Color,
) {
    val label = when {
        selectionLen == 0 -> "No text in field yet"
        wasSelection -> "Source: $selectionLen chars · ~$wordCount words selected"
        else -> "Source: all text · ~$wordCount words"
    }
    Text(
        label,
        fontSize = 10.sp,
        color = fgMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun StyleChipRow(
    selectedPersona: AIPersona,
    onPersonaSelected: (AIPersona) -> Unit,
    selectedAction: AIAction,
    onActionSelected: (AIAction) -> Unit,
    fgMuted: Color,
    accent: Color,
    outline: Color,
) {
    // Single horizontal scroll row that lists actions (REWRITE-class) first,
    // then personas (voice / tone). Visually treated identically — the user
    // doesn't need to know which enum each chip maps to.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AIAction.entries.forEach { action ->
            ChipPill(
                label = actionLabel(action),
                selected = selectedAction == action,
                accent = accent,
                fgMuted = fgMuted,
                outline = outline,
                onClick = { onActionSelected(action) },
            )
        }
        // Visual divider between action presets and voice presets.
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(1.dp)
                .background(outline)
                .padding(horizontal = 2.dp),
        )
        Spacer(Modifier.width(2.dp))
        AIPersona.entries.forEach { persona ->
            ChipPill(
                label = personaLabel(persona),
                selected = selectedPersona == persona,
                accent = accent,
                fgMuted = fgMuted,
                outline = outline,
                onClick = { onPersonaSelected(persona) },
            )
        }
    }
}

@Composable
private fun ChipPill(
    label: String,
    selected: Boolean,
    accent: Color,
    fgMuted: Color,
    outline: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
        tween(180),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) accent else fgMuted,
        tween(180),
        label = "chipFg",
    )
    // Animate scale: 0.95 on press, 1.0 at rest (instant press, eased release)
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = if (isPressed) tween(80) else tween(160),
        label = "chipScale",
    )
    val borderColor = if (selected) accent.copy(alpha = 0.3f) else outline
    Surface(
        color = bg,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier
            .height(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(15.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .background(Color.Transparent, RoundedCornerShape(15.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ResultArea(
    isGenerating: Boolean,
    isStreaming: Boolean,
    streamingText: String,
    text: String?,
    error: String?,
    fg: Color,
    fgMuted: Color,
    surface: Color,
    accent: Color,
    errorColor: Color,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Copy button state — shows a brief ✓ after copy
    var didCopy by remember { mutableStateOf(false) }
    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(1_500)
            didCopy = false
        }
    }

    Surface(
        color = surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                isGenerating -> {
                    // Shimmering skeleton bars — accent-tinted with a travelling
                    // highlight so the loading state feels alive, not frozen.
                    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                    val shimmerOffset by infiniteTransition.animateFloat(
                        initialValue = -1f,
                        targetValue = 2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1_200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "shimmerOffset",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShimmerBar(accent, shimmerOffset, 1f)
                        ShimmerBar(accent, shimmerOffset, 0.82f)
                        ShimmerBar(accent, shimmerOffset, 0.60f)
                    }
                }
                isStreaming && streamingText.isNotBlank() -> {
                    // Typewriter: show buffered tokens as they arrive.
                    // The trailing cursor blinks to indicate live generation.
                    val cursorVisible by rememberInfiniteTransition(label = "cursor").animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "cursorAlpha",
                    )
                    Text(
                        streamingText + "▍",
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .graphicsLayer { alpha = 1f }, // force layer
                        color = fg,
                        fontSize = 13.sp,
                    )
                }
                error != null -> {
                    Text(
                        error,
                        color = errorColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Start,
                    )
                }
                text != null -> {
                    // Show the result text + a Copy icon in the top-right corner
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 28.dp) // make room for copy icon
                                .verticalScroll(rememberScrollState()),
                            color = fg,
                            fontSize = 13.sp,
                        )
                        // Copy / Copied checkmark button
                        IconButton(
                            onClick = {
                                onCopy(text)
                                didCopy = true
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.TopEnd),
                        ) {
                            Icon(
                                imageVector = if (didCopy) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (didCopy) "Copied" else "Copy",
                                tint = if (didCopy) accent else fgMuted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        "Pick an action and tap the button to generate.",
                        color = fgMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Skeleton bar with a travelling shimmer highlight.
 * [shimmerOffset] should range from -1..2 (driven by an InfiniteTransition).
 */
@Composable
private fun ShimmerBar(accent: Color, shimmerOffset: Float, widthFraction: Float) {
    val baseColor = accent.copy(alpha = 0.12f)
    val highlightColor = accent.copy(alpha = 0.32f)
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        baseColor,
                        highlightColor,
                        baseColor,
                    ),
                    startX = shimmerOffset * 400f,
                    endX = (shimmerOffset + 1f) * 400f,
                ),
            ),
    )
}

@Composable
private fun FooterButtons(
    aiMode: AiMode,
    hasResult: Boolean,
    isGenerating: Boolean,
    fg: Color,
    fgMuted: Color,
    accent: Color,
    onAccent: Color,
    outline: Color,
    onDiscard: () -> Unit,
    onRegenerate: () -> Unit,
    onPrimaryAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasResult) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = fgMuted),
            ) {
                Text("Discard", fontSize = 12.sp)
            }
            IconButton(
                onClick = onRegenerate,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Refresh, "Regenerate", tint = fgMuted, modifier = Modifier.size(18.dp))
            }
        }
        FilledTonalButton(
            onClick = onPrimaryAction,
            enabled = !isGenerating,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = accent,
                contentColor = onAccent,
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(if (hasResult) 2f else 1f).height(36.dp),
        ) {
            Icon(
                painter = aiMode.actionIcon(hasResult),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                aiMode.actionLabel(hasResult),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Enum → display helpers ──────────────────────────────────────────────

private fun AiMode.label(): String = when (this) {
    AiMode.REWRITE -> "Rewrite"
    AiMode.APPEND -> "Append"
    AiMode.ENHANCE -> "Enhance"
    AiMode.CONTEXT -> "Context"
}

@Composable
private fun AiMode.icon(): Painter = when (this) {
    AiMode.REWRITE, AiMode.ENHANCE -> painterResource(id = R.drawable.ic_quick_ai_rewrite)
    AiMode.APPEND -> painterResource(id = R.drawable.ic_quick_ai_append)
    AiMode.CONTEXT -> painterResource(id = R.drawable.ic_quick_ai_context)
}

// Per-mode CTA: when a result is on screen, the button commits it; otherwise
// it kicks off generation. Mode-specific verbs make the action unambiguous.
private fun AiMode.actionLabel(hasResult: Boolean): String = when (this) {
    AiMode.REWRITE -> if (hasResult) "Replace text" else "Rewrite"
    AiMode.APPEND -> if (hasResult) "Insert below" else "Generate"
    AiMode.ENHANCE -> if (hasResult) "Apply" else "Enhance prompt"
    AiMode.CONTEXT -> if (hasResult) "Apply" else "Polish"
}

@Composable
private fun AiMode.actionIcon(hasResult: Boolean): Painter = when {
    !hasResult -> icon()
    this == AiMode.APPEND -> painterResource(id = R.drawable.ic_quick_ai_append)
    else -> rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
}

@Composable
private fun personaLabel(persona: AIPersona): String = when (persona) {
    AIPersona.CASUAL -> stringResource(R.string.ai__persona_casual)
    AIPersona.ACADEMIC -> stringResource(R.string.ai__persona_academic)
    AIPersona.POETRY -> stringResource(R.string.ai__persona_poetry)
    AIPersona.PROFESSIONAL -> stringResource(R.string.ai__persona_professional)
    AIPersona.FRIENDLY -> stringResource(R.string.ai__persona_friendly)
    AIPersona.CUSTOM -> stringResource(R.string.ai__persona_custom)
}

@Composable
private fun actionLabel(action: AIAction): String = when (action) {
    AIAction.REWRITE -> stringResource(R.string.ai__action_rewrite)
    AIAction.EXPAND -> stringResource(R.string.ai__action_expand)
    AIAction.SUMMARIZE -> stringResource(R.string.ai__action_summarize)
    AIAction.FIX_GRAMMAR -> stringResource(R.string.ai__action_fix_grammar)
    AIAction.MAKE_FORMAL -> stringResource(R.string.ai__action_make_formal)
    AIAction.MAKE_CASUAL -> stringResource(R.string.ai__action_make_casual)
    AIAction.TRANSLATE -> stringResource(R.string.ai__action_translate)
}

// ── Language picker row (shown only when Translate action is selected) ────

private val TRANSLATE_LANGUAGES = listOf(
    "Spanish", "French", "German", "Italian", "Portuguese",
    "Hindi", "Arabic", "Japanese", "Korean", "Chinese",
    "Russian", "Turkish", "Dutch", "Polish", "Swedish",
    "Greek", "Hebrew", "Thai", "Vietnamese", "Indonesian",
)

@Composable
private fun LanguageChipRow(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    accent: Color,
    fgMuted: Color,
    outline: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TRANSLATE_LANGUAGES.forEach { lang ->
            ChipPill(
                label = lang,
                selected = selectedLanguage == lang,
                accent = accent,
                fgMuted = fgMuted,
                outline = outline,
                onClick = { onLanguageSelected(lang) },
            )
        }
    }
}

