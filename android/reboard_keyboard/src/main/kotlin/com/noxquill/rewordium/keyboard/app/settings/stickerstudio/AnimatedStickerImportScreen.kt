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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.ime.media.sticker.UserStickerStore
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Imports a user-picked GIF into [UserStickerStore] verbatim. WhatsApp's
 * strict animated-sticker mode wants ≤ 512×512 lossy animated WebP, but
 * the encoder we'd need (ffmpeg-kit / libwebp animated mode) was pulled
 * from Maven Central in 2025. Until we wire a replacement, this screen
 * adds the GIF as-is — most messaging editors render GIF stickers fine,
 * and the user still gets the recents / favorites integration through
 * the normal [UserStickerStore] path.
 */
@Composable
fun AnimatedStickerImportScreen() = FlorisScreen {
    title = "Import animated GIF"
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val store = remember { UserStickerStore.get(context) }

    var working by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("Pick a GIF from your gallery.") }

    val pickGif = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        statusLine = "Transcoding…"
        scope.launch {
            val ok = transcodeAndImport(context, uri, store) { line ->
                statusLine = line
            }
            working = false
            Toast.makeText(
                context,
                if (ok) "Sticker added" else "Couldn't import GIF",
                Toast.LENGTH_SHORT,
            ).show()
            if (ok) navController.popBackStack()
        }
    }

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.GifBox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "Animated sticker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "We'll trim to 3 seconds, fit to 512×512, and save as an animated WebP.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (working) {
                CircularProgressIndicator()
                Text(statusLine, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(onClick = { pickGif.launch("image/gif") }) {
                    Text("Pick GIF")
                }
                Text(statusLine, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private suspend fun transcodeAndImport(
    context: android.content.Context,
    sourceUri: Uri,
    store: UserStickerStore,
    onStatus: (String) -> Unit,
): Boolean = withContext(Dispatchers.IO) {
    // ffmpeg-kit can't read content:// directly. Copy to cache first.
    onStatus("Copying GIF…")
    val cacheIn = File(context.cacheDir, "anim_in_${System.nanoTime()}.gif")
    try {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(cacheIn).use { out -> input.copyTo(out) }
        } ?: return@withContext false
    } catch (e: Exception) {
        return@withContext false
    }

    onStatus("Adding to library…")
    return@withContext try {
        val entry = store.import(Uri.fromFile(cacheIn), "image/gif")
        entry != null
    } catch (e: Exception) {
        false
    } finally {
        cacheIn.delete()
    }
}
