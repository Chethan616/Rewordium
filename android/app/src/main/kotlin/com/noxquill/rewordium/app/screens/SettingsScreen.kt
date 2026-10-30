package com.noxquill.rewordium.app.screens

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.noxquill.rewordium.PlatformSetupStatus
import com.noxquill.rewordium.R
import com.noxquill.rewordium.app.ui.AvatarBadge
import com.noxquill.rewordium.app.ui.AvatarPickerSheet
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.Heading
import androidx.compose.material.icons.filled.ChevronRight
import com.noxquill.rewordium.app.ui.SectionLabel
import com.noxquill.rewordium.app.ui.SegmentedSection
import com.noxquill.rewordium.app.ui.SettingRow
import com.noxquill.rewordium.app.ui.SettingSwitchRow
import com.noxquill.rewordium.app.ui.StatusPill
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.avatarName
import com.noxquill.rewordium.app.ui.colorSchemeFor
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.data.AppSettings
import com.noxquill.rewordium.data.RewordiumRepository
import com.noxquill.rewordium.data.RewordiumLinks
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Shield
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.ShapeMotion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    account: AccountInfo?,
    status: PlatformSetupStatus,
    bottomInset: androidx.compose.ui.unit.Dp,
    onPro: () -> Unit,
    onAiSettings: () -> Unit,
    onPersonas: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onLicenses: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenKeyboard: () -> Unit,
    onAccessibility: () -> Unit,
    onAskJade: () -> Unit,
    onClearJadeHistory: () -> Unit,
    onBackupRestored: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: suspend (password: String?, googleIdToken: String?) -> Unit,
    onOpenAdmin: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val avatar by AppSettings.avatar.collectAsState()
    val themeMode by AppSettings.themeMode.collectAsState()
    val palette by AppSettings.palette.collectAsState()
    val hapticsOn by AppSettings.haptics.collectAsState()
    val keyboardAi by AppSettings.keyboardAiRewrite.collectAsState()
    var showAvatarPicker by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmClearChats by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    // Seven quick taps on the version open the admin password prompt — on the owner's own account only;
    // for everyone else the row isn't even tappable.
    val isOwner = remember(account?.email) { com.noxquill.rewordium.data.admin.AdminRepository.isOwnerSignedIn() }
    var versionTaps by remember { mutableStateOf(0) }
    var lastVersionTap by remember { mutableStateOf(0L) }
    var showAdminUnlock by rememberSaveable { mutableStateOf(false) }
    val onVersionTap: (() -> Unit)? = if (!isOwner) null else {
        {
            val now = android.os.SystemClock.uptimeMillis()
            versionTaps = if (now - lastVersionTap < 700) versionTaps + 1 else 1
            lastVersionTap = now
            if (versionTaps >= 7) {
                versionTaps = 0
                haptics.confirm()
                if (com.noxquill.rewordium.data.admin.AdminRepository.isUnlocked()) onOpenAdmin() else showAdminUnlock = true
            } else {
                haptics.tick()
            }
        }
    }

    ExpressiveScaffold(title = "Settings", subtitle = "Make Rewordium yours", heading = Heading.Narrow) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 0 · Profile
            item(key = "profile") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().enterStagger(0),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // The bot on its own library shape in the accent colour, turning slowly.
                            AvatarBadge(
                                type = avatar,
                                size = 84.dp,
                                mood = BotMood.Happy,
                                still = false,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                motion = ShapeMotion.Spin,
                                label = "Your avatar, ${avatarName(avatar)}. Tap to change.",
                                modifier = Modifier
                                    .expressivePress(0.9f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                    ) { haptics.click(); showAvatarPicker = true },
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(account?.name?.takeIf(String::isNotBlank) ?: "Your account", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(account?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                StatusPill(if (account?.isPro == true) "Pro · ${account.planType}" else "${account?.credits ?: 0} credits", active = account?.isPro == true)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Dark ink on the coral card, the same pairing as the floating toolbar.
                            Button(
                                onClick = { haptics.click(); showAvatarPicker = true },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                                modifier = Modifier.weight(1f),
                            ) { Text("Change avatar") }
                            OutlinedButton(
                                onClick = { haptics.click(); confirmSignOut = true },
                                shapes = ButtonDefaults.shapes(),
                                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSecondaryContainer),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.weight(1f),
                            ) { Text("Sign out") }
                        }
                    }
                }
            }

            // 1 · Look & feel
            item(key = "look") {
                Column(Modifier.enterStagger(1), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SectionLabel("Look & feel", Modifier.padding(bottom = 6.dp))
                    Surface(shape = com.noxquill.rewordium.app.ui.segmentShape(0, 4), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Appearance", style = MaterialTheme.typography.titleSmall)
                            ThemeModePicker(themeMode) { haptics.tick(); AppSettings.setThemeMode(it) }
                        }
                    }
                    Surface(shape = com.noxquill.rewordium.app.ui.segmentShape(1, 4), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Colours", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                            PalettePicker(palette) { haptics.tick(); AppSettings.setPalette(it) }
                        }
                    }
                    SettingSwitchRow(Icons.Filled.TouchApp, "Touch feedback", "Little vibrations when you tap around the app", hapticsOn, com.noxquill.rewordium.app.ui.segmentShape(2, 4), Tone.Tertiary) {
                        AppSettings.setHaptics(it)
                    }
                    NotificationsRow(com.noxquill.rewordium.app.ui.segmentShape(3, 4))
                }
            }

            // 2 · Jade & writing
            item(key = "jade") {
                SegmentedSection(
                    title = "Jade & writing",
                    modifier = Modifier.enterStagger(2),
                    rows = listOf(
                        { shape -> SettingRow(Icons.Filled.AutoAwesome, "Ask Jade to tune things", "Change any setting just by asking", shape, Tone.Primary, onClick = onAskJade) },
                        { shape -> SettingRow(Icons.Filled.RecordVoiceOver, "Writing personas", "The voice the keyboard’s AI rewrite uses", shape, Tone.Secondary, onClick = onPersonas) },
                        { shape -> SettingSwitchRow(Icons.Filled.Keyboard, "AI tools in the keyboard", "Show Rewordium’s writing tools while you type", keyboardAi, shape, Tone.Tertiary) { AppSettings.setKeyboardAiRewrite(it) } },
                        { shape -> SettingRow(Icons.Filled.Key, "AI provider", "Use Rewordium’s AI or your own API key", shape, Tone.Primary, onClick = onAiSettings) },
                        { shape -> SettingRow(Icons.Filled.DeleteSweep, "Clear Jade chats", "Delete conversations saved on this device", shape, Tone.Neutral) { confirmClearChats = true } },
                    ),
                )
            }

            // 3 · Keyboard: one card into the Keyboard tab.
            item(key = "keyboard") {
                Column(Modifier.enterStagger(3), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SectionLabel("Keyboard", Modifier.padding(bottom = 6.dp))
                    Surface(
                        onClick = { haptics.click(); onOpenKeyboard() },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().expressivePress(0.97f),
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AvatarBadge(
                                type = "droid",
                                size = 62.dp,
                                mood = if (status.keyboardSelected) BotMood.Happy else BotMood.NeedsYou,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                motion = ShapeMotion.Shift,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("keyboard_", style = Heading.Mono.style(MaterialTheme.typography.titleLarge))
                                Text("Languages, looks, typing and feel", style = MaterialTheme.typography.bodySmall)
                                StatusPill(
                                    when {
                                        status.keyboardSelected -> "On and selected"
                                        status.keyboardEnabled -> "On · not selected"
                                        else -> "Not turned on"
                                    },
                                    status.keyboardSelected,
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }
            }

            // 4 · Accessibility
            item(key = "accessibility") {
                SegmentedSection(
                    title = "Accessibility assistant",
                    modifier = Modifier.enterStagger(4),
                    rows = listOf { shape ->
                        SettingRow(
                            Icons.Filled.AccessibilityNew,
                            if (status.accessibilityEnabled) "Assistant is on" else "Turn on the assistant",
                            if (status.accessibilityEnabled) "Rewrite and reply inside other apps · manage in Android settings"
                            else "Rewrite and reply inside other apps. We’ll explain what it accesses first.",
                            shape,
                            if (status.accessibilityEnabled) Tone.Primary else Tone.Tertiary,
                            onClick = onAccessibility,
                            trailing = { StatusPill(if (status.accessibilityEnabled) "On" else "Off", status.accessibilityEnabled) },
                        )
                    },
                )
            }

            // 5 · Plan
            item(key = "plan") {
                SegmentedSection(
                    title = "Plan",
                    modifier = Modifier.enterStagger(5),
                    rows = listOf { shape ->
                        SettingRow(
                            Icons.Filled.Stars,
                            if (account?.isPro == true) "Rewordium Pro" else "Credits & Pro",
                            if (account?.isPro == true) "${account.planType.replaceFirstChar(Char::uppercase)} plan · active" else "${account?.credits ?: 0} credits left · see plans",
                            shape, Tone.Tertiary, onClick = onPro,
                        )
                    },
                )
            }

            // 6 · Backup & restore: everything in one file on the phone.
            item(key = "backup") {
                BackupSection(modifier = Modifier.enterStagger(6), onRestored = onBackupRestored)
            }

            // 7 · About & legal
            item(key = "about") {
                SegmentedSection(
                    title = "About & legal",
                    modifier = Modifier.enterStagger(7),
                    rows = listOf(
                        { shape -> SettingRow(Icons.Filled.Policy, "Privacy policy", "What we collect and how it’s used", shape, Tone.Neutral, onClick = onPrivacy) },
                        { shape -> SettingRow(Icons.Filled.Gavel, "Terms of service", "The rules for using Rewordium", shape, Tone.Neutral, onClick = onTerms) },
                        { shape -> SettingRow(Icons.Filled.Shield, "Data safety", "What’s collected, why, and what’s shared", shape, Tone.Neutral) { onOpenLink(RewordiumLinks.DATA_SAFETY) } },
                        { shape -> SettingRow(Icons.Filled.Description, "Open-source licenses", "The projects Rewordium is built on", shape, Tone.Neutral, onClick = onLicenses) },
                        { shape ->
                            SettingRow(Icons.Filled.Email, "Contact support", RewordiumLinks.SUPPORT_EMAIL, shape, Tone.Neutral) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${RewordiumLinks.SUPPORT_EMAIL}"))
                                            .putExtra(android.content.Intent.EXTRA_SUBJECT, "Rewordium support"),
                                    )
                                }.onFailure { onOpenLink(RewordiumLinks.WEBSITE) }
                            }
                        },
                        { shape -> SettingRow(Icons.Filled.Info, "Rewordium for Android", "Version $version", shape, Tone.Neutral, onClick = onVersionTap) },
                    ),
                )
            }

            // 8 · Account
            item(key = "account") {
                SegmentedSection(
                    title = "Account",
                    modifier = Modifier.enterStagger(8),
                    rows = listOf(
                        { shape -> SettingRow(Icons.AutoMirrored.Filled.Logout, "Sign out", account?.email ?: "Sign out of Rewordium on this phone", shape, Tone.Neutral) { confirmSignOut = true } },
                        { shape -> SettingRow(Icons.Filled.DeleteForever, "Delete account", "Permanently remove your account and data", shape, Tone.Error) { showDelete = true } },
                    ),
                )
            }
        }
    }

    if (showAdminUnlock) com.noxquill.rewordium.app.screens.admin.AdminUnlockDialog(
        onDismiss = { showAdminUnlock = false },
        onUnlocked = { showAdminUnlock = false; onOpenAdmin() },
    )

    if (showAvatarPicker) AvatarPickerSheet(
        current = avatar,
        onDismiss = { showAvatarPicker = false },
        onConfirm = { AppSettings.setAvatar(it); showAvatarPicker = false },
    )

    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
        title = { Text("Sign out?") },
        text = { Text("You’ll need to sign in again to use Jade and your credits.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; haptics.confirm(); onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay signed in") } },
    )

    if (confirmClearChats) AlertDialog(
        onDismissRequest = { confirmClearChats = false },
        icon = { Icon(Icons.Filled.DeleteSweep, null) },
        title = { Text("Clear Jade chats?") },
        text = { Text("Every Jade conversation saved on this device will be deleted. This can’t be undone.") },
        confirmButton = { TextButton(onClick = { confirmClearChats = false; haptics.reject(); onClearJadeHistory() }) { Text("Clear chats", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmClearChats = false }) { Text("Keep") } },
    )

    if (showDelete) DeleteAccountDialog(
        isPro = account?.isPro == true,
        onDismiss = { showDelete = false },
        onDelete = onDeleteAccount,
        onDeleteOnWeb = { onOpenLink(RewordiumLinks.DELETE_ACCOUNT) },
    )
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModePicker(current: AppSettings.ThemeMode, onSelect: (AppSettings.ThemeMode) -> Unit) {
    val modes = AppSettings.ThemeMode.entries
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        modes.forEachIndexed { index, mode ->
            ToggleButton(
                checked = mode == current,
                onCheckedChange = { onSelect(mode) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                colors = com.noxquill.rewordium.app.ui.segmentColors(),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                // The default 24dp side padding leaves "System" too little room on a phone.
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(
                    when (mode) {
                        AppSettings.ThemeMode.SYSTEM -> Icons.Filled.Smartphone
                        AppSettings.ThemeMode.LIGHT -> Icons.Filled.LightMode
                        AppSettings.ThemeMode.DARK -> Icons.Filled.DarkMode
                    },
                    null,
                    Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    mode.label,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PalettePicker(current: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val palettes = remember { AppSettings.palettes.filter { it.id != "dynamic" || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S } }
    val previews = remember(dark) { palettes.associate { it.id to colorSchemeFor(context, it.id, dark) } }
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(palettes, key = { it.id }) { palette ->
            val selected = palette.id == current
            val scheme = previews.getValue(palette.id)
            val spin by animateFloatAsState(if (selected) 0f else -60f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "palette-spin")
            val grow by animateFloatAsState(if (selected) 1f else 0.86f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "palette-grow")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics {
                    contentDescription = "${palette.label} colours"
                    this.selected = selected
                },
            ) {
                Surface(
                    onClick = { onSelect(palette.id) },
                    shape = if (selected) cookie else CircleShape,
                    color = scheme.primary,
                    modifier = Modifier.size(64.dp).graphicsLayer { scaleX = grow; scaleY = grow; rotationZ = spin },
                ) {
                    Box {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth().background(scheme.primary))
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                Box(Modifier.weight(1f).fillMaxSize().background(scheme.tertiaryContainer))
                                Box(Modifier.weight(1f).fillMaxSize().background(scheme.secondaryContainer))
                            }
                        }
                        if (palette.id == "dynamic") Icon(Icons.Filled.Wallpaper, null, Modifier.align(Alignment.Center).size(22.dp), tint = scheme.onPrimary)
                        else if (selected) Icon(Icons.Filled.Check, null, Modifier.align(Alignment.Center).size(24.dp).graphicsLayer { rotationZ = -spin }, tint = scheme.onPrimary)
                    }
                }
                Text(palette.label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Deleting an account is permanent, so it's confirmed twice and Firebase may
 * ask the user to prove it's them (password or Google) right before.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteAccountDialog(isPro: Boolean, onDismiss: () -> Unit, onDelete: suspend (String?, String?) -> Unit, onDeleteOnWeb: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val needsProof = remember { RewordiumRepository.needsFreshSignIn() }
    val google = remember { RewordiumRepository.signedInWithGoogle }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(password: String?, token: String?) {
        scope.launch {
            busy = true
            error = null
            runCatching { onDelete(password, token) }
                .onFailure { error = it.message ?: "Couldn’t delete your account. Try again."; haptics.reject() }
            busy = false
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken
            }.getOrNull()
            if (!token.isNullOrBlank()) run(null, token) else error = "Google didn’t confirm your account. Try again."
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This permanently deletes your Rewordium account, profile, credits and plan records, and the Jade chats on this phone. It can’t be undone.")
                if (isPro) Text(
                    "Deleting your account doesn’t cancel Rewordium Pro. Cancel it in Google Play → Subscriptions so you aren’t charged again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (needsProof && !google) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Your password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (needsProof && google) Text("Google will ask you to confirm it’s you.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LoadingIndicator(Modifier.size(40.dp).align(Alignment.CenterHorizontally))
                TextButton(onClick = onDeleteOnWeb, contentPadding = PaddingValues(0.dp), enabled = !busy) {
                    Text("Can’t sign in? Request deletion on the web", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (!needsProof || google || password.isNotBlank()),
                onClick = {
                    haptics.reject()
                    when {
                        !needsProof -> run(null, null)
                        google -> {
                            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(context.getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                        }
                        else -> run(password, null)
                    }
                },
            ) { Text(if (needsProof && google) "Confirm with Google" else "Delete forever", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Keep account") } },
    )
}

/**
 * One switch for everything Rewordium sends: push notifications (new features, release notes, offers),
 * Firebase in-app campaigns and the in-app announcements. Turning it on asks Android for permission
 * where needed (13+); if the user said no before, it opens the app's notification settings instead.
 */
@Composable
private fun NotificationsRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled by com.noxquill.rewordium.notifications.RewordiumNotifications.enabled.collectAsState()
    var granted by remember { mutableStateOf(com.noxquill.rewordium.notifications.RewordiumNotifications.hasPermission(context)) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (!ok) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
    }
    val subtitle = when {
        !enabled -> "Off — no updates, release notes or offers"
        !granted -> "On in the app · tap to allow on this phone"
        else -> "New features, release notes and offers"
    }
    SettingSwitchRow(Icons.Filled.Notifications, "Notifications", subtitle, enabled && granted, shape, Tone.Secondary) { on ->
        com.noxquill.rewordium.notifications.RewordiumNotifications.setEnabled(context, on)
        if (on && !granted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
