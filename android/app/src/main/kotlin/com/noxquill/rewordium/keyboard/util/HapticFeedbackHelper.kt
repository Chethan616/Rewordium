package com.noxquill.rewordium.keyboard.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Enhanced haptic feedback with varied patterns
 * Based on FlorisBoard's sophisticated haptic system
 */
class HapticFeedbackHelper(private val context: Context) {
    
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    
    enum class FeedbackType {
        LIGHT,      // Soft tap for normal keys
        MEDIUM,     // Standard tap for most actions
        HEAVY,      // Strong tap for important actions
        DOUBLE,     // Double tap pattern
        SUCCESS,    // Success confirmation
        ERROR,      // Error indication
        LONG_PRESS  // Long press detected
    }
    
    /**
     * Perform haptic feedback with specified type
     */
    fun performHaptic(view: View?, type: FeedbackType = FeedbackType.MEDIUM) {
        if (vibrator?.hasVibrator() != true) return
        
        when (type) {
            FeedbackType.LIGHT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(10)
                }
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            
            FeedbackType.MEDIUM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            }
            
            FeedbackType.HEAVY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            
            FeedbackType.DOUBLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 20, 50, 20)
                    val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 20, 50, 20), -1)
                }
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            
            FeedbackType.SUCCESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 15, 30, 15)
                    val amplitudes = intArrayOf(0, 120, 0, 180)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 15, 30, 15), -1)
                }
                view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            
            FeedbackType.ERROR -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 30, 50, 30, 50, 30)
                    val amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 30, 50, 30, 50, 30), -1)
                }
                view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
            
            FeedbackType.LONG_PRESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, 200))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }
    
    /**
     * Perform key press feedback (optimized for frequent calls)
     */
    fun performKeyPress(view: View?) {
        performHaptic(view, FeedbackType.LIGHT)
    }
    
    /**
     * Perform special key feedback (space, enter, delete)
     */
    fun performSpecialKey(view: View?) {
        performHaptic(view, FeedbackType.MEDIUM)
    }
    
    /**
     * Perform gesture feedback
     */
    fun performGesture(view: View?) {
        performHaptic(view, FeedbackType.LIGHT)
    }
    
    /**
     * Check if device supports haptics
     */
    fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }
}
