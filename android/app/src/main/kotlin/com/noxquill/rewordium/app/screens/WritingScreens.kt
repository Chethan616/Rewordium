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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.noxquill.rewordium.app.ui.MorphBadge
import com.noxquill.rewordium.app.ui.MorphingBackdrop
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.avatar.botTypeFor
import com.noxquill.rewordium.app.ui.avatar.flightEnd
import com.noxquill.rewordium.app.ui.avatar.rememberFlightSpot
import com.noxquill.rewordium.app.ui.avatar.LocalBotFlight
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    account: AccountInfo?,
    platformStatus: PlatformSetupStatus,
    recentTools: List<WritingTool>,
    mascotType: String,
    onOpenTool: (String) -> Unit,
    onOpenRecentTool: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenJade: () -> Unit,
    onOpenPro: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val flight = LocalBotFlight.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotAvatar(type = mascotType, mood = BotMood.Idle, size = 58.dp, label = "Rewordium mascot")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val firstName = account?.name?.trim()?.substringBefore(' ')?.takeIf(String::isNotBlank)
                    Text(
                        firstName ?: "Your writing space",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(if (account?.isPro == true) "Rewordium Pro" else "${account?.credits ?: 0} writing credits", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { haptics.click(); onOpenSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
        item {
            Surface(
                onClick = { haptics.click(); onOpenTool("paraphraser") },
                modifier = Modifier.fillMaxWidth().expressivePress(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Start with a thought", style = MaterialTheme.typography.headlineSmall)
                        Text("Make it clearer while keeping your voice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { haptics.click(); onOpenTool("paraphraser") }, modifier = Modifier.padding(top = 4.dp).expressivePress(), shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Filled.EditNote, null, Modifier.size(19.dp))
                            Text("Start writing", Modifier.padding(start = 8.dp))
                        }
                    }
                    MorphBadge(
                        phase = 1.18f,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(106.dp),
                    ) {
                        BotAvatar(type = "cloud", mood = BotMood.Happy, size = 92.dp, label = "Writing companion", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quick actions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { haptics.click(); onOpenTool("tools") }) { Text("All tools") }
                }
                // One deliberate, scrollable action rail reads faster than a tall
                // collection of uneven chips. It also keeps the Home screen calm.
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 6.dp),
                ) {
                    items(
                        items = writingTools.filter { it.id in setOf("paraphraser", "grammar", "summarizer", "translator", "tone", "document", "humanizer") },
                        key = { it.id },
                    ) { tool ->
                        QuickActionCard(tool = tool, onClick = { haptics.tick(); onOpenTool(tool.id) })
                    }
                    item(key = "jade") {
                        QuickActionCard(
                            title = "Ask Jade",
                            subtitle = "Think it through",
                            icon = Icons.Filled.AutoAwesome,
                            index = 5,
                            onClick = { haptics.tick(); onOpenJade() },
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recent", style = MaterialTheme.typography.titleLarge)
                    Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        if (recentTools.isEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BotAvatar(type = "circle", mood = BotMood.Idle, size = 42.dp, still = true)
                                Column {
                                    Text("Your recent tools will show up here", style = MaterialTheme.typography.titleSmall)
                                    Text("Choose a quick action to get started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else recentTools.forEachIndexed { index, tool ->
                            key(tool.id) {
                                val botType = botTypeFor(tool.id)
                                val spot = rememberFlightSpot()
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        haptics.click()
                                        flight?.toChat(tool.id, botType, BotMood.Idle, spot.coords)
                                        onOpenRecentTool(tool.id)
                                    }.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    BotAvatar(
                                        type = botType,
                                        mood = BotMood.Idle,
                                        size = 40.dp,
                                        still = true,
                                        label = tool.title,
                                        modifier = Modifier.flightEnd(flight, tool.id, spot),
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(tool.title, style = MaterialTheme.typography.titleSmall)
                                        Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            val setupSummary = when {
                platformStatus.accessibilityEnabled && platformStatus.keyboardSelected -> "Keyboard and accessibility are ready"
                platformStatus.accessibilityEnabled -> "Accessibility is on · set up your keyboard"
                platformStatus.keyboardSelected -> "Keyboard is ready · set up accessibility"
                else -> "Set up your keyboard and accessibility tools"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotAvatar(type = "droid", mood = BotMood.Idle, size = 42.dp, still = true)
                Column(Modifier.weight(1f)) {
                    Text("Your setup", style = MaterialTheme.typography.titleSmall)
                    Text(setupSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.ChevronRight, "Open settings") }
            }
        }
        item {
            ElevatedCard(onClick = onOpenPro, modifier = Modifier.fillMaxWidth().expressivePress(), shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotAvatar(type = "star", mood = BotMood.Happy, size = 42.dp, still = true)
                    Column(Modifier.weight(1f)) {
                        Text(if (account?.isPro == true) "Rewordium Pro is active" else "More room for your ideas", style = MaterialTheme.typography.titleSmall)
                        Text(if (account?.isPro == true) "Your writing tools are ready." else "${account?.credits ?: 0} credits · Explore Pro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Icon(Icons.Filled.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(tool: WritingTool, onClick: () -> Unit) = QuickActionCard(
    title = tool.title,
    subtitle = tool.description,
    icon = tool.icon,
    index = writingTools.indexOfFirst { it.id == tool.id }.coerceAtLeast(0),
    onClick = onClick,
)

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    index: Int,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tones = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)
    val foregrounds = listOf(scheme.onPrimaryContainer, scheme.onTertiaryContainer, scheme.onSecondaryContainer)
    val toneIndex = index % tones.size
    Surface(
        onClick = onClick,
        modifier = Modifier.width(148.dp).expressivePress(0.94f),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MorphBadge(
                phase = index + 0.28f,
                color = tones[toneIndex],
                modifier = Modifier.size(42.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(20.dp), tint = foregrounds[toneIndex])
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ToolsScreen(onOpenTool: (String) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = writingTools.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Writing tools", style = MaterialTheme.typography.titleLarge)
                Text("Choose what you want to work on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder = { Text("Find a tool") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { tool -> ToolTile(tool, Modifier.fillMaxWidth()) { onOpenTool(tool.id) } }
        }
    }
}

@Composable
private fun ToolTile(tool: WritingTool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    ElevatedCard(
        onClick = { haptics.click(); onClick() },
        modifier = modifier.expressivePress(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MorphBadge(
                phase = writingTools.indexOfFirst { it.id == tool.id }.coerceAtLeast(0) + 0.24f,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(tool.icon, contentDescription = null, Modifier.align(Alignment.Center).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
fun WritingToolScreen(
    toolId: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onGenerate: suspend (text: String, tone: String, targetLanguage: String) -> String,
) {
    val tool = toolFor(toolId)
    val botFlight = LocalBotFlight.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
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

    Column(Modifier.fillMaxSize().onGloballyPositioned { botFlight?.chatScreen = it }) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { haptics.click(); onBack() }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            val headerBotType = botTypeFor(tool.id)
            val headerMood = if (busy) BotMood.Working else BotMood.Idle
            MorphBadge(
                phase = writingTools.indexOfFirst { it.id == tool.id }.coerceAtLeast(0) + 0.18f,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
                    .onGloballyPositioned { botFlight?.chatBotPlaced(it, headerBotType, headerMood) }
                    .graphicsLayer { alpha = if (botFlight?.hidesChatBot(tool.id) == true) 0f else 1f },
            ) {
                BotAvatar(
                    type = headerBotType,
                    mood = headerMood,
                    size = 42.dp,
                    label = tool.title,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tool.id == "document") TextButton(onClick = { haptics.click(); importText.launch(arrayOf("text/*", "application/pdf")) }) { Text("Import file") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box {
                        MorphingBackdrop(
                            phase = writingTools.indexOfFirst { it.id == tool.id }.coerceAtLeast(0) + 0.14f,
                            modifier = Modifier.align(Alignment.TopEnd).size(100.dp).graphicsLayer { alpha = 0.24f },
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                        Column(Modifier.padding(16.dp)) {
                        Text("Your draft", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; error = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp, max = 192.dp),
                            placeholder = { Text(if (tool.id == "detector") "Paste writing you’d like Jade to review…" else "Type or paste your writing here…") },
                            shape = MaterialTheme.shapes.medium,
                            minLines = 5,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${input.length} / 8,000", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (tool.id == "document") Text("Text files only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        }
                    }
                }
            }
            if (tool.id == "tone") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose a tone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Clear", "Warm", "Confident", "Professional").forEach { tone ->
                                FilterChip(selected = selectedTone == tone, onClick = { haptics.tick(); selectedTone = tone }, label = { Text(tone) })
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
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(error.orEmpty(), Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            item {
                Button(
                    onClick = {
                        haptics.send()
                        scope.launch {
                            busy = true
                            result = ""
                            error = null
                            runCatching { onGenerate(input, selectedTone, targetLanguage) }
                                .onSuccess { result = it; haptics.success() }
                                .onFailure { error = it.message ?: "We couldn’t finish that. Please try again."; haptics.reject() }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (busy) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Text("  Working…")
                    }
                    else {
                        Icon(tool.icon, null, Modifier.size(19.dp))
                        Text("  ${tool.actionLabel}")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("A credit is used when Jade completes a request. Your original text stays here until you clear it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                AnimatedVisibility(result.isNotBlank()) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (tool.id == "detector") "Jade’s review" else "Your result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = {
                                    haptics.click()
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
                TextButton(onClick = { haptics.click(); input = ""; result = ""; error = null }) { Text("Clear draft") }
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
