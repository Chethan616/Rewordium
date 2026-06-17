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

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.app.AppTheme
import com.noxquill.rewordium.keyboard.app.apptheme.FlorisAppTheme
import com.noxquill.rewordium.keyboard.app.settings.stickerstudio.SubjectSegmentationHelper
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Transparent trampoline Activity: launches the system image picker, then
 * shows a Material 3 "Remove background?" prompt before handing off to
 * [UserStickerStore]. Exists because `InputMethodService` isn't an
 * `ActivityResultRegistryOwner`, so the normal Compose
 * `rememberLauncherForActivityResult` can't be used from the IME UI.
 *
 * Theme is `@android:style/Theme.Translucent.NoTitleBar` (see manifest)
 * so the picker UI floats over whatever the user was looking at. After
 * the picker returns, the AlertDialog renders against the transparent
 * background with the M3 surface tint.
 */
class StickerImportActivity : ComponentActivity() {

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        pickedUri.value = uri
    }

    // Holds the URI between picker dismissal and the dialog's Compose
    // first frame. `MutableState` so the dialog reads it reactively.
    private val pickedUri: MutableState<Uri?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            savedInstanceState.getString("picked_uri")?.let { uriStr ->
                pickedUri.value = Uri.parse(uriStr)
            }
        }
        setContent {
            FlorisAppTheme(theme = AppTheme.AUTO) {
                val uri by pickedUri
                var tagsText by remember { mutableStateOf("") }
                if (uri != null) {
                    ImportConfirmationDialog(
                        tagsText = tagsText,
                        onTagsTextChange = { tagsText = it },
                        onKeep = { 
                            val tagsList = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            commit(uri!!, removeBg = false, tags = tagsList) 
                        },
                        onRemoveBg = { 
                            val tagsList = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            commit(uri!!, removeBg = true, tags = tagsList) 
                        },
                        onEdit = {
                            CoroutineScope(Dispatchers.Main).launch {
                                val cachedUri = copyToCache(this@StickerImportActivity, uri!!)
                                if (cachedUri != null) {
                                    val editUri = Uri.parse("ui://ReBoard/settings/sticker-studio/editor?sourceUri=${Uri.encode(cachedUri.toString())}")
                                    val intent = Intent(Intent.ACTION_VIEW, editUri)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                }
                                finish()
                            }
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
        if (savedInstanceState == null && pickedUri.value == null) {
            pickerLauncher.launch("image/*")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pickedUri.value?.let { uri ->
            outState.putString("picked_uri", uri.toString())
        }
    }

    override fun finish() {
        try {
            val keyboardManager = this.keyboardManager().value
            keyboardManager.activeState.imeUiMode = com.noxquill.rewordium.keyboard.ime.ImeUiMode.MEDIA
            keyboardManager.activeState.activeMediaMode = "STICKER"
        } catch (e: Exception) {
            flogDebug { "StickerImportActivity finish error: ${e.message}" }
        }
        com.noxquill.rewordium.keyboard.FlorisImeService.shouldPreserveMediaUiModeOnce = true
        super.finish()
    }

    private fun commit(uri: Uri, removeBg: Boolean, tags: List<String> = emptyList()) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    val store = UserStickerStore.get(this@StickerImportActivity)
                    store.ensureLoaded()
                    val finalUri = if (removeBg) {
                        runBgRemove(this@StickerImportActivity, uri) ?: uri
                    } else {
                        uri
                    }
                    val mime = if (removeBg) "image/webp"
                    else contentResolver.getType(uri) ?: "image/webp"
                    store.import(finalUri, mime, tags)
                    flogDebug { "StickerImportActivity: imported $finalUri (removeBg=$removeBg)" }
                }
            } catch (e: Exception) {
                flogDebug { "StickerImportActivity: import failed: ${e.message}" }
            } finally {
                finish()
            }
        }
    }

    private suspend fun runBgRemove(
        context: android.content.Context,
        sourceUri: Uri,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext null
            val cutout = SubjectSegmentationHelper.run(bitmap) ?: return@withContext null
            val cache = File(context.cacheDir, "sticker_cutout_${System.nanoTime()}.webp")
            FileOutputStream(cache).use {
                cutout.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it)
            }
            Uri.fromFile(cache)
        } catch (e: Exception) {
            flogDebug { "StickerImportActivity.runBgRemove failed: ${e.message}" }
            null
        }
    }

    private suspend fun copyToCache(
        context: android.content.Context,
        sourceUri: Uri,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cache = File(context.cacheDir, "sticker_edit_${System.nanoTime()}.png")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            Uri.fromFile(cache)
        } catch (e: Exception) {
            flogDebug { "StickerImportActivity.copyToCache failed: ${e.message}" }
            null
        }
    }
}

/**
 * Material 3 confirmation dialog: "Remove background?" with options:
 * Remove background (runs ML Kit cutout), Edit (launches Sticker Studio),
 * Keep as-is (raw import), Cancel.
 */
@androidx.compose.runtime.Composable
private fun ImportConfirmationDialog(
    tagsText: String,
    onTagsTextChange: (String) -> Unit,
    onKeep: () -> Unit,
    onRemoveBg: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Make a sticker") },
        text = {
            Column {
                Text(
                    text = "Want to cut the subject out of the background or edit it in the Sticker Studio?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = onTagsTextChange,
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("e.g. John, Reaction, Funny") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = onRemoveBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove background")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onKeep,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep as-is")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
