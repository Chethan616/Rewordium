package com.noxquill.rewordium.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.floor

/**
 * Rewordium's quiet, shared expressive backdrop. It uses the same Material
 * shape morph vocabulary as the DEX reference, but only animates one large
 * decorative layer so the actual writing controls stay still and responsive.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val RewordiumMorphShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Puffy,
    MaterialShapes.SoftBurst,
    MaterialShapes.Sunny,
    MaterialShapes.Clover4Leaf,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val morphs = remember { RewordiumMorphShapes.zipWithNext { from, to -> Morph(from, to) } + Morph(RewordiumMorphShapes.last(), RewordiumMorphShapes.first()) }
    val path = remember { Path() }
    val animation = rememberInfiniteTransition(label = "rewordium-morph")
    val rotation = if (animated) animation.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(44_000, easing = LinearEasing)), label = "morph-rotation",
    ).value else 0f
    val breathe = if (animated) animation.animateFloat(
        initialValue = 0.975f, targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "morph-breathe",
    ).value else 1f

    Canvas(modifier) {
        val safePhase = phase.coerceAtLeast(0f)
        val fromIndex = floor(safePhase).toInt().mod(RewordiumMorphShapes.size)
        val toIndex = (fromIndex + 1).mod(RewordiumMorphShapes.size)
        val progress = safePhase - floor(safePhase)
        morphs[fromIndex].toPath(progress, path)
        val tone = lerp(colors[fromIndex % colors.size], colors[toIndex % colors.size], progress)
        rotate(rotation + safePhase * 42f) {
            scale(breathe) {
                scale(size.width, size.height, pivot = Offset.Zero) {
                    drawPath(path, tone)
                }
            }
        }
    }
}

/** A small morph-backed frame for an avatar or tool glyph. */
@Composable
fun MorphBadge(
    phase: Float,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        MorphingBackdrop(
            phase = phase,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.96f },
            animated = false,
            colors = List(RewordiumMorphShapes.size) { color },
        )
        Box(Modifier.fillMaxSize(), content = content)
    }
}
