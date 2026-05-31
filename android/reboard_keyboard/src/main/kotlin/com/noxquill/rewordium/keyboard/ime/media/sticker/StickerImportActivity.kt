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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Transparent, theme-less Activity whose sole job is to launch the system
 * image picker and forward the selected URI to [UserStickerStore]. This
 * exists because `InputMethodService` is not an `ActivityResultRegistryOwner`,
 * so the normal Compose `rememberLauncherForActivityResult` crashes.
 *
 * Declared in AndroidManifest with `android:theme="@android:style/Theme.Translucent.NoTitleBar"`
 * so the user never sees a visible window — the system picker slides up
 * directly over whatever app is in the foreground.
 */
class StickerImportActivity : Activity() {

    companion object {
        private const val REQUEST_PICK_IMAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immediately launch the system picker.
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(
                Intent.createChooser(pickIntent, "Import sticker"),
                REQUEST_PICK_IMAGE,
            )
        } catch (e: Exception) {
            flogDebug { "StickerImportActivity: no picker available: ${e.message}" }
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                val mime = contentResolver.getType(uri) ?: "image/webp"
                // Import on IO; fire-and-forget — the panel will refresh
                // next time it opens.
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val store = UserStickerStore.get(this@StickerImportActivity)
                        store.ensureLoaded()
                        store.import(uri, mime)
                        flogDebug { "StickerImportActivity: imported $uri" }
                    } catch (e: Exception) {
                        flogDebug { "StickerImportActivity: import failed: ${e.message}" }
                    }
                }
            }
        }
        finish()
    }
}
