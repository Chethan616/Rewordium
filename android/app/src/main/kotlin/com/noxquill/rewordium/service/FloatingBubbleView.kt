package com.noxquill.rewordium.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.content.SharedPreferences
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.airbnb.lottie.LottieAnimationView
import com.noxquill.rewordium.R
import kotlin.math.abs

interface BubbleInteractionListener {
    fun onBubbleTapped()
    fun onBubbleMoved(x: Int, y: Int)
}

class FloatingBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
    private val PREF_X = "bubble_x"
    private val PREF_Y = "bubble_y"
    val wmParams: WindowManager.LayoutParams
    private val bubbleImageView: LottieAnimationView
    private val bubbleSizePx: Int
    var listener: BubbleInteractionListener? = null

    // --- NEW: Constants for animations and state ---
    private val DRAG_THRESHOLD_PX = 15
    private val ANIMATION_DURATION = 250L
    private val IDLE_TIMEOUT_MS = 4500L
    private val IDLE_ALPHA = 0.6f
    private val IDLE_SCALE = 0.75f
    private val ROTATION_DURATION = 3000L // 3 seconds for full rotation

    // --- NEW: State tracking, idle timer, and rotation animator ---
    private val idleHandler = Handler(Looper.getMainLooper())
    private var isIdle = false
    private val idleRunnable = Runnable { enterIdleState() }
    private var rotationAnimator: ObjectAnimator? = null

    // Dragging Info
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    init {
        LayoutInflater.from(context).inflate(R.layout.floating_assistant_layout, this, true)
        bubbleImageView = findViewById<LottieAnimationView>(R.id.bubble_image_view)
        bubbleSizePx = (48 * context.resources.displayMetrics.density).toInt()

        val screenHeight = context.resources.displayMetrics.heightPixels
        val screenWidth = context.resources.displayMetrics.widthPixels

        // Load saved position or use default left edge position
        val savedX = prefs.getInt(PREF_X, 0)
        val savedY = prefs.getInt(PREF_Y, screenHeight / 2 - bubbleSizePx / 2)
        
        wmParams = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        
        // --- NEW: Start the idle timer when the bubble is created ---
        resetIdleTimer()
        
        // --- NEW: Start continuous clockwise rotation ---
        startRotationAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // --- NEW: Any touch stops the idle timer and "wakes up" the bubble ---
                cancelIdleTimer()
                if (isIdle) {
                    exitIdleState()
                }

                isDragging = false
                initialX = wmParams.x
                initialY = wmParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val xDiff = abs(event.rawX - initialTouchX)
                val yDiff = abs(event.rawY - initialTouchY)
                if (!isDragging && (xDiff > DRAG_THRESHOLD_PX || yDiff > DRAG_THRESHOLD_PX)) {
                    isDragging = true
                }
                if (isDragging) {
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = (initialY + (event.rawY - initialTouchY).toInt())
                        .coerceIn(0, context.resources.displayMetrics.heightPixels - bubbleSizePx)
                    updatePosition(newX, newY)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    // Save the final position after dragging
                    savePosition(wmParams.x, wmParams.y)
                    // Animate to the nearest edge after dragging
                    animateToEdge()
                } else {
                    // It's a tap, the service will handle the UI, so don't restart the idle timer here.
                    // The service will call resetIdleTimer() when the bottom sheet closes.
                    listener?.onBubbleTapped()
                }
                return true
            }
        }
        return false
    }

    // --- NEW: Public functions to be called by the Service ---
    fun resetIdleTimer() {
        cancelIdleTimer()
        idleHandler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
    }

    fun cancelIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
    }

    // --- NEW: Animation logic ---
    private fun startRotationAnimation() {
        rotationAnimator = ObjectAnimator.ofFloat(bubbleImageView, "rotation", 0f, 360f).apply {
            duration = ROTATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
    }
    
    private fun stopRotationAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    private fun enterIdleState() {
        if (isIdle) return
        isIdle = true
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(bubbleImageView, "alpha", 1f, IDLE_ALPHA),
                ObjectAnimator.ofFloat(bubbleImageView, "scaleX", 1f, IDLE_SCALE),
                ObjectAnimator.ofFloat(bubbleImageView, "scaleY", 1f, IDLE_SCALE)
            )
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Animation ended for idle state
                }
            })
            start()
        }
    }

    private fun exitIdleState() {
        if (!isIdle) return
        isIdle = false
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(bubbleImageView, "alpha", IDLE_ALPHA, 1f),
                ObjectAnimator.ofFloat(bubbleImageView, "scaleX", IDLE_SCALE, 1f),
                ObjectAnimator.ofFloat(bubbleImageView, "scaleY", IDLE_SCALE, 1f)
            )
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Animation ended for exit idle state
                }
            })
            start()
        }
    }

    private fun updatePosition(x: Int, y: Int) {
        wmParams.x = x
        wmParams.y = y
        windowManager.updateViewLayout(this, wmParams)
        listener?.onBubbleMoved(x, y)
    }
    
    private fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(PREF_X, x)
            .putInt(PREF_Y, y)
            .apply()
    }
    
    private fun animateToEdge() {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val centerOfBubble = wmParams.x + bubbleSizePx / 2
        
        val targetX = if (centerOfBubble < screenWidth / 2) {
            0 // Snap to left edge
        } else {
            screenWidth - bubbleSizePx // Snap to right edge
        }

        val animator = ValueAnimator.ofInt(wmParams.x, targetX)
        animator.duration = ANIMATION_DURATION
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val newX = animation.animatedValue as Int
            updatePosition(newX, wmParams.y)
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Save the final position after animation completes
                savePosition(wmParams.x, wmParams.y)
                resetIdleTimer()
            }
        })
        animator.start()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            // Resume animation when visible
            bubbleImageView.resumeAnimation()
            startRotationAnimation()
        } else {
            // Pause animation when not visible to save resources
            bubbleImageView.pauseAnimation()
            stopRotationAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up the handler when the view is removed
        cancelIdleTimer()
        // Stop rotation animation
        stopRotationAnimation()
        // Stop and clear the Lottie animation to prevent memory leaks
        bubbleImageView.cancelAnimation()
        bubbleImageView.setImageDrawable(null)
    }
}