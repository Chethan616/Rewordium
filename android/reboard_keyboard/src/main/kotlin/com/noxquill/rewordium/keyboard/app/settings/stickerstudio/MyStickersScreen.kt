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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

/**
 * My Stickers — grid of [UserStickerStore.entriesFlow] entries. Tap-and-hold
 * (long-press) on a sticker reveals a small dialog with **Delete** and
 * **Edit**. Delete removes the manifest entry + on-disk file; Edit hands
 * off to [Routes.Settings.StickerEditor] with the sticker's file Uri seeded.
 *
 * This is the deliberate home for delete UX — we moved long-press in the
 * IME panel to mean "favorite" (matching Gboard), so users delete in the
 * studio instead.
 */
@Composable
fun MyStickersScreen() = FlorisScreen {
    title = "My stickers"
    previewFieldVisible = false
    scrollable = false  // grid scrolls itself

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val store = remember { UserStickerStore.get(context) }
    val entries by store.entriesFlow.collectAsState()
    val sortedEntries = remember(entries) { entries.sortedByDescending { it.t } }

    var actionTarget by remember { mutableStateOf<UserStickerStore.Entry?>(null) }

    content {
        if (sortedEntries.isEmpty()) {
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
            items(sortedEntries, key = { it.id }) { entry ->
                val file = store.fileFor(entry)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(entry.id) {
                            detectTapGestures(
                                onLongPress = { actionTarget = entry },
                                onTap = { actionTarget = entry },
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
        }
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
