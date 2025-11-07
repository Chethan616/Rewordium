package com.noxquill.rewordium.keyboard.animation

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.*
import kotlin.random.Random

/**
 * Custom drawable that creates a pulsing spherical animation similar to iOS Siri bubble
 * Features multiple animated waves with varying amplitudes and frequencies
 */
class SiriWaveDrawable(
    private val baseColor: Int,
    private val accentColor: Int = baseColor
) : Drawable() {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Animation properties
    private var animationTime = 0f
    private var pulseScale = 1f
    private var isAnimating = false
    private var frameCounter = 0 // Frame limiting for smoother animation
    
    // Wave properties for creating organic movement
    private val waveCount = 6
    private val waves = mutableListOf<WaveProperties>()
    
    // Animators
    private var waveAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    
    private data class WaveProperties(
        val amplitude: Float,
        val frequency: Float,
        val phase: Float,
        val speed: Float,
        val opacity: Float
    )
    
    init {
        initializeWaves()
        startAnimation()
    }
    
    private fun initializeWaves() {
        waves.clear()
        repeat(waveCount) { i ->
            waves.add(
                WaveProperties(
                    amplitude = 1f + (i * 0.2f), // Tiny amplitude for almost invisible waves
                    frequency = 0.2f + (i * 0.05f), // Extremely low frequency
                    phase = (i * PI / 3).toFloat(), // Evenly spaced phases for no conflicts
                    speed = 0.01f + (i * 0.005f), // Glacially slow speeds
                    opacity = 0.05f + (i * 0.01f) // Barely visible opacity
                )
            )
        }
    }
    
    private fun startAnimation() {
        if (isAnimating) return
        
        isAnimating = true
        
        // SEAMLESS CONTINUOUS WAVE ANIMATION - No visible restart
        waveAnimator = ValueAnimator.ofFloat(0f, Float.MAX_VALUE).apply {
            duration = Long.MAX_VALUE // Infinite duration - no restart
            interpolator = LinearInterpolator()
            repeatCount = 0 // No repeat needed
            
            addUpdateListener { animator ->
                frameCounter++
                // Only update every 10th frame for ultra-smooth, slow motion
                if (frameCounter % 10 == 0) {
                    val rawTime = animator.animatedValue as Float
                    // Create seamless time progression - GLACIALLY SLOW
                    animationTime = rawTime * 0.000005f // Almost static, barely moving
                    invalidateSelf()
                }
            }
            start()
        }
        
        // SEAMLESS CONTINUOUS PULSE ANIMATION - Smooth breathing
        pulseAnimator = ValueAnimator.ofFloat(0f, Float.MAX_VALUE).apply {
            duration = Long.MAX_VALUE // Infinite duration
            interpolator = LinearInterpolator()
            repeatCount = 0 // No repeat needed
            
            addUpdateListener { animator ->
                // Only update pulse every 15th frame for barely perceptible motion
                if (frameCounter % 15 == 0) {
                    val rawTime = animator.animatedValue as Float
                    // Create smooth continuous pulsing - BARELY NOTICEABLE
                    pulseScale = 1f + 0.005f * sin(rawTime * 0.000001f) // Almost static pulse
                    invalidateSelf()
                }
            }
            start()
        }
    }
    
    fun stopAnimation() {
        isAnimating = false
        waveAnimator?.cancel()
        pulseAnimator?.cancel()
        waveAnimator = null
        pulseAnimator = null
    }
    
    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val radius = minOf(bounds.width(), bounds.height()) / 2f * 0.8f * pulseScale
        
        // Draw base circle with gradient
        drawBaseCircle(canvas, centerX, centerY, radius)
        
        // Draw animated waves
        if (isAnimating) {
            drawAnimatedWaves(canvas, centerX, centerY, radius)
        }
    }
    
    private fun drawBaseCircle(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        // Create radial gradient for the base
        val gradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                adjustColorAlpha(baseColor, 0.9f),
                adjustColorAlpha(accentColor, 0.7f),
                adjustColorAlpha(baseColor, 0.4f)
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        
        paint.shader = gradient
        canvas.drawCircle(centerX, centerY, radius, paint)
    }
    
    private fun drawAnimatedWaves(canvas: Canvas, centerX: Float, centerY: Float, baseRadius: Float) {
        waves.forEachIndexed { index, wave ->
            val path = Path()
            val waveRadius = baseRadius * 0.65f
            val numPoints = 80 // More points for ultra-smooth waves
            
            // Calculate seamless wave distortion
            val currentTime = animationTime * wave.speed + wave.phase
            
            for (i in 0 until numPoints) {
                val angle = (i.toFloat() / numPoints) * 2f * PI.toFloat()
                
                // Create seamless organic wave distortion
                val primaryWave = sin(angle * wave.frequency + currentTime) * wave.amplitude
                val secondaryWave = cos(angle * wave.frequency * 1.3f + currentTime * 0.8f) * wave.amplitude * 0.4f
                val distortion = primaryWave + secondaryWave
                
                val currentRadius = waveRadius + distortion
                val x = centerX + cos(angle) * currentRadius
                val y = centerY + sin(angle) * currentRadius
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
            
            // Set wave color with smooth continuous opacity
            val baseOpacity = wave.opacity * (0.6f + 0.4f * sin(currentTime + index))
            val alpha = (baseOpacity * 255).toInt().coerceIn(40, 180)
            wavePaint.color = adjustColorAlpha(if (index % 2 == 0) baseColor else accentColor, alpha / 255f)
            wavePaint.shader = null
            
            canvas.drawPath(path, wavePaint)
        }
    }
    
    private fun adjustColorAlpha(color: Int, alpha: Float): Int {
        val newAlpha = (alpha * 255).toInt().coerceIn(0, 255)
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }
    
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        wavePaint.alpha = alpha
    }
    
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        wavePaint.colorFilter = colorFilter
    }
    
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
    
    // Clean up when drawable is no longer needed
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) {
            stopAnimation()
        } else if (changed || restart) {
            initializeWaves()
            startAnimation()
        }
        return changed
    }
}
