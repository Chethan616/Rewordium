package com.example.yc_startup.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yc_startup.R
import com.example.yc_startup.keyboard.util.EmojiData
import com.example.yc_startup.keyboard.util.KeyboardConstants

import android.widget.TextView
import android.graphics.Shader
import android.graphics.LinearGradient


class KeyboardLayoutManager(private val service: RewordiumAIKeyboardService) {

    private var rootView: View? = null
    private lateinit var keyboardSwitcher: ViewAnimator
    private val MAIN_KEYBOARD_INDEX = 0
    private val EMOJI_KEYBOARD_INDEX = 1

    private lateinit var keyboardRootContainer: LinearLayout
    private lateinit var mainKeyboardContainer: LinearLayout
    private lateinit var emojiKeyboardContainer: LinearLayout
    private lateinit var numberRow: LinearLayout
    private lateinit var rowQwerty: LinearLayout
    private lateinit var rowAsdf: LinearLayout
    private lateinit var rowZxcv: LinearLayout
    private lateinit var bottomRow: LinearLayout
    private lateinit var suggestionsContainer: LinearLayout
    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView
    private lateinit var separator1: View
    private lateinit var separator2: View
    private lateinit var aiButton: FrameLayout
    private var aiTextView: TextView? = null

    private var emojiRecyclerView: RecyclerView? = null
    private var emojiCategoryTabsContainer: HorizontalScrollView? = null
    private var emojiBottomControlRow: LinearLayout? = null
    private var emojiAdapter: EmojiAdapter? = null

    private val letterKeyViews = mutableListOf<Button>()
    private var keyPopup: PopupWindow? = null
    private var popupTextView: TextView? = null
    private var shiftKeyView: ImageButton? = null

    private var keyBackgroundColor: Int = 0
    private var keyTextColor: Int = 0
    private var specialKeyBackgroundColor: Int = 0
    private var keyboardBackgroundColor: Int = 0
    private var currentEmojiCategories: List<Pair<String, List<String>>> = emptyList()

    fun getRootView(): View? = rootView
    fun getKeyboardSwitcher(): ViewAnimator = keyboardSwitcher

    fun initialize(root: View) {
        this.rootView = root
        keyboardRootContainer = root.findViewById(R.id.keyboard_root_container)
        keyboardSwitcher = root.findViewById(R.id.keyboard_switcher)
        mainKeyboardContainer = root.findViewById(R.id.keyboard_container)
        emojiKeyboardContainer = root.findViewById(R.id.emoji_keyboard_container)
        numberRow = root.findViewById(R.id.number_row)
        rowQwerty = root.findViewById(R.id.row_qwerty)
        rowAsdf = root.findViewById(R.id.row_asdfghjkl)
        rowZxcv = root.findViewById(R.id.row_zxcvbnm)
        bottomRow = root.findViewById(R.id.bottom_row)
        aiButton = root.findViewById(R.id.ai_button)
        aiTextView = root.findViewById(R.id.ai_text_view)

        suggestionsContainer = root.findViewById(R.id.suggestions_container)
        suggestion1 = root.findViewById(R.id.suggestion_1)
        suggestion2 = root.findViewById(R.id.suggestion_2)
        suggestion3 = root.findViewById(R.id.suggestion_3)
        separator1 = root.findViewById(R.id.separator_1)
        separator2 = root.findViewById(R.id.separator_2)

        // =========================================================================
        // START OF THE FIX:
        // The previous `bottomMargin` logic has been removed.
        // This listener now handles everything correctly.
        // =========================================================================
        ViewCompat.setOnApplyWindowInsetsListener(keyboardRootContainer) { view, insets ->
            // Get the height of the system navigation bar
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Get the desired "lift" value from your dimens.xml
            val keyboardLift = view.context.resources.getDimensionPixelSize(R.dimen.keyboard_bottom_margin)

            // The total padding is the sum of the required system space and your desired lift
            val totalBottomPadding = navBarHeight + keyboardLift
            
            // Apply the calculated padding. The keyboard's background will fill this space.
            view.updatePadding(bottom = totalBottomPadding)

            insets
        }
        // =========================================================================
        // END OF THE FIX
        // =========================================================================

        setupSuggestionClicks()
        setupAIButton()
        setupAITextViewGradient()
        createKeyPopup()
    }

    fun updateLayout() {
        if (service.paraphraseManager?.isParaphrasingMode == true) {
            return
        }
        if (service.isEmojiKeyboardShown) {
            if (keyboardSwitcher.displayedChild != EMOJI_KEYBOARD_INDEX) {
                keyboardSwitcher.displayedChild = EMOJI_KEYBOARD_INDEX
            }
            setupEmojiKeyboard()
        } else {
            if (keyboardSwitcher.displayedChild != MAIN_KEYBOARD_INDEX) {
                keyboardSwitcher.displayedChild = MAIN_KEYBOARD_INDEX
            }
            if (service.isSymbolsShown) {
                setupSymbolsKeyboard()
            } else {
                setupLettersKeyboard()
            }
        }
    }

    fun updateSuggestions(suggestions: List<String>) {
        service.currentSuggestions = suggestions.take(3)
        val suggestionViews = listOf(suggestion1, suggestion2, suggestion3)
        suggestionsContainer.visibility = View.VISIBLE
        for (i in 0..2) {
            val suggestion = service.currentSuggestions.getOrNull(i)
            val view = suggestionViews[i]
            if (suggestion != null) {
                view.text = suggestion
                view.visibility = View.VISIBLE
            } else {
                view.text = ""
                view.visibility = View.INVISIBLE
            }
        }
        separator1.visibility = if (suggestion2.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        separator2.visibility = if (suggestion3.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }

    fun setupEmojiKeyboard() {
        emojiKeyboardContainer.removeAllViews()
        createEmojiTabs()
        createEmojiRecyclerView()
        createEmojiBottomControlRow()
        emojiCategoryTabsContainer?.let { emojiKeyboardContainer.addView(it) }
        emojiRecyclerView?.let { emojiKeyboardContainer.addView(it) }
        emojiBottomControlRow?.let { emojiKeyboardContainer.addView(it) }
        updateEmojiCategoryTabs()
        updateEmojiList()
    }
    
    private fun createEmojiTabs() {
        emojiCategoryTabsContainer = HorizontalScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(40))
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(service).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            })
        }
    }

    private fun createEmojiRecyclerView() {
        emojiRecyclerView = RecyclerView(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            layoutManager = GridLayoutManager(service, KeyboardConstants.EMOJI_COLUMNS).apply {
                initialPrefetchItemCount = KeyboardConstants.EMOJI_COLUMNS * 3
            }
            setHasFixedSize(true)
            setItemViewCacheSize(40)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createEmojiBottomControlRow() {
        val totalWeight = 8.0f
        val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
        val keyHeight = service.resources.getDimensionPixelSize(R.dimen.ios_key_height)

        emojiBottomControlRow = LinearLayout(service).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyHeight)
            orientation = LinearLayout.HORIZONTAL
            weightSum = totalWeight
            setPadding(0, margin, 0, margin)
        }

        val abcButton = Button(service).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f).apply {
                setMargins(margin, 0, margin, 0)
            }
            text = "ABC"
            setTextColor(keyTextColor)
            background = GradientDrawable().apply { cornerRadius = dpToPx(6f); setColor(specialKeyBackgroundColor) }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) service.switchToLetters()
                true
            }
        }
        emojiBottomControlRow?.addView(abcButton)
        val spacebar = Button(service).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 5.0f).apply {
                setMargins(margin, 0, margin, 0)
            }
            text = "space"
            setTextColor(keyTextColor)
            background = GradientDrawable().apply { cornerRadius = dpToPx(6f); setColor(keyBackgroundColor) }
            setOnTouchListener { _, _ -> service.handleText(" ", fromKey = true); true }
        }
        emojiBottomControlRow?.addView(spacebar)
        val backspaceKey = ImageButton(service).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f).apply {
                setMargins(margin, 0, margin, 0)
            }
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(keyTextColor, PorterDuff.Mode.SRC_IN)
            background = GradientDrawable().apply { cornerRadius = dpToPx(6f); setColor(specialKeyBackgroundColor) }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { service.startTurboDelete(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { service.stopTurboDelete(); true }
                    else -> false
                }
            }
        }
        emojiBottomControlRow?.addView(backspaceKey)
    }

    private fun updateEmojiCategoryTabs() {
        val tabsContainer = emojiCategoryTabsContainer ?: return
        val tabsLayout = tabsContainer.getChildAt(0) as? LinearLayout ?: return
        tabsLayout.removeAllViews()
        val recentEmojis = service.getRecentEmojis()
        val allCategories = mutableListOf<Pair<String, List<String>>>()
        if (recentEmojis.isNotEmpty()) allCategories.add("Recents" to recentEmojis)
        allCategories.addAll(EmojiData.emojiCategories)
        this.currentEmojiCategories = allCategories
        currentEmojiCategories.forEachIndexed { index, (name, _) ->
            val iconResId = when(name) {
                "Recents" -> R.drawable.ic_recent
                "Emoticons" -> R.drawable.ic_emoji_emoticons
                "Misc Symbols & Pictographs" -> R.drawable.ic_emoji_objects
                "Transport & Map" -> R.drawable.ic_emoji_transportation
                "Misc Symbols" -> R.drawable.ic_emoji_symbols
                else -> R.drawable.ic_emoji_symbols
            }
            val tabButton = ImageButton(service, null, android.R.attr.borderlessButtonStyle).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(50), ViewGroup.LayoutParams.MATCH_PARENT)
                setImageResource(iconResId)
                scaleType = ImageView.ScaleType.CENTER
                background = ContextCompat.getDrawable(service, R.drawable.emoji_category_background_selector)
                val tintColor = if (service.currentEmojiCategoryIndex == index) {
                    if (service.isDarkMode) Color.WHITE else Color.BLACK
                } else {
                    if (service.isDarkMode) Color.LTGRAY else Color.DKGRAY
                }
                imageTintList = ColorStateList.valueOf(tintColor)
                isSelected = (service.currentEmojiCategoryIndex == index)
                setOnClickListener { service.handleEmojiCategorySwitch(index) }
            }
            tabsLayout.addView(tabButton)
        }
    }
    
    private fun updateEmojiList() {
        if (service.currentEmojiCategoryIndex >= currentEmojiCategories.size) {
            service.currentEmojiCategoryIndex = 0
        }
        val emojis = currentEmojiCategories.getOrNull(service.currentEmojiCategoryIndex)?.second ?: emptyList()
        emojiAdapter = EmojiAdapter(emojis) { emoji -> service.handleEmojiKeyPress(emoji) }
        emojiRecyclerView?.adapter = emojiAdapter
    }

    fun updateLetterKeys() {
        val isCaps = service.isCapsOn || service.isCapsLock
        letterKeyViews.forEach { button ->
            val originalText = button.tag as? String ?: ""
            button.text = if (isCaps) originalText.uppercase() else originalText.lowercase()
        }
        updateShiftKeyState()
    }

    fun applyTheme(isDarkMode: Boolean, themeColorHex: String) {
        keyBackgroundColor = Color.parseColor(if (isDarkMode) "#333333" else "#FFFFFF")
        keyTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        specialKeyBackgroundColor = Color.parseColor(if (isDarkMode) "#5A5A5A" else "#D1D1D6")
        keyboardBackgroundColor = Color.parseColor(if (isDarkMode) "#1C1C1E" else "#D1D1D6")
        keyboardRootContainer.setBackgroundColor(keyboardBackgroundColor)
        val suggestionTextColor = if(isDarkMode) Color.WHITE else Color.BLACK
        suggestion1.setTextColor(Color.parseColor(service.themeColor))
        suggestion2.setTextColor(suggestionTextColor)
        suggestion3.setTextColor(suggestionTextColor)
        val separatorColor = if(isDarkMode) Color.parseColor("#444444") else Color.parseColor("#CCCCCC")
        separator1.setBackgroundColor(separatorColor)
        separator2.setBackgroundColor(separatorColor)
        createKeyPopup()
        updateLayout()
    }

    fun cleanup() = dismissKeyPopup()

    private fun clearAlphabetRows() {
        numberRow.removeAllViews()
        rowQwerty.removeAllViews()
        rowAsdf.removeAllViews()
        rowZxcv.removeAllViews()
        bottomRow.removeAllViews()
        letterKeyViews.clear()
        shiftKeyView = null
    }

    private fun setupLettersKeyboard() {
        clearAlphabetRows()
        "1234567890".forEach { addKey(numberRow, it.toString()) }
        "qwertyuiop".forEach { addKey(rowQwerty, it.toString(), isLetter = true) }
        addPaddingView(rowAsdf, 0.5f)
        "asdfghjkl".forEach { addKey(rowAsdf, it.toString(), isLetter = true) }
        addPaddingView(rowAsdf, 0.5f)
        addShiftKey()
        "zxcvbnm".forEach { addKey(rowZxcv, it.toString(), isLetter = true) }
        addBackspaceKey(rowZxcv, 1.5f)
        addSpecialKey(bottomRow, "123", weight = 1.5f) { service.switchToSymbols() }
        addSpecialKey(bottomRow, "", R.drawable.ic_globe, 1f) { service.performHapticFeedback() }
        addSpacebarKey(bottomRow, 5.0f)
        addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) { service.switchToEmoji() }
        addReturnKey(bottomRow, 2.0f)
        updateLetterKeys()
    }

    private fun setupSymbolsKeyboard() {
        clearAlphabetRows()
        "1234567890".forEach { addKey(numberRow, it.toString()) }
        "-/:;()$&@\"".forEach { addKey(rowQwerty, it.toString()) }
        ".,?!'*%_^~".forEach { addKey(rowAsdf, it.toString()) }
        addSpecialKey(rowZxcv, "#+=", weight = 1.5f) { service.performHapticFeedback() }
        "[]{}<>".forEach { addKey(rowZxcv, it.toString()) }
        addBackspaceKey(rowZxcv, 1.5f)
        addSpecialKey(bottomRow, "ABC", weight = 1.5f) { service.switchToLetters() }
        addSpecialKey(bottomRow, "", R.drawable.ic_globe, 1f) { service.performHapticFeedback() }
        addSpacebarKey(bottomRow, 5.0f)
        addSpecialKey(bottomRow, "", R.drawable.ic_emoji, 1f) { service.switchToEmoji() }
        addReturnKey(bottomRow, 2.0f)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addKey(parent: ViewGroup, text: String, weight: Float = 1f, isLetter: Boolean = false) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_letter, parent, false) as Button
        keyView.tag = text.lowercase()
        keyView.text = text
        keyView.setTextColor(keyTextColor)
        keyView.background = GradientDrawable().apply { cornerRadius = dpToPx(6f); setColor(keyBackgroundColor) }
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        keyView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val currentText = (v as Button).text.toString()
                    showKeyPopup(v, currentText)
                    service.handleText(currentText, fromKey = true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dismissKeyPopup(); true }
                else -> false
            }
        }
        parent.addView(keyView)
        if (isLetter) letterKeyViews.add(keyView)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addSpecialKey(parent: ViewGroup, text: String, iconResId: Int? = null, weight: Float = 1f, onClick: () -> Unit) {
        val view: View = if (iconResId == null) {
            (LayoutInflater.from(service).inflate(R.layout.ios_key_special, parent, false) as Button).apply { this.text = text; this.setTextColor(keyTextColor) }
        } else {
            (LayoutInflater.from(service).inflate(R.layout.ios_key_icon, parent, false) as ImageButton).apply { setImageResource(iconResId); setColorFilter(keyTextColor, PorterDuff.Mode.SRC_IN) }
        }
        view.background = GradientDrawable().apply { cornerRadius = dpToPx(6f); setColor(specialKeyBackgroundColor) }
        view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onClick()
            }
            true
        }
        parent.addView(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addShiftKey() {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_icon, rowZxcv, false) as ImageButton
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        keyView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) service.toggleShift()
            true
        }
        rowZxcv.addView(keyView)
        shiftKeyView = keyView
        updateShiftKeyState()
    }

    private fun updateShiftKeyState() {
        val keyView = shiftKeyView ?: return
        val iconRes = when {
            service.isCapsLock -> R.drawable.ic_shift_caps_lock
            service.isCapsOn -> R.drawable.ic_shift_filled
            else -> R.drawable.ic_shift
        }
        keyView.setImageResource(iconRes)
        val isActive = service.isCapsLock || service.isCapsOn
        keyView.background = GradientDrawable().apply {
            cornerRadius = dpToPx(6f)
            setColor(if (isActive) Color.WHITE else specialKeyBackgroundColor)
        }
        keyView.setColorFilter(if (isActive) Color.BLACK else keyTextColor, PorterDuff.Mode.SRC_IN)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBackspaceKey(parent: ViewGroup, weight: Float) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_icon, parent, false) as ImageButton
        keyView.setImageResource(R.drawable.ic_backspace)
        keyView.setColorFilter(keyTextColor, PorterDuff.Mode.SRC_IN)
        keyView.background = GradientDrawable().apply { cornerRadius = dpToPx(6f); setColor(specialKeyBackgroundColor) }
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        keyView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { service.startTurboDelete(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { service.stopTurboDelete(); true }
                else -> false
            }
        }
        parent.addView(keyView)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addSpacebarKey(parent: ViewGroup, weight: Float) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_special, parent, false) as Button
        keyView.text = "space"
        keyView.setTextColor(keyTextColor)
        keyView.background = GradientDrawable().apply { cornerRadius = dpToPx(6f); setColor(keyBackgroundColor) }
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        var startX = 0f
        var isSwiping = false
        val swipeThreshold = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
        keyView.setOnTouchListener { _, event ->
            val ic = service.currentInputConnection ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    isSwiping = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - startX
                    if (Math.abs(deltaX) > swipeThreshold) {
                        isSwiping = true
                        val keyCode = if (deltaX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                        startX = event.x
                        service.performHapticFeedback()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isSwiping) {
                        service.handleText(" ", fromKey = false)
                    }
                    true
                }
                else -> false
            }
        }
        parent.addView(keyView)
    }

    private fun addReturnKey(parent: ViewGroup, weight: Float) {
        val keyView = LayoutInflater.from(service).inflate(R.layout.ios_key_special, parent, false) as Button
        keyView.text = service.getReturnKeyLabel().lowercase()
        keyView.setTextColor(Color.WHITE)
        keyView.background = GradientDrawable().apply {
            cornerRadius = dpToPx(6f)
            setColor(Color.parseColor(service.themeColor))
        }
        keyView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            val margin = service.resources.getDimensionPixelSize(R.dimen.ios_key_margin) / 2
            setMargins(margin, margin, margin, margin)
        }
        keyView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) service.handleReturnKey()
            true
        }
        parent.addView(keyView)
    }

    private fun createKeyPopup() {
        val inflater = service.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.key_popup, null)
        popupTextView = popupView.findViewById(R.id.popup_text)
        val popupBackgroundResId = if (service.isDarkMode) R.drawable.key_popup_background_dark else R.drawable.key_popup_background
        popupView.background = ContextCompat.getDrawable(service, popupBackgroundResId)
        popupTextView?.setTextColor(keyTextColor)
        val popupSize = dpToPx(54)
        keyPopup = PopupWindow(popupView, popupSize, popupSize, false).apply {
            animationStyle = 0
            isClippingEnabled = false
        }
    }

    private fun showKeyPopup(anchor: View, text: String) {
        if (keyPopup == null || popupTextView == null) createKeyPopup()
        popupTextView?.text = text
        keyPopup?.let { popup ->
            if (!anchor.isAttachedToWindow) return
            val anchorLocation = IntArray(2)
            anchor.getLocationInWindow(anchorLocation)
            val x = anchorLocation[0] + (anchor.width - popup.width) / 2
            val y = anchorLocation[1] - popup.height - dpToPx(4)
            if (!popup.isShowing) popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            else popup.update(x, y, -1, -1)
        }
    }

    private fun dismissKeyPopup() = keyPopup?.dismiss()
    private fun dpToPx(dp: Int): Int = (dp * service.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, service.resources.displayMetrics)

    private fun setupAITextViewGradient() {
        aiTextView?.doOnLayout {
            val textView = it as TextView
            val paint = textView.paint
            val width = paint.measureText(textView.text.toString())
            val textShader: Shader = LinearGradient(
                0f, 0f, width, textView.textSize,
                intArrayOf(
                    Color.parseColor("#8A2BE2"), // BlueViolet (Purple-ish)
                    Color.parseColor("#4169E1")  // RoyalBlue (Bluish)
                ),
                null, Shader.TileMode.CLAMP
            )
            textView.paint.shader = textShader
            textView.invalidate() // Ensure the view redraws with the new shader
        }
    }

    private fun addPaddingView(parent: ViewGroup, weight: Float) {
        parent.addView(View(service).apply { layoutParams = LinearLayout.LayoutParams(0, 1, weight) })
    }
    private fun setupAIButton() {
        aiButton.setOnClickListener {
            service.handleAIButton()
        }
    }
    private fun setupSuggestionClicks() {
        val listener = View.OnClickListener { view ->
            val suggestion = (view as TextView).text.toString()
            if (suggestion.isNotEmpty()) service.onSuggestionTapped(suggestion)
        }
        suggestion1.setOnClickListener(listener)
        suggestion2.setOnClickListener(listener)
        suggestion3.setOnClickListener(listener)
    }
}