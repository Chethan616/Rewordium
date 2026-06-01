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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
private data class PremadeEntry(
    val slug: String,
    val name: String = "",
    val category: String = "",
)

private const val ASSETS_DIR = "sticker/fluent_flat"
private const val INDEX_FILE = "$ASSETS_DIR/index.json"

/**
 * Premade sticker library — bundled Microsoft Fluent UI Emoji (MIT)
 * subset. Reads `assets/sticker/fluent_flat/index.json`, displays each
 * entry's `{slug}.png` as a thumbnail. Tap opens a choice dialog:
 *
 *  - **Add as-is** — copies the PNG into `filesDir/user_stickers/{uuid}.webp`
 *    (re-encoded for consistency) and registers it through
 *    [UserStickerStore.import]. Appears in the IME panel immediately.
 *  - **Edit first** — navigates to [Routes.Settings.StickerEditor] with
 *    the asset's content URI seeded as the base image.
 *
 * If the index file is missing (assets not yet bundled), the screen falls
 * back to an "empty pack" message rather than crashing — keeps the build
 * green while the asset curation lands incrementally.
 */
@Composable
fun PremadeLibraryScreen() = FlorisScreen {
    title = "Premade stickers"
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val store = remember { UserStickerStore.get(context) }

    var entries by remember { mutableStateOf<List<PremadeEntry>>(emptyList()) }
    var action by remember { mutableStateOf<PremadeEntry?>(null) }

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { loadIndex(context) }
    }

    content {
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Premade pack not bundled yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Curated Microsoft Fluent UI Emoji stickers will land here in an update. For now, use the editor to make your own.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@content
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(80.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(entries, key = { it.slug }) { entry ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { action = entry },
                ) {
                    AsyncImage(
                        model = "file:///android_asset/$ASSETS_DIR/${entry.slug}.png",
                        contentDescription = entry.name.ifBlank { entry.slug },
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
            }
        }
    }

    action?.let { entry ->
        AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(entry.name.ifBlank { entry.slug }) },
            text = { Text("Add this sticker to your library or open it in the editor.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = entry
                    action = null
                    scope.launch {
                        val ok = importAsset(context, target.slug, store)
                        Toast.makeText(
                            context,
                            if (ok) "Added to My Stickers" else "Couldn't add sticker",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text("Add as-is") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val target = entry
                    action = null
                    scope.launch {
                        val tmpFile = withContext(Dispatchers.IO) { copyAssetToCache(context, target.slug) }
                        if (tmpFile != null) {
                            navController.navigate(
                                Routes.Settings.StickerEditor(sourceUri = Uri.fromFile(tmpFile).toString()),
                            )
                        }
                    }
                }) { Text("Edit first") }
            },
        )
    }
}

private fun loadIndex(context: android.content.Context): List<PremadeEntry> {
    return try {
        context.assets.open(INDEX_FILE).bufferedReader().use { reader ->
            val text = reader.readText()
            Json { ignoreUnknownKeys = true }
                .decodeFromString(kotlinx.serialization.builtins.ListSerializer(PremadeEntry.serializer()), text)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun copyAssetToCache(
    context: android.content.Context,
    slug: String,
): File? = withContext(Dispatchers.IO) {
    try {
        val cache = File(context.cacheDir, "premade_${slug}_${System.nanoTime()}.png")
        context.assets.open("$ASSETS_DIR/$slug.png").use { input ->
            FileOutputStream(cache).use { out -> input.copyTo(out) }
        }
        cache
    } catch (e: Exception) {
        null
    }
}

private suspend fun importAsset(
    context: android.content.Context,
    slug: String,
    store: UserStickerStore,
): Boolean = withContext(Dispatchers.IO) {
    try {
        // Decode the PNG, re-encode as lossless WebP so the User store
        // holds a uniform format. Premade PNGs already have transparency
        // and a sensible aspect; we don't re-pad.
        val bitmap = context.assets.open("$ASSETS_DIR/$slug.png").use { BitmapFactory.decodeStream(it) }
            ?: return@withContext false
        val cache = File(context.cacheDir, "premade_import_${slug}_${System.nanoTime()}.webp")
        FileOutputStream(cache).use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }
        val entry = store.import(Uri.fromFile(cache), "image/webp")
        cache.delete()
        entry != null
    } catch (e: Exception) {
        false
    }
}
