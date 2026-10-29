package com.noxquill.rewordium.app.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.KeyboardPersona
import com.noxquill.rewordium.data.KeyboardPersonas

private val personaBots = listOf("flower", "ghost", "cat", "star", "droid", "clover", "drop", "alien", "blob", "hexagon")

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    var personas by remember { mutableStateOf(KeyboardPersonas.all(context)) }
    var active by rememberSaveable { mutableStateOf(KeyboardPersonas.active(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    fun choose(persona: KeyboardPersona) {
        haptics.tick()
        active = persona.name
        KeyboardPersonas.activate(context, persona)
    }

    ExpressiveScaffold(
        title = "Writing personas",
        subtitle = "The voice of the keyboard’s AI rewrite",
        onBack = onBack,
        heading = com.noxquill.rewordium.app.ui.Heading.Whisper,
        actions = { IconButton(onClick = { haptics.click(); name = ""; description = ""; showAdd = true }) { Icon(Icons.Filled.Add, "Create a persona") } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(personas, key = { _, persona -> persona.name }) { index, persona ->
                val selected = persona.name == active
                Surface(
                    onClick = { choose(persona) },
                    shape = segmentShape(index, personas.size),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().enterStagger(index).animateItem().expressivePress(0.98f),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BotAvatar(type = personaBots[index % personaBots.size], mood = if (selected) BotMood.Happy else BotMood.Idle, size = 44.dp, still = !selected, label = "${persona.name} persona")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(persona.name, style = MaterialTheme.typography.titleSmall)
                            // The row's own ink, softened — readable on the coral selected row and the plain ones alike.
                            Text(persona.description, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.78f))
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, "Active", tint = LocalContentColor.current, modifier = Modifier.padding(end = 8.dp))
                        if (!persona.builtIn) IconButton(onClick = {
                            haptics.reject()
                            personas = personas.filterNot { it.name == persona.name }
                            KeyboardPersonas.saveCustom(context, personas)
                            if (active == persona.name) choose(KeyboardPersonas.defaults.first())
                        }) { Icon(Icons.Filled.DeleteOutline, "Delete ${persona.name}") }
                    }
                }
            }
            item(key = "add") {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { haptics.click(); name = ""; description = ""; showAdd = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text("  Create a persona")
                }
            }
        }
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        icon = { BotAvatar(type = "blob", mood = BotMood.Happy, size = 56.dp, label = null) },
        title = { Text("Create a persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(36) }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                OutlinedTextField(value = description, onValueChange = { description = it.take(240) }, label = { Text("Writing style") }, minLines = 2, maxLines = 4, shape = MaterialTheme.shapes.medium)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleanName = name.trim()
                val cleanDescription = description.trim()
                if (cleanName.isNotBlank() && cleanDescription.isNotBlank() && personas.none { it.name.equals(cleanName, true) }) {
                    val persona = KeyboardPersona(cleanName, cleanDescription)
                    personas = personas + persona
                    KeyboardPersonas.saveCustom(context, personas)
                    choose(persona)
                    showAdd = false
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
    )
}
