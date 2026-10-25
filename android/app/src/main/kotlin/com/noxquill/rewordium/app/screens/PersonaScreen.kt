package com.noxquill.rewordium.app.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private data class KeyboardPersona(
    val name: String,
    val description: String,
    val builtIn: Boolean = false,
)

private val defaultPersonas = listOf(
    KeyboardPersona("Happy", "Cheerful, optimistic, and upbeat.", builtIn = true),
    KeyboardPersona("Sad", "Thoughtful, reflective, and gentle.", builtIn = true),
    KeyboardPersona("Humor", "Witty and playful while keeping the point clear.", builtIn = true),
    KeyboardPersona("Formal", "Polished and professional for work.", builtIn = true),
    KeyboardPersona("Casual", "Relaxed and conversational, like a message to a friend.", builtIn = true),
)

private fun readCustomPersonas(prefs: android.content.SharedPreferences): List<KeyboardPersona> = runCatching {
    val json = prefs.getString("custom_personas", null) ?: return@runCatching emptyList()
    val array = JSONArray(json)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("name").trim()
        val description = item.optString("description").trim()
        if (name.isBlank() || description.isBlank()) null else KeyboardPersona(name, description)
    }
}.getOrDefault(emptyList())

private fun saveCustomPersonas(prefs: android.content.SharedPreferences, personas: List<KeyboardPersona>) {
    val json = JSONArray().apply {
        personas.filterNot { it.builtIn }.forEach { persona ->
            put(JSONObject().put("name", persona.name).put("description", persona.description))
        }
    }
    prefs.edit().putString("custom_personas", json.toString()).apply()
}

private fun activatePersona(context: Context, persona: KeyboardPersona) {
    context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("active_persona", persona.name)
        .putString("active_persona_description", persona.description)
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    var personas by remember { mutableStateOf(defaultPersonas + readCustomPersonas(prefs)) }
    var active by rememberSaveable { mutableStateOf(prefs.getString("active_persona", "Happy") ?: "Happy") }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    LaunchedEffect(active, personas) {
        val selected = personas.firstOrNull { it.name == active } ?: personas.first()
        if (selected.name != active) active = selected.name
        activatePersona(context, selected)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Writing personas") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { name = ""; description = ""; showAdd = true }) { Icon(Icons.Filled.Add, "Add a persona") } },
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                ElevatedCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Give every rewrite a voice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Your choice is shared with the Rewordium keyboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            items(personas, key = { it.name }) { persona ->
                val selected = persona.name == active
                ElevatedCard(
                    onClick = {
                        active = persona.name
                        activatePersona(context, persona)
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (selected) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
                            contentDescription = if (selected) "Selected" else null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(persona.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(persona.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!persona.builtIn) {
                            IconButton(onClick = {
                                personas = personas.filterNot { it.name == persona.name }
                                saveCustomPersonas(prefs, personas)
                                if (active == persona.name) {
                                    active = "Happy"
                                    activatePersona(context, defaultPersonas.first())
                                }
                            }) { Icon(Icons.Filled.DeleteOutline, "Delete ${persona.name}") }
                        }
                    }
                }
            }
            item {
                Button(onClick = { name = ""; description = ""; showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("  Create a persona")
                }
            }
        }
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("Create a persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(36) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it.take(240) }, label = { Text("Writing style") }, minLines = 2, maxLines = 4)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleanName = name.trim()
                val cleanDescription = description.trim()
                if (cleanName.isNotBlank() && cleanDescription.isNotBlank() && personas.none { it.name.equals(cleanName, true) }) {
                    val persona = KeyboardPersona(cleanName, cleanDescription)
                    personas = personas + persona
                    saveCustomPersonas(prefs, personas)
                    active = persona.name
                    activatePersona(context, persona)
                    showAdd = false
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
    )
}
