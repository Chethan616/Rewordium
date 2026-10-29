package com.noxquill.rewordium.app.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood

/**
 * Google Play's prominent disclosure for the Accessibility Service API: what
 * is accessed, why, and how it's used, shown in the app before the user is
 * sent to Android's settings, with an explicit choice to agree or decline.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessibilityDisclosureDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = {
            com.noxquill.rewordium.app.ui.AvatarBadge(
                type = "ghost",
                size = 76.dp,
                mood = BotMood.NeedsYou,
                container = MaterialTheme.colorScheme.secondaryContainer,
                motion = com.noxquill.rewordium.app.ui.ShapeMotion.Shift,
                label = "Accessibility assistant",
            )
        },
        title = { Text("Use Rewordium in other apps?", textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Rewordium’s writing assistant uses Android’s Accessibility Service API so it can help you write inside other apps.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DisclosurePoint(
                    Icons.Filled.TextFields, Tone.Primary,
                    "What it accesses",
                    "It notices when you’re in a text field so it can show the floating assistant. When you use the assistant, it reads the text in that field — and for smart replies, the recent messages visible in that conversation.",
                )
                DisclosurePoint(
                    Icons.Filled.Cloud, Tone.Secondary,
                    "How it’s used",
                    "Only the text you ask it to work on is sent to Rewordium’s AI provider to write the suggestion. The result goes back into the field when you choose it.",
                )
                DisclosurePoint(
                    Icons.Filled.Block, Tone.Tertiary,
                    "What it never does",
                    "It isn’t used for ads, isn’t sold, and Rewordium doesn’t keep the text after your request is done.",
                )
                DisclosurePoint(
                    Icons.Filled.ToggleOff, Tone.Neutral,
                    "You’re in control",
                    "Android will ask you to switch the service on. You can turn it off any time in Settings → Accessibility.",
                )
            }
        },
        confirmButton = {
            Button(onClick = onAgree, shapes = ButtonDefaults.shapes()) { Text("Agree & continue") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("No thanks") }
        },
    )
}

@Composable
private fun DisclosurePoint(icon: ImageVector, tone: Tone, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        ToneIcon(icon, tone, size = 36.dp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Remembers that the user agreed to the disclosure (kept for the record; it's shown again each time they turn it on). */
fun recordAccessibilityConsent(context: Context) {
    context.getSharedPreferences("rewordium_app", Context.MODE_PRIVATE).edit()
        .putLong("accessibility_disclosure_accepted_at", System.currentTimeMillis())
        .apply()
}
