package com.example.yc_startup.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.yc_startup.R

class BubbleMenuManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val listener: BubbleListener
) {
    companion object {
        private const val TAG = "BubbleMenuManager"
        private const val MENU_WIDTH_DP = 240
    }

    private enum class MenuState { HIDDEN, SHOWING, VISIBLE, HIDING }
    private var currentState = MenuState.HIDDEN

    private var menuWrapperView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private val actionMap = mapOf(
        R.id.ai_translator to MenuAction.Translate,
        R.id.ai_summary to MenuAction.Summarize,
        R.id.ai_grammar_check to MenuAction.GrammarCheck
    )

    fun isMenuVisible(): Boolean = currentState == MenuState.VISIBLE || currentState == MenuState.SHOWING

    fun showMenu(bubbleCenterX: Int, bubbleBottomY: Int) {
        if (currentState != MenuState.HIDDEN) return
        currentState = MenuState.SHOWING

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        menuWrapperView = inflater.inflate(R.layout.menu_layout, null)

        val cardContent = menuWrapperView?.findViewById<View>(R.id.menu_card_content) ?: run {
            Log.e(TAG, "Could not find menu_card_content in layout!")
            currentState = MenuState.HIDDEN
            return
        }

        actionMap.forEach { (viewId, action) ->
            cardContent.findViewById<View>(viewId)?.setOnClickListener {
                listener.onPerformAction(action)
                hideMenu()
            }
        }

        val menuWidth = dpToPx(MENU_WIDTH_DP)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        menuParams = WindowManager.LayoutParams(
            menuWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleCenterX - menuWidth / 2
            y = bubbleBottomY + dpToPx(8)
        }

        try {
            windowManager.addView(menuWrapperView, menuParams)
            adjustPositionForScreenEdges()

            cardContent.apply {
                alpha = 0f
                scaleX = 0.8f
                scaleY = 0.8f
                pivotY = 0f
                pivotX = (width / 2).toFloat()
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .withLayer()
                    .withEndAction {
                        currentState = MenuState.VISIBLE
                    }
                    .start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing menu", e)
            cleanup()
        }
    }

    fun hideMenu(onHidden: (() -> Unit)? = null) {
        if (currentState != MenuState.VISIBLE) {
            if (currentState == MenuState.HIDDEN || currentState == MenuState.HIDING) {
                onHidden?.invoke()
            }
            return
        }
        currentState = MenuState.HIDING

        val wrapperView = menuWrapperView
        val cardContent = wrapperView?.findViewById<View>(R.id.menu_card_content)

        if (wrapperView == null || cardContent == null) {
            currentState = MenuState.HIDDEN
            onHidden?.invoke()
            return
        }

        cardContent.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withLayer()
            .withEndAction {
                if (wrapperView.parent != null) {
                    windowManager.removeView(wrapperView)
                }
                menuWrapperView = null
                menuParams = null
                currentState = MenuState.HIDDEN
                onHidden?.invoke()
            }
            .start()
    }

    fun updateMenuPosition(bubbleParams: WindowManager.LayoutParams?, bubbleView: View?) {
        if (!isMenuVisible() || bubbleParams == null || bubbleView == null) return
        menuParams?.apply {
            x = bubbleParams.x + bubbleView.width / 2 - (menuWrapperView?.width ?: 0) / 2
            y = bubbleParams.y + bubbleView.height + dpToPx(8)
        }
        adjustPositionForScreenEdges()
    }

    private fun adjustPositionForScreenEdges() {
        val view = menuWrapperView ?: return
        val params = menuParams ?: return
        view.measure(
            View.MeasureSpec.makeMeasureSpec(dpToPx(MENU_WIDTH_DP), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuHeight = view.measuredHeight
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleHeightAndMargin = dpToPx(80)
        if (params.x < dpToPx(8)) params.x = dpToPx(8)
        if (params.x + dpToPx(MENU_WIDTH_DP) > screenWidth) params.x = screenWidth - dpToPx(MENU_WIDTH_DP) - dpToPx(8)
        if (params.y + menuHeight > screenHeight) params.y = params.y - menuHeight - bubbleHeightAndMargin
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating menu layout", e)
        }
    }

    fun cleanup() {
        menuWrapperView?.animate()?.cancel()
        if (menuWrapperView?.parent != null) {
            try { windowManager.removeView(menuWrapperView) }
            catch (e: Exception) { /* Ignore */ }
        }
        menuWrapperView = null
        currentState = MenuState.HIDDEN
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}