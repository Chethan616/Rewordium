package com.noxquill.rewordium.keyboard.app.settings.stickerstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.ime.media.sticker.UserStickerStore
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.graphics.Color as AndroidColor

@Composable
fun AnimatedStickerImportScreen() = FlorisScreen {
    title = "Edit animated GIF"
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val store = remember { UserStickerStore.get(context) }

    var working by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var memeText by remember { mutableStateOf("") }

    val pickGif = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            memeText = ""
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
            if (selectedUri == null) {
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
                    text = "Pick a GIF to trim and add meme text.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { pickGif.launch("image/gif") }) {
                    Text("Pick GIF")
                }
            } else {
                val painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(selectedUri)
                        .decoderFactory(
                            if (android.os.Build.VERSION.SDK_INT >= 28) {
                                ImageDecoderDecoder.Factory()
                            } else {
                                GifDecoder.Factory()
                            }
                        )
                        .build()
                )
                Image(
                    painter = painter,
                    contentDescription = "GIF Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )

                OutlinedTextField(
                    value = memeText,
                    onValueChange = { memeText = it },
                    label = { Text("Meme Text (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (working) {
                    CircularProgressIndicator()
                    Text(statusLine, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = { selectedUri = null }) {
                            Text("Clear")
                        }
                        Button(onClick = {
                            working = true
                            statusLine = "Transcoding GIF..."
                            scope.launch {
                                val ok = transcodeAndImport(context, selectedUri!!, memeText, store) { line ->
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
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun transcodeAndImport(
    context: android.content.Context,
    sourceUri: Uri,
    memeText: String,
    store: UserStickerStore,
    onStatus: (String) -> Unit,
): Boolean = withContext(Dispatchers.IO) {
    try {
        onStatus("Reading GIF...")
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: return@withContext false

        // We use our StandardGifDecoder to read the frames.
        val parser = GifHeaderParser()
        parser.setData(bytes)
        val header = parser.parseHeader()
        if (header.status != 0) return@withContext false

        val decoder = StandardGifDecoder(SimpleBitmapProvider())
        decoder.setData(header, bytes)
        val frameCount = decoder.frameCount
        if (frameCount == 0) return@withContext false

        onStatus("Rendering frames...")
        val outBytes = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        encoder.start(outBytes)
        encoder.setRepeat(0) // loop forever
        
        // Target size to keep it manageable
        val targetSize = 512f
        val w = decoder.width.toFloat()
        val h = decoder.height.toFloat()
        val scale = if (w > h) targetSize / w else targetSize / h
        val outW = (w * scale).toInt()
        val outH = (h * scale).toInt()
        encoder.setSize(outW, outH)

        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = outH * 0.15f
        }
        val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = outH * 0.15f
            style = Paint.Style.STROKE
            strokeWidth = outH * 0.03f
        }

        // Limit to 40 frames max (to keep size and time small)
        val framesToKeep = minOf(frameCount, 40)
        
        for (i in 0 until framesToKeep) {
            onStatus("Frame " + (i + 1) + " / " + framesToKeep)
            decoder.advance()
            val frame = decoder.nextFrame ?: continue
            
            // Scale frame
            val scaled = Bitmap.createScaledBitmap(frame, outW, outH, true)
            
            // Draw text
            if (memeText.isNotBlank()) {
                val canvas = Canvas(scaled)
                val x = outW / 2f
                val fm = paintFill.fontMetrics
                val y = outH - fm.descent - (outH * 0.05f)
                val textUpper = memeText.uppercase()
                canvas.drawText(textUpper, x, y, paintStroke)
                canvas.drawText(textUpper, x, y, paintFill)
            }
            
            encoder.setDelay(decoder.nextDelay)
            encoder.addFrame(scaled)
            scaled.recycle()
        }
        
        onStatus("Finalizing...")
        encoder.finish()

        val cacheOut = File(context.cacheDir, "anim_out_.gif")
        FileOutputStream(cacheOut).use { it.write(outBytes.toByteArray()) }

        onStatus("Adding to library...")
        val entry = store.import(Uri.fromFile(cacheOut), "image/gif")
        cacheOut.delete()
        
        entry != null
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
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
