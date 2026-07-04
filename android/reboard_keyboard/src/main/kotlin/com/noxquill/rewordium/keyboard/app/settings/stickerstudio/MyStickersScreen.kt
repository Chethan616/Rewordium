/*
 * Copyright (C) 2026 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.app.settings.stickerstudio

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.app.Routes
import com.noxquill.rewordium.keyboard.ime.media.sticker.UserStickerStore
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch

@Composable
fun MyStickersScreen() = FlorisScreen {
    title = "My stickers"
    previewFieldVisible = false
    scrollable = false

    var showCreatePackDialog by remember { mutableStateOf(false) }
    actions {
        IconButton(onClick = { showCreatePackDialog = true }) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create Pack")
        }
    }

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val store = remember { UserStickerStore.get(context) }
    val entries by store.entriesFlow.collectAsState()
    val sortedEntries = remember(entries) { entries.sortedByDescending { it.t } }
    val packs by store.packsFlow.collectAsState()

    var actionTarget by remember { mutableStateOf<UserStickerStore.Entry?>(null) }

    content {
        if (sortedEntries.isEmpty() && packs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Brush,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No stickers yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create a sticker or browse the premade library.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@content
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            val packsList = packs.sortedByDescending { it.t }
            for (pack in packsList) {
                val packEntries = sortedEntries.filter { it.packId == pack.id }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        var expanded by remember { mutableStateOf(false) }
                        var showRename by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.MoreVert, null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { expanded = false; showRename = true })
                                DropdownMenuItem(text = { Text("Delete Pack") }, onClick = { 
                                    expanded = false
                                    scope.launch { store.deletePack(pack.id) }
                                })
                            }
                        }
                        
                        if (showRename) {
                            var newName by remember { mutableStateOf(pack.name) }
                            AlertDialog(
                                onDismissRequest = { showRename = false },
                                title = { Text("Rename Pack") },
                                text = {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        if (newName.isNotBlank()) {
                                            scope.launch { store.renamePack(pack.id, newName.trim()) }
                                        }
                                        showRename = false
                                    }) { Text("Rename") }
                                },
                                dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
                            )
                        }
                    }
                }
                
                if (packEntries.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("No stickers in this pack.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                } else {
                    items(packEntries, key = { it.id }) { entry ->
                        StickerItem(entry, store) { actionTarget = entry }
                    }
                }
            }
            
            val uncategorized = sortedEntries.filter { it.packId == null }
            if (uncategorized.isNotEmpty() || packsList.isEmpty()) {
                if (packsList.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Uncategorized", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    }
                }
                items(uncategorized, key = { it.id }) { entry ->
                    StickerItem(entry, store) { actionTarget = entry }
                }
            }
        }
    }

    if (showCreatePackDialog) {
        var packName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePackDialog = false },
            title = { Text("New Sticker Pack") },
            text = {
                OutlinedTextField(
                    value = packName,
                    onValueChange = { packName = it },
                    label = { Text("Pack Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (packName.isNotBlank()) {
                        scope.launch { store.createPack(packName.trim()) }
                    }
                    showCreatePackDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePackDialog = false }) { Text("Cancel") }
            }
        )
    }

    actionTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text("Sticker actions") },
            text = { Text("Pick what to do with this sticker.") },
            confirmButton = {
                TextButton(onClick = {
                    actionTarget = null
                    val fileUri = Uri.fromFile(store.fileFor(entry)).toString()
                    navController.navigate(Routes.Settings.StickerEditor(sourceUri = fileUri))
                }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val toRemove = entry
                    actionTarget = null
                    scope.launch { store.remove(toRemove) }
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Delete")
                }
            },
        )
    }
}

@Composable
private fun StickerItem(entry: UserStickerStore.Entry, store: UserStickerStore, onAction: () -> Unit) {
    val file = store.fileFor(entry)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(entry.id) {
                detectTapGestures(
                    onLongPress = { onAction() },
                    onTap = { onAction() },
                )
            },
    ) {
        AsyncImage(
            model = Uri.fromFile(file),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    }
}
