package com.noxquill.rewordium.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

/**
 * Google Circle-to-Search–style edge glow.
 * Edge bands (color-interpolated between adjacent corners) are drawn first;
 * corner blooms sit on top with soft 6-stop radials.
 */
class GoogleEdgeGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulse = 0.75f
    private var pulseAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startPulse() {
        stopPulse()
        pulseAnimator = ValueAnimator.ofFloat(0.5f, 1f).apply {
            duration = 2400L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                pulse = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Slightly richer / darker than before
        val a = (0.36f * pulse * 255f).toInt().coerceIn(0, 255)
        val eA = (a * 0.42f).toInt()
        val cr = min(w, h) * 0.50f

        // ── 1. Edge bands FIRST (each half interpolates between adjacent corner colors) ──

        val topH = h * 0.42f
        paint.shader = LinearGradient(
            0f, 0f, 0f, topH,
            intArrayOf(
                argb(eA, 0x42, 0x85, 0xF4),
                argb((eA * 0.35f).toInt(), 0x42, 0x85, 0xF4),
                argb(0, 0x42, 0x85, 0xF4),
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w / 2f, topH, paint)

        paint.shader = LinearGradient(
            0f, 0f, 0f, topH,
            intArrayOf(
                argb(eA, 0xEA, 0x43, 0x35),
                argb((eA * 0.35f).toInt(), 0xEA, 0x43, 0x35),
                argb(0, 0xEA, 0x43, 0x35),
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(w / 2f, 0f, w, topH, paint)

        val botTop = h - h * 0.42f
        paint.shader = LinearGradient(
            0f, h, 0f, botTop,
            intArrayOf(
                argb(eA, 0x34, 0xA8, 0x53),
                argb((eA * 0.35f).toInt(), 0x34, 0xA8, 0x53),
                argb(0, 0x34, 0xA8, 0x53),
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, botTop, w / 2f, h, paint)

        paint.shader = LinearGradient(
            0f, h, 0f, botTop,
            intArrayOf(
                argb(eA, 0xFB, 0xBC, 0x04),
                argb((eA * 0.35f).toInt(), 0xFB, 0xBC, 0x04),
                argb(0, 0xFB, 0xBC, 0x04),
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(w / 2f, botTop, w, h, paint)

        val sideW = w * 0.38f
        paint.shader = LinearGradient(
            0f, 0f, sideW, 0f,
            intArrayOf(
                argb((eA * 0.75f).toInt(), 0x42, 0x85, 0xF4),
                argb(0, 0x42, 0x85, 0xF4),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, sideW, h / 2f, paint)

        paint.shader = LinearGradient(
            0f, 0f, sideW, 0f,
            intArrayOf(
                argb((eA * 0.75f).toInt(), 0x34, 0xA8, 0x53),
                argb(0, 0x34, 0xA8, 0x53),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, h / 2f, sideW, h, paint)

        paint.shader = LinearGradient(
            w, 0f, w - sideW, 0f,
            intArrayOf(
                argb((eA * 0.75f).toInt(), 0xEA, 0x43, 0x35),
                argb(0, 0xEA, 0x43, 0x35),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(w - sideW, 0f, w, h / 2f, paint)

        paint.shader = LinearGradient(
            w, 0f, w - sideW, 0f,
            intArrayOf(
                argb((eA * 0.75f).toInt(), 0xFB, 0xBC, 0x04),
                argb(0, 0xFB, 0xBC, 0x04),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(w - sideW, h / 2f, w, h, paint)

        // ── 2. Corner blooms ON TOP — 6-stop soft radials ──
        drawCornerBloom(canvas, 0f, 0f, cr, 0xFF4285F4.toInt(), a)
        drawCornerBloom(canvas, w, 0f, cr, 0xFFEA4335.toInt(), (a * 0.95f).toInt())
        drawCornerBloom(canvas, 0f, h, cr, 0xFF34A853.toInt(), (a * 0.90f).toInt())
        drawCornerBloom(canvas, w, h, cr, 0xFFFBBC04.toInt(), (a * 0.85f).toInt())
    }

    private fun drawCornerBloom(
        canvas: Canvas,
        cornerX: Float,
        cornerY: Float,
        radius: Float,
        color: Int,
        alpha: Int,
    ) {
        if (alpha <= 0) return
        val r = (color shr 16) and 0xFF
        val g = (color shr  8) and 0xFF
        val b = color and 0xFF
        val effectiveRadius = min(radius, min(width, height) * 0.55f)
        paint.shader = RadialGradient(
            cornerX,
            cornerY,
            effectiveRadius,
            intArrayOf(
                argb(alpha, r, g, b),
                argb((alpha * 0.75f).toInt(), r, g, b),
                argb((alpha * 0.40f).toInt(), r, g, b),
                argb((alpha * 0.12f).toInt(), r, g, b),
                argb((alpha * 0.03f).toInt(), r, g, b),
                argb(0, r, g, b),
            ),
            floatArrayOf(0f, 0.18f, 0.40f, 0.65f, 0.85f, 1f),
            Shader.TileMode.CLAMP,
        )
        val pad = effectiveRadius
        canvas.drawRect(
            (cornerX - pad).coerceAtLeast(0f),
            (cornerY - pad).coerceAtLeast(0f),
            (cornerX + pad).coerceAtMost(width.toFloat()),
            (cornerY + pad).coerceAtMost(height.toFloat()),
            paint,
        )
    }

    private fun argb(alpha: Int, r: Int, g: Int, b: Int): Int {
        return android.graphics.Color.argb(alpha.coerceIn(0, 255), r, g, b)
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }
}
