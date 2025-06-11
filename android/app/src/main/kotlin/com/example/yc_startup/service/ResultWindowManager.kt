package com.example.yc_startup.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.example.yc_startup.R

class ResultWindowManager(
    private val serviceContext: Context,
    private val listener: ResultWindowListener
) {
    private val windowManager = serviceContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var resultView: View? = null
    private val themedContext = ContextThemeWrapper(serviceContext, R.style.KeyboardTheme_Light)

    // --- NEW: An enum to represent the UI state ---
    private enum class UIState {
        LOADING, LANGUAGE_SELECTION, RESULT_TEXT
    }

    private fun ensureWindowVisible(onComplete: (() -> Unit)? = null) {
        if (resultView?.parent != null) {
            onComplete?.invoke()
            return
        }
        val inflater = LayoutInflater.from(themedContext)
        resultView = inflater.inflate(R.layout.result_window_layout, null)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else { WindowManager.LayoutParams.TYPE_PHONE }
        val params = WindowManager.LayoutParams(
            dpToPx(340), WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        resultView?.findViewById<ImageButton>(R.id.close_button)?.setOnClickListener { hide() }
        resultView?.findViewById<ImageButton>(R.id.copy_button)?.setOnClickListener { /* ... */ }
        resultView?.findViewById<ImageButton>(R.id.refresh_button)?.setOnClickListener { listener.onRefreshClicked() }

        try {
            windowManager.addView(resultView, params)
            resultView?.apply {
                alpha = 0f; scaleX = 0.9f; scaleY = 0.9f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .withEndAction { onComplete?.invoke() }.start()
            }
        } catch (e: Exception) {
            Log.e("ResultWindow", "Error showing window", e)
        }
    }

    // --- NEW: The central function to control visibility ---
    private fun switchToState(state: UIState) {
        resultView?.apply {
            val loading = findViewById<ProgressBar>(R.id.loading_indicator)
            val langContainer = findViewById<LinearLayout>(R.id.language_selection_container)
            val scrollview = findViewById<ScrollView>(R.id.result_scrollview)

            loading.visibility = if (state == UIState.LOADING) View.VISIBLE else View.GONE
            langContainer.visibility = if (state == UIState.LANGUAGE_SELECTION) View.VISIBLE else View.GONE
            scrollview.visibility = if (state == UIState.RESULT_TEXT) View.VISIBLE else View.GONE
        }
    }

    fun showLanguageSelection(languages: List<String>, onLanguageSelected: (String) -> Unit) {
        ensureWindowVisible {
            switchToState(UIState.LANGUAGE_SELECTION)
            resultView?.apply {
                val buttonHost = findViewById<LinearLayout>(R.id.language_button_host)
                buttonHost.removeAllViews()
                languages.forEach { language ->
                    val button = AppCompatButton(themedContext).apply {
                        text = language
                        isAllCaps = false
                        background = context.getDrawable(R.drawable.glossy_pill_button)
                        setTextColor(context.getColor(android.R.color.white))
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setPadding(48, 0, 48, 0)
                        setOnClickListener {
                            this.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                                this.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                            }.start()
                            postDelayed({ onLanguageSelected(language) }, 150)
                        }
                    }
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(48)
                    ).apply { marginEnd = dpToPx(12) }
                    buttonHost.addView(button, layoutParams)
                }
            }
        }
    }

    fun showLoading() {
        ensureWindowVisible {
            switchToState(UIState.LOADING)
        }
    }

    fun updateContent(title: String, text: String) {
        ensureWindowVisible {
            switchToState(UIState.RESULT_TEXT)
            resultView?.apply {
                findViewById<TextView>(R.id.result_title)?.text = title
                findViewById<TextView>(R.id.result_text)?.text = text
            }
        }
    }

    fun hide() {
        val viewToHide = resultView ?: return
        if (viewToHide.parent == null) return
        viewToHide.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (viewToHide.parent != null) { windowManager.removeView(viewToHide) }
                resultView = null
            }.start()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * serviceContext.resources.displayMetrics.density).toInt()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = serviceContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Result", text)
        clipboard.setPrimaryClip(clip)
    }
}