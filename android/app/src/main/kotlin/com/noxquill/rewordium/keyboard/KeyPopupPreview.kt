package com.noxquill.rewordium.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noxquill.rewordium.keyboard.RewordiumAIKeyboardService

/**
 * 🎯 KEY POPUP PREVIEW
 * Shows a beautiful glass-style popup above the key when pressed
 * Matches iOS/Gboard behavior with professional glass morphism
 */
class KeyPopupPreview(
    private val context: Context,
    private val service: RewordiumAIKeyboardService
) {
    private var popupView: ComposeView? = null
    private var currentAnimator: ValueAnimator? = null
    private val density = context.resources.displayMetrics.density

    private fun Float.dpToPx(): Float = this * density
    
    /**
     * Show popup preview above a key
     */
    fun show(text: String, x: Float, y: Float, keyHeight: Float, isDarkMode: Boolean) {
        android.util.Log.d("KeyPopupPreview", "🎯 show() called for text='$text' at ($x, $y)")
        hide() // Hide any existing popup
        
        // Create popup view
        popupView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            
            setContent {
                KeyPopupContent(
                    text = text,
                    isDarkMode = isDarkMode
                )
            }
            
            // Set layout parameters for positioning
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            
            // 🎯 POSITION ABOVE KEY CENTER - Gboard-style (no overlap!)
            val popupDiameter = 56f.dpToPx()
            val popupRadius = popupDiameter / 2f
            val horizontalOffset = popupRadius
            val gap = 6f.dpToPx()

            val keyTop = y - (keyHeight / 2f)
            val popupLeft = x - horizontalOffset
            val popupTop = keyTop - gap - popupDiameter

            this.x = popupLeft
            this.y = popupTop
            
            // 🎯 CRITICAL: Set high elevation to ensure popup appears above all keys
            this.elevation = 100f
            this.translationZ = 100f
            
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }
        
        // Add to keyboard view
        try {
            val rootView = service.window?.window?.decorView as? ViewGroup
            rootView?.addView(popupView)
            
            // Animate in - faster for snappier feel
            currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 80  // Faster animation (was 100)
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    popupView?.alpha = value
                    popupView?.scaleX = 0.8f + (0.2f * value)
                    popupView?.scaleY = 0.8f + (0.2f * value)
                }
                start()
            }
        } catch (e: Exception) {
            // Silently fail if unable to show popup
        }
    }
    
    /**
     * Hide popup preview
     */
    fun hide() {
        currentAnimator?.cancel()
        currentAnimator = null
        
        popupView?.let { popup ->
            // Animate out - faster for snappier feel
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 60  // Faster hide (was 80)
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    popup.alpha = value
                    popup.scaleX = 0.8f + (0.2f * value)
                    popup.scaleY = 0.8f + (0.2f * value)
                }
                start()
            }
            
            // Remove after animation
            popup.postDelayed({
                try {
                    (popup.parent as? ViewGroup)?.removeView(popup)
                } catch (e: Exception) {
                    // Silently fail
                }
            }, 80)
        }
        
        popupView = null
    }
}

/**
 * Compose content for the key popup
 */
@Composable
private fun KeyPopupContent(
    text: String,
    isDarkMode: Boolean
) {
    val backgroundColor = if (isDarkMode) {
        Color(0xFF333333)  // Match glass key dark color
    } else {
        Color(0xFFFFFFFF)  // Match glass key light color
    }
    
    val textColor = if (isDarkMode) Color.White else Color.Black
    
    // 🎯 CIRCULAR POPUP - Gboard-style round preview
    Box(
        modifier = Modifier
            .size(56.dp)  // Perfect circle
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,  // CIRCULAR!
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.3f)
            )
            .background(
                color = backgroundColor.copy(alpha = 0.95f),  // Match glass effect
                shape = CircleShape  // CIRCULAR!
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = 0.95f),
            fontSize = 26.sp,  // Slightly larger than key text
            fontWeight = FontWeight.SemiBold
        )
    }
}
