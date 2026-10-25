package com.noxquill.rewordium.app.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import kotlinx.coroutines.launch

data class JadeMessage(val fromJade: Boolean, val text: String)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JadeScreen(onSend: suspend (String, List<JadeMessage>) -> String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val messages = remember { mutableStateListOf<JadeMessage>() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messages.size, working) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = { haptics.click(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotAvatar(type = "cloud", mood = if (working) BotMood.Thinking else BotMood.Idle, size = 42.dp, label = "Jade")
                    Column {
                        Text("Jade", style = MaterialTheme.typography.titleLarge)
                        Text("Your writing companion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            actions = {
                if (messages.isNotEmpty()) TextButton(onClick = { haptics.click(); messages.clear(); error = null }) { Text("New chat") }
            },
        )

        if (messages.isEmpty()) {
            Column(
                Modifier.fillMaxSize().weight(1f).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BotAvatar(type = "cloud", mood = BotMood.Thinking, size = 132.dp, label = "Jade")
                Spacer(Modifier.height(18.dp))
                Text("What are you working on?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(7.dp))
                Text("Tell Jade what you want to say. You can refine the words together.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                listOf("Make this clearer", "Help me find a better tone", "Give me a fresh opening").forEach { prompt ->
                    AssistChip(onClick = { haptics.tick(); draft = prompt }, label = { Text(prompt) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)) })
                    Spacer(Modifier.height(6.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(messages) { _, message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromJade) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.Bottom) {
                        if (message.fromJade) BotAvatar(type = "cloud", mood = BotMood.Happy, size = 32.dp, still = true, label = "Jade")
                        Surface(
                            modifier = Modifier.padding(horizontal = 8.dp).weight(1f, fill = false),
                            shape = MaterialTheme.shapes.large,
                            color = if (message.fromJade) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(message.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (message.fromJade) {
                            IconButton(onClick = {
                                haptics.click()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Jade reply", message.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Filled.ContentCopy, "Copy reply", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (working) item {
                    Row(Modifier.padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LoadingIndicator(Modifier.size(32.dp))
                        Text("Jade is thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (error != null) item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(error.orEmpty(), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; error = null },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Jade") },
                    shape = MaterialTheme.shapes.large,
                    maxLines = 4,
                )
                FilledIconButton(
                    onClick = {
                        val prompt = draft.trim()
                        if (prompt.isNotBlank() && !working) {
                            haptics.send()
                            val history = messages.toList()
                            messages.add(JadeMessage(fromJade = false, text = prompt))
                            draft = ""
                            error = null
                            working = true
                            scope.launch {
                                runCatching { onSend(prompt, history) }
                                    .onSuccess { messages.add(JadeMessage(fromJade = true, text = it)); haptics.success() }
                                    .onFailure { error = it.message ?: "Jade couldn’t reply just now. Please try again."; haptics.reject() }
                                working = false
                            }
                        }
                    },
                    enabled = draft.isNotBlank() && !working,
                    modifier = Modifier.size(48.dp),
                ) { Icon(Icons.Filled.Send, "Send to Jade") }
            }
        }
    }
}
