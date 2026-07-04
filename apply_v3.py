import os

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\StickerEditorScreen.kt'

import subprocess
subprocess.run(['git', 'restore', path], check=True)

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Imports
c = c.replace('import androidx.compose.ui.graphics.Color\n', 
'''import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
''')

# 2. States in FlorisScreen
c = c.replace('var showTextDialog by remember { mutableStateOf(false) }',
'''var showTextDialog by remember { mutableStateOf(false) }
    var showDecorateSheet by remember { mutableStateOf(false) }
    var outlineRunning by remember { mutableStateOf(false) }''')

# 3. Toolbar Buttons
target_buttons = '''ToolButton(
                            icon = Icons.Outlined.TextFields,
                            label = "Text",
                            selected = showTextDialog,
                            onClick = { showTextDialog = true }
                        )'''
replacement_buttons = '''ToolButton(
                            icon = Icons.Outlined.TextFields,
                            label = "Text",
                            selected = showTextDialog,
                            onClick = { showTextDialog = true }
                        )
                        ToolButton(
                            icon = Icons.Outlined.RoundedCorner,
                            label = "Outline",
                            selected = outlineRunning,
                            onClick = {
                                if (outlineRunning) return@ToolButton
                                outlineRunning = true
                                val editor = photoEditor ?: return@ToolButton
                                val view = photoEditorView ?: return@ToolButton
                                scope.launch {
                                    val ok = addStickerOutline(editor, view)
                                    outlineRunning = false
                                    if (!ok) {
                                        Toast.makeText(context, "Failed to apply outline", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        ToolButton(
                            icon = Icons.Outlined.EmojiEmotions,
                            label = "Decorate",
                            selected = showDecorateSheet,
                            onClick = { showDecorateSheet = true }
                        )'''
c = c.replace(target_buttons, replacement_buttons)

# 4. showTextDialog replacement
target_text_dialog = '''if (showTextDialog) {
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
    }'''

replacement_text_dialog = '''if (showTextDialog) {
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
                                    val aiManager = com.noxquill.rewordium.keyboard.ime.ai.AIManager.get(context)
                                    val res = aiManager.rewriteTextWithPrompt("Generate a short, funny meme caption suitable for a sticker (max 4 words). Output just the caption without quotes.").getOrNull()
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
    '''
c = c.replace(target_text_dialog, replacement_text_dialog)

# 5. Helper classes and functions at bottom
target_helpers = '''private enum class EditorMode { Idle, Draw, Erase }'''
replacement_helpers = '''private enum class EditorMode { Idle, Draw, Erase }

private enum class StickerTextStyle(val label: String) {
    Plain("Plain"), Bold("Bold"), Meme("Meme"), Bubbly("Bubbly")
}

private val DECORATION_ITEMS = listOf(
    "??" to "Cool", "??" to "Fire", "??" to "Crown", "??" to "100", "??" to "Speech",
    "?" to "Star", "?" to "Sparkle", "??" to "Anger", "??" to "Sweat", "??" to "Sleep",
    "??" to "Heart", "??" to "Broken", "??" to "Warning", "??" to "No", "?" to "Yes",
    "??" to "Top Hat", "??" to "Cap", "??" to "Bow", "??" to "Balloon", "??" to "Gift",
    "??" to "Party", "??" to "Celebrate", "??" to "Rainbow", "??" to "Sun", "??" to "Moon"
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
'''
c = c.replace(target_helpers, replacement_helpers)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Restored features perfectly.")
