package com.example.yc_startup.service

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PixelFormat
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.example.yc_startup.R

class BubbleManager(
    private val context: Context,
    private val listener: BubbleListener
) {
    companion object {
        private const val TAG = "BubbleManager"
        private const val VISIBILITY_DEBOUNCE_MS = 100L
        private const val PREFS_NAME = "BubblePrefs"
        private const val BUBBLE_X = "bubble_x"
        private const val BUBBLE_Y = "bubble_y"
        private const val DEFAULT_X = 100
        private const val DEFAULT_Y = 300
    }

    private val handler = Handler(Looper.getMainLooper())
    private var visibilityUpdater: Runnable? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val gestureDetector: GestureDetector
    private val menuManager: BubbleMenuManager by lazy {
        BubbleMenuManager(context, windowManager, listener)
    }

    init {
        gestureDetector = GestureDetector(context, GestureListener())
    }

    fun updateVisibility(isFocusOnTarget: Boolean) {
        visibilityUpdater?.let { handler.removeCallbacks(it) }
        visibilityUpdater = Runnable {
            if (isFocusOnTarget) {
                performShow()
            } else {
                performHide()
            }
        }
        handler.postDelayed(visibilityUpdater!!, VISIBILITY_DEBOUNCE_MS)
    }

    private fun performShow() {
        if (bubbleView?.parent != null) return
        Log.d(TAG, "Executing performShow()")
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        bubbleView = inflater.inflate(R.layout.bubble_layout, null).apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            setOnTouchListener { _, event -> onTouch(event) }
        }

        // --- NEW: APPLY THE GRADIENT TO THE TEXTVIEW ---
        val aiTextView = bubbleView?.findViewById<TextView>(R.id.ai_text_view)
        aiTextView?.let { applyGradientToText(it) }
        // ---

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else { WindowManager.LayoutParams.TYPE_PHONE }
        val (savedX, savedY) = loadBubblePosition()
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX; y = savedY
        }
        try {
            windowManager.addView(bubbleView, bubbleParams)
            bubbleView?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)
                ?.setDuration(200)?.setInterpolator(DecelerateInterpolator())?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding bubble view", e)
            bubbleView = null
        }
    }

    // --- NEW: HELPER FUNCTION TO APPLY THE GRADIENT ---
    private fun applyGradientToText(textView: TextView) {
        // We need to wait for the view to be laid out to get its width.
        textView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Remove the listener to prevent it from running multiple times
                textView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val paint = textView.paint
                val width = textView.measuredWidth.toFloat()
                val height = textView.measuredHeight.toFloat()

                // Define the bluish-purple gradient colors
                val color1 = Color.parseColor("#8A2BE2") // BlueViolet
                val color2 = Color.parseColor("#4169E1") // RoyalBlue

                val shader = LinearGradient(
                    0f, 0f, width, height,
                    color1, color2,
                    Shader.TileMode.CLAMP
                )
                paint.shader = shader
            }
        })
    }

    private fun performHide() {
        val viewToHide = bubbleView
        if (viewToHide?.parent == null) return
        Log.d(TAG, "Executing performHide()")
        menuManager.hideMenu {
            viewToHide.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                .setDuration(200).setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (viewToHide.parent != null) {
                        windowManager.removeView(viewToHide)
                    }
                    if (bubbleView === viewToHide) {
                        bubbleView = null
                        bubbleParams = null
                    }
                }.start()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            isDragging = true
            bubbleView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            bubbleView?.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(150)?.start()
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (menuManager.isMenuVisible()) {
                menuManager.hideMenu()
            } else {
                bubbleParams?.let { params ->
                    val bubbleCenterX = params.x + (bubbleView?.width ?: 0) / 2
                    val bubbleBottomY = params.y + (bubbleView?.height ?: 0)
                    menuManager.showMenu(bubbleCenterX, bubbleBottomY)
                }
            }
            return true
        }
    }

    private fun onTouch(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            visibilityUpdater?.let { handler.removeCallbacks(it) }
            visibilityUpdater = null
        }
        val gestureConsumed = gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = bubbleParams?.x ?: 0; initialY = bubbleParams?.y ?: 0
                initialTouchX = event.rawX; initialTouchY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> if (isDragging) {
                bubbleParams?.let {
                    it.x = initialX + (event.rawX - initialTouchX).toInt()
                    it.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, it)
                    menuManager.updateMenuPosition(it, bubbleView)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (isDragging) {
                isDragging = false
                bubbleParams?.let { saveBubblePosition(it.x, it.y) }
                bubbleView?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(150)?.start()
                return true
            }
        }
        return gestureConsumed
    }

    fun cleanUp() {
        visibilityUpdater?.let { handler.removeCallbacks(it) }
        bubbleView?.animate()?.cancel()
        if (bubbleView?.parent != null) {
            windowManager.removeView(bubbleView)
        }
        bubbleView = null
        menuManager.cleanup()
    }

    private fun saveBubblePosition(x: Int, y: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(BUBBLE_X, x).putInt(BUBBLE_Y, y).apply()
    }

    private fun loadBubblePosition(): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Pair(prefs.getInt(BUBBLE_X, DEFAULT_X), prefs.getInt(BUBBLE_Y, DEFAULT_Y))
    }
}