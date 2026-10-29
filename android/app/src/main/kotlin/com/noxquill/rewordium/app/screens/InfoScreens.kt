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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.display
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics

const val PRIVACY_URL = com.noxquill.rewordium.data.RewordiumLinks.PRIVACY_POLICY
const val TERMS_URL = com.noxquill.rewordium.data.RewordiumLinks.TERMS

private val privacySections = listOf(
    "Your writing" to "When you ask Rewordium for help — in Jade, the keyboard or the accessibility assistant — the text you choose is sent to the AI provider that writes the answer (Rewordium’s default provider, or the one you set up in AI preferences). Rewordium doesn’t keep that text after your request is done. Avoid sharing sensitive information you don’t want processed.",
    "Jade chats" to "Your Jade conversations are saved only on this phone so you can come back to them. You can delete one chat, or all of them, at any time.",
    "Settings Jade changes" to "When you ask Jade to change a setting, she reads and updates the keyboard and app settings on this phone. Those settings stay on your device.",
    "Reports" to "If you report a Jade reply, that reply and the message before it are sent to the Rewordium team so we can review it and make Jade safer.",
    "Your account" to "When you sign in, we store your name, email, credit balance and subscription status with Firebase so they follow your account across devices. We also record basic, anonymous usage events to keep the app working well.",
    "Keyboard" to "The keyboard works on your device. Words it learns stay on your phone. Its AI tools only send the text you choose when you use them.",
    "Accessibility assistant" to "Runs only after you turn it on in Android settings. It notices text fields to show the floating assistant, and reads the field (and, for smart replies, the visible conversation) only when you use it. It isn’t used for ads.",
    "Your choices" to "Sign out or permanently delete your account any time in Settings → Account. Deleting removes your profile and plan records and clears your Jade chats from this phone.",
    "Questions & data requests" to "Email ${com.noxquill.rewordium.data.RewordiumLinks.SUPPORT_EMAIL} to ask a question, get a copy of your data, or request deletion. You can also request deletion on our website if you can’t sign in.",
)

private val termsSections = listOf(
    "Using Rewordium" to "Rewordium offers writing help, the Rewordium keyboard, an accessibility writing assistant and Jade, an AI companion. You need an account to use AI features.",
    "AI-generated content" to "Jade and the writing tools use AI. Answers can be wrong, incomplete or unexpected, and they aren’t professional, legal, medical or financial advice. Review text before you use or share it, and use Report on any reply that seems harmful.",
    "Fair use" to "Don’t use Rewordium to create content that’s illegal, hateful, harassing, sexually explicit involving minors, or meant to deceive or harm others, and don’t try to misuse or overload the service.",
    "Credits & subscriptions" to "Free accounts get a limited number of AI credits. Rewordium Pro is billed through Google Play, renews automatically until cancelled, and can be cancelled any time in Google Play. Refunds follow Google Play’s policies.",
    "Your account" to "Keep your sign-in details safe. You can delete your account in the app at any time.",
    "Open-source software" to "Rewordium includes open-source software. Licenses and project links are listed under Open-source licenses.",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoScreen(privacy: Boolean, onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    val haptics = LocalHaptics.current
    val sections = if (privacy) privacySections else termsSections
    ExpressiveScaffold(
        title = if (privacy) "Privacy policy" else "Terms of service",
        subtitle = "The short version",
        onBack = onBack,
        heading = Heading.Serif,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(key = "hero") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).enterStagger(0),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BotAvatar(type = if (privacy) "ghost" else "circle", mood = BotMood.Happy, size = 60.dp, label = null)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (privacy) "Your words are yours." else "A few things to know.", style = Heading.SerifItalic.display(30.sp))
                            Text("This is a plain-language summary. The full document on our website is the one that applies.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            itemsIndexed(sections, key = { _, section -> section.first }) { index, (heading, body) ->
                Surface(
                    shape = segmentShape(index, sections.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().enterStagger(index + 1),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "full") {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { haptics.click(); onOpenLink(if (privacy) PRIVACY_URL else TERMS_URL) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(54.dp).expressivePress(),
                ) {
                    Text("Read the full ${if (privacy) "privacy policy" else "terms"}")
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(start = 8.dp).size(18.dp))
                }
            }
        }
    }
}

data class LicenseEntry(val name: String, val license: String, val url: String)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        LicenseEntry("Tatoeba sentences", "CC BY 2.0 FR · word prediction statistics derived from tatoeba.org", "https://tatoeba.org/en/downloads"),
        LicenseEntry("FUTO Swipe dataset", "MIT License · © 2025 FUTO · sentence lists for prediction, swipes for testing", "https://huggingface.co/datasets/futo-org/swipe.futo.org"),
        LicenseEntry("Mozilla Common Voice sentences", "CC0 · via the FUTO Swipe dataset", "https://commonvoice.mozilla.org"),
        LicenseEntry("C4 (Colossal Clean Crawled Corpus)", "ODC-BY 1.0 · Allen Institute for AI · word prediction statistics", "https://huggingface.co/datasets/allenai/c4"),
        LicenseEntry("Jetpack Compose and Material 3", "Apache License 2.0", "https://developer.android.com/jetpack/compose"),
        LicenseEntry("Material 3 Expressive patterns", "Apache License 2.0 · meticha", "https://github.com/meticha/material-3-expressive-catalog"),
        LicenseEntry("MaterialKolor", "MIT License · seed colour schemes", "https://github.com/jordond/MaterialKolor"),
        LicenseEntry("Roboto Flex", "SIL Open Font License 1.1 · Font Bureau", "https://github.com/googlefonts/roboto-flex"),
        LicenseEntry("Instrument Serif", "SIL Open Font License 1.1 · Instrument", "https://github.com/Instrument/instrument-serif"),
        LicenseEntry("Space Mono", "SIL Open Font License 1.1 · Colophon", "https://github.com/googlefonts/spacemono"),
        LicenseEntry("Fredoka", "SIL Open Font License 1.1 · Milena Brandão", "https://github.com/hafontia-zz/Fredoka"),
        LicenseEntry("Comfortaa", "SIL Open Font License 1.1 · Johan Aakerlund", "https://github.com/googlefonts/comfortaa"),
        LicenseEntry("Bot avatars · Libraries.dev", "MIT License · Copyright Jakub Antalik", "https://libraries.dev"),
        LicenseEntry("Firebase Android SDK", "Apache License 2.0", "https://github.com/firebase/firebase-android-sdk"),
        LicenseEntry("Retrofit and OkHttp", "Apache License 2.0", "https://github.com/square/okhttp"),
        LicenseEntry("Google Play Billing and ML Kit", "Google developer terms", "https://developer.android.com/google/play/billing"),
    )
    ExpressiveScaffold(title = "Open-source licenses", subtitle = "Built with gratitude", onBack = onBack, heading = Heading.MonoLight) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.name }) { index, entry ->
                Surface(
                    onClick = { haptics.click(); onOpenLink(entry.url) },
                    shape = segmentShape(index, entries.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().enterStagger(index),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall)
                            Text(entry.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "notices") {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Third-party license notices", style = MaterialTheme.typography.titleMedium)
                        Text(keyboardNotices, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
