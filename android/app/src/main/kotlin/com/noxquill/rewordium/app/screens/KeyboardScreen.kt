package com.noxquill.rewordium.app.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.PlatformSetupStatus
import com.noxquill.rewordium.app.ui.AvatarBadge
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.SectionLabel
import com.noxquill.rewordium.app.ui.SettingRow
import com.noxquill.rewordium.app.ui.SettingSwitchRow
import com.noxquill.rewordium.app.ui.ShapeMotion
import com.noxquill.rewordium.app.ui.StatusPill
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.monoLabel
import com.noxquill.rewordium.app.ui.monoNumber
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AppSettings
import com.noxquill.rewordium.data.KeyboardEngine
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.app.ReboardLanguages
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge
import com.noxquill.rewordium.keyboard.ime.input.InputFeedbackActivationMode
import com.noxquill.rewordium.keyboard.ime.nlp.latin.AutoCorrectLevel
import com.noxquill.rewordium.keyboard.ime.theme.ThemeMode
import com.noxquill.rewordium.keyboard.ime.keyboard.SpaceBarMode
import com.noxquill.rewordium.keyboard.ime.smartbar.CandidatesDisplayMode
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.ViewWeek
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val keyboardPrefs by FlorisPreferenceStore

/** Space above each section of the Keys page (on top of the 3dp gap between list items). */
private val SectionGap = 23.dp

/** Rows in the Features section, across both of its list items. */
private const val FeatureSegments = 13

internal data class AccentSwatch(val label: String, val color: Color)

internal val accentSwatches = listOf(
    AccentSwatch("Wallpaper", Color.Unspecified),
    AccentSwatch("Violet", Color(0xFF7A2BE0)),
    AccentSwatch("Coral", Color(0xFFFF6F61)),
    AccentSwatch("Lime", Color(0xFF9CCC17)),
    AccentSwatch("Ocean", Color(0xFF0B84D9)),
    AccentSwatch("Bubblegum", Color(0xFFE64389)),
    AccentSwatch("Sunshine", Color(0xFFF2A900)),
    AccentSwatch("Mint", Color(0xFF14B87A)),
)

/**
 * Rewordium's own keyboard page: the settings that matter, in three short
 * sections (Language, Appearance, Features), changed in place — no trip to
 * ReBoard's full settings app. Everything else is one ask away with Jade.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyboardScreen(
    status: PlatformSetupStatus,
    bottomInset: Dp,
    onEnableKeyboard: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onAskJade: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenThemeShop: () -> Unit,
) {
    val context = LocalContext.current
    // Started once for the whole run (the app warms it at launch), so later visits draw immediately.
    val ready by KeyboardEngine.ready.collectAsState()
    LaunchedEffect(Unit) { KeyboardEngine.ensure(context) }

    ExpressiveScaffold(title = "keyboard_", subtitle = "Language · Appearance · Features", heading = Heading.Mono) { padding ->
        when (ready) {
            null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                return@ExpressiveScaffold
            }
            false -> {
                Column(
                    Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AvatarBadge(type = "droid", size = 104.dp, mood = BotMood.Sad, container = MaterialTheme.colorScheme.secondaryContainer, motion = ShapeMotion.Spin)
                    Spacer(Modifier.height(16.dp))
                    Text("keyboard offline", style = Heading.Mono.style(MaterialTheme.typography.headlineSmall))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The Rewordium keyboard engine couldn’t start on this device, so its settings can’t be shown.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                return@ExpressiveScaffold
            }
            else -> Unit
        }
        // Long sections are split into several list items, so opening the page builds only what's on
        // screen and the rest is built as it scrolls in. Items sit 3dp apart (the segment gap); each
        // new section adds its own gap above it.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(key = "status") { StatusCard(status, onEnableKeyboard, onChooseKeyboard, Modifier.enterStagger(0)) }
            item(key = "language") { LanguageSection(Modifier.padding(top = SectionGap).enterStagger(1)) }
            item(key = "appearance") { AppearanceSection(onOpenThemeShop, Modifier.padding(top = SectionGap).enterStagger(2)) }
            item(key = "features") { FeaturesSection(Modifier.padding(top = SectionGap).enterStagger(3)) }
            item(key = "features-more") { FeaturesMore(Modifier.enterStagger(3)) }
            item(key = "feel") { FeelSection(Modifier.padding(top = SectionGap).enterStagger(4)) }
            item(key = "more") {
                Column(Modifier.padding(top = SectionGap).enterStagger(5), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SectionLabel("More", Modifier.padding(bottom = 6.dp))
                    SettingRow(Icons.Filled.AutoAwesome, "Ask Jade to tune anything", "Every keyboard option, just by asking", segmentShape(0, 2), Tone.Primary, onClick = onAskJade)
                    SettingRow(Icons.AutoMirrored.Filled.OpenInNew, "Advanced options", "Theme editor, dictionary, smartbar and more in ReBoard", segmentShape(1, 2), Tone.Neutral, onClick = onOpenAdvanced)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(status: PlatformSetupStatus, onEnable: () -> Unit, onChoose: () -> Unit, modifier: Modifier) {
    val haptics = LocalHaptics.current
    var tryText by rememberSaveable { mutableStateOf("") }
    val ready = status.keyboardSelected
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (ready) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (ready) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarBadge(
                    type = "droid",
                    size = 62.dp,
                    mood = if (ready) BotMood.Happy else BotMood.NeedsYou,
                    still = false,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    motion = ShapeMotion.Shift,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when {
                            ready -> "Ready to type"
                            status.keyboardEnabled -> "Almost there"
                            else -> "Let’s switch it on"
                        },
                        style = Heading.Mono.style(MaterialTheme.typography.titleLarge),
                    )
                    Text(
                        when {
                            ready -> "Rewordium is your keyboard. Try it below."
                            status.keyboardEnabled -> "It’s on — now pick it as your keyboard."
                            else -> "Turn on the Rewordium keyboard in Android settings."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (ready) {
                // The field sits on the bright card, not the page, so it writes in the card's own dark ink
                // (the theme default is the page's onSurface — white in dark mode, invisible on lime).
                val ink = LocalContentColor.current
                OutlinedTextField(
                    value = tryText,
                    onValueChange = { tryText = it },
                    placeholder = { Text("Type here to try your changes…") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ink,
                        unfocusedTextColor = ink,
                        cursorColor = ink,
                        focusedBorderColor = ink,
                        unfocusedBorderColor = ink.copy(alpha = 0.45f),
                        focusedPlaceholderColor = ink.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = ink.copy(alpha = 0.6f),
                        focusedContainerColor = ink.copy(alpha = 0.06f),
                        unfocusedContainerColor = ink.copy(alpha = 0.06f),
                        selectionColors = TextSelectionColors(handleColor = ink, backgroundColor = ink.copy(alpha = 0.25f)),
                    ),
                )
            } else {
                Button(
                    onClick = { haptics.click(); if (status.keyboardEnabled) onChoose() else onEnable() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (status.keyboardEnabled) "Choose Rewordium keyboard" else "Turn on the keyboard") }
            }
        }
    }
}

// ── Language ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageSection(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val raw by keyboardPrefs.localization.subtypes.observeAsState()
    val activeId by keyboardPrefs.localization.activeSubtypeId.observeAsState()
    val languages = remember(raw, activeId) { ReboardLanguages.decode(raw, activeId) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val rows = languages.size.coerceAtLeast(1) + 1

    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Language", Modifier.padding(bottom = 6.dp))
        if (languages.isEmpty()) {
            LanguageRow("English (United States)", "QWERTY · default", active = true, shape = segmentShape(0, rows), onClick = {}, onRemove = null)
        }
        languages.forEachIndexed { index, language ->
            LanguageRow(
                name = language.name,
                layout = language.layout,
                active = language.active,
                shape = segmentShape(index, rows),
                onClick = {
                    haptics.tick()
                    scope.launch { ReboardLanguages.activate(context, language.id) }
                },
                onRemove = if (languages.size > 1) {
                    {
                        haptics.reject()
                        scope.launch {
                            ReboardLanguages.remove(context, language.id)
                                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
                        }
                    }
                } else null,
            )
        }
        Surface(
            onClick = { haptics.click(); showAdd = true },
            shape = segmentShape(rows - 1, rows),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Add, null)
                Text("Add a language", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${languages.size.coerceAtLeast(1)} on board", style = monoLabel())
            }
        }
    }

    if (showAdd) AddLanguageSheet(onDismiss = { showAdd = false }) { option ->
        scope.launch {
            ReboardLanguages.add(context, option.tag)
                .onSuccess { haptics.success(); Toast.makeText(context, "${it.name} added — switch with the globe key", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
        }
        showAdd = false
    }
}

@Composable
private fun LanguageRow(name: String, layout: String, active: Boolean, shape: Shape, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().semantics { selected = active; role = Role.RadioButton },
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = active, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(layout.uppercase(), style = monoLabel(), color = if (active) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Filled.DeleteOutline, "Remove $name") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddLanguageSheet(onDismiss: () -> Unit, onPick: (ReboardLanguages.Option) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val options by produceState<List<ReboardLanguages.Option>?>(null) { value = ReboardLanguages.options(context) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add a language", style = Heading.Mono.style(MaterialTheme.typography.headlineSmall))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search languages") },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            val list = options
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
            } else {
                val filtered = list.filter { query.isBlank() || it.name.contains(query, true) || it.tag.contains(query, true) }
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered.size, key = { filtered[it].tag }) { index ->
                        val option = filtered[index]
                        Surface(
                            onClick = { onPick(option) },
                            shape = segmentShape(index, filtered.size),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(option.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(option.layout.uppercase(), style = monoLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (filtered.isEmpty()) item {
                        Text("No languages match “$query”.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

// ── Appearance ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceSection(onOpenThemeShop: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val mode by keyboardPrefs.theme.mode.observeAsState()
    val dayTheme by keyboardPrefs.theme.dayThemeId.observeAsState()
    val nightTheme by keyboardPrefs.theme.nightThemeId.observeAsState()
    val accent by keyboardPrefs.theme.accentColor.observeAsState()
    // Last known themes draw at once; a fresh read (themes can be added in ReBoard) swaps in quietly.
    val themes by KeyboardEngine.themes.collectAsState()
    LaunchedEffect(Unit) { KeyboardEngine.refreshThemes(context) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Appearance", Modifier.padding(bottom = 6.dp))
        Surface(shape = segmentShape(0, 2), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                val modes = listOf(ThemeMode.FOLLOW_SYSTEM to "Auto", ThemeMode.ALWAYS_DAY to "Light", ThemeMode.ALWAYS_NIGHT to "Dark")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    modes.forEachIndexed { index, (value, label) ->
                        ToggleButton(
                            checked = mode == value,
                            onCheckedChange = { haptics.tick(); scope.launch { keyboardPrefs.theme.mode.set(value) } },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            colors = com.noxquill.rewordium.app.ui.segmentColors(),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Icon(
                                when (value) {
                                    ThemeMode.FOLLOW_SYSTEM -> Icons.Filled.BrightnessAuto
                                    ThemeMode.ALWAYS_DAY -> Icons.Filled.LightMode
                                    else -> Icons.Filled.DarkMode
                                },
                                null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label)
                        }
                    }
                }
                val accentColor = if (accent == Color.Unspecified) MaterialTheme.colorScheme.primary else accent
                if (mode != ThemeMode.ALWAYS_NIGHT) ThemeStrip(
                    title = "Light look",
                    options = themes.filter { !it.night },
                    selected = dayTheme.toString(),
                    accent = accentColor,
                ) { id -> haptics.tick(); scope.launch { keyboardPrefs.theme.dayThemeId.set(ExtensionComponentName.from(id)) } }
                if (mode != ThemeMode.ALWAYS_DAY) ThemeStrip(
                    title = "Dark look",
                    options = themes.filter { it.night },
                    selected = nightTheme.toString(),
                    accent = accentColor,
                ) { id -> haptics.tick(); scope.launch { keyboardPrefs.theme.nightThemeId.set(ExtensionComponentName.from(id)) } }
            }
        }
        ThemeShopEntry(segmentShape(1, 2), onOpenThemeShop)
    }
}

/**
 * The way into the Theme shop: everything about how the keyboard looks, moves and who sits on it
 * (key colour, packs, community themes, the builder, motion, mascots, sizes) lives there.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeShopEntry(shape: Shape, onOpen: () -> Unit) {
    val haptics = LocalHaptics.current
    val mascot by keyboardPrefs.theme.mascot.observeAsState()
    Surface(
        onClick = { haptics.confirm(); onOpen() },
        shape = shape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open the theme shop" },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.noxquill.rewordium.app.ui.avatar.BotAvatar(
                    type = mascot.ifBlank { "star" },
                    mood = com.noxquill.rewordium.app.ui.avatar.BotMood.Happy,
                    size = 54.dp,
                    label = null,
                )
                Column(Modifier.weight(1f)) {
                    Text("Theme shop", style = MaterialTheme.typography.titleLarge)
                    Text("Playful packs, community themes, Jade-made looks, key colour, bouncy keys and mascots", style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.noxquill.rewordium.keyboard.ime.theme.studio.ThemePacks.all.forEach { pack ->
                    com.noxquill.rewordium.app.screens.themes.KeyboardMock(pack.themes.first(), Modifier.weight(1f), compact = true)
                }
            }
        }
    }
}

@Composable
private fun ThemeStrip(title: String, options: List<ReboardSettingsBridge.ThemeOption>, selected: String, accent: Color, onPick: (String) -> Unit) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = monoLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options, key = { it.id }) { option ->
                ThemePreview(option, option.id == selected, accent) { onPick(option.id) }
            }
        }
    }
}

/** A tiny drawn keyboard in the theme's colours, so picking a look is visual. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemePreview(option: ReboardSettingsBridge.ThemeOption, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val label = option.label.lowercase()
    val amoled = "amoled" in label
    val borderless = "borderless" in label
    val background = when {
        !option.night -> Color(0xFFE8EAEF)
        amoled -> Color.Black
        else -> Color(0xFF1F1F24)
    }
    val key = when {
        !option.night -> Color.White
        amoled -> Color(0xFF1C1C1C)
        else -> Color(0xFF3A3A42)
    }
    val glyph = if (option.night) Color(0xFFE6E6EA) else Color(0xFF3C4043)
    val grow by animateFloatAsState(if (selected) 1f else 0.94f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "theme-grow")
    val name = option.label.removePrefix("ReBoard Material ").removePrefix("ReBoard ").replace("(Borderless)", "· borderless")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = "${option.label} keyboard theme"; this.selected = selected },
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = background,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(width = 124.dp, height = 84.dp).graphicsLayer { scaleX = grow; scaleY = grow },
        ) {
            Box {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val rows = listOf(10, 9, 7)
                    val gap = 2.dp.toPx()
                    val rowHeight = (size.height - gap * 4) / 4
                    rows.forEachIndexed { r, count ->
                        val keyWidth = (size.width - gap * (10 - 1)) / 10
                        val inset = (size.width - (keyWidth * count + gap * (count - 1))) / 2
                        repeat(count) { k ->
                            val left = inset + k * (keyWidth + gap)
                            val top = r * (rowHeight + gap)
                            if (borderless) {
                                drawCircle(glyph.copy(alpha = 0.7f), radius = 1.2.dp.toPx(), center = Offset(left + keyWidth / 2, top + rowHeight / 2))
                            } else {
                                drawRoundRect(key, Offset(left, top), Size(keyWidth, rowHeight), CornerRadius(2.dp.toPx()))
                            }
                        }
                    }
                    val top = 3 * (rowHeight + gap)
                    val unit = (size.width - gap * 9) / 10
                    drawRoundRect(if (borderless) glyph.copy(alpha = 0.18f) else key, Offset(unit * 2 + gap * 2, top), Size(unit * 6 + gap * 5, rowHeight), CornerRadius(3.dp.toPx()))
                    drawRoundRect(accent, Offset(size.width - unit * 1.6f, top), Size(unit * 1.6f, rowHeight), CornerRadius(3.dp.toPx()))
                }
                if (selected) Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
                }
            }
        }
        Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccentPicker(current: Color, onPick: (Color) -> Unit) {
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(accentSwatches, key = { it.label }) { swatch ->
            val selected = swatch.color == current
            val spin by animateFloatAsState(if (selected) 0f else -60f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "accent-spin")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    onClick = { onPick(swatch.color) },
                    shape = if (selected) cookie else CircleShape,
                    color = if (swatch.color == Color.Unspecified) MaterialTheme.colorScheme.primaryContainer else swatch.color,
                    modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = spin }.semantics { contentDescription = "${swatch.label} key colour"; this.selected = selected },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            swatch.color == Color.Unspecified -> Icon(Icons.Filled.Wallpaper, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.graphicsLayer { rotationZ = -spin })
                            selected -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.graphicsLayer { rotationZ = -spin })
                        }
                    }
                }
                Text(swatch.label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Features ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeaturesSection(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val autocorrect by keyboardPrefs.correction.autoCorrect.observeAsState()
    val level by keyboardPrefs.correction.autoCorrectLevel.observeAsState()
    val glide by keyboardPrefs.glide.enabled.observeAsState()
    val suggestions by keyboardPrefs.suggestion.enabled.observeAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Features", Modifier.padding(bottom = 6.dp))
        val count = FeatureSegments
        PrefSwitch(keyboardPrefs.correction.autoCorrect, Icons.Filled.Spellcheck, "Autocorrect", "Fix typos as you finish each word", segmentShape(0, count), Tone.Primary)
        AnimatedVisibility(autocorrect, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(shape = segmentShape(1, count), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(top = 3.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Strength", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    val levels = listOf(AutoCorrectLevel.MODEST to "Gentle", AutoCorrectLevel.AGGRESSIVE to "Bold")
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        levels.forEachIndexed { index, (value, label) ->
                            ToggleButton(
                                checked = level == value,
                                onCheckedChange = { haptics.tick(); scope.launch { keyboardPrefs.correction.autoCorrectLevel.set(value) } },
                                colors = com.noxquill.rewordium.app.ui.segmentColors(),
                                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            ) { Text(label) }
                        }
                    }
                }
            }
        }
        PrefSwitch(keyboardPrefs.suggestion.enabled, Icons.Filled.Lightbulb, "Word suggestions", "Predictions above the keys", segmentShape(2, count), Tone.Tertiary)
        AnimatedVisibility(suggestions, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 3.dp)) { SuggestionLayoutCard(segmentShape(3, count)) }
        }
        PrefSwitch(keyboardPrefs.glide.enabled, Icons.Filled.Gesture, "Glide typing", "Slide across letters to type", segmentShape(4, count), Tone.Secondary)
        AnimatedVisibility(glide, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 3.dp)) {
                PrefSwitch(keyboardPrefs.glide.showTrail, Icons.AutoMirrored.Filled.ShortText, "Glide trail", "Draw a trail as you glide", segmentShape(5, count), Tone.Secondary)
            }
        }
    }
}

/** The rest of Features (segments 7–12), its own list item so it's only built once scrolled near. */
@Composable
private fun FeaturesMore(modifier: Modifier) {
    val aiTools by AppSettings.keyboardAiRewrite.collectAsState()
    val count = FeatureSegments
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PrefSwitch(keyboardPrefs.correction.autoCapitalization, Icons.Filled.TextFields, "Auto-capitalize", "Capitals at the start of sentences", segmentShape(6, count), Tone.Primary)
        PrefSwitch(keyboardPrefs.correction.doubleSpacePeriod, Icons.Filled.SpaceBar, "Double-space full stop", "Tap space twice for “. ”", segmentShape(7, count), Tone.Tertiary)
        PrefSwitch(keyboardPrefs.emoji.suggestionEnabled, Icons.Filled.EmojiEmotions, "Emoji suggestions", "Emoji that match your words", segmentShape(8, count), Tone.Secondary)
        PrefSwitch(keyboardPrefs.clipboard.historyEnabled, Icons.Filled.ContentPaste, "Clipboard history", "Keep recent copies one tap away", segmentShape(9, count), Tone.Primary)
        SettingSwitchRow(Icons.Filled.AutoAwesome, "AI writing tools", "Rewordium’s rewrite and reply tools in the keyboard", aiTools, segmentShape(10, count), Tone.Tertiary) {
            AppSettings.setKeyboardAiRewrite(it)
        }
        PrefSwitch(keyboardPrefs.dictionary.learnPersonalWords, Icons.AutoMirrored.Filled.MenuBook, "Learn my words", "Remember names and words you use, on this phone", segmentShape(11, count), Tone.Primary)
        ForgetLearningRow(segmentShape(12, count))
    }
}

/** Forgets everything the keyboard learned from typing, after a confirmation. */
@Composable
private fun ForgetLearningRow(shape: Shape) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingRow(Icons.Filled.DeleteOutline, "Forget learned words", "Clear words, phrases and emoji the keyboard picked up", shape, Tone.Tertiary) {
        confirming = true
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { ToneIcon(Icons.Filled.DeleteOutline, Tone.Tertiary, size = 56.dp) },
            title = { Text("Forget learned words?") },
            text = {
                Text(
                    "The keyboard forgets the words, phrases and emoji it learned from your typing. " +
                        "Your own dictionary and contact names stay.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch {
                        val done = ReboardSettingsBridge.forgetLearning(context)
                        Toast.makeText(context, if (done) "Learned words forgotten" else "Couldn’t reach the keyboard", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FeelSection(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptic by keyboardPrefs.inputFeedback.hapticEnabled.observeAsState()
    val sound by keyboardPrefs.inputFeedback.audioEnabled.observeAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Sound & touch", Modifier.padding(bottom = 6.dp))
        SettingSwitchRow(Icons.AutoMirrored.Filled.VolumeUp, "Key sounds", "A soft click on every key", sound, segmentShape(0, 4), Tone.Primary) { on ->
            scope.launch { keyboardPrefs.inputFeedback.audioEnabled.set(on) }
        }
        AnimatedVisibility(sound, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 3.dp)) {
                PrefSlider(keyboardPrefs.inputFeedback.audioVolume, Icons.AutoMirrored.Filled.VolumeUp, "Sound volume", 1..100, 1, segmentShape(1, 4), Tone.Primary) { "$it" }
            }
        }
        SettingSwitchRow(Icons.Filled.Vibration, "Vibration", "Feel each key press", haptic, segmentShape(2, 4), Tone.Secondary) { on ->
            scope.launch {
                keyboardPrefs.inputFeedback.hapticEnabled.set(on)
                // Same as the keyboard's quick setting: an explicit "on" shouldn't be muted by the system default.
                if (on) keyboardPrefs.inputFeedback.hapticActivationMode.set(InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS)
            }
        }
        AnimatedVisibility(haptic, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 3.dp)) {
                PrefSlider(keyboardPrefs.inputFeedback.hapticVibrationStrength, Icons.Filled.Vibration, "Vibration strength", 1..100, 1, segmentShape(3, 4), Tone.Secondary) { "$it" }
            }
        }
    }
}

/** What the space bar says, with a live preview of the key. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpaceBarCard(shape: Shape) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val mode by keyboardPrefs.keyboard.spaceBarMode.observeAsState()
    val storedLabel by keyboardPrefs.keyboard.spaceBarCustomLabel.observeAsState()
    val raw by keyboardPrefs.localization.subtypes.observeAsState()
    val activeId by keyboardPrefs.localization.activeSubtypeId.observeAsState()
    val language = remember(raw, activeId) { ReboardLanguages.decode(raw, activeId).firstOrNull { it.active }?.name ?: "English (United States)" }
    // Typed locally so saving on each keystroke never moves the cursor.
    var label by rememberSaveable { mutableStateOf(storedLabel) }
    val options = listOf(
        SpaceBarMode.CURRENT_LANGUAGE to "Language",
        SpaceBarMode.SPACE_BAR_KEY to "␣ Symbol",
        SpaceBarMode.NOTHING to "Blank",
        SpaceBarMode.CUSTOM_LABEL to "Custom",
    )
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ToneIcon(Icons.Filled.SpaceBar, Tone.Primary)
                Text("Space bar label", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            // A little space key showing exactly what will be printed on it.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        when (mode) {
                            SpaceBarMode.CURRENT_LANGUAGE -> language
                            SpaceBarMode.SPACE_BAR_KEY -> "␣"
                            SpaceBarMode.CUSTOM_LABEL -> label.ifBlank { " " }
                            else -> " "
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, text) ->
                    FilterChip(
                        selected = mode == value,
                        onClick = { haptics.tick(); scope.launch { keyboardPrefs.keyboard.spaceBarMode.set(value) } },
                        label = { Text(text) },
                        shape = CircleShape,
                    )
                }
            }
            AnimatedVisibility(mode == SpaceBarMode.CUSTOM_LABEL, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { value ->
                        label = value.take(24)
                        scope.launch { keyboardPrefs.keyboard.spaceBarCustomLabel.set(label) }
                    },
                    label = { Text("Your label") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** How predictions sit above the keys: three fixed slots, or sized to each word. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestionLayoutCard(shape: Shape) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val mode by keyboardPrefs.suggestion.displayMode.observeAsState()
    val options = listOf(
        CandidatesDisplayMode.CLASSIC to "3 slots",
        CandidatesDisplayMode.DYNAMIC to "Dynamic",
        CandidatesDisplayMode.DYNAMIC_SCROLLABLE to "Scroll",
    )
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ToneIcon(Icons.Filled.ViewWeek, Tone.Tertiary)
                Column(Modifier.weight(1f)) {
                    Text("Suggestion layout", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (mode) {
                            CandidatesDisplayMode.CLASSIC -> "Three equal slots, like a classic keyboard"
                            CandidatesDisplayMode.DYNAMIC -> "Each word gets just the room it needs"
                            else -> "Sized to each word, and swipe for more"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                options.forEachIndexed { index, (value, text) ->
                    ToggleButton(
                        checked = mode == value,
                        onCheckedChange = { haptics.tick(); scope.launch { keyboardPrefs.suggestion.displayMode.set(value) } },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        colors = com.noxquill.rewordium.app.ui.segmentColors(),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) { Text(text, maxLines = 1) }
                }
            }
        }
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

@Composable
internal fun PrefSwitch(pref: PreferenceData<Boolean>, icon: ImageVector, title: String, subtitle: String, shape: Shape, tone: Tone) {
    val scope = rememberCoroutineScope()
    val value by pref.observeAsState()
    SettingSwitchRow(icon, title, subtitle, value, shape, tone) { on -> scope.launch { pref.set(on) } }
}

/** A slider that only writes when the finger lifts, with its value in Space Mono. */
@Composable
internal fun PrefSlider(
    pref: PreferenceData<Int>,
    icon: ImageVector,
    title: String,
    range: IntRange,
    step: Int,
    shape: Shape,
    tone: Tone,
    format: (Int) -> String,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val stored by pref.observeAsState()
    var local by remember(stored) { mutableFloatStateOf(stored.toFloat()) }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ToneIcon(icon, tone)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(format(local.roundToInt()), style = monoNumber(18.sp), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = local,
                onValueChange = { value ->
                    val snapped = (((value - range.first) / step).roundToInt() * step + range.first).toFloat()
                    if (snapped != local) haptics.tick()
                    local = snapped
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                onValueChangeFinished = { scope.launch { pref.set(local.roundToInt()) } },
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}
