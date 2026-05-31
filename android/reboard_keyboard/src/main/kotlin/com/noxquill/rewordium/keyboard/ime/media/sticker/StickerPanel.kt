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

package com.noxquill.rewordium.keyboard.ime.media.sticker

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardFileStorage
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sticker picker — horizontal pack-tab strip ("User", then each installed
 * WhatsApp pack), grid of stickers below. Long-press on a user sticker
 * deletes it; the "+" tile in the User pack opens an image picker.
 *
 * On commit, every sticker (regardless of source) is cloned into
 * [ClipboardFileStorage] so the receiving editor gets a content URI from
 * the keyboard's own provider — avoids permission issues forwarding
 * WhatsApp's content URIs to third-party editors.
 *
 * Note: The image picker uses [Intent.ACTION_GET_CONTENT] launched via
 * [Context.startActivity] with [Intent.FLAG_ACTIVITY_NEW_TASK] because
 * the keyboard runs as an [InputMethodService], not an Activity. The
 * result is handled by [StickerImportActivity] which writes the selected
 * image into the user sticker store.
 *
 * @param onStickerPicked Called with the clipboard-provider content URI,
 *                        the sticker's MIME type, and a short description.
 *                        The caller is responsible for invoking
 *                        `EditorInstance.commitMedia`.
 */
@Composable
fun StickerPanel(
    fg: Color,
    accent: Color,
    onStickerPicked: (Uri, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userStore = remember { UserStickerStore.get(context) }
    val whatsapp = remember { WhatsAppStickerReader(context) }

    // Subscribe to the singleton store's flow so a sticker imported through
    // the trampoline activity shows up the instant it lands on disk.
    val rawEntries by userStore.entriesFlow.collectAsState()
    val userEntries = remember(rawEntries) { rawEntries.sortedByDescending { it.t } }
    var waPacks by remember { mutableStateOf<List<WhatsAppStickerReader.Pack>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    val dim = fg.copy(alpha = 0.55f)

    LaunchedEffect(Unit) {
        userStore.ensureLoaded()
        waPacks = whatsapp.packs()
    }

    val tabs by remember(userEntries, waPacks) {
        derivedStateOf { buildList {
            add(TabSpec.User)
            waPacks.forEachIndexed { i, pack -> add(TabSpec.WhatsApp(i, pack)) }
        } }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Pack-tab strip. Matches the Material 3 filter-chip aesthetic the
        // GIF panel uses: muted surface tint when inactive, accent fill when
        // active, semi-bold label so the selection reads at a glance.
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs) { tab ->
                val idx = tabs.indexOf(tab)
                val isActive = idx == selectedTab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isActive) accent.copy(alpha = 0.28f)
                            else fg.copy(alpha = 0.08f),
                        )
                        .clickable { selectedTab = idx }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = tab.label(),
                        color = if (isActive) fg else dim,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        val active = tabs.getOrNull(selectedTab) ?: TabSpec.User
        when (active) {
            TabSpec.User -> UserGrid(
                entries = userEntries,
                fg = fg, accent = accent,
                onAddClick = {
                    // Launch the transparent helper Activity which can use
                    // the system image picker and forward the result to
                    // UserStickerStore. InputMethodService cannot host
                    // ActivityResult contracts directly.
                    try {
                        val intent = Intent(context, StickerImportActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        flogDebug { "StickerPanel: Failed to launch picker: ${e.message}" }
                        Toast.makeText(context, "Cannot open image picker", Toast.LENGTH_SHORT).show()
                    }
                },
                onPick = { entry ->
                    scope.launch {
                        userStore.touch(entry)
                        val file = userStore.fileFor(entry)
                        if (!file.exists()) return@launch
                        val uri = withContext(Dispatchers.IO) {
                            cloneToClipboardStore(context, Uri.fromFile(file))
                        } ?: return@launch
                        onStickerPicked(uri, entry.mime, "Sticker")
                    }
                },
                onDelete = { entry ->
                    scope.launch {
                        userStore.remove(entry)
                    }
                },
            )
            is TabSpec.WhatsApp -> WhatsAppGrid(
                pack = active.pack,
                fg = fg,
                onPick = { sticker ->
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) {
                            cloneToClipboardStore(context, sticker.uri)
                        } ?: return@launch
                        onStickerPicked(uri, "image/webp", sticker.emojis.ifBlank { "Sticker" })
                    }
                },
            )
        }
    }
}

private sealed interface TabSpec {
    fun label(): String
    object User : TabSpec { override fun label() = "User" }
    data class WhatsApp(val index: Int, val pack: WhatsAppStickerReader.Pack) : TabSpec {
        override fun label(): String = pack.name.ifBlank { "Pack ${index + 1}" }
    }
}

@Composable
private fun UserGrid(
    entries: List<UserStickerStore.Entry>,
    fg: Color,
    accent: Color,
    onAddClick: () -> Unit,
    onPick: (UserStickerStore.Entry) -> Unit,
    onDelete: (UserStickerStore.Entry) -> Unit,
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(entries, key = { it.id }) { entry ->
            val store = remember { UserStickerStore.get(context) }
            val file = store.fileFor(entry)
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fg.copy(alpha = 0.05f))
                    .pointerInput(entry.id) {
                        detectTapGestures(
                            onTap = { onPick(entry) },
                            onLongPress = { onDelete(entry) },
                        )
                    },
            ) {
                AsyncImage(
                    model = Uri.fromFile(file),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun WhatsAppGrid(
    pack: WhatsAppStickerReader.Pack,
    fg: Color,
    onPick: (WhatsAppStickerReader.Sticker) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(pack.stickers, key = { it.uri.toString() }) { sticker ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fg.copy(alpha = 0.05f))
                    .clickable { onPick(sticker) },
            ) {
                AsyncImage(
                    model = sticker.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Copies a file/content URI's bytes into [ClipboardFileStorage] and returns
 * the resulting clipboard-provider URI. We do this on commit because
 * forwarding a foreign content URI (e.g. WhatsApp's) to a third-party
 * editor doesn't carry permission cleanly — the keyboard's own provider
 * grants read access via `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`.
 */
private suspend fun cloneToClipboardStore(
    context: android.content.Context,
    source: Uri,
): Uri? = withContext(Dispatchers.IO) {
    try {
        val id = System.nanoTime()
        val file = ClipboardFileStorage.getFileForId(context, id)
        context.contentResolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, id)
    } catch (e: Exception) {
        flogDebug { "cloneToClipboardStore failed: ${e.message}" }
        null
    }
}
