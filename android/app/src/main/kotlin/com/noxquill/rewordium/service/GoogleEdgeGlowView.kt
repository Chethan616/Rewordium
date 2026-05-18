package com.noxquill.rewordium.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.atan2
import kotlin.math.min

/**
 * Google Circle-to-Search–style edge glow.
 *
 * Lifecycle:
 *   1. showGlow()  → smooth 350ms fade-in → then pulse breathes indefinitely
 *   2. scheduleHide(delayMs) → wait N ms → smooth 700ms fade-out → done
 *   3. cancelHide() → abort any pending scheduled hide
 *
 * The pulse animator keeps running through the entire fade-out so there is
 * no sudden freeze / flicker — the View's composite alpha handles the exit.
 */
class GoogleEdgeGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulse = 0.5f
    private var pulseAnimator: ValueAnimator? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    // Google colors
    private val colorBlue   = Color.argb(255, 0x42, 0x85, 0xF4)
    private val colorRed    = Color.argb(255, 0xEA, 0x43, 0x35)
    private val colorYellow = Color.argb(255, 0xFB, 0xBC, 0x04)
    private val colorGreen  = Color.argb(255, 0x34, 0xA8, 0x53)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        // Start fully transparent — caller fades us in
        alpha = 0f
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fade the view in (350 ms) then begin the breathing pulse.
     * Safe to call multiple times — idempotent.
     */
    fun showGlow() {
        cancelHide()
        animate().cancel()
        startPulse()          // pulse runs from the first frame so there's never a blank flash
        animate()
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Wait [delayMs] then smoothly fade the glow out over 700 ms.
     * The pulse keeps running through the fade so there is zero flicker.
     * [onDone] is invoked on the main thread once the view reaches alpha 0.
     */
    fun scheduleHide(
        delayMs: Long = 1500L,
        fadeDurationMs: Long = 700L,
        onDone: (() -> Unit)? = null,
    ) {
        cancelHide()
        hideRunnable = Runnable {
            hideRunnable = null
            animate().cancel()
            animate()
                .alpha(0f)
                .setDuration(fadeDurationMs)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .withEndAction {
                    stopPulse()
                    onDone?.invoke()
                }
                .start()
        }
        mainHandler.postDelayed(hideRunnable!!, delayMs)
    }

    /** Cancel any pending scheduled hide without changing visibility. */
    fun cancelHide() {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null
    }

    // ── Pulse ─────────────────────────────────────────────────────────────────

    private fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ValueAnimator.ofFloat(0.30f, 1f).apply {
            duration = 1800L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f

        // Map each corner to a 0..1 sweep position
        val br = getAnglePos( w / 2f,  h / 2f)
        val bl = getAnglePos(-w / 2f,  h / 2f)
        val tl = getAnglePos(-w / 2f, -h / 2f)
        val tr = getAnglePos( w / 2f, -h / 2f)

        // Seam color at angle 0 (right edge between TR and BR)
        val seamFraction = (1f - tr) / (1f - tr + br)
        val cSeam = blendColors(colorRed, colorYellow, seamFraction)

        paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(cSeam, colorYellow, colorGreen, colorBlue, colorRed, cSeam),
            floatArrayOf(0f, br, bl, tl, tr, 1f),
        )

        val baseAlpha  = (0.85f * pulse * 255f).toInt()
        val maxStroke  = min(w, h) * 0.22f
        val steps      = 10
        // Corner rounding — match device typical corner radius feel
        val rx = min(w, h) * 0.055f

        for (i in steps downTo 1) {
            val frac = i.toFloat() / steps
            paint.strokeWidth = maxStroke * frac
            // Inner strokes (smallest i) carry most of the alpha for a bright edge line;
            // outer layers fade to create the soft bloom.
            val layerAlpha = 1f - (frac * 0.82f)
            paint.alpha = (baseAlpha * layerAlpha / steps * 2.8f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(0f, 0f, w, h, rx, rx, paint)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getAnglePos(dx: Float, dy: Float): Float {
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        return angle / 360f
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.argb(
            (Color.alpha(c1) * inv + Color.alpha(c2) * ratio).toInt().coerceIn(0, 255),
            (Color.red(c1)   * inv + Color.red(c2)   * ratio).toInt().coerceIn(0, 255),
            (Color.green(c1) * inv + Color.green(c2) * ratio).toInt().coerceIn(0, 255),
            (Color.blue(c1)  * inv + Color.blue(c2)  * ratio).toInt().coerceIn(0, 255),
        )
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        cancelHide()
        stopPulse()
        animate().cancel()
        visibility = View.GONE
        setLayerType(LAYER_TYPE_NONE, null)
        super.onDetachedFromWindow()
    }
}
