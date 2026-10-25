package com.noxquill.rewordium.app.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.noxquill.rewordium.PlatformSetupStatus
import com.noxquill.rewordium.app.model.WritingTool
import com.noxquill.rewordium.app.model.toolFor
import com.noxquill.rewordium.app.model.writingTools
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.JadeMascot
import com.noxquill.rewordium.data.AccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    account: AccountInfo?,
    platformStatus: PlatformSetupStatus,
    onOpenTool: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Good writing starts here", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(account?.name?.let { "Hello, $it" } ?: "Hello, writer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "Open Jade AI", modifier = Modifier.padding(14.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)))
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Stars, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("YOUR WRITING COMPANION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Text("A little more you, in every sentence.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, lineHeight = MaterialTheme.typography.headlineSmall.lineHeight)
                        Text("Smooth out the rough edges while keeping your voice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { onOpenTool("paraphraser") },
                            modifier = Modifier.expressivePress(),
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            Text("Start writing")
                            Icon(Icons.Filled.ChevronRight, null, Modifier.padding(start = 4.dp).size(19.dp))
                        }
                    }
                    JadeMascot(size = 90.dp, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Your words, your way", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("A few thoughtful tools to get you started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onOpenTool("tools") }) { Text("See all") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val featured = writingTools.filter { it.id in setOf("paraphraser", "grammar", "summarizer", "translator") }
                featured.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { tool ->
                            ToolTile(tool, Modifier.weight(1f)) { onOpenTool(tool.id) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            ElevatedCard(
                onClick = onOpenPro,
                modifier = Modifier.fillMaxWidth().expressivePress(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Icon(if (account?.isPro == true) Icons.Filled.Stars else Icons.Filled.Lock, null, Modifier.padding(12.dp).size(23.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (account?.isPro == true) "Rewordium Pro is on" else "Make room for more ideas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (account?.isPro == true) "Your writing tools are ready." else "${account?.credits ?: 0} credits left · Explore Pro", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            PlatformSetupCard(
                status = platformStatus,
                onOpenSettings = onOpenSettings,
                onOpenKeyboard = { onOpenTool("keyboard_settings") },
            )
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
fun ToolsScreen(onOpenTool: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = writingTools.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Writing tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Pick a place to begin. Jade is here if you’re not sure.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            placeholder = { Text("Find a tool") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { tool -> ToolTile(tool, Modifier.fillMaxWidth()) { onOpenTool(tool.id) } }
        }
    }
}

@Composable
private fun ToolTile(tool: WritingTool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.expressivePress(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(tool.icon, contentDescription = null, Modifier.padding(11.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlatformSetupCard(
    status: PlatformSetupStatus,
    onOpenSettings: () -> Unit,
    onOpenKeyboard: () -> Unit,
) {
    ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ready when you are", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val accessText = if (status.accessibilityEnabled) "Accessibility tools are on" else "Set up accessibility tools"
            val keyboardText = when {
                status.keyboardSelected -> "Rewordium keyboard is selected"
                status.keyboardEnabled -> "Choose Rewordium keyboard to start"
                else -> "Enable your Rewordium keyboard"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = if (status.accessibilityEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.padding(9.dp).size(19.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(accessText, style = MaterialTheme.typography.titleSmall)
                    Text("Use writing actions while you type.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = if (status.keyboardSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Icon(Icons.Filled.Keyboard, null, Modifier.padding(9.dp).size(19.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(keyboardText, style = MaterialTheme.typography.titleSmall)
                    Text("Bring Rewordium into the apps you use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onOpenSettings, label = { Text("Accessibility") })
                AssistChip(onClick = onOpenKeyboard, label = { Text("Keyboard") })
            }
        }
    }
}

@Composable
fun WritingToolScreen(
    toolId: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onGenerate: suspend (text: String, tone: String, targetLanguage: String) -> String,
) {
    val tool = toolFor(toolId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember(tool.id) { mutableStateOf("") }
    var result by remember(tool.id) { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("English") }
    var selectedTone by remember { mutableStateOf("Clear") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val importText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                val isPdf = context.contentResolver.getType(uri) == "application/pdf" ||
                    uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)
                if (isPdf) extractPdfText(context, uri) else {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Couldn’t open this file.")
                }
            }.onSuccess { input = it }
                .onFailure { error = it.message ?: "Couldn’t read this document." }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tool.id == "document") TextButton(onClick = { importText.launch(arrayOf("text/plain", "application/pdf")) }) { Text("Import text or PDF") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Your draft", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; error = null },
                            modifier = Modifier.fillMaxWidth().height(216.dp),
                            placeholder = { Text(if (tool.id == "detector") "Paste writing you’d like Jade to review…" else "Type or paste your writing here…") },
                            shape = MaterialTheme.shapes.medium,
                            minLines = 6,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${input.length} / 8,000", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (tool.id == "document") Text("Text files only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (tool.id == "tone") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose a tone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Clear", "Warm", "Confident", "Professional").forEach { tone ->
                                FilterChip(selected = selectedTone == tone, onClick = { selectedTone = tone }, label = { Text(tone) })
                            }
                        }
                    }
                }
            }
            if (tool.id == "translator") {
                item {
                    OutlinedTextField(
                        value = targetLanguage,
                        onValueChange = { targetLanguage = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Translate to") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
            if (error != null) item {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            result = ""
                            error = null
                            runCatching { onGenerate(input, selectedTone, targetLanguage) }
                                .onSuccess { result = it }
                                .onFailure { error = it.message ?: "We couldn’t finish that. Please try again." }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !busy,
                    shape = MaterialTheme.shapes.large,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else {
                        Icon(tool.icon, null, Modifier.size(19.dp))
                        Text("  ${tool.actionLabel}")
                    }
                }
                Text("A credit is used when Jade completes a request. Your original text stays here until you clear it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                AnimatedVisibility(result.isNotBlank()) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (tool.id == "detector") "Jade’s review" else "Your result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Rewordium result", result))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }) { Text("Copy") }
                            }
                            Text(result, style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = onUpgrade) { Text("Need more credits? See Pro") }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { input = ""; result = ""; error = null }) { Text("Clear draft") }
            }
        }
    }
}

private suspend fun extractPdfText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Couldn’t open this PDF.")
    descriptor.use { file ->
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
                    if (length >= 8000) {
                        setLength(8000)
                        break
                    }
                }
                if (isBlank()) throw IllegalStateException("No readable text was found in this PDF.")
            }
        } finally {
            recognizer.close()
            renderer.close()
        }
    }
}
