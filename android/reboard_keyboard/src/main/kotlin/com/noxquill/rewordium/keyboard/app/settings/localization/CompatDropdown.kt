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

package com.noxquill.rewordium.keyboard.app.settings.localization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A drop-in replacement for JetPrefDropdown that avoids the ExposedDropdownMenuBox
 * API incompatibility with newer Material3 Compose versions. Uses a simple
 * OutlinedTextField + DropdownMenu combination instead.
 *
 * Supports generic option types via [optionsLabelProvider].
 */
@Composable
fun <T> CompatDropdown(
    options: List<T>,
    selectedOptionIndex: Int,
    onSelectOption: (Int) -> Unit,
    modifier: Modifier = Modifier,
    expanded: MutableState<Boolean>? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    labelText: String? = null,
    optionsLabelProvider: ((T) -> String)? = null,
) {
    val internalExpanded = remember { mutableStateOf(false) }
    var isExpanded by (expanded ?: internalExpanded)
    val labelFn: (T) -> String = optionsLabelProvider ?: { it.toString() }
    val selectedText = options.getOrElse(selectedOptionIndex) { null }?.let(labelFn) ?: ""

    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { isExpanded = true },
                isError = isError,
                label = labelText?.let { { Text(it) } },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                shape = ShapeDefaults.Small,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    disabledBorderColor = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else if (enabled) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    },
                    disabledTrailingIconColor = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    disabledLabelColor = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                enabled = false, // disabled so it acts as a button; styled via colors above
            )

            if (enabled) {
                DropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false },
                    modifier = Modifier.heightIn(max = 300.dp),
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = labelFn(option),
                                    color = if (index == selectedOptionIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                onSelectOption(index)
                                isExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (modifier != Modifier) {
        Column(modifier = modifier) { content() }
    } else {
        content()
    }
}
