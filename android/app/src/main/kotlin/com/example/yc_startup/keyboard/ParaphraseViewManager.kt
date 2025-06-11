package com.example.yc_startup.keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.yc_startup.R

class ParaphraseViewManager(private val service: RewordiumAIKeyboardService, private val rootView: FrameLayout) {
    var isParaphrasingMode = false
        private set

    private val mainKeyboardView: View = rootView.findViewById(R.id.keyboard_root_container)
    
    private val paraphraseContainer: FrameLayout
    private var currentPersona = "Neutral"
    private var currentOriginalText: String = ""

    init {
        paraphraseContainer = FrameLayout(service).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT // Use MATCH_PARENT to fill the whole area
            )
            visibility = View.GONE
            // Set the background color here to ensure the gap area is not transparent during transitions
            setBackgroundColor(if (service.isDarkMode) Color.parseColor("#1C1C1E") else Color.parseColor("#D1D1D6"))
        }

        // =========================================================================
        // START OF THE FIX: Use a WindowInsetsListener to dynamically add padding
        // that matches the navigation bar height.
        // =========================================================================
        ViewCompat.setOnApplyWindowInsetsListener(paraphraseContainer) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            // Apply the bottom inset as padding to push the content up
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBarInsets.bottom)
            // Return the insets so that child views can also consume them if needed
            insets
        }
        // =========================================================================
        // END OF THE FIX
        // =========================================================================

        rootView.addView(paraphraseContainer)
    }

    // --- All other methods are unchanged ---
    
    fun show(originalText: String) {
        if (isParaphrasingMode) return
        isParaphrasingMode = true
        currentPersona = "Neutral"
        currentOriginalText = originalText

        mainKeyboardView.visibility = View.GONE
        paraphraseContainer.visibility = View.VISIBLE
        
        paraphraseContainer.removeAllViews()
        buildParaphraseShell()
        // It seems there's a typo here, it should probably be `currentPersona`
        service.generateParaphraseWithPersona(originalText, currentPersona)
    }

    fun exitParaphraseMode() {
        if (!isParaphrasingMode) return
        isParaphrasingMode = false
        service.currentParaphraseJob?.cancel()

        paraphraseContainer.visibility = View.GONE
        mainKeyboardView.visibility = View.VISIBLE
        
        service.layoutManager.updateLayout()
    }

    private fun buildParaphraseShell() {
        val shell = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            
            // The container now handles the full height, so the shell can wrap its content
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // The background color is now set on the parent container
        }
        shell.addView(createHeaderBar())
        shell.addView(createPersonaSelector())

        val contentArea = LinearLayout(service).apply {
            tag = "contentArea"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        contentArea.addView(createLoadingIndicator())
        shell.addView(contentArea)
        paraphraseContainer.addView(shell)
    }

    fun updateWithResults(paraphrases: List<String>) {
        val contentArea = paraphraseContainer.findViewWithTag<LinearLayout>("contentArea") ?: return
        contentArea.removeAllViews()

        if (paraphrases.isEmpty()) {
            service.showToast("Failed to generate paraphrases.")
            exitParaphraseMode()
            return
        }
        val scrollView = ScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
        }
        val optionsContainer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        val personaColor = getPersonaColor(currentPersona)
        paraphrases.take(3).forEachIndexed { index, text ->
            val card = createParaphraseCard(text, personaColor)
            card.setOnClickListener {
                service.applyParaphrase(text)
            }
            optionsContainer.addView(card)
            if (index < paraphrases.size - 1 && index < 2) {
                optionsContainer.addView(View(service).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8)
                    )
                })
            }
        }
        scrollView.addView(optionsContainer)
        contentArea.addView(scrollView)
    }

    private fun refreshForNewPersona() {
        paraphraseContainer.removeAllViews()
        buildParaphraseShell()
        service.generateParaphraseWithPersona(currentOriginalText, currentPersona)
    }
    
    fun updatePersonaButtons() {
        // Only update if we're in paraphrase mode
        if (!isParaphrasingMode) return
        
        // If the current persona is no longer in the available personas, reset to "Neutral"
        if (currentPersona != "Neutral" && !service.availablePersonas.contains(currentPersona)) {
            currentPersona = "Neutral"
            // Regenerate with the default persona
            refreshForNewPersona()
        } else {
            // Just rebuild the persona selector with the updated list
            val personaSelector = paraphraseContainer.findViewWithTag<View>("personaSelector")
            personaSelector?.let { view ->
                val parent = view.parent as? ViewGroup
                parent?.let { safeParent ->
                    val index = safeParent.indexOfChild(view)
                    if (index >= 0) {
                        safeParent.removeViewAt(index)
                        safeParent.addView(createPersonaSelector(), index)
                    }
                }
            }
        }
    }

    private fun createHeaderBar(): View {
        return LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44))
            val backButton = TextView(service).apply {
                text = "❮ Back"
                setTextColor(Color.parseColor(service.themeColor))
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
                setOnClickListener { exitParaphraseMode() }
            }
            addView(backButton)
            val titleView = TextView(service).apply {
                text = "Reword"
                setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(titleView)
            addView(View(service).apply { layoutParams = LinearLayout.LayoutParams(backButton.paddingLeft + backButton.paddingRight, 1) })
        }
    }

    private fun createPersonaSelector(): View {
        val selector = HorizontalScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isHorizontalScrollBarEnabled = false
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            tag = "personaSelector"
        }
        val buttonContainer = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        service.availablePersonas.forEach { persona ->
            val button = TextView(service).apply {
                text = persona
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                val isSelected = persona == currentPersona
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor(service.themeColor))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16f)
                    setColor(if (isSelected) Color.parseColor(service.themeColor) else Color.TRANSPARENT)
                    setStroke(1, Color.parseColor(service.themeColor))
                }
                setOnClickListener {
                    if (currentPersona != persona) {
                        currentPersona = persona
                        refreshForNewPersona()
                    }
                }
            }
            buttonContainer.addView(button)
            buttonContainer.addView(View(service).apply { layoutParams = LinearLayout.LayoutParams(dpToPx(8), 1) })
        }
        selector.addView(buttonContainer)
        return selector
    }

    private fun createLoadingIndicator(): View {
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            val progressBar = ProgressBar(service).apply {
                indeterminateTintList = ColorStateList.valueOf(Color.parseColor(service.themeColor))
            }
            addView(progressBar)
            addView(TextView(service).apply {
                text = "Thinking..."
                setTextColor(if (service.isDarkMode) Color.LTGRAY else Color.DKGRAY)
                textSize = 15f
                setPadding(0, dpToPx(16), 0, 0)
            })
        }
    }

    private fun createParaphraseCard(text: String, color: Int): View {
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val normalDrawable = GradientDrawable().apply {
                setColor(if (service.isDarkMode) Color.parseColor("#2C2C2E") else Color.WHITE)
                cornerRadius = dpToPx(12f)
            }
            val pressedDrawable = GradientDrawable().apply {
                setColor(if (service.isDarkMode) Color.parseColor("#3A3A3C") else Color.parseColor("#F0F0F0"))
                cornerRadius = dpToPx(12f)
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
                addState(intArrayOf(), normalDrawable)
            }
        }
        val accentIndicator = View(service).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(4), ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadii = floatArrayOf(dpToPx(12f), dpToPx(12f), 0f, 0f, 0f, 0f, dpToPx(12f), dpToPx(12f))
            }
        }
        card.addView(accentIndicator)
        card.addView(TextView(service).apply {
            this.text = text
            setTextColor(if (service.isDarkMode) Color.WHITE else Color.BLACK)
            textSize = 16f
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        })
        return card
    }

    private fun getPersonaColor(persona: String): Int {
        val colorHex = when (persona) {
            "Happy" -> "#34C759"
            "Sad" -> "#FF9500"
            "Humor" -> "#FF2D55"
            "Formal" -> "#5856D6"
            "Casual" -> "#FF3B30"
            else -> service.themeColor
        }
        return Color.parseColor(colorHex)
    }

    private fun dpToPx(dp: Int): Int = (dp * service.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, service.resources.displayMetrics)
}