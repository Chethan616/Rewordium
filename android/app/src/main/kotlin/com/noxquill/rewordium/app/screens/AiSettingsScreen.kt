package com.noxquill.rewordium.app.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.ExpressiveSwitch
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val prefs = remember { context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("advanced_ai_enabled", false)) }
    var provider by remember { mutableStateOf(prefs.getString("ai_provider", "groq") ?: "groq") }
    var apiKey by remember { mutableStateOf(prefs.getString("ai_api_key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("ai_model", "openai/gpt-oss-120b") ?: "openai/gpt-oss-120b") }
    var endpoint by remember { mutableStateOf(prefs.getString("ai_custom_endpoint", "") ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val providers = listOf("groq" to "Groq", "openai" to "OpenAI", "gemini" to "Gemini", "claude" to "Claude", "custom" to "Custom")

    ExpressiveScaffold(title = "AI provider", subtitle = "Shared by Jade and the keyboard", onBack = onBack, heading = com.noxquill.rewordium.app.ui.Heading.Airy) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().enterStagger(0)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BotAvatar(type = "droid", mood = if (enabled) BotMood.Working else BotMood.Idle, size = 52.dp, label = null)
                        Column(Modifier.weight(1f)) {
                            Text("Use my own API key", style = MaterialTheme.typography.titleMedium)
                            Text("Off uses Rewordium’s AI with your credits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExpressiveSwitch(enabled) { enabled = it; saved = false; haptics.toggle(it) }
                    }
                }
            }
            if (enabled) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Provider", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(providers, key = { it.first }) { (id, label) ->
                                FilterChip(selected = provider == id, onClick = { haptics.tick(); provider = id; saved = false }, label = { Text(label) })
                            }
                        }
                        if (provider == "claude" || provider == "gemini") Text(
                            "Jade can chat with this provider, but changing settings for you needs Rewordium’s AI or an OpenAI-compatible one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                item { OutlinedTextField(value = model, onValueChange = { model = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true, shape = MaterialTheme.shapes.large) }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        leadingIcon = { Icon(Icons.Filled.Key, null) },
                        trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showKey) "Hide key" else "Show key") } },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                }
                if (provider == "custom") item { OutlinedTextField(value = endpoint, onValueChange = { endpoint = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Chat completions URL") }, singleLine = true, shape = MaterialTheme.shapes.large) }
                item { Text("Your API key stays on this phone and is only sent to the provider you choose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Button(
                    onClick = {
                        prefs.edit()
                            .putBoolean("advanced_ai_enabled", enabled)
                            .putString("ai_provider", provider)
                            .putString("ai_api_key", apiKey.trim())
                            .putString("ai_model", model.trim())
                            .putInt("ai_max_tokens", 8192)
                            .putString("ai_custom_endpoint", endpoint.trim())
                            .apply()
                        saved = true
                        haptics.success()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text(if (saved) "Saved ✓" else "Save") }
            }
        }
    }
}
