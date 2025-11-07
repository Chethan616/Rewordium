package com.noxquill.rewordium.keyboard.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Provides smooth, performance-aware animations
 * Based on FlorisBoard's animation system
 */
object AnimationHelper {
    
    // Standard durations
    const val DURATION_FAST = 150L
    const val DURATION_NORMAL = 250L
    const val DURATION_SLOW = 350L
    
    /**
     * Slide view in from bottom with deceleration
     */
    fun slideInFromBottom(
        view: View,
        duration: Long = DURATION_NORMAL,
        performanceMonitor: PerformanceMonitor? = null
    ): AnimatorSet {
        val adjustedDuration = adjustDuration(duration, performanceMonitor)
        
        view.alpha = 0f
        view.translationY = view.height.toFloat()
        
        val translateAnim = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f).apply {
            this.duration = adjustedDuration
            interpolator = DecelerateInterpolator(2f)
        }
        
        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f).apply {
            this.duration = adjustedDuration
            interpolator = LinearInterpolator()
        }
        
        return AnimatorSet().apply {
            playTogether(translateAnim, alphaAnim)
        }
    }
    
    /**
     * Slide view out to bottom with acceleration
     */
    fun slideOutToBottom(
        view: View,
        duration: Long = DURATION_FAST,
        performanceMonitor: PerformanceMonitor? = null,
        onComplete: (() -> Unit)? = null
    ): AnimatorSet {
        val adjustedDuration = adjustDuration(duration, performanceMonitor)
        
        val translateAnim = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.height.toFloat()).apply {
            this.duration = adjustedDuration
            interpolator = AccelerateInterpolator(2f)
        }
        
        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f).apply {
            this.duration = adjustedDuration
            interpolator = LinearInterpolator()
        }
        
        return AnimatorSet().apply {
            playTogether(translateAnim, alphaAnim)
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete?.invoke()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
    }
    
    /**
     * Fade in view smoothly
     */
    fun fadeIn(
        view: View,
        duration: Long = DURATION_NORMAL,
        performanceMonitor: PerformanceMonitor? = null
    ): ObjectAnimator {
        val adjustedDuration = adjustDuration(duration, performanceMonitor)
        view.alpha = 0f
        
        return ObjectAnimator.ofFloat(view, View.ALPHA, 1f).apply {
            this.duration = adjustedDuration
            interpolator = DecelerateInterpolator()
        }
    }
    
    /**
     * Fade out view smoothly
     */
    fun fadeOut(
        view: View,
        duration: Long = DURATION_FAST,
        performanceMonitor: PerformanceMonitor? = null,
        onComplete: (() -> Unit)? = null
    ): ObjectAnimator {
        val adjustedDuration = adjustDuration(duration, performanceMonitor)
        
        return ObjectAnimator.ofFloat(view, View.ALPHA, 0f).apply {
            this.duration = adjustedDuration
            interpolator = AccelerateInterpolator()
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete?.invoke()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
    }
    
    /**
     * Scale and fade in with overshoot (popup effect)
     */
    fun popIn(
        view: View,
        duration: Long = DURATION_NORMAL,
        performanceMonitor: PerformanceMonitor? = null
    ): AnimatorSet {
        val adjustedDuration = adjustDuration(duration, performanceMonitor)
        
        view.alpha = 0f
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f).apply {
            this.duration = adjustedDuration
            interpolator = OvershootInterpolator(1.5f)
        }
        
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f).apply {
            this.duration = adjustedDuration
            interpolator = OvershootInterpolator(1.5f)
        }
        
        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f).apply {
            this.duration = adjustedDuration
            interpolator = LinearInterpolator()
        }
        
        return AnimatorSet().apply {
            playTogether(scaleXAnim, scaleYAnim, alphaAnim)
        }
    }
    
    /**
     * Key press ripple effect
     */
    fun keyPressRipple(
        view: View,
        performanceMonitor: PerformanceMonitor? = null
    ): AnimatorSet {
        val adjustedDuration = adjustDuration(DURATION_FAST, performanceMonitor)
        
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.95f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.95f)
            )
            duration = adjustedDuration / 2
            interpolator = AccelerateInterpolator()
        }
        
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f)
            )
            duration = adjustedDuration / 2
            interpolator = DecelerateInterpolator()
        }
        
        return AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
        }
    }
    
    /**
     * Adjust animation duration based on performance
     */
    private fun adjustDuration(duration: Long, performanceMonitor: PerformanceMonitor?): Long {
        if (performanceMonitor == null) return duration
        
        val multiplier = performanceMonitor.getAnimationDurationMultiplier()
        return (duration * multiplier).toLong()
    }
    
    /**
     * Cancel all animations on view
     */
    fun cancelAnimations(view: View) {
        view.animate().cancel()
    }
}
