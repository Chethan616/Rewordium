package com.noxquill.rewordium.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun JadeMascot(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    mood: String = "calm",
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "jade-float")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "jade-phase",
    )
    val lift = if (animate) sin(phase) * 4f else 0f

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Jade, your Rewordium writing companion" },
    ) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f + lift)
        val r = w * 0.38f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x5595E7BC), Color(0x0095E7BC)),
                center = center,
                radius = r * 1.55f,
            ),
            radius = r * 1.55f,
            center = center,
        )

        val face = Path().apply {
            moveTo(w * 0.22f, h * 0.38f + lift)
            lineTo(w * 0.27f, h * 0.12f + lift)
            quadraticTo(w * 0.29f, h * 0.05f + lift, w * 0.36f, h * 0.15f + lift)
            lineTo(w * 0.43f, h * 0.25f + lift)
            quadraticTo(w * 0.5f, h * 0.22f + lift, w * 0.57f, h * 0.25f + lift)
            lineTo(w * 0.65f, h * 0.15f + lift)
            quadraticTo(w * 0.72f, h * 0.05f + lift, w * 0.74f, h * 0.13f + lift)
            lineTo(w * 0.78f, h * 0.39f + lift)
            cubicTo(w * 0.87f, h * 0.48f + lift, w * 0.85f, h * 0.65f + lift, w * 0.76f, h * 0.75f + lift)
            cubicTo(w * 0.62f, h * 0.9f + lift, w * 0.38f, h * 0.9f + lift, w * 0.24f, h * 0.75f + lift)
            cubicTo(w * 0.13f, h * 0.64f + lift, w * 0.13f, h * 0.48f + lift, w * 0.22f, h * 0.38f + lift)
            close()
        }
        drawPath(face, Brush.linearGradient(listOf(Color(0xFFB1EDC6), Color(0xFF43B985)), Offset(0f, 0f), Offset(w, h)))
        drawPath(face, Color(0xFF166B50), style = Stroke(width = w * 0.018f))

        val eyeColor = Color(0xFF173B2E)
        val eyeY = h * 0.51f + lift
        val eyeRadius = w * 0.027f
        drawCircle(eyeColor, eyeRadius, Offset(w * 0.39f, eyeY))
        drawCircle(eyeColor, eyeRadius, Offset(w * 0.61f, eyeY))
        drawCircle(Color.White.copy(alpha = 0.78f), eyeRadius * 0.32f, Offset(w * 0.398f, eyeY - eyeRadius * 0.35f))
        drawCircle(Color.White.copy(alpha = 0.78f), eyeRadius * 0.32f, Offset(w * 0.608f, eyeY - eyeRadius * 0.35f))

        drawCircle(Color(0xFFEF8C91).copy(alpha = 0.65f), w * 0.035f, Offset(w * 0.31f, h * 0.59f + lift))
        drawCircle(Color(0xFFEF8C91).copy(alpha = 0.65f), w * 0.035f, Offset(w * 0.69f, h * 0.59f + lift))
        val smile = Path().apply {
            moveTo(w * 0.45f, h * 0.62f + lift)
            quadraticTo(w * 0.5f, h * 0.67f + lift, w * 0.55f, h * 0.62f + lift)
        }
        drawPath(smile, eyeColor, style = Stroke(width = w * 0.018f))
        if (mood == "thinking") {
            drawCircle(Color(0xFFFFD580), w * 0.025f, Offset(w * 0.79f, h * 0.24f + lift))
        }
    }
}
