package com.noxquill.rewordium.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(title: String, onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    val haptics = LocalHaptics.current
    val privacy = title.contains("privacy", ignoreCase = true)
    val sections = if (privacy) listOf(
        "Your writing" to "Text you submit is sent to the AI provider configured for Rewordium so it can complete your request. Avoid sharing sensitive information you do not want processed by that provider.",
        "Your account" to "If you sign in, Rewordium stores your profile, credit balance, and subscription state in Firebase so they can follow your account across devices.",
        "Keyboard and accessibility" to "The Rewordium keyboard processes typed text to provide its features. The accessibility service runs only after you enable it in Android settings and supports the writing actions described there.",
        "Controls" to "You can sign out or request account deletion from Settings. Android’s system settings control keyboard and accessibility permissions.",
    ) else listOf(
        "Using Rewordium" to "Rewordium provides writing assistance, an optional keyboard, and accessibility actions. Review generated text before using or sharing it.",
        "AI generated text" to "AI suggestions can be inaccurate or incomplete. They are writing aids and are not professional, legal, medical, or financial advice.",
        "Subscriptions" to "Paid plans are handled through Google Play. Renewal, cancellation, and refund options follow the terms shown in Google Play at purchase.",
        "Open-source software" to "Rewordium includes open-source software. License names and project links are listed in Open-source licenses.",
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            androidx.compose.material3.IconButton(onClick = { haptics.click(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BotAvatar(type = if (privacy) "ghost" else "circle", mood = BotMood.Idle, size = 48.dp, still = true)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (privacy) "Your choices matter." else "A few things to know.", style = MaterialTheme.typography.titleLarge)
                            Text("This in-app overview is a quick guide. Read the full current document on our website.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item {
                androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        sections.forEachIndexed { index, (heading, body) ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(heading, style = MaterialTheme.typography.titleSmall)
                                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { onOpenLink(if (privacy) "https://rewordium.tech/privacy" else "https://rewordium.tech/terms") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Read full ${if (privacy) "privacy policy" else "terms"}")
                    Icon(Icons.Filled.OpenInNew, null, Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}

data class LicenseEntry(val name: String, val license: String, val url: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val keyboardNotices = remember {
        runCatching { context.assets.open("open_source_licenses.md").bufferedReader().use { it.readText() } }
            .getOrElse { "Keyboard license notices are included with the Rewordium keyboard source." }
    }
    val entries = listOf(
        LicenseEntry("ReBoard / FlorisBoard", "Apache License 2.0 · keyboard foundation", "https://github.com/florisboard/florisboard"),
        LicenseEntry("AOSP LatinIME", "Apache License 2.0 · upstream notices included", "https://android.googlesource.com/platform/packages/inputmethods/LatinIME"),
        LicenseEntry("Jetpack Compose and Material 3", "Apache License 2.0", "https://developer.android.com/jetpack/compose"),
        LicenseEntry("Material 3 Expressive patterns", "Apache License 2.0 · meticha", "https://github.com/meticha/material-3-expressive-catalog"),
        LicenseEntry("Bot avatars · Libraries.dev", "MIT License · Copyright Jakub Antalik", "https://libraries.dev"),
        LicenseEntry("Firebase Android SDK", "Apache License 2.0", "https://github.com/firebase/firebase-android-sdk"),
        LicenseEntry("Retrofit and OkHttp", "Apache License 2.0", "https://github.com/square/okhttp"),
        LicenseEntry("Google Play Billing and ML Kit", "Google developer terms", "https://developer.android.com/google/play/billing"),
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Open-source licenses") }, navigationIcon = {
            androidx.compose.material3.IconButton(onClick = { haptics.click(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BotAvatar(type = "clover", mood = BotMood.Happy, size = 46.dp, still = true)
                        Text("Rewordium is built with open-source projects. We’re grateful to the people who make them available.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            Row(Modifier.fillMaxWidth().clickable { onOpenLink(entry.url) }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall)
                                    Text(entry.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Third-party license notices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(keyboardNotices, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
