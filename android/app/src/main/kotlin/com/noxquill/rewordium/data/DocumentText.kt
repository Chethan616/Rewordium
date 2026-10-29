package com.noxquill.rewordium.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a picked text file or PDF (PDF pages via on-device text recognition) into plain text. */
object DocumentText {
    const val MAX_CHARS = 8_000

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val isPdf = context.contentResolver.getType(uri) == "application/pdf" ||
            uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)
        val text = if (isPdf) extractPdfText(context, uri) else {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("Couldn’t open this file.")
        }
        text.trim().take(MAX_CHARS).ifBlank { throw IllegalStateException("No readable text was found in this file.") }
    }

    private fun extractPdfText(context: Context, uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Couldn’t open this PDF.")
        return descriptor.use { file ->
            val renderer = PdfRenderer(file)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                buildString {
                    val pageCount = renderer.pageCount.coerceAtMost(30)
                    for (index in 0 until pageCount) {
                        val page = renderer.openPage(index)
                        val scale = minOf(1.5f, 1800f / maxOf(page.width, page.height).toFloat())
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
                            if (text.isNotBlank()) append(text).append('\n')
                        } finally {
                            bitmap.recycle()
                            page.close()
                        }
                        if (length >= MAX_CHARS) break
                    }
                }
            } finally {
                recognizer.close()
                renderer.close()
            }
        }
    }
}
