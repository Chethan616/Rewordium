package com.noxquill.rewordium.app.ui.avatar

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Still portraits of the bots, drawn the same way [BotAvatar] draws them at rest, for places outside Compose
 * (a notification's icon and picture). Unknown types fall back to the star.
 */
object BotPortrait {
    /** One bot on a transparent square, [sizePx] across. */
    fun single(type: String, mood: BotMood, sizePx: Int): Bitmap = render(sizePx, sizePx, null) {
        drawBot(type, mood, Offset.Zero, sizePx.toFloat(), tilt = 0f)
    }

    /**
     * Several bots huddled together in the middle of a [widthPx] × [heightPx] card of [background], each
     * leaning a little toward its neighbour, as if they're playing.
     */
    fun group(types: List<String>, mood: BotMood, widthPx: Int, heightPx: Int, background: Int): Bitmap =
        render(widthPx, heightPx, Color(background)) {
            val bots = types.ifEmpty { listOf("star") }
            val size = heightPx * 0.78f
            val step = size * 0.74f
            val groupWidth = size + step * (bots.size - 1)
            val left = (widthPx - groupWidth) / 2f
            val top = (heightPx - size) / 2f + heightPx * 0.04f
            bots.forEachIndexed { i, type ->
                val tilt = if (bots.size == 1) 0f else if (i % 2 == 0) -7f else 7f
                drawBot(type, mood, Offset(left + i * step, top), size, tilt)
            }
        }

    private fun render(width: Int, height: Int, background: Color?, block: DrawScope.() -> Unit): Bitmap {
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
            background?.let { drawRect(it) }
            block()
        }
        return image.asAndroidBitmap()
    }

    private fun DrawScope.drawBot(type: String, mood: BotMood, at: Offset, size: Float, tilt: Float) {
        val shape = BOT_SHAPES[type] ?: BOT_SHAPES.getValue("star")
        val body = PathParser().parsePathString(shape.path).toPath()
        val parts = shape.parts?.let { PathParser().parsePathString(it).toPath() }
        val color = Color(shape.color)
        val unit = size / 100f
        translate(at.x, at.y) {
            // A soft contact shadow, as on the avatar.
            drawOval(Color.Black.copy(alpha = 0.16f), topLeft = Offset(26f * unit, 90f * unit), size = Size(48f * unit, 6f * unit))
            rotate(tilt, pivot = Offset(50f * unit, 60f * unit)) {
                scale(unit, unit, pivot = Offset.Zero) {
                    parts?.let { drawPath(it, lerp(color, Color.Black, 0.18f)) }
                    drawPath(body, color)
                    drawPath(body, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f)), center = Offset(34f, 26f), radius = 52f))
                    drawPath(body, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f)), startY = 48f, endY = 96f))
                    drawFace(faceFor(mood), shape, inkFor(color), lookX = 0f, lookY = 0f, eyeOpen = 1f, large = true)
                }
            }
        }
    }
}
