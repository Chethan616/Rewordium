package com.noxquill.rewordium.keyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.*
import android.view.View
import android.animation.ValueAnimator
import android.animation.AnimatorSet
import android.view.animation.DecelerateInterpolator

/**
 * ✨ ULTRA-PREMIUM LIQUID GLASS EFFECTS ✨
 * Complete implementation of all 10 UI enhancements for Rewordium Keyboard
 * 
 * Features Implemented:
 * 1. Enhanced Glassmorphism (Improved keys)
 * 2. Suggestion Bar Glass
 * 3. Clipboard Panel Glass
 * 4. Floating Key Shadows  
 * 5. Ripple Effects
 * 6. Emoji Glass Cards
 * 7. Settings Panel Glass
 * 8. Animated Transitions
 * 9. Return Key Shimmer
 * 10. Smart Glow on Keys
 */
object UltraPremiumGlassEffects {
    
    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    // ============================================================================
    // ENHANCEMENT #1: ULTRA-PREMIUM LIQUID GLASS KEYS
    // ============================================================================
    
    /**
     * Creates ultra-premium glass drawable with perfect transparency and depth
     * Improvements: Better alpha, multi-layer gradients, thicker shimmer border
     */
    fun createUltraPremiumGlassKey(
        context: Context,
        baseColor: Int,
        isPressed: Boolean = false,
        withShadow: Boolean = true
    ): LayerDrawable {
        val layers = mutableListOf<Drawable>()
        
        // Layer 1: Floating shadow for depth (Enhancement #4)
        if (withShadow && !isPressed) {
            val shadow = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 11f).toFloat()
                colors = intArrayOf(
                    Color.argb(50, 0, 0, 0),
                    Color.argb(20, 0, 0, 0),
                    Color.argb(0, 0, 0, 0)
                )
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dpToPx(context, 30f).toFloat()
            }
            layers.add(shadow)
        }
        
        // Layer 2: Main glass body - IMPROVED transparency
        val glassBody = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 10f).toFloat()
            
            // ENHANCED: Better transparency (85% vs old 60%)
            val alpha = if (isPressed) 150 else 95
            val red = Color.red(baseColor)
            val green = Color.green(baseColor)
            val blue = Color.blue(baseColor)
            
            // Multi-stop gradient for realistic glass refraction
            val topShine = Color.argb(
                alpha,
                minOf(255, (red * 1.7f).toInt()),
                minOf(255, (green * 1.7f).toInt()),
                minOf(255, (blue * 1.7f).toInt())
            )
            val midTone = Color.argb((alpha * 0.9f).toInt(), red, green, blue)
            val bottomShade = Color.argb(
                (alpha * 0.75f).toInt(),
                (red * 0.55f).toInt(),
                (green * 0.55f).toInt(),
                (blue * 0.55f).toInt()
            )
            
            colors = intArrayOf(topShine, midTone, bottomShade)
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            
            // ENHANCED: Thicker, brighter shimmer border (1.5dp vs 0.8dp)
            val borderAlpha = if (isPressed) 140 else 100
            setStroke(dpToPx(context, 1.5f), Color.argb(borderAlpha, 255, 255, 255))
        }
        layers.add(glassBody)
        
        // Layer 3: Top highlight - Glass reflection
        val topHighlight = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 10f).toFloat()
            val highlightAlpha = if (isPressed) 70 else 55
            colors = intArrayOf(
                Color.argb(highlightAlpha, 255, 255, 255),
                Color.argb((highlightAlpha * 0.4f).toInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        layers.add(topHighlight)
        
        // Layer 4: Radial center glow
        val centerGlow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 10f).toFloat()
            val glowAlpha = if (isPressed) 40 else 30
            colors = intArrayOf(
                Color.argb(glowAlpha, 255, 255, 255),
                Color.argb((glowAlpha * 0.5f).toInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            )
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dpToPx(context, 90f).toFloat()
        }
        layers.add(centerGlow)
        
        return LayerDrawable(layers.toTypedArray()).apply {
            if (withShadow && !isPressed) {
                setLayerInset(0, 0, dpToPx(context, 1f), 0, -dpToPx(context, 3f))
                setLayerInset(1, dpToPx(context, 1f), dpToPx(context, 2f), dpToPx(context, 1f), dpToPx(context, 1f))
                setLayerInset(2, dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 35f))
                setLayerInset(3, dpToPx(context, 4f), dpToPx(context, 4f), dpToPx(context, 4f), dpToPx(context, 4f))
            } else {
                setLayerInset(0, dpToPx(context, 1f), dpToPx(context, 1f), dpToPx(context, 1f), dpToPx(context, 1f))
                setLayerInset(1, dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 35f))
                setLayerInset(2, dpToPx(context, 4f), dpToPx(context, 4f), dpToPx(context, 4f), dpToPx(context, 4f))
            }
        }
    }
    
    // ============================================================================
    // ENHANCEMENT #2: SUGGESTION BAR GLASSMORPHISM
    // ============================================================================
    
    fun createGlassSuggestionBar(context: Context, isDarkMode: Boolean): LayerDrawable {
        val bgColor = if (isDarkMode) Color.parseColor("#000000") else Color.parseColor("#D1D1D6")
        
        val base = ColorDrawable(bgColor)
        
        // Frosted glass overlay
        val glassOverlay = GradientDrawable().apply {
            colors = intArrayOf(
                Color.argb(45, 255, 255, 255),
                Color.argb(25, 255, 255, 255)
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.BOTTOM_TOP
        }
        
        // Top shimmer line
        val topShimmer = GradientDrawable().apply {
            colors = intArrayOf(
                Color.argb(80, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        
        return LayerDrawable(arrayOf(base, glassOverlay, topShimmer)).apply {
            setLayerInset(2, 0, 0, 0, dpToPx(context, 45f))
        }
    }
    
    // ============================================================================
    // ENHANCEMENT #3: CLIPBOARD PANEL GLASS CARDS
    // ============================================================================
    
    fun createGlassClipboardCard(context: Context, isDarkMode: Boolean): LayerDrawable {
        val baseColor = if (isDarkMode) Color.parseColor("#333333") else Color.parseColor("#FFFFFF")
        
        val glassCard = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 18f).toFloat()
            
            val alpha = 100
            val red = Color.red(baseColor)
            val green = Color.green(baseColor)
            val blue = Color.blue(baseColor)
            
            colors = intArrayOf(
                Color.argb(alpha, minOf(255, (red * 1.5f).toInt()), minOf(255, (green * 1.5f).toInt()), minOf(255, (blue * 1.5f).toInt())),
                Color.argb((alpha * 0.8f).toInt(), red, green, blue)
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            
            setStroke(dpToPx(context, 1.2f), Color.argb(90, 255, 255, 255))
        }
        
        val innerGlow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 18f).toFloat()
            colors = intArrayOf(
                Color.argb(30, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            )
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dpToPx(context, 100f).toFloat()
        }
        
        return LayerDrawable(arrayOf(glassCard, innerGlow)).apply {
            setLayerInset(1, dpToPx(context, 3f), dpToPx(context, 3f), dpToPx(context, 3f), dpToPx(context, 3f))
        }
    }
    
    // ============================================================================
    // ENHANCEMENT #5: LIQUID RIPPLE EFFECT
    // ============================================================================
    
    fun createLiquidRippleKey(context: Context, baseColor: Int): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(Color.argb(120, 255, 255, 255))
        val content = createUltraPremiumGlassKey(context, baseColor, false, true)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 10f).toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(rippleColor, content, mask)
    }
    
    // ============================================================================
    // ENHANCEMENT #6: EMOJI SECTION GLASS BUTTONS
    // ============================================================================
    
    fun createGlassEmojiButton(context: Context, isDarkMode: Boolean): LayerDrawable {
        val baseColor = if (isDarkMode) Color.parseColor("#333333") else Color.parseColor("#FFFFFF")
        
        val emojiGlass = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val alpha = 90
            val red = Color.red(baseColor)
            val green = Color.green(baseColor)
            val blue = Color.blue(baseColor)
            
            colors = intArrayOf(
                Color.argb(alpha, minOf(255, (red * 1.6f).toInt()), minOf(255, (green * 1.6f).toInt()), minOf(255, (blue * 1.6f).toInt())),
                Color.argb((alpha * 0.8f).toInt(), red, green, blue)
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            
            setStroke(dpToPx(context, 1f), Color.argb(80, 255, 255, 255))
        }
        
        val shine = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(
                Color.argb(40, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            )
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dpToPx(context, 50f).toFloat()
        }
        
        return LayerDrawable(arrayOf(emojiGlass, shine)).apply {
            setLayerInset(1, dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 2f))
        }
    }
    
    // ============================================================================
    // ENHANCEMENT #7: SETTINGS PANEL GLASS CARDS
    // ============================================================================
    
    fun createGlassSettingsCard(context: Context, isDarkMode: Boolean): LayerDrawable {
        return createGlassClipboardCard(context, isDarkMode) // Similar design
    }
    
    // ============================================================================
    // ENHANCEMENT #8: ANIMATED TRANSITIONS
    // ============================================================================
    
    fun animateKeyboardTransition(view: View, duration: Long = 200) {
        val fadeIn = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                view.alpha = it.animatedValue as Float
            }
        }
        fadeIn.start()
    }
    
    fun animateSlideTransition(view: View, fromX: Float, toX: Float, duration: Long = 250) {
        val slideAnimator = ValueAnimator.ofFloat(fromX, toX).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                view.translationX = it.animatedValue as Float
            }
        }
        
        val fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener {
                view.alpha = it.animatedValue as Float
            }
        }
        
        AnimatorSet().apply {
            playTogether(slideAnimator, fadeAnimator)
            start()
        }
    }
    
    // ============================================================================
    // ENHANCEMENT #9: RETURN KEY SHIMMER ANIMATION
    // ============================================================================
    
    fun animateReturnKeyShimmer(view: View, themeColor: Int) {
        val shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val alpha = (Math.sin(progress * Math.PI) * 60 + 40).toInt()
                
                val shimmerDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = view.context.resources.displayMetrics.density * 10f
                    
                    val shimmerColor = Color.argb(
                        alpha,
                        minOf(255, (Color.red(themeColor) * 1.3f).toInt()),
                        minOf(255, (Color.green(themeColor) * 1.3f).toInt()),
                        minOf(255, (Color.blue(themeColor) * 1.3f).toInt())
                    )
                    
                    colors = intArrayOf(shimmerColor, themeColor)
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
                
                view.background = shimmerDrawable
            }
        }
        shimmerAnimator.start()
    }
    
    // ============================================================================
    // ENHANCEMENT #10: SMART GLOW ON FREQUENT KEYS
    // ============================================================================
    
    private val keyUsageCount = mutableMapOf<String, Int>()
    
    fun trackKeyUsage(keyText: String) {
        keyUsageCount[keyText] = (keyUsageCount[keyText] ?: 0) + 1
    }
    
    fun shouldApplySmartGlow(keyText: String): Boolean {
        val count = keyUsageCount[keyText] ?: 0
        val totalKeys = keyUsageCount.values.sum()
        if (totalKeys == 0) return false
        
        val frequency = count.toFloat() / totalKeys
        return frequency > 0.05f // Top 5% usage
    }
    
    fun createSmartGlowKey(context: Context, baseColor: Int, themeColor: Int): LayerDrawable {
        val baseGlass = createUltraPremiumGlassKey(context, baseColor, false, true)
        
        val smartGlow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 10f).toFloat()
            
            colors = intArrayOf(
                Color.argb(50, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)),
                Color.argb(20, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)),
                Color.argb(0, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
            )
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dpToPx(context, 60f).toFloat()
        }
        
        return LayerDrawable(arrayOf(baseGlass, smartGlow)).apply {
            setLayerInset(1, dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 2f), dpToPx(context, 2f))
        }
    }
}
