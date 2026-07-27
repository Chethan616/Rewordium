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
import android.view.ViewGroup
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
import androidx.compose.material3.*
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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import coil.imageLoader
import coil.request.ImageRequest
import com.noxquill.rewordium.keyboard.app.settings.stickerstudio.AnimatedGifEncoder
import com.noxquill.rewordium.keyboard.app.settings.stickerstudio.StandardGifDecoder
import com.noxquill.rewordium.keyboard.app.settings.stickerstudio.GifHeaderParser

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
fun StickerEditorScreen(sourceUri: String?, gifMode: Boolean = false) = FlorisScreen {
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
    var showDecorateSheet by remember { mutableStateOf(false) }
    var outlineRunning by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var bgRemovalRunning by remember { mutableStateOf(false) }
    var isGif by remember { mutableStateOf(false) }
    var removeBgRequested by remember { mutableStateOf(false) }
    var outlineRequested by remember { mutableStateOf(false) }
    var imageUri by rememberSaveable { mutableStateOf(sourceUri) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTagsText by remember { mutableStateOf("") }
    var savePackId by remember { mutableStateOf<String?>(null) }
    val packs by store.packsFlow.collectAsState()

    // ── Inline crop mode state ─────────────────────────────────────────
    var cropModeActive by remember { mutableStateOf(false) }
    var cropViewRef by remember { mutableStateOf<CropImageView?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var cropLoading by remember { mutableStateOf(false) }
    var cropAspectRatioState by remember { mutableStateOf<CropRatioPreset>(CropRatioPreset.Free) }

    // Non-triggering refs to prevent recomposition loops in AndroidView
    val lastLoadedUriRef = remember { Ref<Uri>() }
    val lastAspectRatioRef = remember { Ref<CropRatioPreset>() }

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
            scope.launch {
                val isSelectedGif = withContext(Dispatchers.IO) {
                    try {
                        val mimeType = context.contentResolver.getType(uri)
                        var isGifFile = mimeType?.contains("gif") == true || uri.toString().lowercase().endsWith(".gif")
                        if (!isGifFile) {
                            context.contentResolver.openInputStream(uri)?.use { 
                                val bytes = ByteArray(3)
                                it.read(bytes)
                                val header = String(bytes)
                                if (header == "GIF") isGifFile = true
                            }
                        }
                        isGifFile
                    } catch (_: Exception) { false }
                }
                val cachedUri = withContext(Dispatchers.IO) {
                    try {
                        val ext = if (gifMode || isSelectedGif) "gif" else "png"
                        val cache = File(context.cacheDir, "pick_cache_${System.nanoTime()}.$ext")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cache.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@withContext null
                        Uri.fromFile(cache)
                    } catch (_: Exception) { null }
                }
                if (cachedUri != null) {
                    if (gifMode || isSelectedGif) {
                        imageUri = cachedUri.toString()
                        cropModeActive = false
                    } else {
                        pendingCropUri = cachedUri
                        cropModeActive = true
                    }
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
        val uri = Uri.parse(uriStr)
        val mimeType = context.contentResolver.getType(uri)
        var isGifFile = mimeType?.contains("gif") == true || uriStr.lowercase().endsWith(".gif")
        if (!isGifFile) {
            try {
                context.contentResolver.openInputStream(uri)?.use { 
                    val bytes = ByteArray(3)
                    it.read(bytes)
                    val header = String(bytes)
                    if (header == "GIF") isGifFile = true
                }
            } catch (e: Exception) {}
        }
        isGif = gifMode || isGifFile
        if (isGif) {
            val request = coil.request.ImageRequest.Builder(context)
                .data(uri)
                .decoderFactory(
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        coil.decode.ImageDecoderDecoder.Factory()
                    } else {
                        coil.decode.GifDecoder.Factory()
                    }
                )
                .target(view.source)
                .listener(
                    onSuccess = { _, result ->
                        (result.drawable as? android.graphics.drawable.Animatable)?.start()
                    }
                )
                .build()
            context.imageLoader.enqueue(request)
        } else {
            val bmp = decodeUriToBitmap(context, uri, EXPORT_SIZE)
            if (bmp != null) view.source.setImageBitmap(bmp)
        }
    }

    // Auto-launch image picker if no source URI is provided.
    LaunchedEffect(Unit) {
        if (imageUri.isNullOrBlank()) {
            val mime = if (gifMode) "image/gif" else "image/*"
            imagePickerLauncher.launch(mime)
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
                            // 1. Create a FrameLayout to act as a layout boundary
                            val container = android.widget.FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }

                            // 2. Initialize the CropImageView
                            val cropView = CropImageView(ctx).apply {
                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                guidelines = CropImageView.Guidelines.ON
                                cropShape = CropImageView.CropShape.RECTANGLE
                                isAutoZoomEnabled = true
                                lastAspectRatioRef.value = cropAspectRatioState

                                // Swallow internal decode exceptions to prevent hard crashes
                                setOnSetImageUriCompleteListener { _, _, error ->
                                    if (error != null) {
                                        Toast.makeText(ctx, "Failed to load image", Toast.LENGTH_SHORT).show()
                                    }
                                }

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
                                        CropRatioPreset.Free -> fixAspectRatio = false
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
                                        CropRatioPreset.Ratio9_16 -> {
                                            fixAspectRatio = true
                                            aspectRatioX = 9
                                            aspectRatioY = 16
                                        }
                                    }
                                }
                                setImageCropOptions(options)

                                cropViewRef = this
                                pendingCropUri?.let { uri ->
                                    lastLoadedUriRef.value = uri
                                    // Wait for the view to have dimensions before loading the URI
                                    viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                        override fun onGlobalLayout() {
                                            if (width > 0 && height > 0) {
                                                viewTreeObserver.removeOnGlobalLayoutListener(this)
                                                setImageUriAsync(uri)
                                            }
                                        }
                                    })
                                }
                            }

                            // 3. Add the cropper to the container and return the container to Compose
                            container.addView(cropView)
                            container
                        },
                        update = { container ->
                            // 4. Extract the CropImageView from the FrameLayout container
                            val view = container.getChildAt(0) as CropImageView

                            if (lastAspectRatioRef.value != cropAspectRatioState) {
                                lastAspectRatioRef.value = cropAspectRatioState
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
                                        CropRatioPreset.Free -> fixAspectRatio = false
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
                                        CropRatioPreset.Ratio9_16 -> {
                                            fixAspectRatio = true
                                            aspectRatioX = 9
                                            aspectRatioY = 16
                                        }
                                    }
                                }
                                view.setImageCropOptions(options)
                            }

                            val uri = pendingCropUri
                            if (uri != null && lastLoadedUriRef.value != uri) {
                                lastLoadedUriRef.value = uri
                                
                                if (view.width > 0 && view.height > 0) {
                                    view.setImageUriAsync(uri)
                                } else {
                                    view.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                        override fun onGlobalLayout() {
                                            if (view.width > 0 && view.height > 0) {
                                                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                                view.setImageUriAsync(uri)
                                            }
                                        }
                                    })
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
                            onClick = { 
                                val mime = if (gifMode || isGif) "image/gif" else "image/*"
                                imagePickerLauncher.launch(mime) 
                            },
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.Outlined.Crop,
                            label = "Crop",
                            enabled = !imageUri.isNullOrBlank() && !isGif,
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
                                if (isGif) {
                                    removeBgRequested = true
                                    Toast.makeText(context, "Background will be removed when saving.", Toast.LENGTH_SHORT).show()
                                    return@ToolButton
                                }
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
                            icon = Icons.Outlined.RoundedCorner,
                            label = "Outline",
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = {
                                val editor = photoEditor ?: return@ToolButton
                                val view = photoEditorView ?: return@ToolButton
                                if (isGif) {
                                    outlineRequested = true
                                    Toast.makeText(context, "Outline will be added when saving.", Toast.LENGTH_SHORT).show()
                                    return@ToolButton
                                }
                                outlineRunning = true
                                scope.launch {
                                    addStickerOutline(editor, view)
                                    outlineRunning = false
                                }
                            }
                        )
                    }
                    item {
                        ToolButton(
                            icon = Icons.Outlined.EmojiEmotions,
                            label = "Decorate",
                            enabled = !imageUri.isNullOrBlank(),
                            onClick = { showDecorateSheet = true }
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
        var fontSize by remember { mutableStateOf(40f) }
        var selectedStyle by remember { mutableStateOf(StickerTextStyle.Meme) }
        var aiLoading by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add text") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Text Style", style = MaterialTheme.typography.labelMedium)
                        TextButton(
                            onClick = {
                                aiLoading = true
                                scope.launch {
                                    val aiManager = com.noxquill.rewordium.keyboard.ime.ai.AIManager(context)
                                    val res = aiManager.rewriteTextWithPrompt("Generate a short, funny meme caption suitable for a sticker (max 4 words). Output just the caption without quotes.").getOrNull() as? String
                                    aiLoading = false
                                    if (res != null) draft = res
                                }
                            },
                            enabled = !aiLoading
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI Caption ?")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(StickerTextStyle.values().toList()) { style ->
                            FilterChip(
                                selected = selectedStyle == style,
                                onClick = { selectedStyle = style },
                                label = { Text(style.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(12.dp))
                    Text("Size: ", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 24f..96f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.isNotBlank()) {
                        val bmp = renderStyledTextBitmap(draft, textColor.toArgb(), fontSize, selectedStyle)
                        photoEditor?.addImage(bmp)
                        showTextDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            },
        )
    }
    
    if (showDecorateSheet) {
        AlertDialog(
            onDismissRequest = { showDecorateSheet = false },
            title = { Text("Add decoration") },
            text = {
                Column {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(DECORATION_ITEMS) { (emoji, _) ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        val editor = photoEditor ?: return@clickable
                                        scope.launch {
                                            val bmp = withContext(Dispatchers.Default) { emojiToBitmap(emoji, 192) }
                                            editor.addImage(bmp)
                                        }
                                        showDecorateSheet = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 28.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDecorateSheet = false }) { Text("Close") } }
        )
    }
    

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save sticker") },
            text = {
                Column {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedPackName = if (savePackId == null) "None (Uncategorized)" else packs.find { it.id == savePackId }?.name ?: "None"
                    Text("Sticker Pack:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedPackName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Uncategorized)") },
                                onClick = { savePackId = null; expanded = false }
                            )
                            packs.forEach { pack ->
                                DropdownMenuItem(
                                    text = { Text(pack.name) },
                                    onClick = { savePackId = pack.id; expanded = false }
                                )
                            }
                        }
                    }

                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        val editor = photoEditor ?: return@TextButton
                        val view = photoEditorView ?: return@TextButton
                        saving = true
                        scope.launch {
                            val ok = if (isGif) {
                                exportAnimatedGif(
                                    context, editor, view, store, savePackId,
                                    Uri.parse(imageUri!!), removeBgRequested, outlineRequested
                                )
                            } else {
                                exportAndImport(context, editor, view, store, savePackId)
                            }
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

private enum class StickerTextStyle(val label: String) {
    Plain("Plain"), Bold("Bold"), Meme("Meme"), Bubbly("Bubbly")
}

private val DECORATION_ITEMS = listOf(
    "😎" to "Cool", "🔥" to "Fire", "👑" to "Crown", "💯" to "100", "💬" to "Speech",
    "⭐" to "Star", "✨" to "Sparkle", "💢" to "Anger", "💦" to "Sweat", "💤" to "Sleep",
    "❤️" to "Heart", "💔" to "Broken", "⚠️" to "Warning", "❌" to "No", "✅" to "Yes",
    "🎩" to "Top Hat", "🧢" to "Cap", "🎀" to "Bow", "🎈" to "Balloon", "🎁" to "Gift",
    "🥳" to "Party", "🎉" to "Celebrate", "🌈" to "Rainbow", "☀️" to "Sun", "🌙" to "Moon"
)

private fun emojiToBitmap(emoji: String, size: Int): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.8f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText(emoji, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
    return bmp
}

private fun renderStyledTextBitmap(
    text: String,
    textColor: Int,
    fontSize: Float,
    style: StickerTextStyle
): android.graphics.Bitmap {
    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        this.textSize = fontSize
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        this.textSize = fontSize
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        this.style = android.graphics.Paint.Style.STROKE
        strokeWidth = fontSize * 0.15f
    }
    
    val fm = fillPaint.fontMetrics
    val textHeight = fm.bottom - fm.top
    val textWidth = fillPaint.measureText(text)
    
    val pad = fontSize * 0.5f
    val w = (textWidth + pad * 2).toInt()
    val h = (textHeight + pad * 2).toInt()
    
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    
    val x = w / 2f
    val y = h / 2f - (fm.descent + fm.ascent) / 2f
    
    when (style) {
        StickerTextStyle.Plain -> {
            fillPaint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText(text, x, y, fillPaint)
        }
        StickerTextStyle.Bold -> {
            canvas.drawText(text, x, y, fillPaint)
        }
        StickerTextStyle.Meme -> {
            val upper = text.uppercase()
            canvas.drawText(upper, x, y, strokePaint)
            fillPaint.color = android.graphics.Color.WHITE
            canvas.drawText(upper, x, y, fillPaint)
        }
        StickerTextStyle.Bubbly -> {
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
            }
            val rect = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawRoundRect(rect, h/2f, h/2f, bgPaint)
            fillPaint.color = if (androidx.compose.ui.graphics.Color(textColor).luminance() > 0.5f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            canvas.drawText(text, x, y, fillPaint)
        }
    }
    return bmp
}

private suspend fun addStickerOutline(editor: ja.burhanrashid52.photoeditor.PhotoEditor, view: ja.burhanrashid52.photoeditor.PhotoEditorView): Boolean {
    return withContext(Dispatchers.Default) {
        try {
            val width = view.width
            val height = view.height
            if (width <= 0 || height <= 0) return@withContext false
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            withContext(Dispatchers.Main) {
                view.draw(canvas)
            }
            val out = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val outCanvas = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 24f
                strokeJoin = android.graphics.Paint.Join.ROUND
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val pixels = IntArray(width * height)
            bmp.getPixels(pixels, 0, width, 0, 0, width, height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val p = pixels[y * width + x]
                    if (android.graphics.Color.alpha(p) > 10) {
                        outCanvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                    }
                }
            }
            outCanvas.drawBitmap(bmp, 0f, 0f, null)
            withContext(Dispatchers.Main) {
                editor.clearAllViews()
                view.source.setImageBitmap(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}


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
    packId: String? = null
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rasterized = rasterizeEditor(editor) ?: return@withContext false
        val normalized = squarePad(rasterized, EXPORT_SIZE)
        val cacheFile = File(context.cacheDir, "sticker_studio_${System.nanoTime()}.webp")
        FileOutputStream(cacheFile).use {
            // WEBP_LOSSLESS preserves the transparent background that cutouts produce.
            normalized.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it)
        }
        val entry = store.import(Uri.fromFile(cacheFile), "image/webp", packId)
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
    Ratio16_9("16:9"),
    Ratio9_16("9:16")
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

private class Ref<T>(var value: T? = null)



private fun applyOutlineToBitmap(bmp: Bitmap): Bitmap? {
    val width = bmp.width
    val height = bmp.height
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val outCanvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 24f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val pixels = IntArray(width * height)
    bmp.getPixels(pixels, 0, width, 0, 0, width, height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val p = pixels[y * width + x]
            if (android.graphics.Color.alpha(p) > 10) {
                outCanvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }
    }
    outCanvas.drawBitmap(bmp, 0f, 0f, null)
    return out
}

class SimpleBitmapProvider : com.noxquill.rewordium.keyboard.app.settings.stickerstudio.GifDecoder.BitmapProvider {
    override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }
    override fun release(bitmap: Bitmap) {
        bitmap.recycle()
    }
    override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
    override fun release(bytes: ByteArray) {}
    override fun obtainIntArray(size: Int): IntArray = IntArray(size)
    override fun release(array: IntArray) {}
}

private suspend fun exportAnimatedGif(
    context: android.content.Context,
    editor: PhotoEditor,
    view: PhotoEditorView,
    store: UserStickerStore,
    packId: String? = null,
    sourceUri: Uri,
    removeBg: Boolean,
    outline: Boolean
): Boolean = withContext(Dispatchers.IO) {
    try {
        val overlayBitmap = withContext(Dispatchers.Main) {
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            view.source.visibility = android.view.View.INVISIBLE
            view.draw(canvas)
            view.source.visibility = android.view.View.VISIBLE
            bmp
        }

        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: return@withContext false

        val parser = GifHeaderParser()
        parser.setData(bytes)
        val header = parser.parseHeader()
        if (header.status != 0) return@withContext false

        val decoder = StandardGifDecoder(SimpleBitmapProvider())
        decoder.setData(header, bytes)
        val frameCount = decoder.frameCount
        if (frameCount == 0) return@withContext false

        val outBytes = java.io.ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        encoder.start(outBytes)
        encoder.setRepeat(0)

        val targetSize = EXPORT_SIZE.toFloat()
        val w = decoder.width.toFloat()
        val h = decoder.height.toFloat()
        val scale = if (w > h) targetSize / w else targetSize / h
        val outW = (w * scale).toInt()
        val outH = (h * scale).toInt()
        encoder.setSize(outW, outH)

        val framesToKeep = minOf(frameCount, 40)
        
        for (i in 0 until framesToKeep) {
            decoder.advance()
            val originalFrame = decoder.nextFrame ?: continue
            var frame = Bitmap.createScaledBitmap(originalFrame, outW, outH, true)
            
            if (removeBg) {
                val cutout = SubjectSegmentationHelper.run(frame)
                if (cutout != null) frame = cutout
            }
            
            if (outline) {
                val outlined = applyOutlineToBitmap(frame)
                if (outlined != null) frame = outlined
            }
            
            val composite = Bitmap.createBitmap(EXPORT_SIZE, EXPORT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composite)
            
            val left = (EXPORT_SIZE - frame.width) / 2f
            val top = (EXPORT_SIZE - frame.height) / 2f
            canvas.drawBitmap(frame, left, top, null)
            
            val overlayPad = squarePad(overlayBitmap, EXPORT_SIZE)
            canvas.drawBitmap(overlayPad, 0f, 0f, null)
            
            encoder.setDelay(decoder.nextDelay)
            encoder.addFrame(composite)
            
            frame.recycle()
            composite.recycle()
            overlayPad.recycle()
        }
        
        encoder.finish()
        overlayBitmap.recycle()
        
        val cacheOut = File(context.cacheDir, "anim_out_.gif")
        FileOutputStream(cacheOut).use { it.write(outBytes.toByteArray()) }

        val entry = store.import(Uri.fromFile(cacheOut), "image/gif", packId)
        cacheOut.delete()
        
        entry != null
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
