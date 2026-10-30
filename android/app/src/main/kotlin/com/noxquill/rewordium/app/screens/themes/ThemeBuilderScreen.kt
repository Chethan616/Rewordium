package com.noxquill.rewordium.app.screens.themes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.screens.keyboardPrefs
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.ExpressiveSwitch
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.SectionLabel
import com.noxquill.rewordium.app.ui.avatar.BOT_TYPES
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.app.ui.segmentColors
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyFont
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyMotion
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyShape
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeSpec
import com.noxquill.rewordium.keyboard.ime.theme.studio.ThemePacks
import com.noxquill.rewordium.keyboard.ime.theme.studio.ThemeStudio
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The theme the builder opens with (a theme to remix), set just before navigating to it. */
object ThemeDraft {
    var initial: KeyboardThemeSpec? = null
}

private enum class Slot(val label: String) { Background("Background"), Keys("Keys"), Letters("Letters"), Accent("Accent"), Special("Function keys") }

/**
 * Build a keyboard theme by hand: colours, key shape and what it morphs into when pressed, typeface,
 * depth, outline and mascot — all on a live keyboard you can press. Saving installs it as a real
 * keyboard theme and switches to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeBuilderScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val pressStyle by keyboardPrefs.theme.keyPressAnimation.observeAsState()
    val pressStrength by keyboardPrefs.theme.keyPressStrength.observeAsState()
    val motion = remember(pressStyle, pressStrength) { KeyMotion(pressStyle, pressStrength / 100f) }
    var spec by remember {
        mutableStateOf(
            ThemeDraft.initial?.let { it.copy(name = if (it.author == "Rewordium" || it.author == "Jade") "${it.name} remix" else it.name, author = "") }
                ?: ThemePacks.playful.themes.first().copy(name = "My theme", author = ""),
        )
    }
    var editing by remember { mutableStateOf<Slot?>(null) }
    var saving by remember { mutableStateOf(false) }
    var makeTwin by remember { mutableStateOf(false) }

    ExpressiveScaffold(
        title = "theme builder_",
        subtitle = "Press the keys to try it",
        onBack = { ThemeDraft.initial = null; onBack() },
        heading = Heading.Mono,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { haptics.hop(); spec = surprise(spec) }) {
                        Icon(Icons.Filled.Casino, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Surprise me")
                    }
                    Button(
                        onClick = {
                            if (saving) return@Button
                            saving = true
                            haptics.confirm()
                            scope.launch {
                                val result = ThemeStudio.installAndApply(context, spec.copy(author = "You"))
                                if (makeTwin) runCatching { ThemeStudio.install(context, invertedTwin(spec).copy(author = "You")) }
                                saving = false
                                result.onSuccess {
                                    haptics.success()
                                    val message = if (makeTwin) "${spec.name} is on your keyboard, with a ${if (spec.night) "light" else "dark"} twin saved too" else "${spec.name} is on your keyboard"
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    ThemeDraft.initial = null
                                    onSaved()
                                }.onFailure { Toast.makeText(context, it.message ?: "Couldn’t save the theme.", Toast.LENGTH_SHORT).show() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (saving) "Saving…" else "Save & use")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            KeyboardMock(spec, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), interactive = true, motion = motion)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item(key = "basics") { Basics(spec, makeTwin, { makeTwin = it }) { spec = it } }
                item(key = "colours") { Colours(spec) { editing = it } }
                item(key = "shape") { Shapes(spec) { spec = it } }
                item(key = "type") { Type(spec) { spec = it } }
                item(key = "mascot") { MascotPicker(spec) { spec = it } }
            }
        }
    }

    editing?.let { slot ->
        ColorSheet(
            title = slot.label,
            current = when (slot) {
                Slot.Background -> spec.background
                Slot.Keys -> spec.keys
                Slot.Letters -> spec.letters
                Slot.Accent -> spec.accent
                Slot.Special -> spec.special ?: KeyboardThemeSpec.mix(spec.keys, spec.accent, 0.25)
            },
            onPick = { hex ->
                spec = when (slot) {
                    Slot.Background -> spec.copy(background = hex)
                    Slot.Keys -> spec.copy(keys = hex)
                    Slot.Letters -> spec.copy(letters = hex)
                    Slot.Accent -> spec.copy(accent = hex)
                    Slot.Special -> spec.copy(special = hex)
                }
            },
            onAuto = if (slot == Slot.Special) ({ spec = spec.copy(special = null) }) else null,
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Basics(spec: KeyboardThemeSpec, makeTwin: Boolean, onMakeTwinChange: (Boolean) -> Unit, onChange: (KeyboardThemeSpec) -> Unit) {
    val haptics = LocalHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Basics", Modifier.padding(bottom = 6.dp, top = 8.dp))
        Surface(shape = segmentShape(0, 2), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = spec.name,
                    onValueChange = { onChange(spec.copy(name = it.take(40))) },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    listOf(false to "Light", true to "Dark").forEachIndexed { i, (night, label) ->
                        ToggleButton(
                            checked = spec.night == night,
                            onCheckedChange = {
                                haptics.tick()
                                // This only used to flip which slot the theme saves to, not how it looks —
                                // so picking "Light" on a theme you'd built dark kept the dark preview right
                                // under the new label, which read as broken. If the picked mode and the
                                // colours disagree, bring the colours along too.
                                var next = spec.copy(night = night)
                                if (isDarkHex(spec.background) != night) {
                                    next = next.copy(background = recolorForMode(spec.background, night), keys = recolorForMode(spec.keys, night))
                                    next = next.copy(letters = KeyboardThemeSpec.textOn(next.keys), special = null)
                                }
                                onChange(next)
                            },
                            colors = segmentColors(),
                            shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(if (night) Icons.Filled.DarkMode else Icons.Filled.LightMode, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label)
                        }
                    }
                }
                // Every keyboard theme is either a light look or a dark one — there's no single theme that's
                // "both" (the keyboard shows one installed theme per mode). This is which one you're building;
                // "Save & use" puts it in that slot and switches your keyboard to match if it wasn't already.
                Text(
                    if (spec.night) "A dark look. Saving puts it in your dark slot and switches your keyboard to dark, if it wasn't already."
                    else "A light look. Saving puts it in your light slot and switches your keyboard to light, if it wasn't already.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(shape = segmentShape(1, 2), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Also make the ${if (spec.night) "light" else "dark"} twin", style = MaterialTheme.typography.titleSmall)
                    Text("Same colours, inverted — saved for the ${if (spec.night) "light" else "dark"} slot too, so switching modes doesn't lose the look.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExpressiveSwitch(makeTwin) { haptics.tick(); onMakeTwinChange(it) }
            }
        }
    }
}

/** True when [hex] reads as a dark colour (the same brightness axis [recolorForMode] works on). */
private fun isDarkHex(hex: String): Boolean {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(hexColor(hex).toArgb(), hsv)
    return hsv[2] < 0.5f
}

/** [hex] pushed to [night]'s brightness range, hue and roughly its saturation kept. */
private fun recolorForMode(hex: String, night: Boolean): String {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(hexColor(hex).toArgb(), hsv)
    val value = if (night) (hsv[2] * 0.22f).coerceIn(0.08f, 0.30f) else (1f - (1f - hsv[2]) * 0.22f).coerceIn(0.85f, 1f)
    val saturation = if (night) hsv[1].coerceAtLeast(0.35f) else hsv[1] * 0.3f
    return "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation, value)) and 0xFFFFFF)
}

/**
 * The same theme for the opposite mode: hue and the accent stay put, but how light or dark the background
 * and keys are flips, and the letters and function-key tint are rederived so they still read.
 */
private fun invertedTwin(spec: KeyboardThemeSpec): KeyboardThemeSpec {
    val night = !spec.night
    val keys = recolorForMode(spec.keys, night)
    return spec.copy(
        name = spec.name,
        night = night,
        background = recolorForMode(spec.background, night),
        keys = keys,
        letters = KeyboardThemeSpec.textOn(keys),
        special = null,
    )
}

@Composable
private fun Colours(spec: KeyboardThemeSpec, onEdit: (Slot) -> Unit) {
    val haptics = LocalHaptics.current
    val slots = Slot.entries
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Colours", Modifier.padding(bottom = 6.dp, top = 20.dp))
        slots.forEachIndexed { i, slot ->
            val hex = when (slot) {
                Slot.Background -> spec.background
                Slot.Keys -> spec.keys
                Slot.Letters -> spec.letters
                Slot.Accent -> spec.accent
                Slot.Special -> spec.special
            }
            Surface(onClick = { haptics.tick(); onEdit(slot) }, shape = segmentShape(i, slots.size + if (spec.lettersReadable) 0 else 1), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(36.dp).background(hexColor(hex ?: KeyboardThemeSpec.mix(spec.keys, spec.accent, 0.25)), CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    Text(slot.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(hex ?: "Auto", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!spec.lettersReadable) {
            Surface(shape = segmentShape(slots.size, slots.size + 1), color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Letters are hard to read on these keys.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = { haptics.tick(); onEdit(Slot.Letters) }) { Text("Fix") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Shapes(spec: KeyboardThemeSpec, onChange: (KeyboardThemeSpec) -> Unit) {
    val haptics = LocalHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Shape & depth", Modifier.padding(bottom = 6.dp, top = 20.dp))
        Surface(shape = segmentShape(0, 3), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Keys", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                ShapeRow(selected = spec.shape, accent = spec.accent) { picked -> if (picked != null) { haptics.tick(); onChange(spec.copy(shape = picked)) } }
                Text("When pressed, morph into", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                ShapeRow(selected = spec.pressedShape, accent = spec.accent, allowNone = true) { haptics.tick(); onChange(spec.copy(pressedShape = it)) }
            }
        }
        Surface(shape = segmentShape(1, 3), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Depth", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(listOf("Flat", "Soft", "Raised", "High", "Floating")[spec.shadow.coerceIn(0, 4)], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = spec.shadow.toFloat(),
                    onValueChange = { v -> val n = v.toInt(); if (n != spec.shadow) { haptics.tick(); onChange(spec.copy(shadow = n)) } },
                    valueRange = 0f..4f,
                    steps = 3,
                    modifier = Modifier.semantics { contentDescription = "Key depth" },
                )
            }
        }
        Surface(shape = segmentShape(2, 3), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Outline", style = MaterialTheme.typography.titleSmall)
                    Text("A thin line around every key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExpressiveSwitch(spec.outline) { haptics.tick(); onChange(spec.copy(outline = it)) }
            }
        }
    }
}

@Composable
private fun ShapeRow(selected: KeyShape?, accent: String, allowNone: Boolean = false, onPick: (KeyShape?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowNone) item(key = "same") {
            ChoiceTile("Same", selected == null, onClick = { onPick(null) }) {
                Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(KeyShape.entries, key = { it.name }) { shape ->
            ChoiceTile(shape.label, selected == shape, onClick = { onPick(shape) }) {
                Box(Modifier.size(width = 28.dp, height = 36.dp).background(hexColor(accent), shape.composeShape()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceTile(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape: Shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(20.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(62.dp).semantics { contentDescription = label; this.selected = selected },
        ) { Box(contentAlignment = Alignment.Center) { content() } }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Type(spec: KeyboardThemeSpec, onChange: (KeyboardThemeSpec) -> Unit) {
    val haptics = LocalHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Type", Modifier.padding(bottom = 6.dp, top = 20.dp))
        Surface(shape = segmentShape(0, 2), color = MaterialTheme.colorScheme.surfaceContainer) {
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(KeyFont.entries, key = { it.name }) { font ->
                    ChoiceTile(font.label, spec.font == font, onClick = { haptics.tick(); onChange(spec.copy(font = font)) }) {
                        Text("Aa", style = TextStyle(fontFamily = font.composeFamily(), fontWeight = if (spec.bold) FontWeight.Bold else FontWeight.Medium, fontSize = 22.sp))
                    }
                }
            }
        }
        Surface(shape = segmentShape(1, 2), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bold letters", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                ExpressiveSwitch(spec.bold) { haptics.tick(); onChange(spec.copy(bold = it)) }
            }
        }
    }
}

@Composable
private fun MascotPicker(spec: KeyboardThemeSpec, onChange: (KeyboardThemeSpec) -> Unit) {
    val haptics = LocalHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("Mascot", Modifier.padding(bottom = 6.dp, top = 20.dp))
        Surface(shape = segmentShape(0, 1), color = MaterialTheme.colorScheme.surfaceContainer) {
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "none") {
                    ChoiceTile("None", spec.mascot == null, onClick = { haptics.tick(); onChange(spec.copy(mascot = null)) }) {
                        Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(BOT_TYPES, key = { it }) { type ->
                    ChoiceTile(type, spec.mascot == type, onClick = { haptics.hop(); onChange(spec.copy(mascot = type)) }) {
                        BotAvatar(type = type, mood = if (spec.mascot == type) BotMood.Happy else BotMood.Idle, size = 44.dp, still = spec.mascot != type, interactive = false, label = null)
                    }
                }
            }
        }
    }
}

// ── Colour picking ──────────────────────────────────────────────────────────

private val presets = listOf(
    "#FFFFFF", "#F4F1EA", "#EDF0F5", "#FFE4F1", "#FFF4BF", "#DDF7EC", "#D3ECFF", "#E8E6FF",
    "#FF4FA3", "#FF5A3C", "#FF8A00", "#FFB400", "#C8FF3D", "#14B87A", "#0B84D9", "#7C6CFF",
    "#B07CFF", "#E07BFF", "#D4A64A", "#3B5B7A", "#2F6F5E", "#5C9DFF", "#8AB4F8", "#A8C7FA",
    "#3A3A42", "#2A2C30", "#33204F", "#1B2A40", "#1E1033", "#141218", "#0B0B0D", "#000000",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ColorSheet(title: String, current: String, onPick: (String) -> Unit, onAuto: (() -> Unit)?, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    val hsv = remember(current) { FloatArray(3).also { android.graphics.Color.colorToHSV(hexColor(current).toArgb(), it) } }
    var hue by remember(current) { mutableStateOf(hsv[0]) }
    var sat by remember(current) { mutableStateOf(hsv[1]) }
    var value by remember(current) { mutableStateOf(hsv[2]) }
    var hex by remember(current) { mutableStateOf(current) }
    fun emit() {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        val out = "#%06X".format(argb and 0xFFFFFF)
        hex = out
        onPick(out)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).background(hexColor(hex), RoundedCornerShape(18.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (onAuto != null) TextButton(onClick = { onAuto(); onDismiss() }) { Text("Auto") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 8) {
                presets.forEach { preset ->
                    val chosen = preset.equals(hex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(hexColor(preset), if (chosen) RoundedCornerShape(10.dp) else CircleShape)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, if (chosen) RoundedCornerShape(10.dp) else CircleShape)
                            .semantics { contentDescription = "Colour $preset" }
                            .then(Modifier),
                    ) {
                        Surface(onClick = { haptics.tick(); hex = preset; onPick(preset) }, color = Color.Transparent, shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
                    }
                }
            }
            LabeledSlider("Hue", hue, 0f..360f) { hue = it; emit() }
            LabeledSlider("Colourfulness", sat, 0f..1f) { sat = it; emit() }
            LabeledSlider("Brightness", value, 0f..1f) { value = it; emit() }
            OutlinedTextField(
                value = hex,
                onValueChange = { text ->
                    hex = text.take(7)
                    KeyboardThemeSpec.normalizeHex(text)?.let(onPick)
                },
                label = { Text("Hex") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

/** A random but harmonious palette: one hue for the base, a contrasting accent, readable letters. */
private fun surprise(current: KeyboardThemeSpec): KeyboardThemeSpec {
    val r = Random(System.nanoTime())
    val night = r.nextBoolean()
    val hue = r.nextFloat() * 360f
    val accentHue = (hue + listOf(150f, 180f, 210f, 30f).random(r)) % 360f
    fun hsv(h: Float, s: Float, v: Float) = "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) and 0xFFFFFF)
    val background = if (night) hsv(hue, 0.55f, 0.14f) else hsv(hue, 0.16f, 0.98f)
    val keys = if (night) hsv(hue, 0.45f, 0.26f) else hsv(hue, 0.04f, 1f)
    val accent = hsv(accentHue, 0.75f, if (night) 0.98f else 0.88f)
    val shapes = KeyShape.entries
    val shape = shapes.random(r)
    return current.copy(
        night = night,
        background = background,
        keys = keys,
        letters = KeyboardThemeSpec.textOn(keys),
        accent = accent,
        special = null,
        shape = shape,
        pressedShape = (shapes - shape).random(r),
        font = KeyFont.entries.random(r),
        bold = r.nextBoolean(),
        shadow = r.nextInt(0, 4),
        mascot = (BOT_TYPES + listOf<String?>(null)).random(r),
        outline = r.nextInt(4) == 0,
    )
}
