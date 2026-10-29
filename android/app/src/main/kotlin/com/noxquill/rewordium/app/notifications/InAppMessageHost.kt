package com.noxquill.rewordium.app.notifications

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.noxquill.rewordium.BuildConfig
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.toneAt
import com.noxquill.rewordium.notifications.InAppMessages
import com.noxquill.rewordium.notifications.RewordiumNotifications
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Shows what Rewordium has to say, once each: "What's new" after an update, the admin panel's campaigns
 * (card, modal or top banner) and any published while the app is open. Campaigns only while
 * notifications are on in Settings. Also asks once for permission to post notifications (Android 13+).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.InAppMessageHost(onOpenLink: (String) -> Unit) {
    val context = LocalContext.current
    val enabled by RewordiumNotifications.enabled.collectAsState()
    var message by remember { mutableStateOf<InAppMessages.Message?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        RewordiumNotifications.init(context)
        delay(1_800)
        message = InAppMessages.releaseNotesAfterUpdate(context, BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME)
            ?: if (RewordiumNotifications.isEnabled(context)) InAppMessages.nextUnseen(context) else null
        if (message == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && RewordiumNotifications.shouldAskOnce(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // New campaigns published while the app is open.
    DisposableEffect(enabled) {
        val registration = if (enabled) InAppMessages.listen(context, Date()) { incoming -> if (message == null) message = incoming } else null
        onDispose { registration?.remove() }
    }

    fun close() {
        message?.let { InAppMessages.markSeen(context, it.id) }
        message = null
    }

    fun act(url: String?) {
        close()
        if (url.isNullOrBlank()) return
        if (url.startsWith("rewordium://")) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(context.packageName)) }
        } else {
            onOpenLink(url)
        }
    }

    when (val m = message) {
        null -> Unit
        else -> when (m.type) {
            InAppMessages.Type.RELEASE_NOTES -> WhatsNewSheet(m, onDismiss = ::close, onAction = { act(m.actionUrl) })
            InAppMessages.Type.BANNER -> TopBanner(m, onDismiss = ::close, onAction = { act(m.actionUrl) })
            else -> CampaignDialog(m, onDismiss = ::close, onAction = { act(m.actionUrl) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhatsNewSheet(message: InAppMessages.Message, onDismiss: () -> Unit, onAction: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BotAvatar(type = "star", mood = BotMood.Happy, size = 64.dp, label = null)
                Column(Modifier.weight(1f)) {
                    Text(message.title, style = MaterialTheme.typography.headlineSmall)
                    if (message.body.isNotBlank()) Text(message.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message.notes.forEachIndexed { i, note ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToneIcon(Icons.Filled.AutoAwesome, toneAt(i), shape = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst)[i % 3], size = 36.dp)
                    Text(note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = if (message.actionUrl != null) onAction else onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(message.buttonText ?: if (message.actionUrl != null) "Take a look" else "Let’s go")
            }
        }
    }
}

@Composable
private fun CampaignDialog(message: InAppMessages.Message, onDismiss: () -> Unit, onAction: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = if (message.type == InAppMessages.Type.MODAL) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (message.type == InAppMessages.Type.MODAL) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BotAvatar(type = "cloud", mood = BotMood.Happy, size = 72.dp, label = null)
                Text(message.title, style = MaterialTheme.typography.headlineSmall)
                if (message.body.isNotBlank()) Text(message.body, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Not now") }
                    if (message.actionUrl != null) {
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = onAction) { Text(message.buttonText ?: "Open") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TopBanner(message: InAppMessages.Message, onDismiss: () -> Unit, onAction: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        visible = true
        delay(8_000)
        visible = false
        delay(300)
        onDismiss()
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
    ) {
        Surface(
            onClick = if (message.actionUrl != null) onAction else onDismiss,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotAvatar(type = "star", mood = BotMood.Happy, size = 40.dp, still = true, label = null)
                Column(Modifier.weight(1f)) {
                    Text(message.title, style = MaterialTheme.typography.titleSmall)
                    if (message.body.isNotBlank()) Text(message.body, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Dismiss") }
            }
        }
    }
}
