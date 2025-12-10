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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.launch

/**
 * AI Panel composable that shows AI writing assistance options
 * within the keyboard interface.
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
    
    var isGenerating by remember { mutableStateOf(false) }
    var selectedPersona by remember { mutableStateOf(AIPersona.CASUAL) }
    var selectedAction by remember { mutableStateOf(AIAction.REWRITE) }
    var generatedText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wasUsingAllText by remember { mutableStateOf(false) }  // Track if we should replace all text
    
    val isVisible = keyboardManager.activeState.isAiPanelVisible
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight * 4),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ai__panel_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            keyboardManager.activeState.isAiPanelVisible = false
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Persona Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AIPersona.entries.forEach { persona ->
                        PersonaChip(
                            persona = persona,
                            isSelected = selectedPersona == persona,
                            onClick = {
                                selectedPersona = persona
                                aiManager.setPersona(persona)
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AIAction.entries.forEach { action ->
                        ActionChip(
                            action = action,
                            isSelected = selectedAction == action,
                            onClick = { selectedAction = action }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Generate/Result Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ai__generating),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (generatedText != null) {
                        // Show generated text preview
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = generatedText!!,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                generatedText?.let { text ->
                                    // Select all text first if we were rewriting all text,
                                    // this ensures the commitText replaces the old text
                                    if (wasUsingAllText) {
                                        editorInstance.performClipboardSelectAll()
                                    }
                                    // commitText will replace any selected text
                                    editorInstance.commitText(text)
                                    Toast.makeText(context, R.string.ai__text_replaced, Toast.LENGTH_SHORT).show()
                                    keyboardManager.activeState.isAiPanelVisible = false
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Insert",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Generate button
                        Surface(
                            onClick = {
                                scope.launch {
                                    val selectedText = editorInstance.activeContent.selectedText
                                    val hasSelection = selectedText.isNotBlank()
                                    val textToRewrite = if (hasSelection) {
                                        selectedText.toString()
                                    } else {
                                        // If no selection, use all text
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
                                    
                                    val result = aiManager.rewriteText(textToRewrite, selectedAction)
                                    
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
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = getActionLabel(selectedAction),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    // Reset button (if there's generated text or error)
                    if (generatedText != null || errorMessage != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                generatedText = null
                                errorMessage = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaChip(
    persona: AIPersona,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = getPersonaLabel(persona),
                fontSize = 11.sp
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.height(28.dp)
    )
}

@Composable
private fun ActionChip(
    action: AIAction,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = getActionLabel(action),
                fontSize = 11.sp
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.height(28.dp)
    )
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
