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
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import com.noxquill.rewordium.PlatformSetupStatus
import com.noxquill.rewordium.app.ui.JadeMascot
import com.noxquill.rewordium.data.AccountInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    account: AccountInfo?, status: PlatformSetupStatus,
    onSignIn: () -> Unit, onSignOut: () -> Unit, onDelete: () -> Unit,
    onPro: () -> Unit, onKeyboard: () -> Unit, onAccessibility: () -> Unit,
    onEnableKeyboard: () -> Unit, onChooseKeyboard: () -> Unit,
    onAiSettings: () -> Unit, onLicenses: () -> Unit,
    onPersonas: () -> Unit,
    onPrivacy: () -> Unit, onTerms: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboardPrefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    var keyboardAiEnabled by rememberSaveable { mutableStateOf(keyboardPrefs.getBoolean("ai_rewrite_enabled", false)) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = {
            Column {
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Your Rewordium, your way", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
        PrimaryTabRow(selectedTabIndex = tab) {
            androidx.compose.material3.Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Rewordium") })
            androidx.compose.material3.Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Access & keyboard") })
        }
        if (tab == 0) {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            JadeMascot(size = 48.dp, animate = false)
                            Column(Modifier.weight(1f)) {
                                Text(account?.name?.takeIf(String::isNotBlank) ?: "Your account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(account?.email?.takeIf(String::isNotBlank) ?: "Sign in to sync your credits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (account == null) TextButton(onClick = onSignIn) { Text("Sign in") }
                            else TextButton(onClick = onSignOut) { Text("Sign out") }
                        }
                    }
                }
                item {
                    ElevatedCard(onClick = onPro, shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(if (account?.isPro == true) Icons.Filled.Stars else Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (account?.isPro == true) "Rewordium Pro" else "Credits & Rewordium Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (account?.isPro == true) "${account.planType.replaceFirstChar(Char::uppercase)} plan · active" else "${account?.credits ?: 0} credits available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }
                item { SectionLabel("Personalize") }
                item { SettingsAction(Icons.Filled.Key, "AI preferences", "Provider, model, and your API key", onAiSettings) }
                item { SectionLabel("About Rewordium") }
                item { SettingsAction(Icons.Filled.Policy, "Privacy policy", "How account and writing data are handled", onPrivacy) }
                item { SettingsAction(Icons.Filled.Policy, "Terms of service", "Terms for using Rewordium", onTerms) }
                item { SettingsAction(Icons.Filled.OpenInNew, "Open-source licenses", "Credits and notices for the libraries we use", onLicenses) }
                item {
                    val context = LocalContext.current
                    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
                    SettingsAction(Icons.Filled.AutoAwesome, "Rewordium for Android", "Version $version", {})
                }
                if (account != null) item {
                    SettingsAction(Icons.Filled.DeleteForever, "Delete account", "Permanently remove your Rewordium account", { confirmDelete = true }, destructive = true)
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                item {
                    ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Accessibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Use Rewordium’s writing actions in other apps when you choose. Android shows the permission details before you enable the service.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                StatusText(if (status.accessibilityEnabled) "Enabled" else "Not enabled", status.accessibilityEnabled)
                                Button(onClick = onAccessibility) { Text(if (status.accessibilityEnabled) "Review access" else "Open settings") }
                            }
                        }
                    }
                }
                item {
                    ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Icon(Icons.Filled.Keyboard, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Rewordium keyboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            StatusText(when { status.keyboardSelected -> "Enabled and selected"; status.keyboardEnabled -> "Enabled · choose as default"; else -> "Not enabled yet" }, status.keyboardSelected)
                            Text("Set up Rewordium once, then use your keyboard and writing tools in the apps you already use.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onEnableKeyboard) { Text(if (status.keyboardEnabled) "Keyboard settings" else "Enable keyboard") }
                                if (status.keyboardEnabled && !status.keyboardSelected) Button(onClick = onChooseKeyboard) { Text("Choose keyboard") }
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("AI rewrite in keyboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Show writing tools in your keyboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = keyboardAiEnabled, onCheckedChange = {
                                    keyboardAiEnabled = it
                                    keyboardPrefs.edit().putBoolean("ai_rewrite_enabled", it).apply()
                                })
                            }
                            HorizontalDivider()
                            SettingsAction(Icons.Filled.Edit, "Keyboard preferences", "Typing, themes, languages, gestures, stickers, and more", onKeyboard)
                            SettingsAction(Icons.Filled.AutoAwesome, "Writing personas", "Choose the voice Rewordium uses when it rewrites text", onPersonas)
                        }
                    }
                }
                item { Text("Android controls these permissions and the active keyboard. You can change them at any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete your account?") },
        text = { Text("Your Rewordium profile and account data will be permanently removed. This can’t be undone. You may need to sign in again before deletion completes.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete account", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep account") } },
    )
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp, top = 4.dp))
}

@Composable
private fun SettingsAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, destructive: Boolean = false) {
    ElevatedCard(onClick = onClick, shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StatusText(text: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(if (active) Icons.Filled.CheckCircle else Icons.Filled.Shield, null, Modifier.size(17.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
