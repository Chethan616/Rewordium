package com.noxquill.rewordium.app.screens

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.SegmentedSection
import com.noxquill.rewordium.app.ui.SettingRow
import com.noxquill.rewordium.app.ui.SettingSwitchRow
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.data.backup.RewordiumBackup
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val LAST_BACKUP_KEY = "last_backup_at"

private fun Context.appPrefs() = getSharedPreferences("rewordium_app", Context.MODE_PRIVATE)

/**
 * Settings' "Backup & restore": one .zip on the phone with every Jade chat,
 * the app's settings and the whole keyboard, and a way to bring it all back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupSection(modifier: Modifier = Modifier, onRestored: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var lastBackup by remember { mutableLongStateOf(context.appPrefs().getLong(LAST_BACKUP_KEY, 0L)) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(RewordiumBackup.Options()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<RewordiumBackup.Opened?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(RewordiumBackup.MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Backing up…"
            RewordiumBackup.create(context, uri, options)
                .onSuccess { summary ->
                    haptics.success()
                    lastBackup = summary.createdAt
                    context.appPrefs().edit().putLong(LAST_BACKUP_KEY, summary.createdAt).apply()
                    toast("Backed up ${plural(summary.jadeChats, "Jade chat")}${if (summary.keyboardSettings) ", your keyboard" else ""} and settings.")
                }
                .onFailure { haptics.reject(); toast("Couldn’t back up: ${it.message}") }
            busy = null
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Reading backup…"
            RewordiumBackup.open(context, uri)
                .onSuccess { opened = it }
                .onFailure { haptics.reject(); toast(it.message ?: "Couldn’t read that file.") }
            busy = null
        }
    }

    SegmentedSection(
        title = "Backup & restore",
        modifier = modifier,
        rows = listOf(
            { shape ->
                SettingRow(
                    Icons.Filled.Backup,
                    "Back up now",
                    if (lastBackup > 0L) "Last backup ${DateUtils.getRelativeTimeSpanString(lastBackup, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
                    else "Chats, settings and keyboard in one file on your phone",
                    shape,
                    Tone.Primary,
                ) { showOptions = true }
            },
            { shape ->
                SettingRow(Icons.Filled.SettingsBackupRestore, "Restore a backup", "Bring back chats and settings from a backup file", shape, Tone.Secondary) {
                    pick.launch(arrayOf(RewordiumBackup.MIME_TYPE, "application/x-zip-compressed", "application/octet-stream"))
                }
            },
        ),
    )

    if (showOptions) BackupOptionsSheet(
        options = options,
        onChange = { options = it },
        onDismiss = { showOptions = false },
        onSave = {
            haptics.click()
            showOptions = false
            save.launch(RewordiumBackup.suggestedFileName())
        },
    )

    opened?.let { backup ->
        RestoreDialog(
            summary = backup.summary,
            onDismiss = { backup.discard(); opened = null },
            onRestore = {
                opened = null
                scope.launch {
                    busy = "Restoring…"
                    RewordiumBackup.restore(context, backup)
                        .onSuccess { result ->
                            haptics.success()
                            onRestored()
                            toast(
                                buildString {
                                    append("Restored ${plural(result.jadeChats, "Jade chat")}")
                                    if (result.appSettings) append(", your settings")
                                    if (result.keyboard) append(" and your keyboard")
                                    append(".")
                                    result.keyboardError?.let { append(" The keyboard part couldn’t be restored: $it") }
                                },
                            )
                        }
                        .onFailure { haptics.reject(); toast("Couldn’t restore: ${it.message}") }
                    busy = null
                }
            },
        )
    }

    busy?.let { BusyDialog(it) }
}

private fun plural(count: Int, noun: String) = "$count $noun${if (count == 1) "" else "s"}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupOptionsSheet(
    options: RewordiumBackup.Options,
    onChange: (RewordiumBackup.Options) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                ToneIcon(Icons.Filled.Backup, Tone.Primary, shape = MaterialShapes.Cookie9Sided, size = 52.dp, spin = true)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Back up", style = Heading.Narrow.style(MaterialTheme.typography.headlineMedium))
                    Text("Saved as one file on your phone, wherever you choose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val count = 4
            SettingSwitchRow(Icons.AutoMirrored.Filled.Chat, "Jade chats", "Every conversation saved on this phone", options.jadeChats, segmentShape(0, count), Tone.Primary) {
                onChange(options.copy(jadeChats = it))
            }
            SettingSwitchRow(Icons.Filled.Palette, "App settings", "Look, avatar, personas and AI provider choice", options.appSettings, segmentShape(1, count), Tone.Tertiary) {
                onChange(options.copy(appSettings = it))
            }
            SettingSwitchRow(Icons.Filled.Keyboard, "Keyboard", "Every setting, languages, themes and learned words", options.keyboard, segmentShape(2, count), Tone.Secondary) {
                onChange(options.copy(keyboard = it, clipboard = it && options.clipboard))
            }
            SettingSwitchRow(
                Icons.Filled.ContentPaste,
                "Clipboard history",
                "Text you copied — it can include passwords or private messages",
                options.clipboard,
                segmentShape(3, count),
                Tone.Neutral,
            ) { onChange(options.copy(clipboard = it, keyboard = options.keyboard || it)) }
            Text(
                "Your AI API key, sign-in, credits and Pro status are never put in a backup — they stay with your account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
            Button(
                onClick = onSave,
                enabled = options.jadeChats || options.appSettings || options.keyboard,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save backup file", style = MaterialTheme.typography.titleMedium) }
            Row(Modifier.height(16.dp)) {}
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestoreDialog(summary: RewordiumBackup.Summary, onDismiss: () -> Unit, onRestore: () -> Unit) {
    val made = remember(summary.createdAt) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(summary.createdAt)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ToneIcon(Icons.Filled.SettingsBackupRestore, Tone.Secondary, shape = MaterialShapes.Clover4Leaf, size = 56.dp, spin = true) },
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Made $made${if (summary.appVersion.isNotBlank()) " · Rewordium ${summary.appVersion}" else ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (summary.jadeChats > 0) RestoreLine(Icons.AutoMirrored.Filled.Chat, Tone.Primary, plural(summary.jadeChats, "Jade chat"))
                if (summary.appSettings) RestoreLine(Icons.Filled.Palette, Tone.Tertiary, "App settings and personas")
                if (summary.keyboardSettings) {
                    val extras = listOfNotNull(
                        summary.keyboardThemes.takeIf { it > 0 }?.let { plural(it, "theme") },
                        summary.keyboardLayouts.takeIf { it > 0 }?.let { plural(it, "layout") },
                        "learned words".takeIf { summary.learnedWords },
                    )
                    RestoreLine(Icons.Filled.Keyboard, Tone.Secondary, "Keyboard settings" + if (extras.isEmpty()) "" else ", " + extras.joinToString(", "))
                }
                if (summary.clipboardItems > 0) RestoreLine(Icons.Filled.ContentPaste, Tone.Neutral, plural(summary.clipboardItems, "clipboard item"))
                Text(
                    "Chats are added to the ones already here. App and keyboard settings are replaced with the backup’s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onRestore, shapes = ButtonDefaults.shapes()) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestoreLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Tone, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToneIcon(icon, tone, size = 32.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BusyDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { LoadingIndicator(Modifier.size(56.dp)) },
        title = { Text(text) },
        text = { Text("Keep Rewordium open for a moment.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {},
    )
}
