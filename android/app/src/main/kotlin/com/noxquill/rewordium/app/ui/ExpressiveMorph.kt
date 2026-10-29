@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.noxquill.rewordium.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import java.util.IdentityHashMap
import kotlin.math.floor

/**
 * Every decorative shape in Rewordium comes straight from the Material 3
 * Expressive shape library ([MaterialShapes]). A shape at rest is always a
 * whole library shape — never a frame caught halfway between two — and any
 * motion is a slow spin or a spring morph from one library shape to the next.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object ExpressiveShapes {
    /** Round-ish library shapes that frame an icon or a bot well. */
    val badges: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.SoftBurst,
            MaterialShapes.Puffy,
            MaterialShapes.Cookie12Sided,
            MaterialShapes.Flower,
            MaterialShapes.Pentagon,
            MaterialShapes.Clover8Leaf,
        )
    }

    /** Every bot has its own signature library shape, the same wherever it appears. */
    fun avatar(type: String): RoundedPolygon = pick(type)

    /** A stable library shape for any key, so a row keeps its shape across launches. */
    fun pick(key: Any): RoundedPolygon = badges[key.hashCode().mod(badges.size)]

    /** A shape-shifting loop that starts on [first] and visits a few friends. */
    fun loop(first: RoundedPolygon, vararg then: RoundedPolygon): List<RoundedPolygon> = listOf(first, *then)
}

/** Each accent family has a signature library shape, so colour and shape keep the same rhythm. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Tone.shape(): RoundedPolygon = when (this) {
    Tone.Primary -> MaterialShapes.Cookie9Sided
    Tone.Secondary -> MaterialShapes.Clover4Leaf
    Tone.Tertiary -> MaterialShapes.Sunny
    Tone.Error -> MaterialShapes.Cookie6Sided
    Tone.Neutral -> MaterialShapes.Cookie12Sided
}

@Composable
private fun rememberSpin(enabled: Boolean, periodMillis: Int): State<Float>? {
    if (!enabled) return null
    val transition = rememberInfiniteTransition(label = "shape-spin")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing)),
        label = "shape-spin-angle",
    )
}

/**
 * A whole Material shape filled with [color], with [content] upright in its
 * centre. With [spin] the shape (never the content) turns slowly; the angle
 * is read in the draw phase, so spinning never recomposes.
 */
@Composable
fun ShapeBadge(
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    spin: Boolean = false,
    spinMillis: Int = 40_000,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val outline = shape.toShape()
    val turn = rememberSpin(spin, spinMillis)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = turn?.value ?: 0f }
                .background(color, outline),
        )
        content()
    }
}

/**
 * A shape that rests on one library shape, then springs into the next, round
 * and round [shapes] — the same morph-and-settle motion as the Material
 * loading indicator. Rest poses are always whole library shapes.
 */
@Composable
fun ShapeShifter(
    shapes: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
    holdMillis: Long = 2_600L,
    spin: Boolean = true,
    spinMillis: Int = 30_000,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // `step` names the shape being morphed into; `phase` springs from step-1 to step.
    // Bumping `step` at rest leaves the drawn shape unchanged, so there is never a jump.
    val step = remember(shapes) { mutableIntStateOf(0) }
    val phase = remember(shapes) { Animatable(0f) }
    LaunchedEffect(shapes) {
        if (shapes.size < 2) return@LaunchedEffect
        while (true) {
            delay(holdMillis)
            step.intValue += 1
            phase.animateTo(step.intValue.toFloat(), spring(dampingRatio = 0.62f, stiffness = 110f))
        }
    }
    // Only the morph in play is built (and each pair only once per app run), so opening a screen stays cheap.
    val current = step.intValue
    val morph = remember(shapes, current) {
        MorphCache.between(shapes[(current - 1).mod(shapes.size)], shapes[current.mod(shapes.size)])
    }
    val turn = rememberSpin(spin, spinMillis)
    val outline = remember { OutlineCache() }
    Box(modifier, contentAlignment = Alignment.Center) {
        // The spin lives on the layer, so turning never redraws; the outline is rebuilt only while morphing.
        Canvas(Modifier.matchParentSize().graphicsLayer { rotationZ = turn?.value ?: 0f }) {
            val path = outline.of(morph, phase.value - (current - 1))
            scale(size.width, size.height, pivot = Offset.Zero) { drawPath(path, color) }
        }
        content()
    }
}

/** Each pair of library shapes is matched into a [Morph] once and reused everywhere. */
private object MorphCache {
    private val byStart = IdentityHashMap<RoundedPolygon, IdentityHashMap<RoundedPolygon, Morph>>()

    fun between(start: RoundedPolygon, end: RoundedPolygon): Morph = synchronized(this) {
        byStart.getOrPut(start) { IdentityHashMap() }.getOrPut(end) { Morph(start, end) }
    }
}

/** A morph's outline at one progress, rebuilt only when the morph or the progress actually changes. */
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
 * A whole library [shape] that springs into its new shape whenever [shape]
 * changes — a picked avatar, a sign-in/sign-up switch. At rest it is always
 * exactly [shape].
 */
@Composable
fun ShapeMorph(
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    spin: Boolean = true,
    spinMillis: Int = 40_000,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var from by remember { mutableStateOf(shape) }
    var to by remember { mutableStateOf(shape) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(shape) {
        if (shape == to) return@LaunchedEffect
        from = to
        to = shape
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 160f))
    }
    val morph = remember(from, to) { MorphCache.between(from, to) }
    val turn = rememberSpin(spin, spinMillis)
    val outline = remember { OutlineCache() }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().graphicsLayer { rotationZ = turn?.value ?: 0f }) {
            val path = outline.of(morph, progress.value)
            scale(size.width, size.height, pivot = Offset.Zero) { drawPath(path, color) }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val PagerShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Puffy,
    MaterialShapes.SoftBurst,
    MaterialShapes.Sunny,
    MaterialShapes.Clover4Leaf,
)

/**
 * A large backdrop whose shape is driven by a continuous [phase] — a pager
 * position, say. Whole numbers are whole library shapes; the in-between
 * frames only ever show while the phase is moving.
 */
@Composable
fun MorphingBackdrop(
    phase: Float,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    ),
) {
    val outline = remember { OutlineCache() }
    val animation = rememberInfiniteTransition(label = "rewordium-morph")
    val rotation = if (animated) animation.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(44_000, easing = LinearEasing)), label = "morph-rotation",
    ) else null
    val breathe = if (animated) animation.animateFloat(
        initialValue = 0.975f, targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "morph-breathe",
    ) else null

    val safePhase = phase.coerceAtLeast(0f)
    val fromIndex = floor(safePhase).toInt().mod(PagerShapes.size)
    val toIndex = (fromIndex + 1).mod(PagerShapes.size)
    val morph = remember(fromIndex) { MorphCache.between(PagerShapes[fromIndex], PagerShapes[toIndex]) }
    // Turning and breathing happen on the layer; the outline is only rebuilt while the phase moves.
    Canvas(
        modifier.graphicsLayer {
            rotationZ = (rotation?.value ?: 0f) + safePhase * 42f
            val b = breathe?.value ?: 1f
            scaleX = b
            scaleY = b
        },
    ) {
        val progress = safePhase - floor(safePhase)
        val path = outline.of(morph, progress)
        val tone = lerp(colors[fromIndex % colors.size], colors[toIndex % colors.size], progress)
        scale(size.width, size.height, pivot = Offset.Zero) { drawPath(path, tone) }
    }
}
