package com.noxquill.rewordium.keyboard

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.LinearLayout
import com.noxquill.rewordium.keyboard.gesture.SwipeGestureEngine
import com.noxquill.rewordium.keyboard.util.KeyboardConstants
import kotlin.math.*

/**
 * SWIPE TYPING FIX: Custom LinearLayout that intercepts touch events for swipe gesture detection
 * This solves the issue where individual key views consume touch events before gestures can be detected
 */
class SwipeInterceptorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private var gestureEngine: SwipeGestureEngine? = null
    private var layoutManager: Any? = null
    
    // Touch tracking for swipe detection
    private var initialX = 0f
    private var initialY = 0f
    private var isInterceptingGesture = false
    private var currentGestureId = 0L
    private val swipeThreshold = 40f // Minimum distance to start intercepting
    
    fun setGestureEngine(engine: SwipeGestureEngine?, manager: Any?) {
        this.gestureEngine = engine
        this.layoutManager = manager
        Log.d(KeyboardConstants.TAG, "🎯 SwipeInterceptorLayout: Gesture engine ${if (engine != null) "SET" else "CLEARED"}")
    }
    
    /**
     * Check if touch coordinates are in the emoji category tabs area
     * This allows horizontal scrolling specifically for emoji category switching
     */
    private fun isInEmojiTabsArea(x: Float, y: Float): Boolean {
        val service = context as? RewordiumAIKeyboardService
        if (service == null || !service.isEmojiKeyboardVisible()) {
            return false
        }
        
        // Emoji tabs are typically in the top ~48dp of the emoji keyboard
        val tabsHeight = 48 * resources.displayMetrics.density // Convert 48dp to pixels
        val isInTabsArea = y <= tabsHeight
        
        Log.v(KeyboardConstants.TAG, "🎯 Emoji tabs check: y=$y, tabsHeight=$tabsHeight, inTabsArea=$isInTabsArea")
        return isInTabsArea
    }
    
    /**
     * Check if gesture is a horizontal swipe (for emoji category switching)
     */
    private fun isHorizontalSwipe(deltaX: Float, deltaY: Float): Boolean {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        
        // Consider it horizontal if X movement is significantly more than Y movement
        val isHorizontal = absX > absY && absX > swipeThreshold
        Log.v(KeyboardConstants.TAG, "🎯 Swipe direction check: deltaX=$deltaX, deltaY=$deltaY, isHorizontal=$isHorizontal")
        return isHorizontal
    }
    
    /**
     * Check if gesture is a vertical swipe down (system dismiss gesture)
     */
    private fun isSwipeDown(deltaX: Float, deltaY: Float): Boolean {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        
        // Consider it swipe down if Y movement is significantly more than X movement and downward
        val isSwipeDown = deltaY > swipeThreshold && absY > absX * 1.5f
        Log.v(KeyboardConstants.TAG, "🎯 Swipe down check: deltaX=$deltaX, deltaY=$deltaY, isSwipeDown=$isSwipeDown")
        return isSwipeDown
    }
    
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Safety check: If no gesture engine, don't intercept
        if (gestureEngine == null) {
            return super.onInterceptTouchEvent(event)
        }
                
        return try {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Always let the DOWN event pass through initially
                    initialX = event.x
                    initialY = event.y
                    isInterceptingGesture = false
                    Log.v(KeyboardConstants.TAG, "� Touch down at (${event.x}, ${event.y}) - not intercepting yet")
                    false
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (!isInterceptingGesture) {
                        // Check if this looks like a swipe gesture
                        val deltaX = event.x - initialX
                        val deltaY = event.y - initialY
                        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                        
                        if (distance > swipeThreshold) {
                            val service = context as? RewordiumAIKeyboardService
                            
                            // PREVENT SWIPE DOWN DISMISSAL: Block swipe down gestures to prevent keyboard closing
                            val isSwipeDownGesture = isSwipeDown(deltaX, deltaY)
                            if (isSwipeDownGesture) {
                                Log.d(KeyboardConstants.TAG, "🚫 BLOCKED: Swipe down gesture intercepted to prevent keyboard dismissal")
                                return true // INTERCEPT to block the swipe down and prevent keyboard closing
                            }
                            
                            // SMART EMOJI BLOCKING: Allow horizontal swipes in tabs area, block all others
                            if (service?.isEmojiKeyboardVisible() == true) {
                                val inTabsArea = isInEmojiTabsArea(initialX, initialY)
                                val isHorizontal = isHorizontalSwipe(deltaX, deltaY)
                                
                                Log.d(KeyboardConstants.TAG, "🎯 Emoji gesture analysis: inTabsArea=$inTabsArea, isHorizontal=$isHorizontal at (${initialX}, ${initialY})")
                                
                                if (inTabsArea && isHorizontal) {
                                    // Allow horizontal swipe in emoji tabs area - don't intercept
                                    Log.d(KeyboardConstants.TAG, "✅ ALLOWED: Horizontal swipe in emoji tabs area")
                                    return false
                                } else {
                                    // Block all other gestures when emoji keyboard is visible
                                    Log.d(KeyboardConstants.TAG, "🚫 BLOCKED: Gesture prevented - emoji keyboard is visible (inTabs=$inTabsArea, isHoriz=$isHorizontal)")
                                    return true // INTERCEPT to block the gesture
                                }
                            }
                            
                            // Normal gesture processing for main keyboard
                            isInterceptingGesture = true
                            Log.d(KeyboardConstants.TAG, "🎯 SWIPE DETECTED: Intercepting gesture (distance: ${distance}px)")
                            
                            // Start the gesture in the gesture engine
                            try {
                                val downEvent = MotionEvent.obtain(
                                    event.downTime, event.downTime, MotionEvent.ACTION_DOWN,
                                    initialX, initialY, 0
                                )
                                currentGestureId = gestureEngine?.startGesture(downEvent) ?: 0L
                                downEvent.recycle()
                                
                                // Add current point
                                gestureEngine?.addGesturePoint(currentGestureId, event)
                            } catch (e: Exception) {
                                Log.e(KeyboardConstants.TAG, "Error starting gesture: ${e.message}")
                                isInterceptingGesture = false
                                return false
                            }
                            return true // Start intercepting from now on
                        }
                    }
                    
                    // If already intercepting, continue processing
                    if (isInterceptingGesture) {
                        try {
                            gestureEngine?.addGesturePoint(currentGestureId, event)
                        } catch (e: Exception) {
                            Log.e(KeyboardConstants.TAG, "Error processing gesture move: ${e.message}")
                            isInterceptingGesture = false
                            return false
                        }
                        return true
                    }
                    
                    false // Let children handle it
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isInterceptingGesture) {
                        Log.d(KeyboardConstants.TAG, "🎯 Gesture completed/cancelled")
                        try {
                            if (event.action == MotionEvent.ACTION_UP) {
                                gestureEngine?.endGesture(currentGestureId, event)
                            } else {
                                gestureEngine?.cancelGesture(currentGestureId)
                            }
                        } catch (e: Exception) {
                            Log.e(KeyboardConstants.TAG, "Error completing gesture: ${e.message}")
                        }
                        isInterceptingGesture = false
                        return true
                    }
                    false
                }
                
                else -> false
            }
        } catch (e: Exception) {
            Log.e(KeyboardConstants.TAG, "Error in touch interception: ${e.message}")
            isInterceptingGesture = false
            false
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If we're intercepting, handle the event
        if (isInterceptingGesture && gestureEngine != null) {
            Log.v(KeyboardConstants.TAG, "📱 Handling intercepted touch event: ${event.action}")
            return try {
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        gestureEngine?.addGesturePoint(currentGestureId, event)
                    }
                    MotionEvent.ACTION_UP -> {
                        gestureEngine?.endGesture(currentGestureId, event)
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        gestureEngine?.cancelGesture(currentGestureId)
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(KeyboardConstants.TAG, "Error handling intercepted touch: ${e.message}")
                isInterceptingGesture = false
                false
            }
        }
        
        return super.onTouchEvent(event)
    }
}
