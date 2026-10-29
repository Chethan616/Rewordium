/*
 * Copyright (C) 2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.noxquill.rewordium.keyboard.ime.media.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.noxquill.rewordium.keyboard.ime.input.InputFeedbackController
import com.noxquill.rewordium.keyboard.ime.input.LocalInputFeedbackController
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import java.util.IdentityHashMap
import kotlin.math.floor

/*
 * The Material 3 Expressive kit for the keyboard's emoji, emoticon, GIF and sticker panels and their search
 * overlays. Colours grow out of the active keyboard theme (the panel's own background and text, seeded by
 * the enter key's accent, with the accent washed through every surface); motion is the expressive spring
 * scheme; icons are the official Material Symbols (Rounded); every decorative shape is a whole
 * MaterialShapes shape, and shapes only pass through in-between forms while they are moving.
 */

// ── Theme ────────────────────────────────────────────────────────────────────

/** Wraps a media surface in an expressive Material theme grown from the active keyboard theme. */
@Composable
fun MediaExpressiveTheme(content: @Composable () -> Unit) {
    val scheme = rememberKeyboardColorScheme()
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
private fun rememberKeyboardColorScheme(): ColorScheme {
    val base = MaterialTheme.colorScheme
    val window = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val media = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    val key = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val enter = rememberSnyggThemeQuery(
        FlorisImeUi.Key.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to KeyCode.ENTER),
    )
    val windowBg = window.background()
    val mediaBg = media.background()
    val keyBg = key.background()
    val foreground = media.foreground(default = key.foreground())
    val accent = enter.background()
    return remember(windowBg, mediaBg, keyBg, foreground, accent, base) {
        keyboardColorScheme(windowBg, mediaBg, keyBg, foreground, accent, base.primary)
    }
}

private fun keyboardColorScheme(
    windowBg: Color,
    mediaBg: Color,
    keyBg: Color,
    foreground: Color,
    accent: Color,
    fallbackAccent: Color,
): ColorScheme {
    // Themes often leave one of the panel or the window transparent: lay the panel over the window.
    val window = windowBg.takeIf { it.isSpecified && it.alpha > 0.05f }?.copy(alpha = 1f)
    val panel = when {
        mediaBg.isSpecified && mediaBg.alpha >= 0.95f -> mediaBg
        mediaBg.isSpecified && mediaBg.alpha > 0f && window != null -> mediaBg.compositeOver(window)
        window != null -> window
        keyBg.isSpecified && keyBg.alpha > 0.05f -> keyBg.copy(alpha = 1f)
        else -> Color(0xFF1C1B1F)
    }
    val isDark = panel.luminance() < 0.4f
    val onPanel = foreground.takeIf { it.isSpecified && contrast(it.copy(alpha = 1f), panel) >= 3.0 }?.copy(alpha = 1f)
        ?: if (isDark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val seed = accent.takeIf { it.isSpecified && it.alpha > 0.5f }?.copy(alpha = 1f) ?: fallbackAccent
    // Vibrant keeps the theme's hue but turns the containers up, so the panels feel like the theme.
    val generated = dynamicColorScheme(seedColor = seed, isDark = isDark, style = PaletteStyle.Vibrant)
    val primary = if (contrast(seed, panel) >= 2.2) seed else generated.primary
    val onPrimary = if (primary.luminance() > 0.45f) Color(0xFF15131A) else Color.White
    // Every surface carries a wash of the accent, so grey themes still read as "theirs".
    val washed = seed.copy(alpha = if (isDark) 0.07f else 0.05f).compositeOver(panel)
    fun tint(amount: Float) = onPanel.copy(alpha = amount).compositeOver(washed)
    return generated.copy(
        primary = primary,
        onPrimary = onPrimary,
        surfaceTint = primary,
        background = panel,
        onBackground = onPanel,
        surface = panel,
        onSurface = onPanel,
        surfaceVariant = tint(0.10f),
        onSurfaceVariant = tint(0.74f),
        surfaceDim = panel,
        surfaceBright = tint(0.17f),
        surfaceContainerLowest = tint(0.03f),
        surfaceContainerLow = tint(0.05f),
        surfaceContainer = tint(0.08f),
        surfaceContainerHigh = tint(0.11f),
        surfaceContainerHighest = tint(0.15f),
        outline = tint(0.45f),
        outlineVariant = tint(0.18f),
        inverseSurface = onPanel,
        inverseOnSurface = panel,
    )
}

private fun contrast(a: Color, b: Color): Double {
    val la = a.luminance() + 0.05
    val lb = b.luminance() + 0.05
    return if (la > lb) la / lb else lb / la
}

/** A container colour and the colour drawn on it. */
@Immutable
data class Tone(val container: Color, val content: Color)

/** The theme's three tonal families; panels walk through them so a grid isn't one flat colour. */
@Composable
fun rememberTones(): List<Tone> {
    val c = MaterialTheme.colorScheme
    return remember(c) {
        listOf(
            Tone(c.primaryContainer, c.onPrimaryContainer),
            Tone(c.secondaryContainer, c.onSecondaryContainer),
            Tone(c.tertiaryContainer, c.onTertiaryContainer),
        )
    }
}

fun List<Tone>.at(index: Int): Tone = this[index.mod(size)]

// ── Feedback ─────────────────────────────────────────────────────────────────

/** Touch feedback for the media panels, routed through the keyboard's own sound and vibration settings. */
@Stable
class MediaFeedback internal constructor(private val controller: InputFeedbackController) {
    /** Sending something: an emoji, a GIF, a sticker. */
    fun tap() = controller.keyPress(TextKeyData.UNSPECIFIED)

    /** Switching a tab, a chip or a segment. */
    fun tick() = controller.uiTick()

    /** A long press opened something (variants, actions). */
    fun reveal() = controller.uiReveal()

    fun delete() = controller.keyPress(TextKeyData.DELETE)

    fun deleteRepeat() = controller.keyRepeatedAction(TextKeyData.DELETE)
}

@Composable
fun rememberMediaFeedback(): MediaFeedback {
    val controller = LocalInputFeedbackController.current
    return remember(controller) { MediaFeedback(controller) }
}

// ── Shapes ───────────────────────────────────────────────────────────────────

/** Whole Material shapes used across the panels; each slot keeps its own silhouette. */
object MediaShapes {
    val blobs: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Sunny,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Flower,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Puffy,
            MaterialShapes.SoftBurst,
            MaterialShapes.Cookie12Sided,
            MaterialShapes.Pentagon,
            MaterialShapes.Clover8Leaf,
            MaterialShapes.Cookie7Sided,
            MaterialShapes.Gem,
        )
    }

    fun blob(index: Int): RoundedPolygon = blobs[index.mod(blobs.size)]
}

/** Each pair of library shapes is matched into a [Morph] once per process and reused. */
private object MorphCache {
    private val byStart = IdentityHashMap<RoundedPolygon, IdentityHashMap<RoundedPolygon, Morph>>()

    fun between(start: RoundedPolygon, end: RoundedPolygon): Morph = synchronized(this) {
        byStart.getOrPut(start) { IdentityHashMap() }.getOrPut(end) { Morph(start, end) }
    }
}

/** A morph's outline at one progress, rebuilt only when the morph or the progress changes. */
private class OutlineCache {
    private val path = Path()
    private var morph: Morph? = null
    private var progress = Float.NaN

    fun of(morph: Morph, progress: Float): Path {
        if (morph !== this.morph || progress != this.progress) {
            morph.toPath(progress, path)
            this.morph = morph
            this.progress = progress
        }
        return path
    }
}

/**
 * A library [shape] filled with [color] behind upright [content]. When [shape] changes it springs into the
 * new one and turns a little, the Material 3 Expressive shape morph; at rest it is exactly [shape].
 */
@Composable
fun MorphBlob(
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    turnPerChange: Float = 45f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var from by remember { mutableStateOf(shape) }
    var to by remember { mutableStateOf(shape) }
    var turns by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(shape) {
        if (shape === to) return@LaunchedEffect
        from = to
        to = shape
        turns++
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f))
    }
    val morph = remember(from, to) { MorphCache.between(from, to) }
    val outline = remember { OutlineCache() }
    val animatedColor by androidx.compose.animation.animateColorAsState(color, label = "blob-color")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val p = progress.value
            val angle = (turns - 1 + p) * turnPerChange
            rotate(angle) {
                val path = outline.of(morph, p.coerceIn(0f, 1f))
                scale(size.width, size.height, pivot = Offset.Zero) { drawPath(path, animatedColor) }
            }
        }
        content()
    }
}

/**
 * The tab-strip indicator: one shape that slides under the selected tab on a bouncy spring, morphing from
 * the previous tab's shape into the next one's (and through their colours) as it travels.
 */
@Composable
fun SlidingMorphIndicator(
    position: Float,
    count: Int,
    indicatorSize: Dp,
    shapeAt: (Int) -> RoundedPolygon,
    colorAt: (Int) -> Color,
    modifier: Modifier = Modifier,
) {
    val outline = remember { OutlineCache() }
    val clamped = position.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
    val fromIndex = floor(clamped).toInt()
    val toIndex = (fromIndex + 1).coerceAtMost(count - 1)
    val morph = remember(fromIndex, toIndex) { MorphCache.between(shapeAt(fromIndex), shapeAt(toIndex)) }
    Canvas(modifier) {
        if (count <= 0) return@Canvas
        val fraction = clamped - fromIndex
        val slot = size.width / count
        val side = indicatorSize.toPx()
        val cx = slot * (clamped + 0.5f)
        val color = lerp(colorAt(fromIndex), colorAt(toIndex), fraction)
        translate(cx - side / 2f, (size.height - side) / 2f) {
            rotate(clamped * 40f, pivot = Offset(side / 2f, side / 2f)) {
                val path = outline.of(morph, fraction)
                scale(side, side, pivot = Offset.Zero) { drawPath(path, color) }
            }
        }
    }
}

/**
 * A whole library [shape] filled with [color] behind upright [content]. It springs in (scale and a quarter
 * turn) when [visible] flips on, so a press or selection lands with a bounce.
 */
@Composable
fun ShapeBlob(
    shape: RoundedPolygon,
    color: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "blob",
    )
    val outline = shape.toShape()
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = progress.coerceAtLeast(0f)
                    scaleX = p
                    scaleY = p
                    rotationZ = (1f - p) * -40f
                    alpha = p.coerceIn(0f, 1f)
                }
                .background(color, outline),
        )
        content()
    }
}

/** A whole library shape that turns slowly — only for empty states, never in a grid. */
@Composable
fun SpinningShape(shape: RoundedPolygon, color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "spin-angle",
    )
    val outline = shape.toShape()
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = turn }
                .background(color, outline),
        )
        content()
    }
}

// ── Tiles ────────────────────────────────────────────────────────────────────

/**
 * A pressable media tile (GIF, emoticon). Pressing squishes it and rounds its corners on the expressive
 * spring, the way Material 3 Expressive buttons morph; the animation runs in the layer, so a grid of
 * tiles never recomposes while one is pressed.
 */
@Composable
fun MediaTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    corner: Dp = 18.dp,
    pressedCorner: Dp = 30.dp,
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius = animateDpAsState(
        targetValue = if (pressed) pressedCorner else corner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "tile-corner",
    )
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "tile-scale",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                shape = RoundedCornerShape(radius.value)
                clip = true
            }
            .background(color)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A sticker on its own MaterialShape cushion: the shape squishes and twists while pressed and springs
 * back, the sticker itself stays upright.
 */
@Composable
fun ShapeTile(
    onClick: () -> Unit,
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "shape-tile",
    )
    val outline = shape.toShape()
    Box(
        modifier
            .aspectRatio(1f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = press.value
                    scaleX = 1f - 0.1f * p
                    scaleY = 1f - 0.1f * p
                    rotationZ = 22f * p
                }
                .background(color, outline),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(10.dp)
                .graphicsLayer {
                    val s = 1f + 0.08f * press.value
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** A small badge in a tile's corner (favourite star). */
@Composable
fun BoxScope.TileBadge(icon: ImageVector, contentDescription: String?) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .size(22.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialShapes.Cookie9Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
    }
}

/** A dim veil and the expressive loading indicator while a picture downloads. */
@Composable
fun BoxScope.TileBusy(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(Modifier.size(38.dp))
        }
    }
}

// ── Chips ────────────────────────────────────────────────────────────────────

/**
 * A tonal, borderless filter chip for the tab strips: pill shaped, with a Material Symbol that switches to
 * its filled form when selected and pops in on a spring.
 */
@Composable
fun MediaChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    selectedIcon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    tone: Tone? = null,
) {
    val selectedTone = tone ?: Tone(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = selectedTone.container,
        selectedLabelColor = selectedTone.content,
        selectedLeadingIconColor = selectedTone.content,
    )
    val pop by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chip-pop",
    )
    val shownIcon = if (selected) selectedIcon ?: icon else icon
    val iconModifier = Modifier
        .size(18.dp)
        .graphicsLayer {
            val s = 1f + 0.18f * (pop * (1f - pop)) * 4f
            scaleX = s
            scaleY = s
        }
    val leading: (@Composable () -> Unit)? = when {
        label == null -> null
        iconContent != null -> iconContent
        shownIcon != null -> {
            { Icon(shownIcon, contentDescription = null, modifier = iconModifier) }
        }
        else -> null
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            when {
                label != null -> Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
                iconContent != null -> iconContent()
                shownIcon != null -> Icon(shownIcon, contentDescription = contentDescription, modifier = iconModifier)
            }
        },
        modifier = modifier
            .height(36.dp)
            .semantics { role = Role.Tab },
        leadingIcon = leading,
        shape = CircleShape,
        colors = colors,
        border = null,
    )
}

// ── Empty and loading states ─────────────────────────────────────────────────

/** An expressive empty state: a slowly turning library shape holding a symbol, a title and a hint. */
@Composable
fun MediaEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpinningShape(shape, MaterialTheme.colorScheme.primaryContainer, Modifier.size(76.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

/** The panels' loading state: the Material 3 Expressive contained loading indicator, centred. */
@Composable
fun MediaLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(Modifier.size(60.dp))
    }
}

// ── Action sheet ─────────────────────────────────────────────────────────────

/** One row of a [MediaActionSheet]. */
data class MediaAction(
    val icon: ImageVector,
    val label: String,
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * What a long press opens: a scrim over the panel and an expressive card with the item's [preview] (on a
 * turning MaterialShape) and its [actions] as a connected list, the primary one filled with the accent.
 */
@Composable
fun BoxScope.MediaActionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: List<MediaAction>,
    caption: String? = null,
    preview: @Composable BoxScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .widthIn(max = 520.dp)
                    .animateEnterExit(
                        enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.9f),
                    )
                    // Swallow taps on the card so they don't reach the scrim.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        SpinningShape(MaterialShapes.Cookie12Sided, MaterialTheme.colorScheme.tertiaryContainer, Modifier.matchParentSize())
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center,
                            content = preview,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (caption != null) {
                            Text(
                                caption,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                            )
                        }
                        actions.forEachIndexed { index, action ->
                            ActionRow(action, index, actions.size)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(action: MediaAction, index: Int, count: Int) {
    val big = 20.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
    val container = if (action.primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (action.primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(onClick = action.onClick, shape = shape, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(action.icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(action.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Search ───────────────────────────────────────────────────────────────────

/**
 * The search bar the panels open (and the overlays type into). It isn't a real text field: the keyboard
 * below feeds the query, so it draws the text and a blinking caret itself.
 *
 * The search symbol sits on a MaterialShape that morphs whenever [shape] changes (the panels give each
 * mode its own shape; the overlays move to the next shape with every letter typed). With no query the
 * [hints] take turns, sliding up one after another; while [active] the bar wears an accent outline.
 */
@Composable
fun MediaSearchField(
    query: String,
    hints: List<String>,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    busy: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var hintIndex by remember(hints) { mutableIntStateOf(0) }
    LaunchedEffect(hints, query.isEmpty()) {
        if (hints.size < 2 || query.isNotEmpty()) return@LaunchedEffect
        while (true) {
            delay(2_800)
            hintIndex = (hintIndex + 1) % hints.size
        }
    }
    val outline by animateDpAsState(if (active) 2.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "search-outline")
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (outline > 0.dp) BorderStroke(outline, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.height(48.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // While typing a search the shape carries the way back out (Gboard's back arrow), still morphing per letter.
            MorphBlob(
                shape = shape,
                color = if (onBack != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (onBack != null) {
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Close search", onClick = onBack)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Icon(
                    imageVector = if (onBack != null) Symbols.ArrowBack else Symbols.Search,
                    contentDescription = if (onBack != null) "Close search" else null,
                    tint = if (onBack != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (query.isEmpty()) {
                    if (active) Caret()
                    val motion = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = hints.getOrElse(hintIndex) { "" },
                        transitionSpec = {
                            (slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                                (slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()))
                        },
                        label = "search-hint",
                    ) { hint ->
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        query,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        // While typing, keep the end (where the caret is) in view.
                        overflow = if (active) TextOverflow.StartEllipsis else TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) Caret()
                }
            }
            if (busy) {
                LoadingIndicator(Modifier.size(30.dp))
            }
            if (onClear != null && query.isNotEmpty()) {
                IconButton(onClick = onClear, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
                    Icon(Symbols.Close, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                }
            } else if (!busy) {
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun Caret() {
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "caret-alpha",
    )
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .width(2.dp)
            .height(22.dp)
            .alpha(blink)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
    )
}

// ── Delete ───────────────────────────────────────────────────────────────────

/**
 * A delete button that repeats while held, like the keyboard's own delete key. Its shape morphs from a
 * pill to a rounded square while pressed (the Material 3 Expressive icon-button press) and its symbol
 * fills in.
 */
@Composable
fun RepeatingDeleteButton(
    onDelete: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner = animateDpAsState(
        targetValue = if (pressed) 12.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "delete-corner",
    )
    val container by androidx.compose.animation.animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "delete-color",
    )
    val scope = rememberCoroutineScope()
    val latestDelete by rememberUpdatedState(onDelete)
    val latestRepeat by rememberUpdatedState(onRepeat)
    Box(
        modifier
            .graphicsLayer {
                shape = RoundedCornerShape(corner.value)
                clip = true
            }
            .background(container)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val press = PressInteraction.Press(down.position)
                    interaction.tryEmit(press)
                    latestDelete()
                    val repeater = scope.launch {
                        delay(420)
                        while (true) {
                            latestRepeat()
                            delay(55)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    repeater.cancel()
                    interaction.tryEmit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "Delete"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (pressed) Symbols.BackspaceFilled else Symbols.Backspace,
            null,
            tint = if (pressed) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** A section heading in a grid: a tonal pill holding the section's symbol on its own shape. */
@Composable
fun MediaSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone? = null,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
) {
    val t = tone ?: Tone(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    Box(modifier.padding(start = 2.dp, top = 12.dp, bottom = 6.dp)) {
        Row(
            Modifier
                .background(t.container, CircleShape)
                .padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(t.content, shape.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = t.container, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = t.content, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** ABC: back to the letters. A pill that squares up while pressed, the expressive button press. */
@Composable
fun MediaAbcButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 12.dp else 25.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "abc-corner")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        interactionSource = interaction,
        modifier = modifier.semantics { contentDescription = "Back to the keyboard" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("ABC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}
