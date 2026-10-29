/*
 * Copyright (C) 2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.clipboard

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.app.apptheme.FlorisAppTheme
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider
import com.noxquill.rewordium.keyboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.mimeTypeFilterOf

class FlorisCopyToClipboardActivity : ComponentActivity() {
    companion object {
        private const val PREVIEW_MAX_PX = 1080
    }

    private var error: CopyToClipboardError? = null
    private var bitmap: Bitmap? = null
    private val clipboardManager by lazy { systemService(AndroidClipboardManager::class) }
    private val filter = mimeTypeFilterOf("image/*")

    internal enum class CopyToClipboardError {
        UNKNOWN_ERROR,
        TYPE_NOT_SUPPORTED_ERROR;

        @Composable
        fun showError(): String {
            val textId = when (this) {
                UNKNOWN_ERROR -> R.string.send_to_clipboard__unknown_error
                TYPE_NOT_SUPPORTED_ERROR -> R.string.send_to_clipboard__type_not_supported_error
            }
            return stringRes(id = textId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            Content()
        }
    }

    override fun onPause() {
        finish()
        super.onPause()
    }

    private fun handleIntent(intent: Intent) {
        val type = intent.type
        val action = intent.action

        if (Intent.ACTION_SEND != action || type == null) {
            error = CopyToClipboardError.UNKNOWN_ERROR
            return
        }
        if (!filter.matches(type) || !intent.hasExtra(Intent.EXTRA_STREAM)) {
            error = CopyToClipboardError.TYPE_NOT_SUPPORTED_ERROR
            return
        }

        val uri: Uri? =
            if (AndroidVersion.ATLEAST_API33_T) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

        if (uri == null) {
            error = CopyToClipboardError.TYPE_NOT_SUPPORTED_ERROR
            return
        }
        runCatching { copyImageToClipboard(uri, type) }
            .onSuccess { bitmap = it }
            .onFailure { e ->
                flogError { "Copy to clipboard failed: $e" }
                error = CopyToClipboardError.UNKNOWN_ERROR
            }
    }

    /**
     * Copies the shared image into the keyboard's own clipboard provider and puts that URI on the clipboard.
     * The sender's URI can't go there directly: the share only lent us read access, which the system refuses
     * to pass on ("UID … does not have permission to content://media/…").
     *
     * @return a small preview of the image, or null when it can't be decoded (the copy still worked).
     */
    private fun copyImageToClipboard(source: Uri, sharedType: String): Bitmap? {
        val mimeType = runCatching { contentResolver.getType(source) }.getOrNull()
            ?.takeIf { filter.matches(it) && !it.endsWith("/*") }
            ?: sharedType.takeIf { !it.endsWith("/*") }
            ?: "image/png"
        val values = ContentValues(3).apply {
            put(ClipboardMediaProvider.Columns.MediaUri, source.toString())
            put(ClipboardMediaProvider.Columns.MimeTypes, mimeType)
            put(OpenableColumns.DISPLAY_NAME, "image")
        }
        val ownUri = contentResolver.insert(ClipboardMediaProvider.IMAGE_CLIPS_URI, values)
        check(ownUri != null && ownUri.lastPathSegment != "0") { "Couldn't copy the shared image" }
        clipboardManager.setPrimaryClip(ClipData.newUri(contentResolver, "image", ownUri))
        return decodePreview(ownUri)
    }

    /** Decodes [uri] scaled down to at most [PREVIEW_MAX_PX] on its long side, so huge photos can't run us out of memory. */
    private fun decodePreview(uri: Uri): Bitmap? = runCatching {
        if (AndroidVersion.ATLEAST_API28_P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > PREVIEW_MAX_PX) {
                    val scale = PREVIEW_MAX_PX.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_MAX_PX) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    @Composable
    private fun Content() {
        val prefs by FlorisPreferenceStore
        ProvideLocalizedResources(
            resourcesContext = this,
            appName = R.string.app_name,
            forceLayoutDirection = LayoutDirection.Ltr,
        ) {
            val theme by prefs.other.settingsTheme.observeAsState()
            FlorisAppTheme(theme) {
                BottomSheet {
                    Row {
                        Text(
                            text = error?.showError()
                                ?: bitmap?.let { stringRes(id = R.string.send_to_clipboard__description__copied_image_to_clipboard) }
                                ?: stringRes(R.string.send_to_clipboard__unknown_error),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    bitmap?.let {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                modifier = Modifier
                                    .padding(start = 64.dp, end = 64.dp, top = 32.dp, bottom = 8.dp),
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BottomSheet(
        content: @Composable ColumnScope.() -> Unit,
    ) {
        ModalBottomSheet(
            modifier = Modifier.navigationBarsPadding(),
            onDismissRequest = { finish() }
        ) {
            Column {
                content()
                Button(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(16.dp),
                    onClick = { finish() },
                    colors = ButtonDefaults.textButtonColors(),
                ) {
                    Text(text = stringRes(id = R.string.action__ok))
                }
            }
        }
    }
}
