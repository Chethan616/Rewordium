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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.canhub.cropper.CropImageOptions
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.autoMirrorForRtl
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
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
 *  - Replace the base image from gallery (inline Material 3 crop UI)
 *  - Auto-cut-out background via ML Kit Subject Segmentation
 *  - Draw with brush / eraser
 *  - Add text with color
 *  - Undo / Redo
 *  - Save: rasterize the editor, scale to 512x512, encode lossless WebP,
 *    push through [UserStickerStore.import] — IME panel picks it up
 *    instantly via the existing `entriesFlow` collector.
 *
 * Crop flow uses an inline [CropImageView] embedded in Compose with
 * Material 3 buttons — no external activity needed.
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

    // ── Editor state ───────────────────────────────────────────────────
    var photoEditorView by remember { mutableStateOf<PhotoEditorView?>(null) }
    var photoEditor by remember { mutableStateOf<PhotoEditor?>(null) }
    var brushColor by remember { mutableStateOf(PALETTE[0]) }
    var brushSize by remember { mutableStateOf(20f) }
    var mode by remember { mutableStateOf(EditorMode.Idle) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var bgRemovalRunning by remember { mutableStateOf(false) }
    var imageUri by rememberSaveable { mutableStateOf(sourceUri) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTagsText by remember { mutableStateOf("") }

    // ── Inline crop mode state ─────────────────────────────────────────
    var cropModeActive by remember { mutableStateOf(false) }
    var cropViewRef by remember { mutableStateOf<CropImageView?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var cropLoading by remember { mutableStateOf(false) }
    var cropAspectRatioState by remember { mutableStateOf<CropRatioPreset>(CropRatioPreset.Free) }

    LaunchedEffect(cropModeActive) {
        title = if (cropModeActive) "Crop image" else "Sticker editor"
    }

    navigationIcon {
        val nav = LocalNavController.current
        if (cropModeActive) {
            IconButton(onClick = {
                cropModeActive = false
                pendingCropUri = null
                cropViewRef = null
            }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancel crop"
                )
            }
        } else {
            FlorisIconButton(
                onClick = { nav.popBackStack() },
                modifier = Modifier.autoMirrorForRtl(),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
            )
        }
    }

    actions {
        if (cropModeActive) {
            IconButton(
                onClick = {
                    val view = cropViewRef ?: return@IconButton
                    cropLoading = true
                    scope.launch {
                        val croppedBmp = withContext(Dispatchers.IO) {
                            try {
                                view.getCroppedImage()
                            } catch (_: Exception) { null }
                        }
                        cropLoading = false
                        if (croppedBmp != null) {
                            val cacheFile = withContext(Dispatchers.IO) {
                                try {
                                    val f = File(
                                        context.cacheDir,
                                        "crop_result_${System.nanoTime()}.png",
                                    )
                                    FileOutputStream(f).use {
                                        croppedBmp.compress(
                                            Bitmap.CompressFormat.PNG, 100, it,
                                        )
                                    }
                                    f
                                } catch (_: Exception) { null }
                            }
                            if (cacheFile != null) {
                                imageUri = Uri.fromFile(cacheFile).toString()
                            } else {
                                Toast.makeText(
                                    context, "Crop failed", Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                context, "Crop failed", Toast.LENGTH_SHORT,
                            ).show()
                        }
                        cropModeActive = false
                        pendingCropUri = null
                        cropViewRef = null
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Confirm crop"
                )
            }
        } else {
            IconButton(
                onClick = {
                    saveTagsText = ""
                    showSaveDialog = true
                },
                enabled = !saving && !bgRemovalRunning && !imageUri.isNullOrBlank()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Save sticker"
                )
            }
        }
    }

    // ── Image picker (replaces CropImageContract) ──────────────────────
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            // Copy the transient content URI to a local cache file so the
            // CropImageView can read it even after the picker closes.
            scope.launch {
                val cachedUri = withContext(Dispatchers.IO) {
                    try {
                        val cache = File(context.cacheDir, "pick_cache_${System.nanoTime()}.png")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cache.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@withContext null
                        Uri.fromFile(cache)
                    } catch (_: Exception) { null }
                }
                if (cachedUri != null) {
                    pendingCropUri = cachedUri
                    cropModeActive = true
                } else {
                    Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Seed the editor with the passed-in URI on first composition.
    LaunchedEffect(photoEditorView, imageUri) {
        val view = photoEditorView ?: return@LaunchedEffect
        val uriStr = imageUri
        if (uriStr.isNullOrBlank()) return@LaunchedEffect
        val bmp = decodeUriToBitmap(context, Uri.parse(uriStr), EXPORT_SIZE)
        if (bmp != null) view.source.setImageBitmap(bmp)
    }

    // Auto-launch image picker if no source URI is provided.
    LaunchedEffect(Unit) {
        if (imageUri.isNullOrBlank()) {
            imagePickerLauncher.launch("image/*")
        }
    }

    content {
        if (cropModeActive) {
            // ────────────────────────────────────────────────────────────
            //  INLINE CROP MODE — Material 3 UI
            // ────────────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                val surfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
                val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                val guidelineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f).toArgb()

                // Crop canvas — fills remaining vertical space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .checkerboard()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            CropImageView(ctx).apply {
                                guidelines = CropImageView.Guidelines.ON
                                cropShape = CropImageView.CropShape.RECTANGLE
                                isAutoZoomEnabled = true
                                
                                val options = CropImageOptions().apply {
                                    backgroundColor = AndroidColor.TRANSPARENT
                                    borderLineColor = primaryColor
                                    borderCornerColor = primaryColor
                                    guidelinesColor = guidelineColor
                                    borderLineThickness = 4f
                                    borderCornerThickness = 8f
                                    borderCornerLength = 36f
                                    guidelinesThickness = 2f
                                    
                                    when (cropAspectRatioState) {
                                        CropRatioPreset.Free -> {
                                            fixAspectRatio = false
                                        }
                                        CropRatioPreset.Square -> {
                                            fixAspectRatio = true
                                            aspectRatioX = 1
                                            aspectRatioY = 1
                                        }
                                        CropRatioPreset.Ratio4_3 -> {
                                            fixAspectRatio = true
                                            aspectRatioX = 4
                                            aspectRatioY = 3
                                        }
                                        CropRatioPreset.Ratio16_9 -> {
                                            fixAspectRatio = true
                                            aspectRatioX = 16
                                            aspectRatioY = 9
                                        }
                                    }
                                }
                                setImageCropOptions(options)
                                
                                cropViewRef = this
                                pendingCropUri?.let { setImageUriAsync(it) }
                            }
                        },
                        update = { view ->
                            val options = CropImageOptions().apply {
                                backgroundColor = AndroidColor.TRANSPARENT
                                borderLineColor = primaryColor
                                borderCornerColor = primaryColor
                                guidelinesColor = guidelineColor
                                borderLineThickness = 4f
                                borderCornerThickness = 8f
                                borderCornerLength = 36f
                                guidelinesThickness = 2f
                                guidelines = CropImageView.Guidelines.ON
                                cropShape = CropImageView.CropShape.RECTANGLE
                                autoZoomEnabled = true
                                
                                when (cropAspectRatioState) {
                                    CropRatioPreset.Free -> {
                                        fixAspectRatio = false
                                    }
                                    CropRatioPreset.Square -> {
                                        fixAspectRatio = true
                                        aspectRatioX = 1
                                        aspectRatioY = 1
                                    }
                                    CropRatioPreset.Ratio4_3 -> {
                                        fixAspectRatio = true
                                        aspectRatioX = 4
                                        aspectRatioY = 3
                                    }
                                    CropRatioPreset.Ratio16_9 -> {
                                        fixAspectRatio = true
                                        aspectRatioX = 16
                                        aspectRatioY = 9
                                    }
                                }
                            }
                            view.setImageCropOptions(options)
                            pendingCropUri?.let { uri ->
                                if (view.imageUri != uri) {
                                    view.setImageUriAsync(uri)
                                }
                            }
                        },
                    )
                    if (cropLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // ── Aspect Ratio Presets Row ──────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aspect Ratio:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CropRatioPreset.values().forEach { preset ->
                            item {
                                PresetChip(
                                    label = preset.label,
                                    selected = cropAspectRatioState == preset,
                                    onClick = {
                                        cropAspectRatioState = preset
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Crop tool buttons ──────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = {
                        cropViewRef?.rotateImage(-90)
                    }) {
                        Icon(
                            Icons.Outlined.RotateLeft,
                            contentDescription = "Rotate left",
                        )
                    }
                    FilledTonalIconButton(onClick = {
                        cropViewRef?.rotateImage(90)
                    }) {
                        Icon(
                            Icons.Outlined.RotateRight,
                            contentDescription = "Rotate right",
                        )
                    }
                    FilledTonalIconButton(onClick = {
                        cropViewRef?.flipImageHorizontally()
                    }) {
                        Icon(
                            Icons.Outlined.Flip,
                            contentDescription = "Flip",
                        )
                    }
                }

                // ── Cancel / Crop action bar ──────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            cropModeActive = false
                            pendingCropUri = null
                            cropViewRef = null
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val view = cropViewRef ?: return@Button
                            cropLoading = true
                            scope.launch {
                                val croppedBmp = withContext(Dispatchers.IO) {
                                    try {
                                        view.getCroppedImage()
                                    } catch (_: Exception) { null }
                                }
                                cropLoading = false
                                if (croppedBmp != null) {
                                    val cacheFile = withContext(Dispatchers.IO) {
                                        try {
                                            val f = File(
                                                context.cacheDir,
                                                "crop_result_${System.nanoTime()}.png",
                                            )
                                            FileOutputStream(f).use {
                                                croppedBmp.compress(
                                                    Bitmap.CompressFormat.PNG, 100, it,
                                                )
                                            }
                                            f
                                        } catch (_: Exception) { null }
                                    }
                                    if (cacheFile != null) {
                                        imageUri = Uri.fromFile(cacheFile).toString()
                                    } else {
                                        Toast.makeText(
                                            context, "Crop failed", Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context, "Crop failed", Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                cropModeActive = false
                                pendingCropUri = null
                                cropViewRef = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Crop")
                    }
                }
            }
        } else {
            // ────────────────────────────────────────────────────────────
            //  NORMAL EDITOR MODE
            // ────────────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                // Canvas slot — fills leftover vertical space (weight=1f)
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
                            .checkerboard()
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PhotoEditorView(ctx).also { view ->
                                    view.setBackgroundColor(AndroidColor.TRANSPARENT)
                                    view.source.setBackgroundColor(AndroidColor.TRANSPARENT)
                                    photoEditorView = view
                                    photoEditor = PhotoEditor.Builder(ctx, view)
                                        .setPinchTextScalable(true)
                                        .build()
                                }
                            },
                        )
                        if (imageUri.isNullOrBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { imagePickerLauncher.launch("image/*") }
                                    .padding(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Choose a photo to start",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                        ToolButton(
                            icon = Icons.Outlined.PhotoLibrary,
                            label = "Photo",
                            onClick = { imagePickerLauncher.launch("image/*") },
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.Outlined.Crop,
                            label = "Crop",
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = {
                                val view = photoEditorView ?: return@ToolButton
                                scope.launch {
                                    val cached = exportSourceAsCacheFile(context, view)
                                        ?: return@launch
                                    pendingCropUri = Uri.fromFile(cached)
                                    cropModeActive = true
                                }
                            },
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.Outlined.AutoAwesome,
                            label = "Cutout",
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = {
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
                            },
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.Outlined.Brush,
                            label = "Draw",
                            selected = mode == EditorMode.Draw,
                            enabled = !imageUri.isNullOrBlank(),
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
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = {
                                val editor = photoEditor ?: return@ToolButton
                                mode = EditorMode.Erase
                                editor.setBrushDrawingMode(true)
                                editor.setShape(
                                    ShapeBuilder().withShapeType(ShapeType.Brush)
                                        .withShapeSize(brushSize),
                                )
                                editor.brushEraser()
                            },
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.Outlined.TextFields,
                            label = "Text",
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = {
                                photoEditor?.setBrushDrawingMode(false)
                                mode = EditorMode.Idle
                                showTextDialog = true
                            },
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.AutoMirrored.Outlined.Undo,
                            label = "Undo",
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = { photoEditor?.undo() },
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.AutoMirrored.Outlined.Redo,
                            label = "Redo",
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = { photoEditor?.redo() },
                        )
                    }
                }

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            saveTagsText = ""
                            showSaveDialog = true
                        },
                        enabled = !saving && !bgRemovalRunning && !imageUri.isNullOrBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save sticker")
                    }
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

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save sticker") },
            text = {
                Column {
                    Text(
                        text = "Add tags to categorize this sticker (comma separated):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = saveTagsText,
                        onValueChange = { saveTagsText = it },
                        label = { Text("Tags") },
                        placeholder = { Text("e.g. John, Reaction, Funny") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        val tagsList = saveTagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val editor = photoEditor ?: return@TextButton
                        val view = photoEditorView ?: return@TextButton
                        saving = true
                        scope.launch {
                            val ok = exportAndImport(context, editor, view, store, tagsList)
                            saving = false
                            if (ok) {
                                Toast.makeText(context, "Sticker saved", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private enum class EditorMode { Idle, Draw, Erase }

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1.0f else 0.38f
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = (if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = (if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
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
    tags: List<String> = emptyList(),
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rasterized = rasterizeEditor(editor) ?: return@withContext false
        val normalized = squarePad(rasterized, EXPORT_SIZE)
        val cacheFile = File(context.cacheDir, "sticker_studio_${System.nanoTime()}.webp")
        FileOutputStream(cacheFile).use {
            // WEBP_LOSSLESS preserves the transparent background that cutouts produce.
            normalized.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it)
        }
        val entry = store.import(Uri.fromFile(cacheFile), "image/webp", tags)
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

private enum class CropRatioPreset(val label: String) {
    Free("Free"),
    Square("1:1"),
    Ratio4_3("4:3"),
    Ratio16_9("16:9")
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Modifier.checkerboard(
    squareSize: Float = 24f,
    color1: Color = Color.LightGray.copy(alpha = 0.35f),
    color2: Color = Color.White.copy(alpha = 0.35f)
): Modifier = this.drawBehind {
    val width = size.width
    val height = size.height
    val numCols = (width / squareSize).toInt() + 1
    val numRows = (height / squareSize).toInt() + 1
    for (r in 0..numRows) {
        for (c in 0..numCols) {
            val color = if ((r + c) % 2 == 0) color1 else color2
            drawRect(
                color = color,
                topLeft = Offset(c * squareSize, r * squareSize),
                size = Size(squareSize, squareSize)
            )
        }
    }
}

