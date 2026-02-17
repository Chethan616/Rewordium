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

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.aiManager
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * AiPanel — compact Material 3 overlay inside the keyboard.
 * Uses keyboard snygg theme tokens (system keyboard colors) for a native look.
 */
@Composable
fun AiPanel(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val aiManager by context.aiManager()

    var showApiKeySnackbar by remember { mutableStateOf(false) }

    // ── System keyboard theme colors ──
    val windowStyle   = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val keyStyle      = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val smartbarStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)

    val bgColor        = windowStyle.background()
    val surfaceColor   = smartbarStyle.background()
    val onSurface      = keyStyle.foreground()
    val primary        = smartbarStyle.foreground().takeIf { it.alpha > 0f } ?: onSurface
    val onPrimary      = bgColor
    val outline        = onSurface.copy(alpha = 0.14f)
    val surfaceVariant = surfaceColor.copy(alpha = 0.55f)
    val onSurfaceVar   = onSurface.copy(alpha = 0.70f)
    val errorColor     = Color(0xFFBA1A1A)

    // ── State ──
    var isGenerating    by remember { mutableStateOf(false) }
    var selectedPersona by remember { mutableStateOf(AIPersona.CASUAL) }
    var selectedAction  by remember { mutableStateOf(AIAction.REWRITE) }
    var generatedText   by remember { mutableStateOf<String?>(null) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var wasUsingAllText by remember { mutableStateOf(false) }
    var aiMode          by remember { mutableStateOf(AiMode.REWRITE) }

    // ── API-key toast ──
    LaunchedEffect(showApiKeySnackbar) {
        if (showApiKeySnackbar) {
            Toast.makeText(context, "No API key configured. Go to Settings → Advanced AI.", Toast.LENGTH_LONG).show()
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            } catch (_: Exception) {}
            showApiKeySnackbar = false
        }
    }

    val isVisible = keyboardManager.activeState.isAiPanelVisible

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit  = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight * 4),
            color = bgColor,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // ════════ Header ════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.ai__panel_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                        letterSpacing = 0.3.sp
                    )
                    IconButton(
                        onClick = { keyboardManager.activeState.isAiPanelVisible = false; onDismiss() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = onSurfaceVar, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ════════ Mode Toggle ════════
                Surface(
                    color = surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(
                            AiMode.REWRITE to R.string.ai__mode_rewrite,
                            AiMode.APPEND to R.string.ai__mode_append
                        ).forEach { (mode, labelRes) ->
                            val selected = aiMode == mode
                            val bg by animateColorAsState(
                                if (selected) primary else Color.Transparent, tween(220), label = "modeBg"
                            )
                            val fg by animateColorAsState(
                                if (selected) onPrimary else onSurfaceVar, tween(220), label = "modeFg"
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

                // ════════ Persona chips ════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AIPersona.entries.forEach { persona ->
                        val isSelected = selectedPersona == persona
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPersona = persona; aiManager.setPersona(persona) },
                            label = {
                                Text(
                                    getPersonaLabel(persona),
                                    fontSize = 11.sp,
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
                                enabled = true, selected = isSelected,
                                borderWidth = 0.8.dp, borderColor = outline,
                                selectedBorderColor = primary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ════════ Action chips ════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AIAction.entries.forEach { action ->
                        val isSelected = selectedAction == action
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedAction = action },
                            label = {
                                Text(
                                    getActionLabel(action),
                                    fontSize = 11.sp,
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
                                enabled = true, selected = isSelected,
                                borderWidth = 0.8.dp, borderColor = outline,
                                selectedBorderColor = primary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ════════ Center: loading / result / error / generate CTA ════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isGenerating -> {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = primary,
                                trackColor = outline
                            )
                        }
                        generatedText != null -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = surfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    generatedText!!,
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 12.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = onSurface
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    generatedText?.let { text ->
                                        if (aiMode == AiMode.REWRITE) {
                                            if (wasUsingAllText) editorInstance.performClipboardSelectAll()
                                            editorInstance.commitText(text)
                                            Toast.makeText(context, R.string.ai__text_replaced, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val endPos = editorInstance.activeContent.text.length
                                            editorInstance.setSelection(endPos, endPos)
                                            editorInstance.commitText("\n\n$text")
                                            Toast.makeText(context, R.string.ai__text_inserted, Toast.LENGTH_SHORT).show()
                                        }
                                        keyboardManager.activeState.isAiPanelVisible = false
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = primary.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    if (aiMode == AiMode.REWRITE) Icons.Default.Check else Icons.Default.VerticalAlignBottom,
                                    if (aiMode == AiMode.REWRITE) "Replace" else "Insert Below",
                                    tint = primary, modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { generatedText = null; errorMessage = null },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Refresh, "Reset", tint = onSurfaceVar, modifier = Modifier.size(16.dp))
                            }
                        }
                        errorMessage != null -> {
                            Text(
                                errorMessage!!, fontSize = 12.sp, color = errorColor,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { generatedText = null; errorMessage = null },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Refresh, "Retry", tint = onSurfaceVar, modifier = Modifier.size(16.dp))
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val sel = editorInstance.activeContent.selectedText
                                        val hasSel = sel.isNotBlank()
                                        val text = if (hasSel) sel.toString() else editorInstance.activeContent.text.toString()
                                        wasUsingAllText = !hasSel
                                        if (text.isBlank()) { errorMessage = context.getString(R.string.ai__error_no_text); return@launch }
                                        isGenerating = true; errorMessage = null
                                        val result = if (aiMode == AiMode.APPEND) {
                                            aiManager.continueTextWithAction(text, selectedAction)
                                        } else {
                                            aiManager.rewriteText(text, selectedAction)
                                        }
                                        isGenerating = false
                                        result.fold(
                                            onSuccess = { generatedText = it },
                                            onFailure = { e ->
                                                val msg = e.message ?: context.getString(R.string.ai__error_api)
                                                if (msg.contains("No API key", true)) { showApiKeySnackbar = true; errorMessage = null }
                                                else errorMessage = msg
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = primary,
                                    contentColor = onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(getActionLabel(selectedAction), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
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
