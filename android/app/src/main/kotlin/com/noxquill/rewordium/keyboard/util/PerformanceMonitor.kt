package com.noxquill.rewordium.keyboard.util

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Monitors keyboard performance and adapts rendering quality
 * Based on FlorisBoard's adaptive rendering approach
 */
class PerformanceMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var lastFrameTime = System.currentTimeMillis()
    private var currentFps = 60f
    
    // Performance thresholds
    private val TARGET_FPS = 60f
    private val LOW_FPS_THRESHOLD = 45f
    private val CRITICAL_FPS_THRESHOLD = 30f
    
    // Adaptive quality settings
    var renderQuality = RenderQuality.HIGH
        private set
    
    enum class RenderQuality {
        HIGH,       // 60fps - full animations
        MEDIUM,     // 45-60fps - reduced animations
        LOW         // <45fps - minimal animations
    }
    
    private val fpsCheckRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastFrameTime
            
            if (elapsed > 0) {
                currentFps = (frameCount * 1000f) / elapsed
                
                // Adapt render quality based on FPS
                renderQuality = when {
                    currentFps >= LOW_FPS_THRESHOLD -> RenderQuality.HIGH
                    currentFps >= CRITICAL_FPS_THRESHOLD -> RenderQuality.MEDIUM
                    else -> RenderQuality.LOW
                }
                
                // Only log if FPS is critically low (disabled to reduce log spam)
                // if (currentFps < CRITICAL_FPS_THRESHOLD) {
                //     Log.d(KeyboardConstants.TAG, "⚡ Performance: FPS=$currentFps, Quality=$renderQuality")
                // }
            }
            
            // Reset counters
            frameCount = 0
            lastFrameTime = currentTime
            
            // Check again in 1 second
            handler.postDelayed(this, 1000)
        }
    }
    
    fun start() {
        handler.post(fpsCheckRunnable)
    }
    
    fun stop() {
        handler.removeCallbacks(fpsCheckRunnable)
    }
    
    fun recordFrame() {
        frameCount++
    }
    
    fun getCurrentFps(): Float = currentFps
    
    /**
     * Check if animations should be simplified
     */
    fun shouldReduceAnimations(): Boolean {
        return renderQuality != RenderQuality.HIGH
    }
    
    /**
     * Get animation duration multiplier based on performance
     */
    fun getAnimationDurationMultiplier(): Float {
        return when (renderQuality) {
            RenderQuality.HIGH -> 1f
            RenderQuality.MEDIUM -> 0.7f
            RenderQuality.LOW -> 0.5f
        }
    }
}
