package com.noxquill.rewordium.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.atan2
import kotlin.math.min

/**
 * Continuous Google Circle-to-Search–style edge glow.
 * Smoothly blends the 4 Google colors around the screen edges.
 */
class GoogleEdgeGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulse = 0.5f
    private var pulseAnimator: ValueAnimator? = null
    
    // Google Colors
    private val colorBlue = Color.argb(255, 0x42, 0x85, 0xF4)
    private val colorRed = Color.argb(255, 0xEA, 0x43, 0x35)
    private val colorYellow = Color.argb(255, 0xFB, 0xBC, 0x04)
    private val colorGreen = Color.argb(255, 0x34, 0xA8, 0x53)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    fun startPulse() {
        stopPulse()
        pulseAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 1800L
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

        val cx = w / 2f
        val cy = h / 2f

        // Get angles for corners mapped to 0..1 for SweepGradient
        val br = getAnglePos(w / 2f, h / 2f)
        val bl = getAnglePos(-w / 2f, h / 2f)
        val tl = getAnglePos(-w / 2f, -h / 2f)
        val tr = getAnglePos(w / 2f, -h / 2f)

        // Interpolate the color at exactly 0.0 (the right edge, between Top-Right and Bottom-Right)
        val fraction = (1f - tr) / (1f - tr + br)
        val cRight = blendColors(colorRed, colorYellow, fraction)

        val positions = floatArrayOf(0f, br, bl, tl, tr, 1f)
        val colors = intArrayOf(cRight, colorYellow, colorGreen, colorBlue, colorRed, cRight)

        paint.shader = SweepGradient(cx, cy, colors, positions)

        val baseAlpha = (0.8f * pulse * 255f).toInt()

        val maxStroke = min(w, h) * 0.20f
        val steps = 8
        val rx = min(w, h) * 0.06f

        for (i in steps downTo 1) {
            val stepFraction = i.toFloat() / steps
            paint.strokeWidth = maxStroke * stepFraction
            
            // Alpha distribution: thickest strokes are faint, thinnest are solid
            val layerAlphaFraction = 1f - (stepFraction * 0.85f)
            paint.alpha = (baseAlpha * layerAlphaFraction / steps * 2.5f).toInt().coerceIn(0, 255)
            
            canvas.drawRoundRect(0f, 0f, w, h, rx, rx, paint)
        }
    }

    private fun getAnglePos(dx: Float, dy: Float): Float {
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        return angle / 360f
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio).toInt()
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
        return Color.argb(a.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }
}
