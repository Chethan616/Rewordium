package com.noxquill.rewordium.app.screens.themes

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.jade.JadeController
import com.noxquill.rewordium.app.screens.AccentPicker
import com.noxquill.rewordium.app.screens.PrefSlider
import com.noxquill.rewordium.app.screens.PrefSwitch
import com.noxquill.rewordium.app.screens.SpaceBarCard
import com.noxquill.rewordium.app.screens.keyboardPrefs
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.SectionLabel
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.avatar.BOT_TYPES
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.app.ui.monoLabel
import com.noxquill.rewordium.app.ui.monoNumber
import com.noxquill.rewordium.app.ui.segmentColors
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.data.RewordiumRepository
import com.noxquill.rewordium.data.themes.CommunityThemes
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyMotion
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyPressAnimation
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeSpec
import com.noxquill.rewordium.keyboard.ime.theme.studio.ThemePacks
import com.noxquill.rewordium.keyboard.ime.theme.studio.ThemeStudio
import com.noxquill.rewordium.keyboard.ime.theme.studio.keyPressMotion
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Where a theme in the shop comes from, which decides what its sheet offers. */
sealed interface ShopItem {
    val spec: KeyboardThemeSpec
    data class Curated(override val spec: KeyboardThemeSpec) : ShopItem
    data class FromJade(override val spec: KeyboardThemeSpec) : ShopItem
    data class Community(val shared: CommunityThemes.Shared) : ShopItem { override val spec get() = shared.spec }
    data class Installed(val theme: ThemeStudio.InstalledTheme) : ShopItem { override val spec get() = theme.spec }
}

/**
 * The Theme shop: curated packs (playful, expressive, professional, clean Android), themes from the
 * community, themes Jade designs from a description, your own, and every appearance setting. Each theme
 * previews live (press the keys) and can be tried on the real keyboard before it's installed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeShopScreen(
    jade: JadeController,
    authorName: String?,
    onBack: () -> Unit,
    onOpenBuilder: (KeyboardThemeSpec?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val pressStyle by keyboardPrefs.theme.keyPressAnimation.observeAsState()
    val pressStrength by keyboardPrefs.theme.keyPressStrength.observeAsState()
    val motion = remember(pressStyle, pressStrength) { KeyMotion(pressStyle, pressStrength / 100f) }

    var detail by remember { mutableStateOf<ShopItem?>(null) }
    var installedVersion by remember { mutableIntStateOf(0) }
    val installed = remember { mutableStateListOf<ThemeStudio.InstalledTheme>() }
    LaunchedEffect(installedVersion) {
        val list = ThemeStudio.installed(context)
        installed.clear()
        installed.addAll(list)
    }

    var sort by rememberSaveable { mutableStateOf(CommunityThemes.Sort.Popular) }
    var communityVersion by remember { mutableIntStateOf(0) }
    var community by remember { mutableStateOf<Result<List<CommunityThemes.Shared>>?>(null) }
    LaunchedEffect(sort, communityVersion) {
        community = null
        community = CommunityThemes.list(sort)
    }

    ExpressiveScaffold(title = "theme shop_", subtitle = "Looks · Motion · Mascots", onBack = onBack, heading = Heading.Mono) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(key = "hero") {
                HeroCard(
                    motion = motion,
                    onBuild = { haptics.confirm(); onOpenBuilder(null) },
                    onSurprise = {
                        haptics.hop()
                        detail = ShopItem.Curated(ThemePacks.all.flatMap { it.themes }.random())
                    },
                    modifier = Modifier.enterStagger(0),
                )
            }
            item(key = "jade") {
                JadeThemeCard(jade = jade, onDesigned = { detail = ShopItem.FromJade(it) }, modifier = Modifier.padding(top = 20.dp).enterStagger(1))
            }
            ThemePacks.all.forEachIndexed { index, pack ->
                item(key = "pack-${pack.id}") {
                    Column(Modifier.padding(top = 24.dp).enterStagger(2 + index)) {
                        SectionLabel(pack.title, Modifier.padding(bottom = 2.dp))
                        Text(pack.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                        ThemeRow(pack.themes.map { ShopItem.Curated(it) }) { detail = it }
                    }
                }
            }
            item(key = "community") {
                Column(Modifier.padding(top = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Community", Modifier.weight(1f))
                        val sorts = CommunityThemes.Sort.entries
                        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            sorts.forEachIndexed { i, value ->
                                ToggleButton(
                                    checked = sort == value,
                                    onCheckedChange = { haptics.tick(); sort = value },
                                    colors = segmentColors(),
                                    shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                ) { Text(value.name) }
                            }
                        }
                    }
                    Text("Themes people made and shared. Preview any of them before you get it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                    AnimatedContent(targetState = community, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "community") { state ->
                        when {
                            state == null -> Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                            state.isFailure -> EmptyNote(state.exceptionOrNull()?.message ?: "Couldn’t load community themes.", "Try again") { communityVersion++ }
                            state.getOrThrow().isEmpty() -> EmptyNote("No shared themes yet. Build one and be the first!", "Build a theme") { onOpenBuilder(null) }
                            else -> ThemeRow(state.getOrThrow().map { ShopItem.Community(it) }) { detail = it }
                        }
                    }
                }
            }
            if (installed.isNotEmpty()) item(key = "mine") {
                Column(Modifier.padding(top = 24.dp)) {
                    SectionLabel("Your themes", Modifier.padding(bottom = 10.dp))
                    ThemeRow(installed.map { ShopItem.Installed(it) }) { detail = it }
                }
            }
            item(key = "customize-motion") { MotionSection(Modifier.padding(top = 28.dp)) }
            item(key = "customize-mascot") { MascotSection(Modifier.padding(top = 24.dp)) }
            item(key = "customize-layout") { LayoutSection(Modifier.padding(top = 24.dp)) }
            item(key = "customize-keys") { KeysSection(Modifier.padding(top = 24.dp)) }
        }
    }

    detail?.let { item ->
        ThemeSheet(
            item = item,
            motion = motion,
            authorName = authorName,
            onDismiss = { detail = null },
            onEdit = { detail = null; onOpenBuilder(item.spec) },
            onChanged = { installedVersion++; communityVersion++ },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(motion: KeyMotion, onBuild: () -> Unit, onSurprise: () -> Unit, modifier: Modifier) {
    // The hero keyboard cycles through the packs so the shop opens alive.
    val showcase = remember { ThemePacks.all.map { it.themes.first() } }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_200)
            index = (index + 1) % showcase.size
        }
    }
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Make it yours", style = MaterialTheme.typography.headlineSmall)
                    Text("Colours, key shapes that morph, bouncy keys and a buddy on the space bar.", style = MaterialTheme.typography.bodyMedium)
                }
                BotAvatar(type = showcase[index].mascot ?: "star", mood = BotMood.Happy, size = 56.dp, label = null)
            }
            AnimatedContent(targetState = showcase[index], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hero-theme") { spec ->
                KeyboardMock(spec, Modifier.fillMaxWidth(), interactive = true, motion = motion)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBuild, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Brush, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Build a theme")
                }
                FilledTonalButton(onClick = onSurprise) {
                    Icon(Icons.Filled.Casino, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Surprise me")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JadeThemeCard(jade: JadeController, onDesigned: (KeyboardThemeSpec) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var prompt by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ideas = listOf("cosy autumn café", "neon arcade night", "calm ocean morning", "strawberry milkshake", "minimal paper")
    fun design(text: String) {
        if (text.isBlank() || busy) return
        busy = true
        error = null
        haptics.tick()
        scope.launch {
            runCatching { jade.designTheme(text) }
                .onSuccess { haptics.success(); onDesigned(it) }
                .onFailure { error = it.message ?: "Jade couldn’t design that one. Try again." }
            busy = false
        }
    }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotAvatar(type = "cloud", mood = if (busy) BotMood.Working else BotMood.Idle, size = 48.dp, label = "Jade")
                Column(Modifier.weight(1f)) {
                    Text("Make it with Jade", style = MaterialTheme.typography.titleMedium)
                    Text("Describe a mood, a place or a team. Jade picks the colours, shapes, font and mascot.", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it.take(200) },
                placeholder = { Text("e.g. sunset at the beach") },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(50),
                colors = run {
                    // On the tertiary card: text, hint and outline in the card's own ink, not the theme's light purple.
                    val ink = MaterialTheme.colorScheme.onTertiaryContainer
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ink,
                        unfocusedTextColor = ink,
                        disabledTextColor = ink.copy(alpha = 0.7f),
                        focusedPlaceholderColor = ink.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = ink.copy(alpha = 0.6f),
                        focusedBorderColor = ink,
                        unfocusedBorderColor = ink.copy(alpha = 0.55f),
                        disabledBorderColor = ink.copy(alpha = 0.3f),
                        cursorColor = ink,
                        focusedTrailingIconColor = ink,
                        unfocusedTrailingIconColor = ink,
                        disabledTrailingIconColor = ink.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { design(prompt) }),
                trailingIcon = {
                    if (busy) LoadingIndicator(Modifier.size(32.dp))
                    else IconButton(onClick = { design(prompt) }, enabled = prompt.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Design it") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ideas) { idea ->
                    Surface(
                        onClick = { prompt = idea; design(idea) },
                        enabled = !busy,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(idea, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            AnimatedVisibility(error != null) {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ThemeRow(items: List<ShopItem>, onOpen: (ShopItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(items, key = { item ->
            when (item) {
                is ShopItem.Community -> "c-" + item.shared.id
                is ShopItem.Installed -> "i-" + item.theme.componentId
                else -> "t-" + item.spec.name
            }
        }) { item -> ThemeCard(item) { onOpen(item) } }
    }
}

@Composable
private fun ThemeCard(item: ShopItem, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Surface(
        onClick = { haptics.tick(); onClick() },
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(196.dp).semantics { contentDescription = "${item.spec.name} keyboard theme" },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardMock(item.spec, Modifier.fillMaxWidth(), compact = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.spec.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when (item) {
                        is ShopItem.Community -> "by ${item.shared.author} · ${item.shared.downloads} ↓"
                        is ShopItem.Installed -> "Installed"
                        else -> if (item.spec.night) "Dark" else "Light"
                    }
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                item.spec.mascot?.let { BotAvatar(type = it, mood = BotMood.Idle, size = 28.dp, still = true, interactive = false, label = null) }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String, action: String, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotAvatar(type = "ghost", mood = BotMood.NeedsYou, size = 44.dp, still = true, label = null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

// ── A theme's sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ThemeSheet(
    item: ShopItem,
    motion: KeyMotion,
    authorName: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val spec = item.spec
    var trying by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var tryText by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // The sheet's own scope is cancelled by the time it is disposed, so the preview must end detached;
    // a preview left running would pin the keyboard's look and ignore the light/dark choice.
    DisposableEffect(Unit) { onDispose { ThemeStudio.endPreviewDetached(context) } }
    LaunchedEffect(trying) {
        if (trying) {
            if (ThemeStudio.preview(context, spec)) runCatching { focus.requestFocus() }
            else { trying = false; Toast.makeText(context, "The keyboard engine isn’t available.", Toast.LENGTH_SHORT).show() }
        } else {
            ThemeStudio.endPreview(context)
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.name, style = MaterialTheme.typography.headlineSmall)
                    val by = when (item) {
                        is ShopItem.Community -> "by ${item.shared.author} · ${item.shared.downloads} downloads"
                        is ShopItem.FromJade -> "Designed by Jade just now"
                        is ShopItem.Installed -> "On this phone"
                        else -> "By Rewordium"
                    }
                    Text(by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                spec.mascot?.let { BotAvatar(type = it, mood = BotMood.Happy, size = 52.dp, label = null) }
            }
            KeyboardMock(spec, Modifier.fillMaxWidth(), interactive = true, motion = motion)
            Text("Press the keys to feel them.", style = monoLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (spec.night) "Dark" else "Light")
                Tag("${spec.shape.label} keys")
                spec.pressedShape?.let { Tag("Morphs to ${it.label.lowercase()}") }
                Tag("${spec.font.label} type")
                if (spec.outline) Tag("Outlined")
                spec.mascot?.let { Tag("Mascot: $it") }
            }
            AnimatedVisibility(trying, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OutlinedTextField(
                    value = tryText,
                    onValueChange = { tryText = it },
                    label = { Text("Type here — this is the real keyboard") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    haptics.confirm()
                    scope.launch {
                        ThemeStudio.endPreview(context)
                        val result = when (item) {
                            is ShopItem.Installed -> runCatching { ThemeStudio.apply(context, item.theme.componentId, spec); item.theme.componentId }
                            else -> ThemeStudio.installAndApply(context, spec)
                        }
                        busy = false
                        result.onSuccess {
                            if (item is ShopItem.Community) CommunityThemes.countDownload(item.shared.id)
                            haptics.success()
                            toast("${spec.name} is on your keyboard")
                            onChanged()
                            onDismiss()
                        }.onFailure { toast(it.message ?: "Couldn’t apply this theme.") }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(if (item is ShopItem.Community) Icons.Filled.Download else Icons.Filled.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(if (item is ShopItem.Community) "Get it and use it" else "Use this theme")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { haptics.tick(); trying = !trying }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Keyboard, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (trying) "Stop trying" else "Try on keyboard")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remix")
                }
            }
            val canShare = item is ShopItem.Installed || item is ShopItem.FromJade
            if (canShare || (item is ShopItem.Community && item.shared.mine) || item is ShopItem.Installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (canShare) TextButton(onClick = {
                        if (RewordiumRepository.currentUser == null) { toast("Sign in to share themes."); return@TextButton }
                        scope.launch {
                            CommunityThemes.publish(spec, authorName ?: "Someone")
                                .onSuccess { haptics.success(); toast("Shared with the community"); onChanged() }
                                .onFailure { toast(it.message ?: "Couldn’t share it.") }
                        }
                    }) {
                        Icon(Icons.Filled.CloudUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share to community")
                    }
                    if (item is ShopItem.Community && item.shared.mine) TextButton(onClick = {
                        scope.launch {
                            CommunityThemes.delete(item.shared.id).onSuccess { toast("Removed from the community"); onChanged(); onDismiss() }
                                .onFailure { toast(it.message ?: "Couldn’t remove it.") }
                        }
                    }) { Text("Unshare") }
                    if (item is ShopItem.Installed) TextButton(onClick = {
                        scope.launch {
                            runCatching { ThemeStudio.delete(context, item.theme.componentId) }
                                .onSuccess { toast("Theme removed"); onChanged(); onDismiss() }
                                .onFailure { toast(it.message ?: "Couldn’t remove it.") }
                        }
                    }) {
                        Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

// ── Customize: everything that used to be Keys → Appearance, and more ─────

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MotionSection(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val accent by keyboardPrefs.theme.accentColor.observeAsState()
    val style by keyboardPrefs.theme.keyPressAnimation.observeAsState()
    val strength by keyboardPrefs.theme.keyPressStrength.observeAsState()
    val count = 3
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Customize", Modifier.padding(bottom = 6.dp))
        Surface(shape = segmentShape(0, count), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Key colour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                AccentPicker(accent) { color -> haptics.tick(); scope.launch { keyboardPrefs.theme.accentColor.set(color) } }
            }
        }
        Surface(shape = segmentShape(1, count), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToneIcon(Icons.Filled.TouchApp, Tone.Tertiary)
                    Column(Modifier.weight(1f)) {
                        Text("Key press", style = MaterialTheme.typography.titleSmall)
                        Text("How keys move under your thumb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyPressAnimation.entries.forEach { value ->
                        ToggleButton(
                            checked = style == value,
                            onCheckedChange = { haptics.tick(); scope.launch { keyboardPrefs.theme.keyPressAnimation.set(value) } },
                            colors = segmentColors(),
                        ) { Text(value.label) }
                    }
                }
                MotionDemo(KeyMotion(style, strength / 100f))
            }
        }
        PrefSlider(keyboardPrefs.theme.keyPressStrength, Icons.Filled.Speed, "Animation strength", 25..150, 5, segmentShape(2, count), Tone.Primary) { "$it%" }
    }
}

/** Five sample keys that ripple through the chosen animation whenever it changes, and press on tap. */
@Composable
private fun MotionDemo(motion: KeyMotion) {
    val haptics = LocalHaptics.current
    val pressed = remember { mutableStateListOf(false, false, false, false, false) }
    LaunchedEffect(motion) {
        for (i in pressed.indices) {
            pressed[i] = true
            delay(70)
            pressed[i] = false
            delay(40)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("h", "e", "l", "l", "o").forEachIndexed { i, label ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (pressed[i]) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (pressed[i]) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .keyPressMotion(pressed[i], motion)
                    .pressState { down -> pressed[i] = down; if (down) haptics.tick() },
            ) {
                Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

/** Reports press and release, for keys that animate while held. */
private fun Modifier.pressState(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onPress = {
        onChange(true)
        tryAwaitRelease()
        onChange(false)
    })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MascotSection(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val mascot by keyboardPrefs.theme.mascot.observeAsState()
    val count = 3
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Mascot", Modifier.padding(bottom = 6.dp))
        Surface(shape = segmentShape(0, count), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToneIcon(Icons.Filled.Pets, Tone.Secondary)
                    Column(Modifier.weight(1f)) {
                        Text("A buddy on the space bar", style = MaterialTheme.typography.titleSmall)
                        Text(if (mascot.isBlank()) "None — pick one below" else "Your keyboard’s ${mascot}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "none") {
                        MascotChoice(selected = mascot.isBlank(), label = "None", onClick = { haptics.tick(); scope.launch { keyboardPrefs.theme.mascot.set("") } }) {
                            Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(BOT_TYPES, key = { it }) { type ->
                        MascotChoice(selected = mascot == type, label = type, onClick = { haptics.hop(); scope.launch { keyboardPrefs.theme.mascot.set(type) } }) {
                            BotAvatar(type = type, mood = if (mascot == type) BotMood.Happy else BotMood.Idle, size = 46.dp, still = mascot != type, interactive = false, label = null)
                        }
                    }
                }
            }
        }
        PrefSwitch(keyboardPrefs.theme.mascotReactions, Icons.Filled.AutoAwesome, "Reactions", "Hops and grins when you hit space, naps when you stop", segmentShape(1, count), Tone.Tertiary)
        PrefSwitch(keyboardPrefs.theme.mascotLively, Icons.Filled.Visibility, "Lively", "Blinks and looks around (uses a little more battery)", segmentShape(2, count), Tone.Primary)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MascotChoice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape: Shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(22.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(64.dp).semantics { contentDescription = "$label mascot"; this.selected = selected },
        ) { Box(contentAlignment = Alignment.Center) { content() } }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LayoutSection(modifier: Modifier) {
    val count = 5
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Size & spacing", Modifier.padding(bottom = 6.dp))
        PrefSlider(keyboardPrefs.keyboard.heightFactorPortrait, Icons.Filled.Height, "Keyboard height", 50..150, 5, segmentShape(0, count), Tone.Tertiary) { "$it%" }
        PrefSlider(keyboardPrefs.keyboard.fontSizeMultiplierPortrait, Icons.Filled.FontDownload, "Key text size", 50..150, 5, segmentShape(1, count), Tone.Secondary) { "$it%" }
        FloatPrefSlider(keyboardPrefs.keyboard.keySpacingHorizontal, Icons.Filled.ViewWeek, "Space between keys", 0f..10f, 0.5f, segmentShape(2, count), Tone.Primary)
        FloatPrefSlider(keyboardPrefs.keyboard.keySpacingVertical, Icons.Filled.SwapVert, "Space between rows", 0f..10f, 0.5f, segmentShape(3, count), Tone.Tertiary)
        PrefSlider(keyboardPrefs.keyboard.bottomOffsetPortrait, Icons.Filled.VerticalAlignTop, "Lift from the bottom", 0..60, 1, segmentShape(4, count), Tone.Secondary) { "$it dp" }
    }
}

@Composable
private fun KeysSection(modifier: Modifier) {
    val roundedBar by keyboardPrefs.keyboard.experimentalRoundedSmartbar.observeAsState()
    val count = if (roundedBar) 11 else 10
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Keys", Modifier.padding(bottom = 6.dp))
        SpaceBarCard(segmentShape(0, count))
        PrefSwitch(keyboardPrefs.keyboard.numberRow, Icons.Filled.Pin, "Number row", "Numbers above the letters", segmentShape(1, count), Tone.Primary)
        PrefSwitch(keyboardPrefs.keyboard.hintedNumberRowEnabled, Icons.Filled.Pin, "Number hints", "Long-press the top row for digits", segmentShape(2, count), Tone.Tertiary)
        PrefSwitch(keyboardPrefs.keyboard.hintedSymbolsEnabled, Icons.Filled.Widgets, "Symbol hints", "Long-press letters for symbols", segmentShape(3, count), Tone.Secondary)
        PrefSwitch(keyboardPrefs.keyboard.utilityKeyEnabled, Icons.Filled.Tune, "Utility key", "An extra key beside space", segmentShape(4, count), Tone.Primary)
        PrefSwitch(keyboardPrefs.keyboard.popupEnabled, Icons.Filled.TouchApp, "Key pop-ups", "Show the letter as you tap it", segmentShape(5, count), Tone.Tertiary)
        PrefSwitch(keyboardPrefs.keyboard.mergeHintPopupsEnabled, Icons.Filled.Widgets, "One long-press menu", "Hints and accents together", segmentShape(6, count), Tone.Secondary)
        PrefSlider(keyboardPrefs.keyboard.longPressDelay, Icons.Filled.Timer, "Long-press delay", 100..700, 10, segmentShape(7, count), Tone.Primary) { "$it ms" }
        PrefSwitch(keyboardPrefs.keyboard.oneHandedModeEnabled, Icons.Filled.PanTool, "One-handed mode", "Shrink the keyboard to one side", segmentShape(8, count), Tone.Tertiary)
        PrefSwitch(keyboardPrefs.keyboard.experimentalRoundedSmartbar, Icons.Filled.RoundedCorner, "Rounded suggestion bar", "A soft, floating bar above the keys", segmentShape(9, count), Tone.Secondary)
        AnimatedVisibility(roundedBar, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            PrefSlider(keyboardPrefs.keyboard.experimentalRoundedSmartbarRadius, Icons.Filled.RoundedCorner, "Bar roundness", 4..48, 1, segmentShape(10, count), Tone.Secondary) { "$it dp" }
        }
    }
}

@Composable
private fun FloatPrefSlider(pref: PreferenceData<Float>, icon: ImageVector, title: String, range: ClosedFloatingPointRange<Float>, step: Float, shape: Shape, tone: Tone) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val stored by pref.observeAsState()
    var local by remember(stored) { mutableFloatStateOf(stored) }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ToneIcon(icon, tone)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("%.1f dp".format(local), style = monoNumber(18.sp), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = local,
                onValueChange = { value ->
                    val snapped = ((value / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
                    if (snapped != local) haptics.tick()
                    local = snapped
                },
                valueRange = range,
                onValueChangeFinished = { scope.launch { pref.set(local) } },
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}
