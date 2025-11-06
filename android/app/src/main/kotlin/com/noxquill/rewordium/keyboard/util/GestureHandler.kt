package com.noxquill.rewordium.keyboard.util

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.abs

/**
 * Handles gesture detection for keyboard panels
 * Inspired by FlorisBoard's smooth gesture handling
 */
class GestureHandler(
    private val motionEventPool: ViewPool.ObjectPool<MotionEvent> = ViewPool.ObjectPool(
        maxPoolSize = 50,
        factory = { MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0) }
    )
) {
    private var velocityTracker: VelocityTracker? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    
    // Gesture thresholds
    private val SWIPE_THRESHOLD = 100f
    private val SWIPE_VELOCITY_THRESHOLD = 1000f
    private val TOUCH_SLOP = 8f
    
    interface GestureListener {
        fun onSwipeDown(velocity: Float) {}
        fun onSwipeUp(velocity: Float) {}
        fun onSwipeLeft(velocity: Float) {}
        fun onSwipeRight(velocity: Float) {}
        fun onDragStart(x: Float, y: Float) {}
        fun onDrag(deltaX: Float, deltaY: Float) {}
        fun onDragEnd() {}
    }
    
    fun handleTouch(view: View, event: MotionEvent, listener: GestureListener): Boolean {
        // Pool motion events for recycling
        val pooledEvent = motionEventPool.acquire()
        
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                isDragging = false
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - initialTouchX
                val deltaY = event.y - initialTouchY
                
                if (!isDragging && (abs(deltaX) > TOUCH_SLOP || abs(deltaY) > TOUCH_SLOP)) {
                    isDragging = true
                    listener.onDragStart(initialTouchX, initialTouchY)
                }
                
                if (isDragging) {
                    listener.onDrag(deltaX, deltaY)
                }
                
                return true
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    listener.onDragEnd()
                }
                
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val velocityY = velocityTracker?.yVelocity ?: 0f
                
                val deltaX = event.x - initialTouchX
                val deltaY = event.y - initialTouchY
                
                // Detect swipe gestures
                when {
                    abs(deltaY) > abs(deltaX) && abs(deltaY) > SWIPE_THRESHOLD -> {
                        if (deltaY > 0 && velocityY > SWIPE_VELOCITY_THRESHOLD) {
                            listener.onSwipeDown(velocityY)
                        } else if (deltaY < 0 && velocityY < -SWIPE_VELOCITY_THRESHOLD) {
                            listener.onSwipeUp(velocityY)
                        }
                    }
                    abs(deltaX) > SWIPE_THRESHOLD -> {
                        if (deltaX > 0 && velocityX > SWIPE_VELOCITY_THRESHOLD) {
                            listener.onSwipeRight(velocityX)
                        } else if (deltaX < 0 && velocityX < -SWIPE_VELOCITY_THRESHOLD) {
                            listener.onSwipeLeft(velocityX)
                        }
                    }
                }
                
                velocityTracker?.recycle()
                velocityTracker = null
                motionEventPool.release(pooledEvent)
                isDragging = false
                return true
            }
        }
        
        return false
    }
    
    fun reset() {
        velocityTracker?.recycle()
        velocityTracker = null
        isDragging = false
    }
}
