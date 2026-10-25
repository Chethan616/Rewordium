package com.noxquill.rewordium.app.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.JadeMascot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("advanced_ai_enabled", false)) }
    var provider by remember { mutableStateOf(prefs.getString("ai_provider", "groq") ?: "groq") }
    var apiKey by remember { mutableStateOf(prefs.getString("ai_api_key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("ai_model", "openai/gpt-oss-120b") ?: "openai/gpt-oss-120b") }
    var endpoint by remember { mutableStateOf(prefs.getString("ai_custom_endpoint", "") ?: "") }
    var menuOpen by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val providers = listOf("groq", "openai", "gemini", "claude", "custom")
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("AI preferences") }, navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f))) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        JadeMascot(size = 52.dp, animate = false)
                        Column(Modifier.weight(1f)) {
                            Text("Choose how Rewordium thinks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("These settings are shared with Jade and the keyboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use my own provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Turn off to use the configured Rewordium provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it; saved = false })
                    }
                }
            }
            if (enabled) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Provider", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { menuOpen = true }) {
                            Text(provider.replaceFirstChar(Char::uppercase))
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                providers.forEach { option -> DropdownMenuItem(text = { Text(option.replaceFirstChar(Char::uppercase)) }, onClick = { provider = option; menuOpen = false; saved = false }) }
                            }
                        }
                    }
                }
                item { OutlinedTextField(value = model, onValueChange = { model = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true, shape = MaterialTheme.shapes.large) }
                item { OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("API key") }, leadingIcon = { Icon(Icons.Filled.Key, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = MaterialTheme.shapes.large) }
                if (provider == "custom") item { OutlinedTextField(value = endpoint, onValueChange = { endpoint = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("API endpoint") }, singleLine = true, shape = MaterialTheme.shapes.large) }
                item { Text("Your API key stays on this device and is sent to the provider you choose. Only use a key you trust this device with.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Button(onClick = {
                    prefs.edit()
                        .putBoolean("advanced_ai_enabled", enabled)
                        .putString("ai_provider", provider)
                        .putString("ai_api_key", apiKey)
                        .putString("ai_model", model)
                        .putInt("ai_max_tokens", 8192)
                        .putString("ai_custom_endpoint", endpoint)
                        .apply()
                    saved = true
                }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Saved" else "Save preferences") }
            }
        }
    }
}
