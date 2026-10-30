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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.ShapeBadge
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.display
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.data.RewordiumLinks
import com.noxquill.rewordium.keyboard.app.ReleaseFlags

private const val MANAGE_PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

/** Rewordium Pro subscriptions were sold in the earlier Rewordium app, so that's where they're managed. */
private const val SUBSCRIPTION_APP_PACKAGE = "com.noxquill.rewordium"

/**
 * What this release offers instead of plans: the keyboard has no limits, and the optional AI tools run on a
 * fixed included allowance — how much is left, that it doesn't refill, and the two ways to keep going (your
 * own AI key, or carrying on without AI). Someone whose Rewordium account has Pro from an earlier
 * subscription sees that, with a way to manage or cancel it in Google Play.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiAllowanceScreen(
    account: AccountInfo?,
    packageName: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onAiSettings: () -> Unit,
    onSignIn: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val total = ReleaseFlags.FREE_AI_USES
    val left = (account?.credits ?: 0).coerceAtLeast(0)
    ExpressiveScaffold(title = "AI writing uses", subtitle = "No subscriptions", onBack = onBack, heading = Heading.SerifItalic) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth().enterStagger(0)) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShapeBadge(shape = MaterialShapes.Cookie9Sided, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(120.dp)) {
                            BotAvatar(type = "star", mood = BotMood.Happy, size = 80.dp, label = null)
                        }
                        Text("No subscriptions, no account", style = Heading.SerifItalic.display(36.sp), textAlign = TextAlign.Center)
                        Text(
                            "Typing, autocorrect, glide typing, suggestions, themes and stickers have no limits and don’t need an account.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item(key = "allowance") {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().enterStagger(1)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("AI tools", style = MaterialTheme.typography.titleLarge)
                        when {
                            account?.isPro == true -> {
                                Text("Unlimited AI with your Pro plan", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (account.planType == "gifted") "Pro was added to your account by the Rewordium team."
                                    else "Your Rewordium account has Pro from an earlier subscription. You keep it while that subscription is active, and you can manage or cancel it in Google Play.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (account.planType != "gifted") OutlinedButton(
                                    onClick = { haptics.click(); onOpenLink("$MANAGE_PLAY_SUBSCRIPTIONS?package=$SUBSCRIPTION_APP_PACKAGE") },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Manage or cancel in Google Play")
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                                }
                            }
                            account == null -> {
                                Text(
                                    "The built-in AI needs a quick sign-in on this phone. Everything else works without one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(onClick = { haptics.click(); onSignIn() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("$left", style = Heading.Serif.display(44.sp))
                                    Text("of $total included AI uses left", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                }
                                LinearWavyProgressIndicator(progress = { left / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    "Each rewrite, tone change, summary, translation or Jade message uses one. They don’t refill in this version. " +
                                        "When they run out, the keyboard keeps working exactly as before.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "own-key") {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().enterStagger(2)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToneIcon(Icons.Filled.Key, Tone.Primary)
                            Text("Use your own AI key", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Add a key from Groq, OpenAI, Google Gemini or Anthropic. Your AI requests then go to that provider on your own account and don’t use your included AI uses.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = { haptics.click(); onAiSettings() }, shapes = ButtonDefaults.shapes()) { Text("Set up your own key") }
                    }
                }
            }
            item(key = "privacy") {
                Row(Modifier.enterStagger(3), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneIcon(Icons.Filled.Lock, Tone.Tertiary, size = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "AI tools only run when you tap one, and only the text you choose is sent to the AI provider. Ordinary typing, suggestions and autocorrect stay on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onOpenLink(RewordiumLinks.PRIVACY_POLICY) }, contentPadding = PaddingValues(0.dp)) { Text("How your text is handled") }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item(key = "keyboard-note") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Keyboard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Rewordium is a one-time purchase on Google Play. There are no subscriptions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
