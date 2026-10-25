package com.noxquill.rewordium.app.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.PlatformSetupStatus
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.MorphBadge
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.util.KeyboardConstants

@Composable
fun SettingsScreen(
    account: AccountInfo?, status: PlatformSetupStatus,
    onBack: () -> Unit,
    onSignIn: () -> Unit, onSignOut: () -> Unit, onDelete: () -> Unit,
    onPro: () -> Unit, onKeyboard: () -> Unit, onAccessibility: () -> Unit,
    onKeyboardSection: (String) -> Unit,
    onEnableKeyboard: () -> Unit, onChooseKeyboard: () -> Unit,
    onAiSettings: () -> Unit, onLicenses: () -> Unit,
    onPersonas: () -> Unit,
    onPrivacy: () -> Unit, onTerms: () -> Unit,
    mascotType: String,
    onMascotSelected: (String) -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMascotPicker by remember { mutableStateOf(false) }
    var pendingMascot by rememberSaveable { mutableStateOf(mascotType) }
    val context = LocalContext.current
    val keyboardPrefs = remember { context.getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE) }
    var keyboardAiEnabled by rememberSaveable { mutableStateOf(keyboardPrefs.getBoolean("ai_rewrite_enabled", false)) }
    var hapticsEnabled by rememberSaveable { mutableStateOf(keyboardPrefs.getBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true)) }
    val haptics = LocalHaptics.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Settings", style = MaterialTheme.typography.titleLarge)
                    Text("Your Rewordium, your way", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { haptics.tick(); tab = 0 }, text = { Text("Rewordium") })
            Tab(selected = tab == 1, onClick = { haptics.tick(); tab = 1 }, text = { Text("Access & keyboard") })
        }
        if (tab == 0) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptics.click()
                            pendingMascot = mascotType
                            showMascotPicker = true
                        }.expressivePress(),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BotAvatar(type = mascotType, mood = BotMood.Idle, size = 52.dp, interactive = false, label = "Choose your Rewordium mascot")
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(account?.name?.takeIf(String::isNotBlank) ?: "Your account", style = MaterialTheme.typography.titleMedium)
                                Text(account?.email?.takeIf(String::isNotBlank) ?: "Choose a mascot · Sign in to sync credits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { haptics.click(); pendingMascot = mascotType; showMascotPicker = true }) { Text("Avatar") }
                                TextButton(onClick = { haptics.click(); if (account == null) onSignIn() else onSignOut() }) { Text(if (account == null) "Sign in" else "Sign out") }
                            }
                        }
                    }
                }
                item {
                    ElevatedCard(onClick = { haptics.click(); onPro() }, modifier = Modifier.fillMaxWidth().expressivePress(), shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BotAvatar(type = "star", mood = BotMood.Happy, size = 40.dp, still = true)
                            Column(Modifier.weight(1f)) {
                                Text(if (account?.isPro == true) "Rewordium Pro" else "Credits & Rewordium Pro", style = MaterialTheme.typography.titleMedium)
                                Text(if (account?.isPro == true) "${account.planType.replaceFirstChar(Char::uppercase)} plan · active" else "${account?.credits ?: 0} credits available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }
                item {
                    SettingsSection("Writing preferences") {
                        SettingsRow(Icons.Filled.Key, "AI preferences", "Provider, model, and your API key", onAiSettings)
                        SettingsDivider()
                        SettingsRow(Icons.Filled.AutoAwesome, "Writing personas", "Choose the voice used when text is rewritten", onPersonas)
                    }
                }
                item {
                    SettingsSection("About Rewordium") {
                        SettingsRow(Icons.Filled.Policy, "Privacy policy", "How account and writing data are handled", onPrivacy)
                        SettingsDivider()
                        SettingsRow(Icons.Filled.Policy, "Terms of service", "Terms for using Rewordium", onTerms)
                        SettingsDivider()
                        SettingsRow(Icons.Filled.OpenInNew, "Open-source licenses", "Libraries, credits, and notices", onLicenses)
                        SettingsDivider()
                        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
                        SettingsRow(Icons.Filled.AutoAwesome, "Rewordium for Android", "Version $version", {})
                    }
                }
                if (account != null) item {
                    SettingsSection("Account") {
                        SettingsRow(Icons.Filled.DeleteForever, "Delete account", "Permanently remove your account", { confirmDelete = true }, destructive = true)
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    SettingsSection("Accessibility") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Use Rewordium’s writing actions in other apps when you choose. Android shows permission details before you enable the service.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                StatusText(if (status.accessibilityEnabled) "Enabled" else "Not enabled", status.accessibilityEnabled)
                                Button(onClick = { haptics.click(); onAccessibility() }) { Text(if (status.accessibilityEnabled) "Review access" else "Open settings") }
                            }
                        }
                    }
                }
                item {
                    SettingsSection("Keyboard") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatusText(when { status.keyboardSelected -> "Enabled and selected"; status.keyboardEnabled -> "Enabled · choose as default"; else -> "Not enabled yet" }, status.keyboardSelected)
                            Text("Set it up once, then write in the apps you already use.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { haptics.click(); onEnableKeyboard() }) { Text(if (status.keyboardEnabled) "Keyboard settings" else "Enable keyboard") }
                                if (status.keyboardEnabled && !status.keyboardSelected) Button(onClick = { haptics.click(); onChooseKeyboard() }) { Text("Choose keyboard") }
                            }
                        }
                    }
                }
                item {
                    SettingsSection("ReBoard control center") {
                        KeyboardControlCard(onOpen = onKeyboard)
                        SettingsDivider()
                        SettingsRow(Icons.Filled.AutoAwesome, "Typing & suggestions", "Glide typing, corrections, candidates, and personal learning", { onKeyboardSection("typing") })
                        SettingsDivider()
                        SettingsRow(Icons.Filled.Edit, "Dictionary & language", "Private words, language tools, and spelling", { onKeyboardSection("dictionary") })
                        SettingsDivider()
                        SettingsRow(Icons.Filled.Bolt, "Gestures & shortcuts", "Swipe actions, cursor controls, and smartbar behavior", { onKeyboardSection("gestures") })
                        SettingsDivider()
                        SettingsRow(Icons.Filled.Keyboard, "Keyboard layout & feel", "Keys, themes, input feedback, and display choices", { onKeyboardSection("keyboard") })
                        SettingsDivider()
                        SettingsRow(Icons.Filled.Shield, "Private backup & restore", "Keep a local copy of your settings and learned words", { onKeyboardSection("other/backup") })
                    }
                }
                item {
                    SettingsSection("Writing in the keyboard") {
                        SettingsRow(Icons.Filled.AutoAwesome, "Writing personas", "Pick the voice Rewordium uses", onPersonas)
                        SettingsDivider()
                        SettingsSwitchRow("AI rewrite in keyboard", "Show writing tools while you type", keyboardAiEnabled) {
                            keyboardAiEnabled = it
                            keyboardPrefs.edit().putBoolean("ai_rewrite_enabled", it).apply()
                            haptics.toggle(it)
                        }
                    }
                }
                item {
                    SettingsSection("Feel") {
                        SettingsSwitchRow("Touch feedback", "Small haptic responses to taps and actions", hapticsEnabled) {
                            hapticsEnabled = it
                            keyboardPrefs.edit().putBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, it).apply()
                            haptics.toggle(it)
                        }
                    }
                }
                item { Text("Android controls accessibility permission and the active keyboard. You can change either at any time.", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete your account?") },
        text = { Text("Your Rewordium profile and account data will be permanently removed. This can’t be undone. You may need to sign in again before deletion completes.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; haptics.reject(); onDelete() }) { Text("Delete account", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep account") } },
    )

    if (showMascotPicker) Dialog(onDismissRequest = { showMascotPicker = false }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Choose your mascot", style = MaterialTheme.typography.headlineSmall)
                    Text("Every Rewordium bot has its own shape and expression.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(320.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(mascotTypes, key = { it }) { type ->
                        val selected = type == pendingMascot
                        ElevatedCard(
                            onClick = { haptics.tick(); pendingMascot = type },
                            modifier = Modifier.fillMaxWidth().expressivePress(0.9f),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.elevatedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                BotAvatar(type = type, mood = if (selected) BotMood.Happy else BotMood.Idle, size = 48.dp, interactive = false, still = true, label = "$type mascot")
                                Text(type.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        haptics.success()
                        onMascotSelected(pendingMascot)
                        showMascotPicker = false
                    },
                    modifier = Modifier.fillMaxWidth().expressivePress(),
                ) { Text("Use ${pendingMascot.replaceFirstChar(Char::uppercase)} mascot") }
                TextButton(onClick = { showMascotPicker = false }, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun KeyboardControlCard(onOpen: () -> Unit) {
    val haptics = LocalHaptics.current
    Surface(
        onClick = { haptics.click(); onOpen() },
        modifier = Modifier.fillMaxWidth().padding(10.dp).expressivePress(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MorphBadge(
                phase = 2.22f,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                modifier = Modifier.size(52.dp),
            ) {
                BotAvatar(type = "droid", mood = BotMood.Happy, size = 46.dp, still = true, label = "ReBoard settings", modifier = Modifier.align(Alignment.Center))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("All ReBoard settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Every keyboard customization in one place", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Icon(Icons.Filled.OpenInNew, "Open ReBoard settings", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().clickable { haptics.click(); onClick() }.expressivePress().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MorphBadge(
            phase = (title.length % 5) + 0.22f,
            color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(icon, null, Modifier.align(Alignment.Center).size(20.dp), tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val mascotTypes = listOf("clover", "flower", "triangle", "square", "blob", "ghost", "circle", "drop", "star", "droid", "mech", "alien", "hexagon", "cat", "cloud", "pill", "pebble", "puddle")

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MorphBadge(
            phase = 3.22f,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Filled.Bolt, null, Modifier.align(Alignment.Center).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusText(text: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(if (active) Icons.Filled.CheckCircle else Icons.Filled.Shield, null, Modifier.size(17.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
