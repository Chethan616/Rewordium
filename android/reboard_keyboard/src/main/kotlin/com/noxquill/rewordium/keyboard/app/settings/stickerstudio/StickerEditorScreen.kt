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
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.ime.media.sticker.UserStickerStore
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.SaveSettings
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val EXPORT_SIZE = 512

private val PALETTE = listOf(
    Color(0xFF000000), Color(0xFFFFFFFF),
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFFEB3B),
    Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA),
    Color(0xFF6D4C41), Color(0xFFEC407A),
)

/**
 * Sticker editor — Compose shell around `PhotoEditorView` (MIT,
 * Burhanrashid52). Hosts a 1:1 square canvas the user can:
 *
 *  - Replace the base image from gallery (image_cropper auto-crops to
 *    1:1 with built-in camera + gallery picker)
 *  - Auto-cut-out background via ML Kit Subject Segmentation
 *  - Draw with brush / eraser
 *  - Add text with color
 *  - Undo / Redo
 *  - Save: rasterize the editor, scale to 512x512, encode lossless WebP,
 *    push through [UserStickerStore.import] — IME panel picks it up
 *    instantly via the existing `entriesFlow` collector.
 *
 * If [sourceUri] is non-null on first composition, that URI is loaded as
 * the base image; otherwise the canvas starts transparent and the user
 * is prompted to pick one.
 */
@Composable
fun StickerEditorScreen(sourceUri: String?) = FlorisScreen {
    title = "Sticker editor"
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val store = remember { UserStickerStore.get(context) }

    var photoEditorView by remember { mutableStateOf<PhotoEditorView?>(null) }
    var photoEditor by remember { mutableStateOf<PhotoEditor?>(null) }
    var brushColor by remember { mutableStateOf(PALETTE[0]) }
    var brushSize by remember { mutableStateOf(20f) }
    var mode by remember { mutableStateOf(EditorMode.Idle) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var bgRemovalRunning by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent ?: return@rememberLauncherForActivityResult
            scope.launch {
                val bmp = decodeUriToBitmap(context, uri, EXPORT_SIZE)
                if (bmp != null) photoEditorView?.source?.setImageBitmap(bmp)
            }
        } else {
            result.error?.let {
                Toast.makeText(context, "Crop failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchCropper(allowCamera: Boolean) {
        cropLauncher.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    imageSourceIncludeGallery = true,
                    imageSourceIncludeCamera = allowCamera,
                    fixAspectRatio = true,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    outputCompressFormat = Bitmap.CompressFormat.PNG,
                    cropShape = CropImageView.CropShape.RECTANGLE,
                ),
            ),
        )
    }

    // Runtime camera permission. The cropper will offer "take a photo"
    // only when CAMERA is granted; otherwise we silently strip the camera
    // option from the source picker so the user can still pick from the
    // gallery without seeing a confusing "permission denied" screen.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> launchCropper(allowCamera = granted) }

    // Seed the editor with the passed-in URI on first composition.
    LaunchedEffect(photoEditorView, sourceUri) {
        val view = photoEditorView ?: return@LaunchedEffect
        if (sourceUri.isNullOrBlank()) return@LaunchedEffect
        val bmp = decodeUriToBitmap(context, Uri.parse(sourceUri), EXPORT_SIZE)
        if (bmp != null) view.source.setImageBitmap(bmp)
    }

    content {
        Column(modifier = Modifier.fillMaxSize()) {
            // Canvas slot — fills any leftover vertical space (weight=1f)
            // while keeping the canvas itself square. Wrapping in a Box
            // that takes the weight, then nesting the square canvas
            // inside, prevents the previous bug where Spacer(weight=1f)
            // pushed the Save bar off-screen on tall devices.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PhotoEditorView(ctx).also { view ->
                                photoEditorView = view
                                photoEditor = PhotoEditor.Builder(ctx, view)
                                    .setPinchTextScalable(true)
                                    .build()
                            }
                        },
                    )
                    if (saving || bgRemovalRunning) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (saving) "Saving…" else "Removing background…",
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            // Tools row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ToolButton(icon = Icons.Outlined.PhotoLibrary, label = "Photo", onClick = {
                        val cameraAlreadyGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (cameraAlreadyGranted) {
                            launchCropper(allowCamera = true)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    })
                }
                item {
                    ToolButton(icon = Icons.Outlined.Crop, label = "Crop", onClick = {
                        // Re-crop the current source — export, rerun cropper.
                        val view = photoEditorView ?: return@ToolButton
                        scope.launch {
                            val cached = exportSourceAsCacheFile(context, view) ?: return@launch
                            cropLauncher.launch(
                                CropImageContractOptions(
                                    uri = Uri.fromFile(cached),
                                    cropImageOptions = CropImageOptions(
                                        fixAspectRatio = true,
                                        aspectRatioX = 1,
                                        aspectRatioY = 1,
                                        outputCompressFormat = Bitmap.CompressFormat.PNG,
                                    ),
                                ),
                            )
                        }
                    })
                }
                item {
                    ToolButton(icon = Icons.Outlined.AutoAwesome, label = "Cutout", onClick = {
                        val view = photoEditorView ?: return@ToolButton
                        bgRemovalRunning = true
                        scope.launch {
                            val bmp = view.source.drawableToBitmap()
                            if (bmp == null) {
                                bgRemovalRunning = false
                                return@launch
                            }
                            val cutout = SubjectSegmentationHelper.run(bmp)
                            bgRemovalRunning = false
                            if (cutout != null) {
                                view.source.setImageBitmap(cutout)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Couldn't find a subject — try a clearer photo.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    })
                }
                item {
                    ToolButton(
                        icon = Icons.Outlined.Brush,
                        label = "Draw",
                        selected = mode == EditorMode.Draw,
                        onClick = {
                            val editor = photoEditor ?: return@ToolButton
                            mode = EditorMode.Draw
                            editor.setBrushDrawingMode(true)
                            editor.setShape(
                                ShapeBuilder().withShapeType(ShapeType.Brush)
                                    .withShapeColor(brushColor.toArgb())
                                    .withShapeSize(brushSize),
                            )
                            showColorSheet = true
                        },
                    )
                }
                item {
                    ToolButton(
                        icon = Icons.Outlined.Delete,
                        label = "Eraser",
                        selected = mode == EditorMode.Erase,
                        onClick = {
                            val editor = photoEditor ?: return@ToolButton
                            mode = EditorMode.Erase
                            editor.setBrushDrawingMode(true)
                            // In PhotoEditor 3.x the eraser uses the same
                            // ShapeBuilder size as the brush; brushEraser()
                            // just flips the mode to eraser. Reuse the
                            // current brushSize via setShape.
                            editor.setShape(
                                ShapeBuilder().withShapeType(ShapeType.Brush)
                                    .withShapeSize(brushSize),
                            )
                            editor.brushEraser()
                        },
                    )
                }
                item {
                    ToolButton(icon = Icons.Outlined.TextFields, label = "Text", onClick = {
                        photoEditor?.setBrushDrawingMode(false)
                        mode = EditorMode.Idle
                        showTextDialog = true
                    })
                }
                item {
                    ToolButton(icon = Icons.AutoMirrored.Outlined.Undo, label = "Undo", onClick = {
                        photoEditor?.undo()
                    })
                }
                item {
                    ToolButton(icon = Icons.AutoMirrored.Outlined.Redo, label = "Redo", onClick = {
                        photoEditor?.redo()
                    })
                }
            }

            // Brush size slider — visible when a brush tool is active.
            if (mode == EditorMode.Draw || mode == EditorMode.Erase) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Size", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = brushSize,
                        onValueChange = {
                            brushSize = it
                            val builder = ShapeBuilder()
                                .withShapeType(ShapeType.Brush)
                                .withShapeSize(brushSize)
                            if (mode == EditorMode.Draw) {
                                builder.withShapeColor(brushColor.toArgb())
                            }
                            photoEditor?.setShape(builder)
                            if (mode == EditorMode.Erase) {
                                photoEditor?.brushEraser()
                            }
                        },
                        valueRange = 4f..80f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Cancel / Save action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val editor = photoEditor ?: return@Button
                        val view = photoEditorView ?: return@Button
                        saving = true
                        scope.launch {
                            val ok = exportAndImport(context, editor, view, store)
                            saving = false
                            if (ok) {
                                Toast.makeText(context, "Sticker saved", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !saving && !bgRemovalRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save sticker")
                }
            }
        }
    }

    if (showColorSheet) {
        AlertDialog(
            onDismissRequest = { showColorSheet = false },
            title = { Text("Brush color") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (brushColor == c) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    brushColor = c
                                    photoEditor?.setShape(
                                        ShapeBuilder().withShapeType(ShapeType.Brush)
                                            .withShapeColor(c.toArgb())
                                            .withShapeSize(brushSize),
                                    )
                                },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColorSheet = false }) { Text("Done") } },
        )
    }

    if (showTextDialog) {
        var draft by remember { mutableStateOf("") }
        var textColor by remember { mutableStateOf(PALETTE[0]) }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add text") },
            text = {
                Column {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PALETTE.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (textColor == c) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    )
                                    .clickable { textColor = c },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.isNotBlank()) {
                        photoEditor?.addText(draft, textColor.toArgb())
                    }
                    showTextDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private enum class EditorMode { Idle, Draw, Erase }

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Image helpers ──────────────────────────────────────────────────────────

private suspend fun decodeUriToBitmap(
    context: android.content.Context,
    uri: Uri,
    maxDim: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let { scaleToMax(it, maxDim) }
        }
    } catch (e: Exception) {
        null
    }
}

private fun scaleToMax(src: Bitmap, maxDim: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= maxDim && h <= maxDim) return src
    val ratio = maxDim.toFloat() / maxOf(w, h)
    val tw = (w * ratio).toInt()
    val th = (h * ratio).toInt()
    return Bitmap.createScaledBitmap(src, tw, th, true)
}

/** Extract the current base bitmap from the PhotoEditorView's ImageView. */
private fun android.widget.ImageView.drawableToBitmap(): Bitmap? {
    val d = drawable ?: return null
    return try {
        if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
            d.bitmap
        } else {
            val bmp = Bitmap.createBitmap(
                maxOf(d.intrinsicWidth, 1),
                maxOf(d.intrinsicHeight, 1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            bmp
        }
    } catch (e: Exception) {
        null
    }
}

private suspend fun exportSourceAsCacheFile(
    context: android.content.Context,
    view: PhotoEditorView,
): File? = withContext(Dispatchers.IO) {
    val bmp = view.source.drawableToBitmap() ?: return@withContext null
    val cache = File(context.cacheDir, "sticker_studio_tmp_${System.nanoTime()}.png")
    try {
        FileOutputStream(cache).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache
    } catch (e: Exception) {
        cache.delete()
        null
    }
}

/**
 * Rasterize the editor (base image + every overlay layer) into a flat
 * 512x512 ARGB bitmap, encode as lossless WebP, and push to
 * [UserStickerStore]. The IME subscribes to its `entriesFlow` so the new
 * sticker shows up in the User tab and Recents the moment this returns.
 */
private suspend fun exportAndImport(
    context: android.content.Context,
    editor: PhotoEditor,
    view: PhotoEditorView,
    store: UserStickerStore,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rasterized = rasterizeEditor(editor) ?: return@withContext false
        val normalized = squarePad(rasterized, EXPORT_SIZE)
        val cacheFile = File(context.cacheDir, "sticker_studio_${System.nanoTime()}.webp")
        FileOutputStream(cacheFile).use {
            // WEBP_LOSSLESS preserves the transparent background that cutouts produce.
            normalized.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it)
        }
        val entry = store.import(Uri.fromFile(cacheFile), "image/webp")
        cacheFile.delete()
        entry != null
    } catch (e: Exception) {
        false
    }
}

private suspend fun rasterizeEditor(editor: PhotoEditor): Bitmap? {
    return kotlin.coroutines.suspendCoroutine { cont ->
        editor.saveAsBitmap(
            SaveSettings.Builder()
                .setTransparencyEnabled(true)
                .setClearViewsEnabled(false)
                .build(),
            object : ja.burhanrashid52.photoeditor.OnSaveBitmap {
                override fun onBitmapReady(saveBitmap: Bitmap?) {
                    cont.resumeWith(Result.success(saveBitmap))
                }
                override fun onFailure(e: Exception?) {
                    cont.resumeWith(Result.success(null))
                }
            },
        )
    }
}

/** Letter-pad / scale-fit a bitmap into a [size]×[size] transparent square. */
private fun squarePad(src: Bitmap, size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
    val scale = size.toFloat() / maxOf(src.width, src.height)
    val tw = src.width * scale
    val th = src.height * scale
    val left = (size - tw) / 2f
    val top = (size - th) / 2f
    val dst = RectF(left, top, left + tw, top + th)
    canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}
