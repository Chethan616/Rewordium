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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import com.noxquill.rewordium.keyboard.app.AppTheme
import com.noxquill.rewordium.keyboard.app.apptheme.FlorisAppTheme
import com.noxquill.rewordium.keyboard.app.settings.stickerstudio.SubjectSegmentationHelper
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
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
        setContent {
            FlorisAppTheme(theme = AppTheme.AUTO) {
                val uri by pickedUri
                if (uri != null) {
                    ImportConfirmationDialog(
                        onKeep = { commit(uri!!, removeBg = false) },
                        onRemoveBg = { commit(uri!!, removeBg = true) },
                        onCancel = { finish() },
                    )
                }
            }
        }
        pickerLauncher.launch("image/*")
    }

    private fun commit(uri: Uri, removeBg: Boolean) {
        // Fire-and-forget. The store push is reactive, so the IME's
        // sticker panel picks up the new entry without waiting for us.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val store = UserStickerStore.get(this@StickerImportActivity)
                store.ensureLoaded()
                val finalUri = if (removeBg) {
                    runBgRemove(this@StickerImportActivity, uri) ?: uri
                } else {
                    uri
                }
                val mime = if (removeBg) "image/webp"
                else contentResolver.getType(uri) ?: "image/webp"
                store.import(finalUri, mime)
                flogDebug { "StickerImportActivity: imported $finalUri (removeBg=$removeBg)" }
            } catch (e: Exception) {
                flogDebug { "StickerImportActivity: import failed: ${e.message}" }
            }
        }
        finish()
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
}

/**
 * Material 3 confirmation dialog: "Remove background?" with three actions
 * — Remove (runs ML Kit cutout), Keep as-is (raw import), Cancel.
 */
@androidx.compose.runtime.Composable
private fun ImportConfirmationDialog(
    onKeep: () -> Unit,
    onRemoveBg: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Make a sticker") },
        text = {
            Text(
                text = "Want to cut the subject out of the background? " +
                    "We'll keep transparency for messaging apps that need it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onRemoveBg) { Text("Remove background") }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text("Keep as-is") }
        },
    )
}
