package com.noxquill.rewordium.app.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.noxquill.rewordium.app.ui.JadeMascot
import kotlinx.coroutines.launch

data class JadeMessage(val fromJade: Boolean, val text: String)

@Composable
fun JadeScreen(onSend: suspend (String, List<JadeMessage>) -> String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<JadeMessage>() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messages.size, working) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            JadeMascot(size = 54.dp, mood = if (working) "thinking" else "calm")
            Column(Modifier.weight(1f)) {
                Text("Jade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Your writing companion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (messages.isNotEmpty()) TextButton(onClick = { messages.clear(); error = null }) { Text("New chat") }
        }
        if (messages.isEmpty()) {
            Column(
                Modifier.fillMaxSize().weight(1f).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                JadeMascot(size = 132.dp, mood = "thinking")
                Spacer(Modifier.height(16.dp))
                Text("What are you working on?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("Tell Jade what you want to say, and we’ll find the words together.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                listOf("Make this sound clearer", "Help me find the right tone", "Give me a fresh way to start").forEach { prompt ->
                    AssistChip(onClick = { draft = prompt }, label = { Text(prompt) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)) })
                    Spacer(Modifier.height(6.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(messages) { _, message ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromJade) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        if (message.fromJade) JadeMascot(size = 30.dp, animate = false)
                        Surface(
                            modifier = Modifier.padding(horizontal = 7.dp),
                            shape = MaterialTheme.shapes.large,
                            color = if (message.fromJade) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(message.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (message.fromJade) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Jade reply", message.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.ContentCopy, "Copy reply", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (working) item {
                    Row(Modifier.padding(start = 38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Jade is thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (error != null) item { Text(error.orEmpty(), Modifier.padding(start = 38.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; error = null },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Jade") },
                shape = MaterialTheme.shapes.extraLarge,
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    val prompt = draft.trim()
                    if (prompt.isNotBlank() && !working) {
                        val history = messages.toList()
                        messages.add(JadeMessage(fromJade = false, text = prompt))
                        draft = ""
                        error = null
                        working = true
                        scope.launch {
                            runCatching { onSend(prompt, history) }
                                .onSuccess { messages.add(JadeMessage(fromJade = true, text = it)) }
                                .onFailure { error = it.message ?: "Jade couldn’t reply just now. Please try again." }
                            working = false
                        }
                    }
                },
                enabled = draft.isNotBlank() && !working,
                modifier = Modifier.size(54.dp).clip(CircleShape),
            ) {
                Icon(Icons.Filled.Send, "Send to Jade", tint = if (draft.isNotBlank() && !working) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
