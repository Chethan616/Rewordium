package com.noxquill.rewordium.service

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.animation.PathInterpolator
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.noxquill.rewordium.BuildConfig
import com.noxquill.rewordium.R
import kotlinx.coroutines.*
import java.util.*

/**
 * Custom gradient overlay view for RGB wave animation
 */
class GradientOverlayView(context: Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var overlayHeight = 0f
    private var gradientOffset = 0f
    private var overlayAlpha = 0f
    private val screenWidth = resources.displayMetrics.widthPixels.toFloat()
    
    companion object {
        private const val TAG = "GradientOverlayView"
    }
    
    init {
        // Enable hardware acceleration for smooth performance
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }
    
    fun updateGradientProperties(height: Float, offset: Float, alpha: Float) {
        if (overlayHeight != height || gradientOffset != offset || overlayAlpha != alpha) {
            overlayHeight = height
            gradientOffset = offset
            overlayAlpha = alpha.coerceIn(0f, 1f)
            invalidate()
        }
    }
    
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        if (width <= 0 || height <= 0 || overlayAlpha <= 0 || overlayHeight <= 0) {
            return
        }
        
        // Buttery smooth gradient with soft edges and perfect color transitions
        val gradientHeight = overlayHeight + 200f // Extra soft blending area
        val startY = height - overlayHeight
        
        // Ultra-smooth 16-color RGB spectrum for seamless transitions
        val colors = intArrayOf(
            android.graphics.Color.argb((overlayAlpha * 200).toInt(), 255, 20, 60),    // Vibrant Crimson
            android.graphics.Color.argb((overlayAlpha * 190).toInt(), 255, 65, 54),    // Red Orange
            android.graphics.Color.argb((overlayAlpha * 180).toInt(), 255, 87, 34),    // Deep Orange  
            android.graphics.Color.argb((overlayAlpha * 170).toInt(), 255, 111, 0),    // Orange
            android.graphics.Color.argb((overlayAlpha * 160).toInt(), 255, 152, 0),    // Amber
            android.graphics.Color.argb((overlayAlpha * 150).toInt(), 255, 193, 7),    // Yellow
            android.graphics.Color.argb((overlayAlpha * 140).toInt(), 139, 195, 74),   // Light Green
            android.graphics.Color.argb((overlayAlpha * 130).toInt(), 76, 175, 80),    // Green
            android.graphics.Color.argb((overlayAlpha * 120).toInt(), 0, 188, 212),    // Cyan
            android.graphics.Color.argb((overlayAlpha * 110).toInt(), 3, 169, 244),    // Light Blue
            android.graphics.Color.argb((overlayAlpha * 100).toInt(), 33, 150, 243),   // Blue
            android.graphics.Color.argb((overlayAlpha * 90).toInt(), 63, 81, 181),     // Indigo
            android.graphics.Color.argb((overlayAlpha * 80).toInt(), 103, 58, 183),    // Deep Purple
            android.graphics.Color.argb((overlayAlpha * 70).toInt(), 156, 39, 176),    // Purple
            android.graphics.Color.argb((overlayAlpha * 40).toInt(), 233, 30, 99),     // Pink fade
            android.graphics.Color.argb(0, 255, 255, 255)                             // Transparent top
        )
        
        val positions = floatArrayOf(
            0f, 0.06f, 0.12f, 0.18f, 0.24f, 0.30f, 0.36f, 0.42f, 
            0.48f, 0.54f, 0.60f, 0.66f, 0.72f, 0.78f, 0.88f, 1f
        )
        
        // Smooth flowing gradient with perfect blending
        val animatedGradient = android.graphics.LinearGradient(
            gradientOffset - screenWidth * 0.2f, startY,
            width + gradientOffset + screenWidth * 0.2f, startY + gradientHeight,
            colors,
            positions,
            android.graphics.Shader.TileMode.CLAMP
        )
        
        paint.shader = animatedGradient
        
        // Draw the buttery smooth gradient overlay
        canvas.drawRect(
            0f,
            startY,
            width,
            height,
            paint
        )
        
        // Ultra-soft glow effect for premium feel
        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val softGlow = android.graphics.LinearGradient(
            0f, startY - 120f,
            0f, startY + 120f,
            intArrayOf(
                android.graphics.Color.argb(0, 255, 255, 255),
                android.graphics.Color.argb((overlayAlpha * 60).toInt(), 255, 255, 255),
                android.graphics.Color.argb((overlayAlpha * 80).toInt(), 255, 255, 255),
                android.graphics.Color.argb((overlayAlpha * 40).toInt(), 255, 255, 255),
                android.graphics.Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        glowPaint.shader = softGlow
        canvas.drawRect(0f, startY - 120f, width, startY + 120f, glowPaint)
    }
}

/**
 * MyAccessibilityService provides an overlay interface for AI-powered text rewriting.
 * 
 * This accessibility service monitors allowed applications for keyboard input events
 * and provides a floating bubble interface that allows users to rewrite text using
 * various AI personas (Casual, Academic, Poetry, Custom).
 * 
 * Key Features:
 * - Floating bubble overlay that appears when keyboard is visible in allowed apps
 * - Bottom sheet interface for text input and persona selection  
 * - Focused editor for detailed text editing
 * - Credit management and user authentication integration
 * - Persistent operation across screen on/off cycles
 * 
 * Security: Only activates in pre-approved applications for user privacy
 * Performance: Uses coroutines for non-blocking API calls and UI updates
 */
class MyAccessibilityService : AccessibilityService(), BubbleInteractionListener {

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val PERIODIC_CHECK_INTERVAL = 5000L // 5 seconds
        private const val WINDOW_STATE_DELAY = 100L // Delay for window state stability
        
        // 60 FPS Optimized Animation Constants
        private const val BUTTERY_SMOOTH_DURATION = 250L // Optimized for 60 FPS (15 frames)
        private const val FAST_ANIMATION_DURATION = 150L // Fast animation (9 frames)
        private const val SLOW_ANIMATION_DURATION = 350L // Slow animation (21 frames)
        private const val ULTRA_SMOOTH_DURATION = 300L // Ultra smooth but performant
        private const val SUGGESTION_ANIMATION_DELAY = 50L // Reduced for smoother cascade
        private const val STAGGER_DELAY = 30L // Micro stagger optimized for 60fps
        private const val API_DELAY = 200L // Delay for API processing
        
        // 60 FPS Optimized Animation Curves - using hardware accelerated interpolators
        private val SMOOTH_ENTER_INTERPOLATOR = android.view.animation.DecelerateInterpolator(1.2f)
        private val SMOOTH_EXIT_INTERPOLATOR = android.view.animation.AccelerateInterpolator(1.1f)
        private val BOUNCE_INTERPOLATOR = android.view.animation.OvershootInterpolator(0.8f) // Reduced bounce for smoothness
        private val ELASTIC_INTERPOLATOR = android.view.animation.DecelerateInterpolator(1.0f) // Changed to decelerate for smoother feel
        private val BUTTERY_INTERPOLATOR = android.view.animation.PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f) // Cubic bezier for ultimate smoothness
    }
    
    private val GROQ_API_KEY = "Bearer ${BuildConfig.GROQ_API_KEY}"

    private lateinit var windowManager: WindowManager
    private var floatingBubbleView: FloatingBubbleView? = null
    private var bottomSheetView: View? = null
    private var focusedEditorView: View? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<com.google.android.material.card.MaterialCardView>

    // Instance of our credit management utility.
    private lateinit var creditManager: CreditManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var generationJob: Job? = null
    private var periodicCheckJob: Job? = null
    private var isStoppedByUser = false
    private var isPerformingManualTransition = false
    private var isGenerating = false
    private var isScreenOn = true
    private var generatingFromFocusedEditor = false
    private var isTransitioningFromFocusedEditor = false
    private var customPersonaDialog: AlertDialog? = null // Track the custom persona dialog
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Screen turned off - hiding UI")
                    isScreenOn = false
                    stopPeriodicCheck()
                    hideFloatingBubble()
                    hideBottomSheetInstantly()
                    // Dismiss custom persona dialog if it's showing
                    customPersonaDialog?.dismiss()
                    customPersonaDialog = null
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Screen turned on")
                    isScreenOn = true
                    startPeriodicCheck()
                    // Check immediately when screen turns on
                    serviceScope.launch {
                        delay(WINDOW_STATE_DELAY)
                        checkAndShowBubble()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "User unlocked device")
                    isScreenOn = true
                    startPeriodicCheck()
                    // Also check when user unlocks (in case SCREEN_ON was missed)
                    serviceScope.launch {
                        delay(API_DELAY)
                        checkAndShowBubble()
                    }
                }
            }
        }
    }

    // User status receiver to handle login/logout and credit updates
    private val userStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.noxquill.rewordium.ACCESSIBILITY_USER_STATUS_UPDATED" -> {
                    val isLoggedIn = intent.getBooleanExtra("isLoggedIn", false)
                    val isPro = intent.getBooleanExtra("isPro", false)
                    val credits = intent.getIntExtra("credits", 0)
                    
                    if (BuildConfig.DEBUG) Log.d(TAG, "User status updated - LoggedIn: $isLoggedIn, Pro: $isPro, Credits: $credits")
                    
                    // Recreate credit manager to refresh the status
                    creditManager = CreditManager(this@MyAccessibilityService)
                    
                    // Log the current status for debugging
                    if (BuildConfig.DEBUG) Log.d(TAG, "CreditManager refreshed - isLoggedIn: ${creditManager.isUserLoggedIn()}, isPro: ${creditManager.isProUser()}, credits: ${creditManager.getCredits()}")
                }
            }
        }
    }
    private var activeSourceNode: AccessibilityNodeInfo? = null

    private var selectedPersona = "Casual"
    private var customPersonaPrompt = ""
    private var isPersonaDropdownVisible = false
    
    // Random persona templates for creative inspiration
    private val randomPersonas = listOf(
        "a witty pirate captain who speaks in nautical metaphors",
        "a sophisticated British detective with keen observation skills",
        "a cheerful Disney character who sees magic in everything",
        "a wise ancient philosopher pondering life's mysteries",
        "a sassy valley girl with attitude and style",
        "a dramatic Shakespearean actor with poetic flair",
        "a curious scientist explaining everything with wonder",
        "a friendly neighborhood superhero giving encouragement",
        "a zen master speaking in calm, mindful phrases",
        "a passionate Italian chef describing life like cooking",
        "a tech-savvy millennial using modern slang and references",
        "a mystical fortune teller revealing hidden meanings",
        "a quirky professor who loves fun facts and trivia",
        "a motivational life coach spreading positivity",
        "a rebellious teenager with a unique perspective on life",
        "a gentle grandparent sharing wisdom with love",
        "a sarcastic comedian who finds humor in everything",
        "a romantic poet expressing thoughts in beautiful verse",
        "a brave explorer describing grand adventures",
        "a nurturing therapist providing emotional support",
        "a creative artist seeing beauty in all things",
        "a logical robot analyzing situations with precision",
        "a free-spirited hippie spreading peace and love",
        "a medieval knight upholding honor and valor",
        "a space-age futurist discussing tomorrow's possibilities"
    )

    private val allowedPackageNames by lazy {
        setOf(
            "com.whatsapp",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "org.telegram.messenger",
            "com.discord",
            "com.google.android.gm",
            "com.yahoo.mobile.client.android.mail",
            "com.facebook.katana",
            "com.instagram.android",
            packageName
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        creditManager = CreditManager(this)
        
        // Debug current state immediately after connecting
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Service connected - immediate login status check")
            Log.d(TAG, "Initial CreditManager state - isLoggedIn: ${creditManager.isUserLoggedIn()}, isPro: ${creditManager.isProUser()}, credits: ${creditManager.getCredits()}")
        }
        
        // Register screen state receiver with all screen events
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, screenFilter)
        }
        
        // Register user status receiver
        val userStatusFilter = IntentFilter().apply {
            addAction("com.noxquill.rewordium.ACCESSIBILITY_USER_STATUS_UPDATED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(userStatusReceiver, userStatusFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(userStatusReceiver, userStatusFilter)
        }
        
        // Check current screen state
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive
        
        if (isScreenOn) {
            checkAndShowBubble()
            startPeriodicCheck()
        }
    }

    private fun startPeriodicCheck() {
        periodicCheckJob?.cancel()
        periodicCheckJob = serviceScope.launch {
            while (isScreenOn) {
                delay(PERIODIC_CHECK_INTERVAL) // Check every 5 seconds when screen is on
                if (isScreenOn) {
                    checkAndShowBubble()
                }
            }
        }
    }

    private fun stopPeriodicCheck() {
        periodicCheckJob?.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Handle different types of events that indicate app/window changes
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Accessibility event: ${event.eventType}, package: ${event.packageName}")
                
                // Check if user navigated away from allowed apps - dismiss dialog if so
                val currentPackage = event.packageName?.toString()
                if (customPersonaDialog?.isShowing == true && 
                    currentPackage != null && 
                    !allowedPackageNames.contains(currentPackage) &&
                    currentPackage != packageName) { // Not in our own app either
                    if (BuildConfig.DEBUG) Log.d(TAG, "User navigated away from allowed apps, dismissing dialog")
                    customPersonaDialog?.dismiss()
                    customPersonaDialog = null
                }
                
                serviceScope.launch {
                    delay(WINDOW_STATE_DELAY) // Small delay to ensure window state is stable
                    checkAndShowBubble()
                }
            }
        }
    }

    override fun onBubbleTapped() {
        floatingBubbleView?.cancelIdleTimer()
        if (bottomSheetView == null) {
            showBottomSheet()
        } else if (focusedEditorView == null) {
            bottomSheetBehavior.state = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun showBottomSheet() {
        activeSourceNode = findFocusedNode()
        val originalText = activeSourceNode?.text?.toString() ?: ""

        val themedContext = ContextThemeWrapper(this, R.style.Theme_App_Translucent)
        val inflater = LayoutInflater.from(themedContext)

        bottomSheetView = inflater.inflate(R.layout.layout_bottom_sheet, null)
        bottomSheetView?.alpha = 0f

        // *** FIX FOR SWIPE GESTURES ***
        // Using FLAG_NOT_FOCUSABLE allows system gestures (like back) to pass through,
        // while views inside the window (like buttons) can still be clicked.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        windowManager.addView(bottomSheetView, params)

        // Enable hardware acceleration for 60 FPS performance
        bottomSheetView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        bottomSheetView?.isFocusableInTouchMode = true
        bottomSheetView?.requestFocus()
        bottomSheetView?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (focusedEditorView != null) {
                    hideFocusedEditor()
                } else {
                    hideBottomSheet()
                }
                return@setOnKeyListener true
            }
            false
        }
        val bottomSheetContent = bottomSheetView?.findViewById<LinearLayout>(R.id.bottom_sheet_content)
        val bottomSheetCard = bottomSheetView?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.bottom_sheet_card)
        val scrimView = bottomSheetView?.findViewById<View>(R.id.scrim_view)
        
        if (bottomSheetContent == null || bottomSheetCard == null || scrimView == null) {
            Log.e(TAG, "Failed to find required views in bottom sheet layout")
            cleanupBottomSheetView()
            return
        }
        
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetCard)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // Force curved corner clipping is now handled by MaterialCardView
        bottomSheetCard.radius = 100f

        // Pre-set initial states for all UI elements to prevent layout shifts
        bottomSheetView?.alpha = 0f
        bottomSheetView?.scaleX = 0.95f
        bottomSheetView?.scaleY = 0.95f
        bottomSheetView?.translationY = 50f
        
        // Pre-configure cascade elements to prevent flash
        val elementsToPreConfigure = listOf(
            bottomSheetView?.findViewById<View>(R.id.edit_text_content),
            bottomSheetView?.findViewById<View>(R.id.persona_selector_layout),
            bottomSheetView?.findViewById<View>(R.id.button_rewrite),
            bottomSheetView?.findViewById<View>(R.id.settings_icon_container)
        ).filterNotNull()
        
        elementsToPreConfigure.forEach { element ->
            element.alpha = 0f
            element.translationY = 20f
            element.scaleX = 0.98f
            element.scaleY = 0.98f
        }

        // Single coordinated animation sequence for 60 FPS smoothness
        bottomSheetView?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.translationY(0f)
            ?.setDuration(BUTTERY_SMOOTH_DURATION)
            ?.setInterpolator(BUTTERY_INTERPOLATOR)
            ?.withStartAction {
                // Set BottomSheet state immediately to prevent conflicts
                bottomSheetBehavior.state = if (originalText.isNotEmpty()) {
                    BottomSheetBehavior.STATE_EXPANDED
                } else {
                    BottomSheetBehavior.STATE_COLLAPSED
                }
            }
            ?.withEndAction {
                // Start cascade animation only after main animation completes
                animateUIElementsCascade(bottomSheetView!!)
            }
            ?.start()

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(p0: View, p1: Int) {
                if (p1 == BottomSheetBehavior.STATE_HIDDEN) {
                    cleanupBottomSheetView()
                }
            }
            override fun onSlide(p0: View, p1: Float) {
                scrimView.alpha = p1.coerceAtLeast(0f)
            }
        })
        scrimView.setOnClickListener {
            if (!isGenerating) {
                hideBottomSheet()
            }
        }

        setupPanelUI(bottomSheetView!!, originalText)
        
        // Apply current theme to the bottom sheet based on saved preference, not system theme
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        if (BuildConfig.DEBUG) Log.d(TAG, "showBottomSheet: applying user preference theme, isDarkMode=$isDarkMode")
        // Apply theme with a slight delay to ensure all views are properly inflated
        Handler(Looper.getMainLooper()).postDelayed({
            applyTheme(isDarkMode)
        }, 50)
    }

    private fun setupPanelUI(view: View, originalText: String) {
        val editTextContent = view.findViewById<EditText>(R.id.edit_text_content)
        val btnRewrite = view.findViewById<Button>(R.id.button_rewrite)

        view.findViewById<View>(R.id.settings_icon_container)?.let { settingsIcon ->
            settingsIcon.setOnClickListener {
                toggleSettings()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
                val typedArray = obtainStyledAttributes(attrs)
                val backgroundRes = typedArray.getResourceId(0, 0)
                settingsIcon.setBackgroundResource(backgroundRes)
                typedArray.recycle()
            }
        }

        val personaSelectorLayout = view.findViewById<LinearLayout>(R.id.persona_selector_layout)
        val personaSelectorText = view.findViewById<TextView>(R.id.persona_selector_text)
        val personaSelectorArrow = view.findViewById<ImageView>(R.id.persona_selector_arrow)
        val personaOptionsCard = view.findViewById<View>(R.id.persona_options_card)
        val personaNormal = view.findViewById<View>(R.id.persona_normal)
        val personaShakespeare = view.findViewById<View>(R.id.persona_shakespeare)
        val personaYoda = view.findViewById<View>(R.id.persona_yoda)
        val personaCustom = view.findViewById<View>(R.id.persona_custom)

        editTextContent.setOnClickListener {
            isPerformingManualTransition = true
            showFocusedEditor(editTextContent.text.toString())
        }
        editTextContent.isFocusable = false
        editTextContent.setText(originalText)

        updatePersonaSelectorText(personaSelectorText)

        personaSelectorLayout.setOnClickListener {
            isPersonaDropdownVisible = !isPersonaDropdownVisible
            animatePersonaDropdown(personaOptionsCard, personaSelectorArrow, isPersonaDropdownVisible)
        }

        personaNormal.setOnClickListener { selectPersona("Casual", personaSelectorText, personaSelectorArrow, personaOptionsCard) }
        personaShakespeare.setOnClickListener { selectPersona("Academic", personaSelectorText, personaSelectorArrow, personaOptionsCard) }
        personaYoda.setOnClickListener { selectPersona("Poetry", personaSelectorText, personaSelectorArrow, personaOptionsCard) }
        personaCustom.setOnClickListener { showCustomPersonaDialog(view.context, personaSelectorText, personaSelectorArrow, personaOptionsCard) }

        btnRewrite.setOnClickListener {
            if (isGenerating) return@setOnClickListener
            isPerformingManualTransition = true
            isGenerating = true
            startGeneration(editTextContent.text.toString())
        }
    }

    /**
     * 60 FPS Optimized UI cascade animation for buttery smooth performance
     */
    private fun animateUIElementsCascade(containerView: View, show: Boolean = true) {
        val elementsToAnimate = listOf(
            containerView.findViewById<View>(R.id.edit_text_content),
            containerView.findViewById<View>(R.id.persona_selector_layout),
            containerView.findViewById<View>(R.id.button_rewrite),
            containerView.findViewById<View>(R.id.settings_icon_container)
        ).filterNotNull()
        
        if (show) {
            // 60 FPS optimized entrance cascade - minimal transforms for maximum smoothness
            elementsToAnimate.forEachIndexed { index, element ->
                element.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(BUTTERY_SMOOTH_DURATION)
                    .setStartDelay(index * STAGGER_DELAY)
                    .setInterpolator(BUTTERY_INTERPOLATOR)
                    .start()
            }
        } else {
            // 60 FPS optimized exit cascade
            elementsToAnimate.forEachIndexed { index, element ->
                element.animate()
                    .alpha(0f)
                    .translationY(-15f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(FAST_ANIMATION_DURATION)
                    .setStartDelay(index * (STAGGER_DELAY / 2))
                    .setInterpolator(SMOOTH_EXIT_INTERPOLATOR)
                    .start()
            }
        }
    }

    /**
     * 60 FPS Optimized persona dropdown animation
     */
    private fun animatePersonaDropdown(personaOptionsCard: View, arrowView: ImageView, show: Boolean) {
        val rotation = if (show) 180f else 0f
        
        // Optimized arrow rotation for 60 FPS
        arrowView.animate()
            .rotation(rotation)
            .setDuration(BUTTERY_SMOOTH_DURATION)
            .setInterpolator(BUTTERY_INTERPOLATOR)
            .start()
        
        if (show) {
            // 60 FPS optimized dropdown entrance
            personaOptionsCard.visibility = View.VISIBLE
            personaOptionsCard.alpha = 0f
            personaOptionsCard.scaleY = 0.85f
            personaOptionsCard.scaleX = 0.95f
            personaOptionsCard.translationY = -10f
            
            personaOptionsCard.animate()
                .alpha(1f)
                .scaleY(1f)
                .scaleX(1f)
                .translationY(0f)
                .setDuration(BUTTERY_SMOOTH_DURATION)
                .setInterpolator(BUTTERY_INTERPOLATOR)
                .withStartAction {
                    // Animate individual option items
                    animatePersonaOptions(personaOptionsCard, true)
                }
                .start()
        } else {
            // 60 FPS optimized exit animation
            personaOptionsCard.animate()
                .alpha(0f)
                .scaleY(0.9f)
                .scaleX(0.96f)
                .translationY(-8f)
                .setDuration(FAST_ANIMATION_DURATION)
                .setInterpolator(SMOOTH_EXIT_INTERPOLATOR)
                .withEndAction { 
                    personaOptionsCard.visibility = View.GONE 
                    // Reset transforms for next show
                    personaOptionsCard.scaleY = 1f
                    personaOptionsCard.scaleX = 1f
                    personaOptionsCard.translationY = 0f
                }
                .start()
        }
    }

    private fun animatePersonaOptions(personaOptionsCard: View, show: Boolean) {
        try {
            val container = personaOptionsCard as? ViewGroup ?: return
            
            if (show) {
                // 60 FPS optimized staggered entrance for persona options
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    child.alpha = 0f
                    child.translationX = -30f
                    child.scaleX = 0.9f
                    child.scaleY = 0.9f
                    
                    child.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(BUTTERY_SMOOTH_DURATION)
                        .setStartDelay(i * STAGGER_DELAY)
                        .setInterpolator(BUTTERY_INTERPOLATOR)
                        .start()
                }
            } else {
                // 60 FPS optimized staggered exit
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    
                    child.animate()
                        .alpha(0f)
                        .translationX(20f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(FAST_ANIMATION_DURATION)
                        .setStartDelay((i * (STAGGER_DELAY / 2)).toLong())
                        .setInterpolator(SMOOTH_EXIT_INTERPOLATOR)
                        .start()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error animating persona options: ${e.message}")
            }
        }
    }

    private fun updatePersonaSelectorText(textView: TextView) {
        val personaText = when {
            selectedPersona.equals("Custom", ignoreCase = true) && customPersonaPrompt.isNotBlank() -> {
                val preview = customPersonaPrompt.take(12)
                if (customPersonaPrompt.length > 12) "$preview..." else customPersonaPrompt
            }
            else -> selectedPersona
        }
        textView.text = personaText
    }

    private fun selectPersona(persona: String, personaTextView: TextView, arrowView: ImageView, personaOptionsCard: View) {
        selectedPersona = persona
        updatePersonaSelectorText(personaTextView)
        
        // Animate closing with smooth transition
        isPersonaDropdownVisible = false
        animatePersonaDropdown(personaOptionsCard, arrowView, false)
        
        // Add a subtle feedback animation to the selector
        personaTextView.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(100)
            .withEndAction {
                personaTextView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
            
        Toast.makeText(this, "Persona selected: $persona", Toast.LENGTH_SHORT).show()
    }

    private fun getPersonaPrompt(): String {
        return when (selectedPersona) {
            "Casual" -> "You MUST adopt the persona of a friendly, easy-going person. Your goal is to make the text sound more natural, relaxed, and conversational. Use contractions (like it's, you're) and common phrasing."
            "Academic" -> "You MUST adopt the persona of a university professor or researcher. Your goal is to make the text sound formal, scholarly, and objective. Use complex sentences, precise vocabulary, and avoid colloquialisms or emotional language. The tone should be authoritative and well-structured."
            "Poetry" -> "You MUST adopt the persona of a poet. Your goal is to transform the text into a short, lyrical piece. Use literary devices like metaphors, similes, imagery, and rhythm to convey the core message in an artistic and evocative way."
            "Custom" -> if (customPersonaPrompt.isNotBlank()) "You MUST adopt the following specific persona: '$customPersonaPrompt'" else ""
            else -> ""
        }
    }
    
    /**
     * Generates a random persona description for creative inspiration
     */
    private fun generateRandomPersona(): String {
        return randomPersonas.random()
    }

    private fun startGeneration(textToRewrite: String) {
        // --- The Login Check Gate ---
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "startGeneration called - checking login status")
            Log.d(TAG, "CreditManager state - isLoggedIn: ${creditManager.isUserLoggedIn()}, isPro: ${creditManager.isProUser()}, credits: ${creditManager.getCredits()}")
            if (generatingFromFocusedEditor) {
                Log.d(TAG, "Generation originated from focused editor")
            }
        }
        
        // If we're not coming from focused editor, handle the focused editor cleanup
        if (!generatingFromFocusedEditor) {
            val tempFocusedView = focusedEditorView
            
            // Clear the reference immediately to prevent race conditions
            if (tempFocusedView != null) {
                focusedEditorView = null
                
                // Safely remove the view if it's still attached
                try {
                    if (tempFocusedView.isAttachedToWindow) {
                        windowManager.removeView(tempFocusedView)
                        Log.d(TAG, "Successfully removed focusedEditorView in startGeneration")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing focused editor in startGeneration", e)
                }
            }
        }
        
        // Make sure bottom sheet is visible and ready
        if (bottomSheetView?.visibility != View.VISIBLE) {
            bottomSheetView?.visibility = View.VISIBLE
            bottomSheetView?.alpha = 1f
        }
        
        if (!creditManager.isUserLoggedIn()) {
            try {
                val snackbarView = bottomSheetView?.findViewById<View>(R.id.bottom_sheet_root) ?: run {
                    Toast.makeText(this, "Please log in to use Rewordium", Toast.LENGTH_LONG).show()
                    return
                }

                // Create standard Snackbar first
                val snackbar = Snackbar.make(snackbarView, "Please log in to use Rewordium", Snackbar.LENGTH_LONG)
                
                // Set action to open the app
                snackbar.setAction("LOG IN") {
                    try {
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching app", e)
                    }
                }
                
                // Set custom styling and ensure it appears on top
                snackbar.view.apply {
                    setBackgroundResource(R.drawable.snackbar_background)
                    elevation = 16f // High elevation to appear above bottom sheet
                    translationZ = 16f // Additional z-translation for older devices
                }
                snackbar.setActionTextColor(resources.getColor(android.R.color.white, null))
                
                // Show the snackbar
                snackbar.show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing login prompt", e)
                Toast.makeText(this, "Please log in to use Rewordium", Toast.LENGTH_LONG).show()
            }
            Log.w(TAG, "Action blocked: User is not logged in.")
            isPerformingManualTransition = false
            isGenerating = false
            return
        }

        // --- The Credit Check Gate ---
        if (!creditManager.canPerformAction()) {
            Toast.makeText(this, "Out of credits. Please open the Rewordium app to upgrade.", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Action blocked: User is out of credits.")
            isPerformingManualTransition = false
            isGenerating = false
            return
        }

        focusedEditorView = null
        
        // Enhanced logic: If no text entered, read on-screen content with beautiful gradient wave
        if (textToRewrite.isBlank()) {
            val screenContent = readOnScreenContent()
            if (screenContent.isBlank()) {
                Toast.makeText(this, "No text to process and no screen content found", Toast.LENGTH_SHORT).show()
                isPerformingManualTransition = false
                isGenerating = false
                return
            }
            
            // Show beautiful RGB gradient wave animation
            showRGBGradientWave()
            
            // Update the input field with screen content and continue processing
            bottomSheetView?.findViewById<EditText>(R.id.edit_text_content)?.setText(screenContent)
            
            // Show feedback to user
            Toast.makeText(this, "📖 Reading screen content to help you respond!", Toast.LENGTH_LONG).show()
            
            // Continue with the screen content as input
            return startGeneration(screenContent)
        }
        isStoppedByUser = false

        // --- RESTORED ORIGINAL PROMPT LOGIC ---
        val emailKeywords = listOf("email", "mail", "write", "compose", "send", "letter", "message to", "inform", "notify")
        val isGenerationTask = emailKeywords.any { keyword ->
            textToRewrite.trim().lowercase().contains(keyword)
        } || textToRewrite.trim().startsWith("/generate", ignoreCase = true)

        val personaInstruction = getPersonaPrompt()

        val finalPrompt = if (isGenerationTask) {
            val userCommand = if (textToRewrite.trim().startsWith("/generate", ignoreCase = true)) {
                textToRewrite.substring("/generate".length).trim()
            } else {
                textToRewrite.trim()
            }
            """
            You are an expert text generator. 
            $personaInstruction

            Your task is to generate a complete, ready-to-send email or message based on the user's request.
            If it's an email, it MUST include a clear Subject line, a proper Greeting, a well-structured Body, and an appropriate Closing.
            Your entire response MUST BE only the generated content itself. Do not include any explanations, titles, or surrounding conversational text.

            USER'S REQUEST: "$userCommand"
            """.trimIndent()
        } else {
            when (selectedPersona) {
                "Casual" ->
                    """
                    $personaInstruction
                    Rewrite the user's text into three distinct casual styles, each on a new line:
                    1. Friendly & Warm: Extra positive and approachable.
                    2. Clear & Direct: Efficient but still relaxed.
                    3. Playful & Witty: A bit more fun and clever.

                    Your output MUST BE ONLY the three rewritten sentences, each on a new line.
                    ABSOLUTELY NO labels (like "1.", "Friendly:"), titles, or any other extra text.

                    ---
                    USER'S TEXT: "$textToRewrite"
                    YOUR RESPONSE:
                    """.trimIndent()
                "Academic" ->
                    """
                    $personaInstruction
                    Rewrite the user's text into three distinct academic styles, each on a new line:
                    1. Formal Statement: A clear, declarative thesis-style sentence.
                    2. Detailed Explanation: A more elaborate version with structured reasoning.
                    3. Concise Summary: A brief, objective summary suitable for an abstract.

                    Your output MUST BE ONLY the three rewritten sentences, each on a new line.
                    ABSOLUTELY NO labels, titles, or any other extra text.

                    ---
                    USER'S TEXT: "$textToRewrite"
                    YOUR RESPONSE:
                    """.trimIndent()
                "Poetry" ->
                    """
                    $personaInstruction
                    Transform the user's text into a beautiful, flowing piece of poetry. 
                    Create a single, well-crafted poetic response that captures the essence of the original text.
                    The response should be at least 4-6 lines long, with rich imagery and emotional depth.
                    Use literary devices like metaphor, alliteration, and rhythm to create a compelling piece.
                    
                    ABSOLUTELY NO labels, titles, or any other extra text - just the poem itself.

                    ---
                    USER'S TEXT: "$textToRewrite"
                    YOUR RESPONSE:
                    """.trimIndent()
                else -> // For Custom Persona
                    """
                    $personaInstruction
                    Your task is to rewrite the user's text into three distinct stylistic variations, all firmly in character. Each should be on a new line.
                    Your output MUST BE ONLY the three rewritten sentences.
                    ABSOLUTELY NO labels, titles, or any other extra text.
                    
                    ---
                    USER'S TEXT: "$textToRewrite"
                    YOUR RESPONSE:
                    """.trimIndent()
            }
        }
        // --- END OF RESTORED LOGIC ---

        val btnRewrite = bottomSheetView!!.findViewById<Button>(R.id.button_rewrite)
        val thinkingLayout = bottomSheetView!!.findViewById<LinearLayout>(R.id.thinking_layout)
        val btnStop = bottomSheetView!!.findViewById<Button>(R.id.button_stop)
        val thinkingText = bottomSheetView!!.findViewById<TextView>(R.id.thinking_text_view)
        
        btnRewrite.visibility = View.GONE
        thinkingLayout.visibility = View.VISIBLE
        bottomSheetView!!.findViewById<LinearLayout>(R.id.suggestion_layout).visibility = View.GONE
        bottomSheetView!!.findViewById<LinearLayout>(R.id.suggestions_container).removeAllViews()

        thinkingText.text = when (selectedPersona) {
            "Academic" -> "Consulting archives..."
            "Poetry" -> "Weaving words into verse..."
            "Custom" -> "Channeling your persona..."
            else -> "Making it sound natural..."
        }

        btnStop.setOnClickListener {
            isPerformingManualTransition = true
            isStoppedByUser = true
            generationJob?.cancel()

            // Enhanced stop animation with scale and fade
            thinkingLayout.animate()
                .alpha(0f)
                .scaleY(0.8f)
                .setDuration(FAST_ANIMATION_DURATION)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    thinkingLayout.visibility = View.GONE
                    thinkingLayout.alpha = 1f
                    thinkingLayout.scaleY = 1f
                    btnRewrite.visibility = View.VISIBLE
                    btnRewrite.alpha = 0f
                    btnRewrite.scaleY = 0.8f
                    btnRewrite.animate()
                        .alpha(1f)
                        .scaleY(1f)
                        .setDuration(FAST_ANIMATION_DURATION)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                        .start()
                    isPerformingManualTransition = false
                    isGenerating = false
                }
                .start()
        }

        generationJob = serviceScope.launch {
            try {
                // Send intent to MainActivity to consume credit via Flutter
                if (BuildConfig.DEBUG) Log.d(TAG, "Requesting credit consumption via Flutter...")
                val consumeCreditIntent = Intent("com.noxquill.rewordium.CONSUME_CREDIT_REQUEST")
                consumeCreditIntent.setPackage(packageName)
                sendBroadcast(consumeCreditIntent)
                
                // Small delay to allow credit consumption to process
                delay(API_DELAY)

                if (BuildConfig.DEBUG) Log.d(TAG, "Making API request...")
                val request = GroqRequest(model = "llama-3.1-8b-instant", messages = listOf(Message("user", finalPrompt)))
                val response = withContext(Dispatchers.IO) { 
                    try {
                        RetrofitClient.instance.getGroqCompletion(GROQ_API_KEY, request)
                    } catch (e: Exception) {
                        Log.e(TAG, "API request failed", e)
                        throw e
                    }
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val content = response.body()!!.choices.firstOrNull()?.message?.content ?: ""

                    // --- RESTORED SUGGESTION PARSING ---
                    val suggestions = if (isGenerationTask || selectedPersona == "Poetry") {
                        listOf(content.trim())
                    } else {
                        content.lines().filter { it.isNotBlank() }.take(3)
                    }

                    withContext(Dispatchers.Main) {
                        displaySuggestions(suggestions, bottomSheetView!!.findViewById(R.id.suggestions_container), bottomSheetView!!.findViewById(R.id.suggestion_layout), activeSourceNode, isGenerationTask)
                    }
                } else {
                     withContext(Dispatchers.Main) {
                        Toast.makeText(this@MyAccessibilityService, "API Error: ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                     Log.i(TAG, "Generation job was cancelled.")
                } else {
                    Log.e(TAG, "Generation error", e)
                     withContext(Dispatchers.Main) {
                        Toast.makeText(this@MyAccessibilityService, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    try {
                        if (!isStoppedByUser) {
                            thinkingLayout.visibility = View.GONE
                            btnRewrite.visibility = View.VISIBLE
                        } else {
                            // User manually stopped generation - ensure consistent state
                            Log.d(TAG, "Generation was stopped by user - ensuring UI is consistent")
                            thinkingLayout.visibility = View.GONE
                            btnRewrite.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating UI after generation", e)
                    } finally {
                        // Always make sure we reset these flags to avoid UI getting stuck
                        // **FIX 1: REMOVED `generatingFromFocusedEditor = false` FROM HERE**
                        isPerformingManualTransition = false
                        isGenerating = false
                        isTransitioningFromFocusedEditor = false
                        // Only clear focused editor reference if it wasn't already cleared
                        if (focusedEditorView != null) {
                            focusedEditorView = null
                        }
                    }
                }
            }
        }
    }

    private fun toggleSettings() {
        val settingsSection = bottomSheetView?.findViewById<View>(R.id.settings_section) ?: return
        val settingsIcon = bottomSheetView?.findViewById<View>(R.id.settings_icon_container)

        if (settingsSection.visibility == View.VISIBLE) {
            settingsSection.visibility = View.GONE
            settingsIcon?.isSelected = false
        } else {
            settingsSection.visibility = View.VISIBLE
            settingsIcon?.isSelected = true
            initializeSettings()
        }
    }

    private fun initializeSettings() {
        val settingsSection = bottomSheetView?.findViewById<View>(R.id.settings_section) ?: return
        val darkModeSwitch = settingsSection.findViewById<SwitchCompat>(R.id.switch_dark_mode)
        val creditsTextView = settingsSection.findViewById<TextView>(R.id.credits_remaining_text)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        
        // Initialize dark mode switch
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        darkModeSwitch?.isChecked = isDarkMode
        
        // Apply current theme to the UI
        applyTheme(isDarkMode)
        
        darkModeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            
            // Apply theme with smooth animation
            animateThemeTransition(isChecked)
            
            // Apply the new theme
            applyTheme(isChecked)
            
            // Show feedback
            val message = if (isChecked) "Dark mode enabled" else "Light mode enabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        
        // Update credits display
        if (creditManager.isProUser()) {
            creditsTextView.text = "Unlimited Credits (Pro)"
        } else {
            val creditCount = creditManager.getCredits()
            creditsTextView.text = "Credits Remaining: $creditCount"
        }
    }

    private fun showCustomPersonaDialog(context: Context, personaTextView: TextView, arrowView: ImageView, personaOptionsCard: View) {
        // First close the dropdown with animation
        isPersonaDropdownVisible = false
        animatePersonaDropdown(personaOptionsCard, arrowView, false)
        
        // Hide the bottom sheet while dialog is open
        bottomSheetView?.visibility = View.GONE
        
        // Get current theme settings
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_custom_persona, null)
        val builder = AlertDialog.Builder(context).setView(dialogView)
        val dialog = builder.create()
        customPersonaDialog = dialog // Store reference for cleanup
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Enhanced window configuration for stability
        dialog.window?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            } else {
                @Suppress("DEPRECATION")
                setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
            
            // Use flags that allow input while preventing dismissal
            addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                     WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                     WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Remove flags that block input
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            
            // Ensure proper layout parameters
            attributes = attributes?.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or 
                               WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        }
        
        // Allow dialog to receive input but prevent accidental dismissal
        dialog.setCancelable(true)  // Allow back button to work
        dialog.setCanceledOnTouchOutside(false)  // Prevent dismissal on outside touch
        
        // Enhanced dismiss function with animation
        fun dismissWithAnimation() {
            dialogView.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(FAST_ANIMATION_DURATION)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction { 
                    if (dialog.isShowing) {
                        try {
                            dialog.dismiss() // This will trigger the OnDismissListener
                        } catch (e: Exception) {
                            Log.e(TAG, "Error dismissing custom persona dialog", e)
                        }
                    }
                }
                .start()
        }
        
        // Handle back button press properly
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissWithAnimation()
                true
            } else {
                false
            }
        }
        
        val editText = dialogView.findViewById<EditText>(R.id.dialog_edit_text_persona)
        editText.setText(customPersonaPrompt)
        
        // Ensure EditText can receive input properly
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.isEnabled = true
        editText.isCursorVisible = true
        
        // Set input type to allow multiline text input
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                           android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                           android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        
        // Apply theme colors to dialog elements
        applyDialogTheme(dialogView, isDarkMode)
        
        // Add smooth dialog entrance animation
        dialog.setOnShowListener {
            dialogView.alpha = 0f
            dialogView.scaleX = 0.7f
            dialogView.scaleY = 0.7f
            dialogView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(BUTTERY_SMOOTH_DURATION)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                .withEndAction {
                    // Request focus for EditText after animation completes
                    editText.requestFocus()
                    // Show soft keyboard if needed
                    try {
                        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        inputMethodManager?.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not show soft keyboard for custom persona dialog", e)
                    }
                }
                .start()
        }
        
        // *** THE CRITICAL FIX ***
        // This listener ensures that our protective flag is reset and bottom sheet is shown when dialog closes.
        dialog.setOnDismissListener {
            if (BuildConfig.DEBUG) Log.d(TAG, "Custom persona dialog dismissed, resetting manual transition flag and showing bottom sheet.")
            customPersonaDialog = null // Clear the reference
            // Show bottom sheet back when dialog is dismissed with proper state management
            bottomSheetView?.let { sheet ->
                sheet.visibility = View.VISIBLE
                sheet.alpha = 1f
                // Ensure bottom sheet behavior is properly set to expanded or collapsed
                try {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting bottom sheet state after dialog dismiss: ${e.message}")
                }
            }
            // Delay resetting the flag to prevent periodic check interference
            Handler(Looper.getMainLooper()).postDelayed({
                isPerformingManualTransition = false
                if (BuildConfig.DEBUG) Log.d(TAG, "Manual transition flag reset after dialog dismiss delay")
            }, 300) // 300ms delay to prevent race conditions
        }
        
        dialogView.findViewById<Button>(R.id.dialog_button_cancel).setOnClickListener { 
            dismissWithAnimation()
        }
        
        dialogView.findViewById<Button>(R.id.dialog_button_set).setOnClickListener {
            val customPrompt = editText.text.toString().trim()
            if (customPrompt.isNotEmpty()) {
                customPersonaPrompt = customPrompt
                selectedPersona = "Custom"
                updatePersonaSelectorText(personaTextView)
                
                // Add success feedback animation
                personaTextView.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction {
                        personaTextView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
                    
                Toast.makeText(this, "Custom persona set!", Toast.LENGTH_SHORT).show()
            }
            dismissWithAnimation()
        }
        
        // Random persona generator button
        dialogView.findViewById<Button>(R.id.dialog_button_random).setOnClickListener {
            // Generate random persona with smooth animation
            val randomButton = it as Button
            
            // Button press animation
            randomButton.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    randomButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
            
            // Generate and set random persona
            val randomPersona = generateRandomPersona()
            editText.setText(randomPersona)
            
            // Scroll to top of EditText to show the beginning of the text
            editText.setSelection(0)
            
            // Add sparkle animation to EditText
            editText.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(200)
                .withEndAction {
                    editText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
            
            // Show feedback
            Toast.makeText(this, "✨ Random persona generated!", Toast.LENGTH_SHORT).show()
        }
        
        // *** THE CRITICAL FIX ***
        // Set the flag to true right before showing the dialog to prevent the
        // periodic checker from interfering.
        if (BuildConfig.DEBUG) Log.d(TAG, "Showing custom persona dialog, setting manual transition flag.")
        isPerformingManualTransition = true
        
        try {
            dialog.show()
            
            // Additional safety check - ensure dialog stays visible
            Handler(Looper.getMainLooper()).postDelayed({
                if (dialog.isShowing && customPersonaDialog == dialog) {
                    // Dialog is still properly showing, good
                    if (BuildConfig.DEBUG) Log.d(TAG, "Custom persona dialog is stable and showing properly")
                } else {
                    // Dialog was dismissed unexpectedly, log for debugging
                    if (BuildConfig.DEBUG) Log.w(TAG, "Custom persona dialog was dismissed unexpectedly")
                }
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing custom persona dialog", e)
            // Reset state if dialog failed to show
            isPerformingManualTransition = false
            bottomSheetView?.visibility = View.VISIBLE
            customPersonaDialog = null
        }
    }
    
    private fun applyDialogTheme(dialogView: View, isDarkMode: Boolean) {
        try {
            // Define colors based on theme
            val backgroundColor: Int
            val titleTextColor: Int
            val editTextColor: Int
            val editHintColor: Int
            val setButtonBg: Int
            val setButtonText: Int
            val randomButtonBg: Int
            val randomButtonText: Int
            
            if (isDarkMode) {
                backgroundColor = 0xFF1E1E1E.toInt() // Darker, sleeker background
                titleTextColor = resources.getColor(android.R.color.white, null)
                editTextColor = resources.getColor(android.R.color.white, null)
                editHintColor = 0xFF888888.toInt() // Subtle gray hint
                setButtonBg = 0xFFFFFFFF.toInt() // White button in dark mode
                setButtonText = 0xFF000000.toInt() // Black text on white button
                randomButtonBg = 0xFFFFFFFF.toInt() // White random button in dark mode
                randomButtonText = 0xFF000000.toInt() // Black text on white button
            } else {
                backgroundColor = 0xFFFAFAFA.toInt() // Ultra-light, sleek background
                titleTextColor = resources.getColor(android.R.color.black, null)
                editTextColor = resources.getColor(android.R.color.black, null)
                editHintColor = 0xFF999999.toInt() // Light gray hint
                setButtonBg = 0xFF000000.toInt() // Black button in light mode
                setButtonText = 0xFFFFFFFF.toInt() // White text on black button
                randomButtonBg = 0xFF000000.toInt() // Black random button in light mode
                randomButtonText = 0xFFFFFFFF.toInt() // White text on black button
            }
            
            // Apply background color to main dialog container
            val dialogBackground = android.graphics.drawable.GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = 80f // MAXIMUM circular dialog - super rounded
                // Remove border for sleeker look
            }
            dialogView.background = dialogBackground
            
            // Apply title text color - find the TextView with "Custom Persona" text
            try {
                // Find the title TextView by traversing the view hierarchy
                val titleLayout = dialogView.findViewById<LinearLayout>(dialogView.context.resources.getIdentifier("title_layout", "id", dialogView.context.packageName))
                    ?: (dialogView as? LinearLayout)?.getChildAt(0) as? LinearLayout
                
                titleLayout?.let { layout ->
                    for (i in 0 until layout.childCount) {
                        val child = layout.getChildAt(i)
                        if (child is TextView && child.text.toString().contains("Custom Persona")) {
                            child.setTextColor(titleTextColor)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback: Find any TextView with "Custom Persona" text in the entire dialog
                findTextViewWithText(dialogView, "Custom Persona")?.setTextColor(titleTextColor)
            }
            
            // Apply EditText colors with ultra-rounded background
            dialogView.findViewById<EditText>(R.id.dialog_edit_text_persona)?.apply {
                setTextColor(editTextColor)
                setHintTextColor(editHintColor)
                
                // Create ultra-rounded background for EditText
                val editTextBackground = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (isDarkMode) 0xFF2A2A2A.toInt() else 0xFFEEEEEE.toInt())
                    cornerRadius = 50f // MAXIMUM circular EditText - super rounded
                    // Remove border for sleeker look
                }
                background = editTextBackground
                elevation = 2f // Subtle elevation
            }
            
            // Apply SET PERSONA button with MAXIMUM circular styling
            dialogView.findViewById<Button>(R.id.dialog_button_set)?.apply {
                val setButtonBackground = android.graphics.drawable.GradientDrawable().apply {
                    setColor(setButtonBg)
                    cornerRadius = 60f // MAXIMUM circular button - super rounded pill shape
                }
                background = setButtonBackground
                setTextColor(setButtonText)
                elevation = 8f // Higher elevation for premium feel
                stateListAnimator = null // Remove default state animations for cleaner look
            }
            
            // CANCEL button with MAXIMUM circular red styling
            dialogView.findViewById<Button>(R.id.dialog_button_cancel)?.apply {
                val cancelButtonBackground = android.graphics.drawable.GradientDrawable().apply {
                    if (isDarkMode) {
                        setColor(0x20FF4444.toInt()) // Subtle red tint in dark mode
                    } else {
                        setColor(0x15FF3333.toInt()) // Very light red tint in light mode
                    }
                    cornerRadius = 40f // MAXIMUM circular cancel button - super rounded
                }
                background = cancelButtonBackground
                setTextColor(if (isDarkMode) 0xFFFF7777.toInt() else 0xFFFF4444.toInt())
                elevation = 4f // Moderate elevation
                stateListAnimator = null // Clean look
            }
            
            // Random button - MAXIMUM circular with theme colors
            dialogView.findViewById<Button>(R.id.dialog_button_random)?.apply {
                val randomButtonBackground = android.graphics.drawable.GradientDrawable().apply {
                    setColor(randomButtonBg) // Theme-appropriate color
                    cornerRadius = 50f // MAXIMUM circle - super rounded (even more than button size for ultimate roundness)
                }
                background = randomButtonBackground
                setTextColor(randomButtonText) // Theme-appropriate text color
                elevation = 10f // Highest elevation for the accent button
                stateListAnimator = null // Clean, sleek look
                
                // Add subtle scaling animation on touch
                setOnTouchListener { view, motionEvent ->
                    when (motionEvent.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        }
                    }
                    false // Let the click event continue
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to custom persona dialog", e)
        }
    }
    
    /**
     * Helper method to find a TextView with specific text in the view hierarchy
     */
    private fun findTextViewWithText(parent: View, text: String): TextView? {
        if (parent is TextView && parent.text.toString().contains(text)) {
            return parent
        }
        
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val result = findTextViewWithText(child, text)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    /**
     * Apply theme to focused editor elements for dark/light mode support
     */
    private fun applyFocusedEditorTheme() {
        focusedEditorView?.let { editorView ->
            try {
                // Get current theme settings
                val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val isDarkMode = prefs.getBoolean("dark_mode", false)
                
                // Define colors based on theme
                val editTextColor: Int
                val editHintColor: Int
                val editTextBackgroundColor: Int
                val buttonBackgroundColor: Int
                val buttonIconColor: Int
                val containerBackgroundColor: Int
                
                if (isDarkMode) {
                    editTextColor = resources.getColor(android.R.color.white, null)
                    editHintColor = 0xFF888888.toInt() // Gray hint in dark mode
                    editTextBackgroundColor = 0xFF2A2A2A.toInt() // Dark gray background
                    buttonBackgroundColor = 0xFFFFFFFF.toInt() // White button in dark mode
                    buttonIconColor = 0xFF000000.toInt() // Black icon on white button
                    containerBackgroundColor = 0xFF1E1E1E.toInt() // Dark container
                } else {
                    editTextColor = resources.getColor(android.R.color.black, null)
                    editHintColor = 0xFF666666.toInt() // Dark gray hint in light mode
                    editTextBackgroundColor = 0xFFF5F5F5.toInt() // Light gray background
                    buttonBackgroundColor = 0xFF000000.toInt() // Black button in light mode
                    buttonIconColor = 0xFFFFFFFF.toInt() // White icon on black button
                    containerBackgroundColor = 0xFFFAFAFA.toInt() // Light container
                }
                
                // Apply theme to EditText
                editorView.findViewById<EditText>(R.id.focused_edit_text)?.apply {
                    setTextColor(editTextColor)
                    setHintTextColor(editHintColor)
                    
                    // Create themed background
                    val editBackground = android.graphics.drawable.GradientDrawable().apply {
                        setColor(editTextBackgroundColor)
                        cornerRadius = 24f // Rounded corners
                        setStroke(2, if (isDarkMode) 0xFF404040.toInt() else 0xFFE0E0E0.toInt())
                    }
                    background = editBackground
                }
                
                // Apply theme to Generate button
                editorView.findViewById<MaterialButton>(R.id.focused_button_generate)?.apply {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(buttonBackgroundColor)
                    iconTint = android.content.res.ColorStateList.valueOf(buttonIconColor)
                    elevation = 4f // Subtle elevation
                }
                
                // Apply theme to container background
                val containerBackground = android.graphics.drawable.GradientDrawable().apply {
                    setColor(containerBackgroundColor)
                    cornerRadius = 24f // Rounded corners for modern look
                    // Add subtle shadow effect
                    if (isDarkMode) {
                        setStroke(1, 0xFF333333.toInt()) // Subtle border in dark mode
                    } else {
                        setStroke(1, 0xFFE0E0E0.toInt()) // Light border in light mode
                    }
                }
                editorView.background = containerBackground
                
            } catch (e: Exception) {
                Log.e(TAG, "Error applying theme to focused editor", e)
            }
        }
    }
    
    private fun hideBottomSheet() {
        if (isGenerating || isTransitioningFromFocusedEditor || generatingFromFocusedEditor) return
        
        // 60 FPS optimized exit animation - minimal transforms for smoothness
        bottomSheetView?.animate()
            ?.alpha(0f)
            ?.scaleX(0.96f)
            ?.scaleY(0.96f)
            ?.translationY(20f)
            ?.setDuration(FAST_ANIMATION_DURATION)
            ?.setInterpolator(SMOOTH_EXIT_INTERPOLATOR)
            ?.withStartAction {
                // Start cascade exit immediately for coordinated animation
                bottomSheetView?.let { sheet ->
                    animateUIElementsCascade(sheet, false)
                }
            }
            ?.withEndAction {
                if (bottomSheetView != null && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    cleanupBottomSheetView()
                }
            }
            ?.start() ?: run {
            // Fallback if bottomSheetView is null
            if (bottomSheetView != null && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                cleanupBottomSheetView()
            }
        }
    }

        /**
     * Instantly removes the bottom sheet view without animation.
     * This is used for system-level events like going home or locking the screen.
     */
    private fun hideBottomSheetInstantly() {
        // Don't hide during focused editor transitions
        if (isTransitioningFromFocusedEditor || generatingFromFocusedEditor) {
            Log.d(TAG, "Skipping instant hide during focused editor flow")
            return
        }
        
        if (bottomSheetView != null) {
            // This directly calls the cleanup logic, bypassing the
            // BottomSheetBehavior's hide animation.
            cleanupBottomSheetView()
        }
    }

    // *** FIX FOR CRASHING ***
    // This function is now the single, robust point of cleanup.
    private fun cleanupBottomSheetView() {
        try {
            // Cancel any ongoing generation
            generationJob?.cancel()
            
            // Safely remove focused editor view
            focusedEditorView?.let { view ->
                try {
                    if (view.isAttachedToWindow) {
                        windowManager.removeView(view)
                        Log.d(TAG, "Successfully removed focused editor view during cleanup")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing focused editor view during cleanup", e)
                }
                Unit // Explicit unit return
            }
            focusedEditorView = null
            
            // Safely remove bottom sheet view
            bottomSheetView?.let { view ->
                try {
                    if (view.isAttachedToWindow) {
                        windowManager.removeView(view)
                        Log.d(TAG, "Successfully removed bottom sheet view during cleanup")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing bottom sheet view during cleanup", e)
                }
                Unit // Explicit unit return
            }
            bottomSheetView = null
            
            // Clean up other resources
            activeSourceNode?.recycle()
            activeSourceNode = null
            
            // Clean up gradient wave animation
            hideRGBGradientWave()
            
            floatingBubbleView?.resetIdleTimer()
            
            // **FIX 2: Reset ALL relevant state flags here. This is the central reset point.**
            isGenerating = false
            isPerformingManualTransition = false
            generatingFromFocusedEditor = false // <-- The crucial fix.
            isTransitioningFromFocusedEditor = false
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cleanup complete, all state flags reset.")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled error during cleanup", e)
        }
    }

    private fun showFocusedEditor(currentText: String) {
        if (focusedEditorView != null) return
        bottomSheetView?.visibility = View.GONE

        val themedContext = ContextThemeWrapper(this, R.style.Theme_App_Translucent)
        val inflater = LayoutInflater.from(themedContext)
        focusedEditorView = inflater.inflate(R.layout.layout_focused_editor, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        windowManager.addView(focusedEditorView, params)

        // Apply theme to focused editor
        applyFocusedEditorTheme()

        focusedEditorView?.apply {
            alpha = 0f
            translationY = 150f
            animate().alpha(1f).translationY(0f).setDuration(BUTTERY_SMOOTH_DURATION).start()
        }

        // *** FIX: Added listener to detect and handle touches outside the view's bounds ***
        focusedEditorView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideFocusedEditor()
                true // Consume the event, as we've handled it.
            } else {
                false // Don't consume the event, let children (like EditText) handle it.
            }
        }

        focusedEditorView?.isFocusableInTouchMode = true
        focusedEditorView?.requestFocus()
        focusedEditorView?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                hideFocusedEditor()
                true
            } else false
        }

        val focusedEditText = focusedEditorView!!.findViewById<EditText>(R.id.focused_edit_text)
        focusedEditText.setText(currentText)
        focusedEditText.requestFocus()
        focusedEditText.setSelection(currentText.length)

        focusedEditorView!!.findViewById<MaterialButton>(R.id.focused_button_generate).setOnClickListener {
            if (isGenerating || isTransitioningFromFocusedEditor) return@setOnClickListener
            val newText = focusedEditText.text.toString()

            isPerformingManualTransition = true
            isTransitioningFromFocusedEditor = true
            generatingFromFocusedEditor = true

            bottomSheetView?.findViewById<EditText>(R.id.edit_text_content)?.setText(newText)

            focusedEditorView?.animate()
                ?.alpha(0f)
                ?.translationY(100f)
                ?.setDuration(200)
                ?.withEndAction {
                    try {
                        val viewToRemove = focusedEditorView
                        focusedEditorView = null
                        if (viewToRemove?.isAttachedToWindow == true) {
                            windowManager.removeView(viewToRemove)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing focused editor view", e)
                    }

                    bottomSheetView?.visibility = View.VISIBLE
                    bottomSheetView?.alpha = 0f

                    // *** THE CRITICAL FIX FOR THE SWIPE GESTURE ISSUE ***
                    // Re-apply the layout params to force the WindowManager to update its focus state.
                    // This tells the system that no overlay is actively stealing focus anymore,
                    // allowing system gestures to work again.
                    bottomSheetView?.let {
                        try {
                            val bsParams = it.layoutParams as WindowManager.LayoutParams
                            windowManager.updateViewLayout(it, bsParams)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating bottom sheet layout", e)
                        }
                    }

                    bottomSheetView?.animate()
                        ?.alpha(1f)
                        ?.setDuration(150)
                        ?.withStartAction {
                            isTransitioningFromFocusedEditor = false
                            isGenerating = true
                            startGeneration(newText)
                        }
                        ?.start()
                }
                ?.start()
        }
    }

    private fun hideFocusedEditor(andCleanupVariable: Boolean = true, andReleaseLock: Boolean = true) {
        try {
            // Always use a local copy of the reference
            val view = focusedEditorView
            
            // If the focused editor view is null, just update the state
            if (view == null) {
                bottomSheetView?.visibility = View.VISIBLE
                if (andReleaseLock) isPerformingManualTransition = false
                return
            }
            
            // If cleanup is requested, null out the class member immediately
            // This ensures no other code can access it while we're in the process of removing it
            if (andCleanupVariable) focusedEditorView = null
            
            // Check if the view is actually attached to the window
            val isAttached = try { 
                view.isAttachedToWindow 
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if focused editor is attached", e)
                false
            }
            
            if (isAttached) {
                // If we're generating text, or we've been told to force remove, do it immediately
                if (isGenerating || generatingFromFocusedEditor) {
                    try {
                        windowManager.removeView(view)
                        Log.d(TAG, "Successfully removed focused editor view during generation")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing focused editor view during generation", e)
                    } finally {
                        // Ensure we always show the bottom sheet and update state
                        // Use a slight delay to ensure proper UI thread synchronization
                        Handler(Looper.getMainLooper()).post {
                            bottomSheetView?.visibility = View.VISIBLE
                            bottomSheetView?.alpha = 1f
                            if (andReleaseLock) isPerformingManualTransition = false
                        }
                    }
                    return
                }
                
                // Otherwise use animation with proper error handling
                try {
                    view.animate()
                        .alpha(0f)
                        .translationY(100f)
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .rotation(1f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .withEndAction {
                            try {
                                if (view.isAttachedToWindow) {
                                    windowManager.removeView(view)
                                    Log.d(TAG, "Successfully removed focused editor view after animation")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error removing focused editor view after animation", e)
                            } finally {
                                bottomSheetView?.visibility = View.VISIBLE
                                if (andReleaseLock) isPerformingManualTransition = false
                            }
                        }
                        .start()
                } catch (e: Exception) {
                    Log.e(TAG, "Error animating focused editor view", e)
                    // Fallback to immediate removal if animation fails
                    try {
                        if (view.isAttachedToWindow) {
                            windowManager.removeView(view)
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error in fallback removal of focused editor view", e2)
                    } finally {
                        bottomSheetView?.visibility = View.VISIBLE
                        if (andReleaseLock) isPerformingManualTransition = false
                    }
                }
            } else {
                // View is not attached to window, just update state
                Log.d(TAG, "Focused editor view was not attached to window")
                bottomSheetView?.visibility = View.VISIBLE
                if (andReleaseLock) isPerformingManualTransition = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error hiding focused editor", e)
            // Emergency cleanup to prevent app from getting stuck
            if (andCleanupVariable) focusedEditorView = null
            bottomSheetView?.visibility = View.VISIBLE
            if (andReleaseLock) isPerformingManualTransition = false
        }
    }

    private fun displaySuggestions(suggestions: List<String>, container: LinearLayout, suggestionLayout: LinearLayout, sourceNode: AccessibilityNodeInfo?, isGenerationTask: Boolean = false) {
        if (suggestions.isEmpty()) {
            Toast.makeText(this, "No suggestions found.", Toast.LENGTH_SHORT).show()
            return
        }
        suggestionLayout.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_App_Translucent))
        container.removeAllViews()
        
        // Get current theme settings
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        val suggestionTextColor = if (isDarkMode) {
            resources.getColor(android.R.color.white, null)
        } else {
            resources.getColor(android.R.color.black, null)
        }
        
        suggestions.forEachIndexed { index, suggestionText ->
            val suggestionItemView = inflater.inflate(R.layout.item_suggestion, container, false)
            val textView = suggestionItemView.findViewById<TextView>(R.id.text_suggestion)
            
            // Enhanced entrance animation with scale and rotation
            suggestionItemView.alpha = 0f
            suggestionItemView.translationY = 80f
            suggestionItemView.scaleX = 0.8f
            suggestionItemView.scaleY = 0.8f
            suggestionItemView.rotation = -5f
            
            container.addView(suggestionItemView)
            
            // Smooth entrance animation with overshoot interpolator
            suggestionItemView.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setDuration(SLOW_ANIMATION_DURATION)
                .setStartDelay(index * SUGGESTION_ANIMATION_DELAY)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
                
            textView.text = suggestionText.trim()
            // Apply theme-appropriate text color
            textView.setTextColor(suggestionTextColor)
            
            if (isGenerationTask) {
                textView.maxLines = 20
                textView.minLines = 3
            }
            
            suggestionItemView.setOnClickListener {
                // *** THE CRITICAL FIX ***
                // The user's click signifies the end of this specific interaction flow.
                // We must reset the protective flag here so that hideBottomSheet() can execute.
                if (generatingFromFocusedEditor) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Suggestion clicked after focused edit. Resetting flag.")
                    generatingFromFocusedEditor = false
                }
                
                if (sourceNode == null) {
                    Toast.makeText(this, "Error: Lost reference to text field.", Toast.LENGTH_SHORT).show()
                    // Also hide the sheet in case of error
                    hideBottomSheet()
                    return@setOnClickListener
                }
                
                // Add click feedback animation
                suggestionItemView.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        val arguments = Bundle().apply { 
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, suggestionText) 
                        }
                        AccessibilityNodeInfo.obtain(sourceNode).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        
                        // Now this call will succeed because the flag has been reset.
                        hideBottomSheet()
                    }
                    .start()
            }
        }
    }

    private fun findFocusedNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    private fun isKeyboardVisible(): Boolean {
        for (window in windows) { if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) { return true } }
        return false
    }

    private fun checkAndShowBubble() {
        if (!isScreenOn || isPerformingManualTransition || isGenerating || generatingFromFocusedEditor || isTransitioningFromFocusedEditor) {
            if (BuildConfig.DEBUG) Log.d(TAG, "checkAndShowBubble blocked - screenOn: $isScreenOn, transition: $isPerformingManualTransition, generating: $isGenerating, fromFocused: $generatingFromFocusedEditor, transitioning: $isTransitioningFromFocusedEditor")
            return
        }
        
        // Additional check: don't interfere if custom persona dialog is showing
        if (customPersonaDialog?.isShowing == true) {
            if (BuildConfig.DEBUG) Log.d(TAG, "checkAndShowBubble blocked - custom persona dialog is showing")
            return
        }
        
        val activeAppPackage = rootInActiveWindow?.packageName?.toString()
        val isAppAllowed = allowedPackageNames.contains(activeAppPackage)
        val keyboardOpen = isKeyboardVisible()
        
        if (BuildConfig.DEBUG) Log.d(TAG, "checkAndShowBubble - app: $activeAppPackage, allowed: $isAppAllowed, keyboard: $keyboardOpen")

        if (isAppAllowed && keyboardOpen) {
            if (floatingBubbleView == null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Showing floating bubble for app: $activeAppPackage")
                showFloatingBubble()
            }
        } else {
            // This block is triggered when you swipe home or switch apps.
            if (floatingBubbleView != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Hiding floating bubble - app not allowed or keyboard closed")
                hideFloatingBubble()
            }
            // Only hide bottom sheet if we're not in a dialog flow
            if (bottomSheetView != null && customPersonaDialog?.isShowing != true) {
                // MODIFIED: Use the instant hide function for a fast exit.
                hideBottomSheetInstantly()
            }
        }
    }

    private fun showFloatingBubble() {
        try {
            if (floatingBubbleView != null) return
            floatingBubbleView = FloatingBubbleView(this).apply { listener = this@MyAccessibilityService }
            windowManager.addView(floatingBubbleView, floatingBubbleView!!.wmParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating bubble", e)
            floatingBubbleView = null
        }
    }

    private fun hideFloatingBubble() {
        try {
            floatingBubbleView?.let { 
                try {
                    if (it.isAttachedToWindow) {
                        windowManager.removeView(it)
                        Log.d(TAG, "Successfully removed floating bubble view")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing floating bubble view", e)
                }
                Unit // Explicit unit return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding floating bubble", e)
        } finally {
            floatingBubbleView = null
        }
    }

    override fun onBubbleMoved(x: Int, y: Int) {}
    
    /**
     * Applies theme colors to the bottom sheet UI elements
     */
    private fun applyTheme(isDarkMode: Boolean) {
        bottomSheetView?.let { sheet ->
            try {
                // Apply colors based on dark mode setting
                val backgroundColor: Int
                val textColor: Int
                val secondaryTextColor: Int
                val generateButtonBg: Int
                val generateButtonText: Int
                val personaSelectorColor: Int
                val personaOptionsTextColor: Int
                
                if (isDarkMode) {
                    backgroundColor = resources.getColor(android.R.color.background_dark, null)
                    textColor = resources.getColor(android.R.color.white, null)
                    secondaryTextColor = resources.getColor(android.R.color.secondary_text_dark, null)
                    // Force white button in dark mode regardless of theme resources
                    generateButtonBg = 0xFFFFFFFF.toInt()  // White in dark mode
                    generateButtonText = 0xFF000000.toInt() // Black in dark mode
                    personaSelectorColor = 0xFF33A1FF.toInt() // Keep blue accent for persona selector
                    personaOptionsTextColor = 0xFFFFFFFF.toInt() // White text for persona options
                } else {
                    backgroundColor = resources.getColor(android.R.color.background_light, null)
                    textColor = resources.getColor(android.R.color.black, null)
                    secondaryTextColor = resources.getColor(android.R.color.secondary_text_light, null)
                    // Force black button in light mode regardless of theme resources
                    generateButtonBg = 0xFF000000.toInt()  // Black in light mode
                    generateButtonText = 0xFFFFFFFF.toInt() // White in light mode
                    personaSelectorColor = 0xFF33A1FF.toInt() // Keep blue accent for persona selector
                    personaOptionsTextColor = 0xFF000000.toInt() // Black text for persona options
                }
                
                // Apply colors to main UI elements
                sheet.findViewById<LinearLayout>(R.id.bottom_sheet_content)?.setBackgroundColor(backgroundColor)
                sheet.findViewById<EditText>(R.id.edit_text_content)?.apply {
                    setTextColor(textColor)
                    setHintTextColor(secondaryTextColor)
                }
                
                // Apply colors to settings section
                sheet.findViewById<TextView>(R.id.dark_mode_label)?.setTextColor(textColor)
                sheet.findViewById<TextView>(R.id.credits_remaining_text)?.setTextColor(textColor)
                
                // Apply colors to thinking text
                sheet.findViewById<TextView>(R.id.thinking_text_view)?.setTextColor(secondaryTextColor)
                
                // Apply colors to generate button - using direct color values to override any resource-based colors
                sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_rewrite)?.apply {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(generateButtonBg)
                    setTextColor(generateButtonText)
                    iconTint = android.content.res.ColorStateList.valueOf(generateButtonText)
                }
                
                // Debug logging
                // Log.d(TAG, "Set generate button colors - isDarkMode: $isDarkMode, bg: ${Integer.toHexString(generateButtonBg)}, text: ${Integer.toHexString(generateButtonText)}")
                
                
                // Apply colors to persona selector
                sheet.findViewById<TextView>(R.id.persona_selector_text)?.setTextColor(personaSelectorColor)
                sheet.findViewById<ImageView>(R.id.persona_selector_arrow)?.imageTintList = android.content.res.ColorStateList.valueOf(personaSelectorColor)
                
                // Apply colors to persona option buttons
                sheet.findViewById<Button>(R.id.persona_normal)?.setTextColor(personaOptionsTextColor)
                sheet.findViewById<Button>(R.id.persona_shakespeare)?.setTextColor(personaOptionsTextColor)
                sheet.findViewById<Button>(R.id.persona_yoda)?.setTextColor(personaOptionsTextColor)
                // Custom persona button should keep blue color
                sheet.findViewById<Button>(R.id.persona_custom)?.setTextColor(personaSelectorColor)
                
                // Apply colors to tool icon - white in dark mode, dark gray in light mode
                val toolIconColor = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF666666.toInt()
                sheet.findViewById<ImageView>(R.id.ic_grammarly_g)?.imageTintList = android.content.res.ColorStateList.valueOf(toolIconColor)
                
                // Apply colors to suggestion text items
                val suggestionTextColor = if (isDarkMode) {
                    resources.getColor(android.R.color.white, null)
                } else {
                    resources.getColor(android.R.color.black, null)
                }
                
                // Update all existing suggestion text views
                sheet.findViewById<LinearLayout>(R.id.suggestions_container)?.let { container ->
                    for (i in 0 until container.childCount) {
                        val suggestionItem = container.getChildAt(i)
                        val textView = suggestionItem?.findViewById<TextView>(R.id.text_suggestion)
                        textView?.setTextColor(suggestionTextColor)
                    }
                }
                
                // Debug logging
                // Log.d(TAG, "Applied theme - isDarkMode: $isDarkMode, personaSelector: ${Integer.toHexString(personaSelectorColor)}, personaOptions: ${Integer.toHexString(personaOptionsTextColor)}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error applying theme", e)
            }
        }
    }
    
    /**
     * Animates the theme transition with smooth color changes
     */
    private fun animateThemeTransition(toDarkMode: Boolean) {
        bottomSheetView?.let { sheet ->
            // Create a subtle pulse animation to indicate theme change
            sheet.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(150)
                .withEndAction {
                    sheet.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
            
            // Add a gentle fade effect
            sheet.alpha = 0.8f
            sheet.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(SMOOTH_ENTER_INTERPOLATOR)
                .start()
                
            // Special animation for the generate button to emphasize theme change
            val buttonColor: Int
            val textColor: Int
            val personaOptionsColor: Int
            val blueAccent = 0xFF33A1FF.toInt()
            
            if (toDarkMode) {
                buttonColor = 0xFFFFFFFF.toInt()
                textColor = 0xFF000000.toInt()
                personaOptionsColor = 0xFFFFFFFF.toInt()
            } else {
                buttonColor = 0xFF000000.toInt()
                textColor = 0xFFFFFFFF.toInt()
                personaOptionsColor = 0xFF000000.toInt()
            }
            
            sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_rewrite)?.let { button ->
                // First phase - shrink and fade slightly
                button.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .alpha(0.8f)
                    .setDuration(150)
                    .withEndAction {
                        // Apply new colors
                        button.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonColor)
                        button.setTextColor(textColor)
                        button.iconTint = android.content.res.ColorStateList.valueOf(textColor)
                        
                        // Second phase - grow back with new colors
                        button.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(200)
                            .setInterpolator(BOUNCE_INTERPOLATOR)
                            .start()
                    }
                    .start()
            }
            
            // Animate persona selector colors
            sheet.findViewById<TextView>(R.id.persona_selector_text)?.let { personaText ->
                personaText.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .alpha(0.7f)
                    .setDuration(150)
                    .withEndAction {
                        personaText.setTextColor(blueAccent)
                        personaText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(200)
                            .setInterpolator(BOUNCE_INTERPOLATOR)
                            .start()
                    }
                    .start()
            }
            
            // Animate persona options colors
            val personaButtons = listOf(
                sheet.findViewById<Button>(R.id.persona_normal),
                sheet.findViewById<Button>(R.id.persona_shakespeare),
                sheet.findViewById<Button>(R.id.persona_yoda)
            ).filterNotNull()
            
            personaButtons.forEachIndexed { index, button ->
                button.animate()
                    .alpha(0.7f)
                    .setDuration(100)
                    .setStartDelay((index * 50).toLong())
                    .withEndAction {
                        button.setTextColor(personaOptionsColor)
                        button.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
            
            // Animate custom persona button to keep blue
            sheet.findViewById<Button>(R.id.persona_custom)?.let { customButton ->
                customButton.animate()
                    .alpha(0.7f)
                    .setDuration(100)
                    .setStartDelay(150)
                    .withEndAction {
                        customButton.setTextColor(blueAccent)
                        customButton.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }
    }
    
    /**
     * Google Circle to Search Style Gradient Overlay
     * Creates a beautiful gradient that slides up from bottom with flowing colors
     */
    private var gradientOverlayView: GradientOverlayView? = null
    private var gradientAnimator: android.animation.ValueAnimator? = null
    private var flowingGradientAnimator: android.animation.ValueAnimator? = null
    private var isGradientActive = false
    private var gradientHideHandler: Handler? = null
    private var gradientHideRunnable: Runnable? = null
    
    private fun showRGBGradientWave() {
        // Prevent multiple instances
        if (isGradientActive) {
            Log.d(TAG, "Gradient overlay already active, preventing duplicate")
            return
        }
        
        try {
            isGradientActive = true
            
            // Clean up any existing gradient view without affecting active state
            gradientAnimator?.cancel()
            gradientAnimator = null
            flowingGradientAnimator?.cancel()
            flowingGradientAnimator = null
            
            // Remove existing view if present
            gradientOverlayView?.let { existingView ->
                try {
                    windowManager.removeView(existingView)
                } catch (e: Exception) {
                    // View might already be removed, ignore
                }
            }
            
            val themedContext = ContextThemeWrapper(this, R.style.Theme_App_Translucent)
            
            // Create custom gradient view with direct property access
            val customGradientView = GradientOverlayView(themedContext)
            gradientOverlayView = customGradientView
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY 
                else 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            
            windowManager.addView(gradientOverlayView, params)
            
            // Start the Google-style gradient animation
            startGoogleGradientAnimation()
            
            // Auto-hide after 3 seconds with proper cleanup
            gradientHideHandler = Handler(Looper.getMainLooper())
            gradientHideRunnable = Runnable {
                hideRGBGradientWave()
            }
            gradientHideHandler?.postDelayed(gradientHideRunnable!!, 3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing Google gradient overlay", e)
            isGradientActive = false
        }
    }
    
    private fun startGoogleGradientAnimation() {
        gradientOverlayView?.let { view ->
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels.toFloat()
            val screenWidth = displayMetrics.widthPixels.toFloat()
            
            // Ultra-smooth, fast slide-up with buttery easing curves
            gradientAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1400L // Much faster slide-up for instant gratification
                interpolator = android.view.animation.PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f) // Smooth ease-out curve
                
                addUpdateListener { animator ->
                    if (!isGradientActive || gradientOverlayView == null) {
                        cancel()
                        return@addUpdateListener
                    }
                    
                    val progress = animator.animatedValue as Float
                    
                    // Buttery smooth height animation with accelerated start, smooth finish
                    val smoothInterpolator = android.view.animation.PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
                    val overlayHeight = smoothInterpolator.getInterpolation(progress) * screenHeight
                    
                    // Smooth alpha curve - fast fade in, sustained visibility
                    val alpha = when {
                        progress < 0.25f -> (progress * 4.0f).coerceAtMost(0.9f) // Super fast fade in
                        progress > 0.8f -> 0.9f - ((progress - 0.8f) * 2.5f).coerceAtMost(0.4f) // Gentle fade at end
                        else -> 0.9f // Maximum visibility
                    }.coerceIn(0.1f, 0.9f)
                    
                    // Direct method call for smooth property updates
                    gradientOverlayView?.updateGradientProperties(overlayHeight, 0f, alpha)
                }
                
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Start flowing RGB gradient when slide-up completes
                        if (isGradientActive && gradientOverlayView != null) {
                            startFlowingGradientAnimation()
                        }
                    }
                    
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        gradientAnimator = null
                    }
                })
                
                start()
            }
        }
    }
    
    private fun startFlowingGradientAnimation() {
        gradientOverlayView?.let { view ->
            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            val screenHeight = resources.displayMetrics.heightPixels.toFloat()
            
            // Buttery smooth flowing RGB gradient with hypnotic wave patterns
            flowingGradientAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 6000L // Smooth, not too fast, not too slow
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                
                addUpdateListener { animator ->
                    if (!isGradientActive || gradientOverlayView == null) {
                        cancel()
                        return@addUpdateListener
                    }
                    
                    val progress = animator.animatedValue as Float
                    
                    // Silky smooth multi-layered wave motion
                    val primaryWave = (progress * screenWidth * 1.2f) % (screenWidth * 1.5f)
                    val secondaryWave = kotlin.math.sin(progress * kotlin.math.PI * 2.5).toFloat() * screenWidth * 0.12f
                    val tertiaryWave = kotlin.math.cos(progress * kotlin.math.PI * 1.8).toFloat() * screenWidth * 0.06f
                    val flowingOffset = primaryWave + secondaryWave + tertiaryWave
                    
                    // Smooth breathing alpha - more organic feeling
                    val breathingAlpha = 0.8f + (kotlin.math.sin(progress * kotlin.math.PI * 2.8).toFloat() * 0.15f)
                    
                    // Full screen height with buttery smooth flowing offset
                    gradientOverlayView?.updateGradientProperties(screenHeight, flowingOffset, breathingAlpha.coerceIn(0.65f, 0.95f))
                }
                
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        flowingGradientAnimator = null
                    }
                })
                
                start()
            }
        }
    }
    
    private fun hideRGBGradientWave() {
        if (!isGradientActive && gradientOverlayView == null) return
        
        try {
            // Cancel scheduled hide operation
            gradientHideHandler?.removeCallbacks(gradientHideRunnable ?: return)
            gradientHideHandler = null
            gradientHideRunnable = null
            
            // Cancel all animations cleanly
            gradientAnimator?.cancel()
            gradientAnimator = null
            flowingGradientAnimator?.cancel()
            flowingGradientAnimator = null
            
            gradientOverlayView?.let { view ->
                // Professional slide-down exit animation
                view.animate()
                    .alpha(0f)
                    .scaleY(0.95f)
                    .translationY(30f)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f))
                    .withEndAction {
                        try {
                            windowManager.removeView(view)
                        } catch (e: Exception) {
                            Log.w(TAG, "Gradient overlay already removed", e)
                        } finally {
                            gradientOverlayView = null
                            isGradientActive = false
                        }
                    }
                    .start()
            } ?: run {
                // Immediate cleanup if view is null
                isGradientActive = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during gradient cleanup", e)
            isGradientActive = false
            gradientOverlayView = null
        }
    }
    
    /**
     * Reads all visible text content from the current screen
     * This helps users when they don't know what to reply to screen content
     */
    private fun readOnScreenContent(): String {
        val contentBuilder = StringBuilder()
        
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "No root node available for screen reading")
                return ""
            }
            
            // Extract text from all visible text elements
            extractTextFromNode(rootNode, contentBuilder)
            
            val screenContent = contentBuilder.toString().trim()
            
            // Filter and clean the content
            val cleanedContent = cleanScreenContent(screenContent)
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Screen content extracted (${cleanedContent.length} chars): ${cleanedContent.take(100)}...")
            }
            
            return cleanedContent
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading screen content", e)
            return ""
        }
    }
    
    private fun extractTextFromNode(node: AccessibilityNodeInfo?, contentBuilder: StringBuilder) {
        if (node == null) return
        
        try {
            // ULTRA-AGGRESSIVE pre-filtering - check node properties first
            val className = node.className?.toString() ?: ""
            val packageName = node.packageName?.toString() ?: ""
            val isInputField = className.contains("EditText") || 
                             className.contains("TextInputEditText") ||
                             className.contains("AutoCompleteTextView") ||
                             node.isEditable
            
            // Get text from current node if it's visible and has meaningful content
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrBlank() && 
                node.isVisibleToUser && 
                isRelevantContent(nodeText)) {
                
                // TRIPLE-CHECK placeholder filtering for input fields
                if (isInputField) {
                    // First check: Ultra-aggressive WhatsApp filtering
                    val lowerText = nodeText.lowercase().trim()
                    if (lowerText == "message" || lowerText == "type a message" || 
                        lowerText.contains("message") && lowerText.length <= 20) {
                        Log.d(TAG, "🚫 PRE-BLOCKED input field text: '$nodeText' (WhatsApp-style)")
                        return // Don't even process children if this is WhatsApp placeholder
                    }
                    
                    // Second check: Check against hint attribute
                    val hint = node.hintText?.toString()
                    if (!hint.isNullOrBlank() && nodeText.equals(hint, ignoreCase = true)) {
                        Log.d(TAG, "🚫 PRE-BLOCKED hint match: '$nodeText' = hint '$hint'")
                        return
                    }
                    
                    // Third check: Standard placeholder filtering
                    if (isPlaceholderOrHintText(node, nodeText)) {
                        Log.d(TAG, "🚫 PRE-BLOCKED placeholder: '$nodeText'")
                        return
                    }
                } else {
                    // For non-input fields, still check but less aggressively
                    if (isPlaceholderOrHintText(node, nodeText)) {
                        Log.d(TAG, "🚫 PRE-BLOCKED non-input placeholder: '$nodeText'")
                        return
                    }
                }
                
                // Only add if it passes all filters
                if (contentBuilder.isNotEmpty()) {
                    contentBuilder.append(" ")
                }
                contentBuilder.append(nodeText.trim())
            }
            
            // Also check contentDescription for additional context (with filtering)
            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrBlank() && 
                isRelevantContent(contentDesc) &&
                !isPlaceholderOrHintText(node, contentDesc) &&
                !contentBuilder.toString().contains(contentDesc)) {
                
                if (contentBuilder.isNotEmpty()) {
                    contentBuilder.append(" ")
                }
                contentBuilder.append(contentDesc.trim())
            }
            
            // Recursively extract from child nodes
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                extractTextFromNode(child, contentBuilder)
                child?.recycle() // Properly recycle child nodes
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting text from node", e)
        }
    }
    
    private fun isPlaceholderOrHintText(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            val lowerText = text.lowercase().trim()
            
            // NUCLEAR OPTION: Block any "message" text immediately
            if (lowerText == "message" || lowerText == "messages") {
                Log.d(TAG, "🚫 NUCLEAR BLOCK: '$text' (message variant)")
                return true
            }
            
            // ULTRA-AGGRESSIVE WhatsApp-specific blocking
            val packageName = node.packageName?.toString()?.lowercase() ?: ""
            if (packageName.contains("whatsapp") || packageName.contains("telegram") || 
                packageName.contains("messenger") || packageName.contains("signal")) {
                
                // Block ANY text that could be WhatsApp placeholder in messaging apps
                val messagingPlaceholders = listOf(
                    "message", "type a message", "write a message", "enter message",
                    "say something", "reply", "respond", "chat", "text message",
                    "type here", "compose message", "start typing", "add text",
                    "type here to chat", "enter text", "write something", "send message"
                )
                
                if (messagingPlaceholders.any { pattern -> 
                    lowerText == pattern || 
                    (lowerText.contains(pattern) && lowerText.length <= pattern.length + 10) ||
                    (pattern.contains(lowerText) && lowerText.length >= 4)
                }) {
                    Log.d(TAG, "🚫 ULTRA-AGGRESSIVE messaging app block: '$text' in $packageName")
                    return true
                }
            }
            
            // EMERGENCY BLOCK for input fields with "message" anywhere
            val className = node.className?.toString() ?: ""
            val isInputField = className.contains("EditText") || 
                             className.contains("TextInputEditText") ||
                             className.contains("AutoCompleteTextView") ||
                             className.contains("MultiAutoCompleteTextView") ||
                             node.isEditable
                             
            if (isInputField && lowerText.contains("message") && lowerText.length <= 30) {
                Log.d(TAG, "🚫 EMERGENCY input field block: '$text'")
                return true
            }
            
            // Check if the node is an EditText or input field
            if (isInputField) {
                // Check for hint attribute FIRST (most reliable)
                val hint = node.hintText?.toString()
                if (!hint.isNullOrBlank()) {
                    // If there's a hint and the text matches it, it's placeholder text
                    if (text.equals(hint, ignoreCase = true)) {
                        Log.d(TAG, "🚫 BLOCKED hint text: '$text' (hint: '$hint')")
                        return true
                    }
                    
                    // If the text is similar to the hint, block it
                    if (lowerText.contains(hint.lowercase()) || hint.lowercase().contains(lowerText)) {
                        Log.d(TAG, "🚫 BLOCKED similar to hint: '$text' (hint: '$hint')")
                        return true
                    }
                }
            }
            
            // Additional WhatsApp-specific checks (reuse existing packageName variable)
            if (packageName.contains("whatsapp") || packageName.contains("telegram")) {
                val whatsappPlaceholders = listOf(
                    "message", "type a message", "write a message", "enter message",
                    "say something", "reply", "respond", "chat", "text message",
                    "type here", "compose message", "start typing", "add text"
                )
                
                if (whatsappPlaceholders.any { pattern -> 
                    lowerText == pattern || 
                    lowerText.contains(pattern) ||
                    pattern.contains(lowerText)
                }) {
                    Log.d(TAG, "🚫 BLOCKED WhatsApp/Telegram placeholder: '$text'")
                    return true
                }
            }
            
            if (isInputField) {
                // Comprehensive placeholder pattern database
                val placeholderPatterns = listOf(
                    // Messaging apps (expanded)
                    "message", "type a message", "write a message", "enter message",
                    "say something", "what's on your mind", "add a comment",
                    "reply", "respond", "chat", "text message", "send message",
                    "type here to chat", "compose message", "start typing",
                    "write something", "enter text here", "type your message",
                    "send a message", "write here", "compose", "draft",
                    
                    // Social media placeholders (expanded)
                    "what's happening", "share your thoughts", "write something",
                    "add a caption", "describe this", "tell us more",
                    "what do you think", "share an update", "post something",
                    "write a post", "compose", "what's new", "status update",
                    "share", "post", "update status", "add story", "create post",
                    
                    // Search and input placeholders (expanded)
                    "search", "search here", "enter search", "find", "look for",
                    "type here", "enter text", "input text", "write here",
                    "add text", "enter details", "fill in", "complete",
                    "search messages", "search contacts", "find people",
                    "search chats", "find conversations", "type to search",
                    
                    // Email and forms (expanded)
                    "enter email", "email address", "your email", "username",
                    "password", "enter password", "confirm password",
                    "first name", "last name", "full name", "phone number",
                    "mobile number", "address", "city", "state", "zip code",
                    "enter name", "your name", "display name", "nickname",
                    
                    // Generic placeholders and hints (expanded)
                    "placeholder", "hint", "example", "e.g.", "for example",
                    "optional", "not required", "leave blank", "skip",
                    "tap to", "click to", "press to", "select", "choose",
                    "pick", "browse", "upload", "attach", "add file",
                    "tap here", "click here", "press here", "touch here",
                    
                    // App-specific common placeholders (expanded)
                    "send a snap", "add to your story", "compose tweet",
                    "new post", "create post", "write caption", "add location",
                    "tag people", "add hashtag", "mention someone", "share photo",
                    "add photo", "take photo", "record video", "attach file",
                    "add media", "insert", "paste", "copy", "share content",
                    
                    // Input field indicators (expanded)
                    "required", "required field", "mandatory", "must fill",
                    "please enter", "please provide", "please specify",
                    "enter your", "provide your", "type your", "input your",
                    "fill this", "complete this", "add your", "insert your"
                )
                
                // Ultra-comprehensive pattern matching
                for (pattern in placeholderPatterns) {
                    when {
                        // Exact match (case insensitive)
                        lowerText == pattern -> {
                            Log.d(TAG, "🚫 BLOCKED exact placeholder: '$text'")
                            return true
                        }
                        // Text contains pattern
                        lowerText.contains(pattern) && pattern.length >= 4 -> {
                            Log.d(TAG, "🚫 BLOCKED containing placeholder: '$text' (contains '$pattern')")
                            return true
                        }
                        // Pattern contains text (for short hints) - but be more selective
                        pattern.contains(lowerText) && lowerText.length >= 4 && lowerText.length <= 15 -> {
                            Log.d(TAG, "🚫 BLOCKED partial placeholder: '$text' (part of '$pattern')")
                            return true
                        }
                    }
                }
                
                // Enhanced short text detection for placeholders
                if (text.length <= 30) {
                    val shortPlaceholderPrefixes = listOf(
                        "type", "enter", "add", "write", "search", "find",
                        "input", "insert", "choose", "select", "pick",
                        "provide", "specify", "fill", "complete", "compose",
                        "send", "share", "post", "upload", "attach"
                    )
                    
                    if (shortPlaceholderPrefixes.any { prefix -> 
                        lowerText.startsWith(prefix + " ") || 
                        lowerText.startsWith(prefix) && lowerText.length <= prefix.length + 5
                    }) {
                        Log.d(TAG, "🚫 BLOCKED short placeholder by prefix: '$text'")
                        return true
                    }
                    
                    // Check for ellipsis or common placeholder endings
                    if (lowerText.contains("...") || 
                        lowerText.endsWith(" here") ||
                        lowerText.endsWith("here") ||
                        lowerText.endsWith(" now") ||
                        lowerText.endsWith(" text") ||
                        lowerText.endsWith("text")) {
                        Log.d(TAG, "🚫 BLOCKED placeholder by ending: '$text'")
                        return true
                    }
                }
                
                // Check if text looks like a UI instruction
                val instructionWords = listOf("tap", "click", "press", "swipe", "scroll", "drag", "touch")
                if (instructionWords.any { word -> lowerText.contains(word) }) {
                    Log.d(TAG, "🚫 BLOCKED UI instruction: '$text'")
                    return true
                }
                
                // Check for single word placeholders that are too generic
                val words = lowerText.split("\\s+".toRegex())
                if (words.size == 1 && words[0].length <= 20) {
                    val genericSingleWords = listOf(
                        "message", "text", "comment", "reply", "search", 
                        "username", "email", "password", "name", "title",
                        "description", "caption", "note", "memo", "reminder",
                        "status", "post", "update", "share", "send", "chat",
                        "compose", "write", "type", "enter", "add", "insert"
                    )
                    if (genericSingleWords.contains(words[0])) {
                        Log.d(TAG, "🚫 BLOCKED generic single word placeholder: '$text'")
                        return true
                    }
                }
            }
            
            // Check for very common placeholder words regardless of input field type
            val alwaysBlockWords = listOf("message", "placeholder", "hint", "example")
            if (alwaysBlockWords.any { word -> lowerText == word }) {
                Log.d(TAG, "🚫 BLOCKED always-block word: '$text'")
                return true
            }
            
            return false
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking placeholder text", e)
            return false
        }
    }
    
    private fun isRelevantContent(text: String): Boolean {
        if (text.length < 2) return false
        
        // Filter out common UI elements, system text, and irrelevant content
        val irrelevantPatterns = listOf(
            "button", "tab", "menu", "navigation", "toolbar", "status bar",
            "loading", "progress", "spinner", "icon", "image", "ad",
            "advertisement", "sponsored", "cookie", "privacy policy",
            "terms of service", "settings", "more options", "share",
            "like", "comment", "follow", "subscribe", "notification",
            "back", "forward", "refresh", "search", "close", "minimize",
            "maximize", "home", "profile", "account", "login", "logout"
        )
        
        val lowerText = text.lowercase()
        
        // Skip if it contains mostly irrelevant UI terms
        if (irrelevantPatterns.any { pattern -> lowerText.contains(pattern) }) {
            return false
        }
        
        // Skip if it's mostly numbers, symbols, or very short
        if (text.length < 10 && !text.any { it.isLetter() }) {
            return false
        }
        
        // Skip single words that are likely UI elements
        if (text.trim().split("\\s+".toRegex()).size == 1 && text.length < 15) {
            return false
        }
        
        return true
    }
    
    private fun cleanScreenContent(content: String): String {
        if (content.isBlank()) return ""
        
        // FINAL NUCLEAR FILTER: Remove any remaining placeholder text
        var cleaned = content.replace("\\s+".toRegex(), " ").trim()
        
        // Nuclear placeholder removal - split by spaces and filter each word
        val words = cleaned.split(" ")
        val filteredWords = words.filter { word ->
            val lowerWord = word.lowercase().trim()
            // Block any word that's exactly "message" or common placeholder patterns
            when {
                lowerWord == "message" -> {
                    Log.d(TAG, "🚫 FINAL FILTER blocked word: '$word'")
                    false
                }
                lowerWord == "messages" -> {
                    Log.d(TAG, "🚫 FINAL FILTER blocked word: '$word'")
                    false
                }
                lowerWord.startsWith("type") && lowerWord.length <= 8 -> {
                    Log.d(TAG, "🚫 FINAL FILTER blocked type word: '$word'")
                    false
                }
                lowerWord.startsWith("enter") && lowerWord.length <= 8 -> {
                    Log.d(TAG, "🚫 FINAL FILTER blocked enter word: '$word'")
                    false
                }
                else -> true
            }
        }
        
        cleaned = filteredWords.joinToString(" ").trim()
        
        // Remove duplicate sentences (common in UI with repeated elements)
        val sentences = cleaned.split(". ")
        val uniqueSentences = sentences.distinct()
        cleaned = uniqueSentences.joinToString(". ")
        
        // Final sanity check - if the entire content is just placeholder-like, return empty
        val finalCheck = cleaned.lowercase().trim()
        if (finalCheck == "message" || finalCheck == "type a message" || 
            finalCheck == "enter message" || finalCheck.length <= 15 && 
            (finalCheck.contains("message") || finalCheck.contains("type") || finalCheck.contains("enter"))) {
            Log.d(TAG, "🚫 FINAL SANITY CHECK blocked entire content: '$cleaned'")
            return ""
        }
        
        // Limit content length to avoid overwhelming the AI
        if (cleaned.length > 1500) {
            cleaned = cleaned.take(1500) + "..."
        }
        
        // Only add context if we have actual content
        if (cleaned.isBlank()) {
            Log.d(TAG, "⚠️ No meaningful content found after filtering")
            return ""
        }
        
        // Add context for the AI about what this content represents
        val contextualPrompt = """
            Screen content detected: "$cleaned"
            
            Please help me respond appropriately to this content. Generate a suitable reply based on the context and selected persona style.
        """.trimIndent()
        
        return contextualPrompt
    }

    override fun onInterrupt() {
        serviceJob.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicCheck()
        
        // Dismiss custom persona dialog if it's showing
        customPersonaDialog?.dismiss()
        customPersonaDialog = null
        
        // Clean up gradient wave
        hideRGBGradientWave()
        
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
        }
        try {
            unregisterReceiver(userStatusReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
        }
        hideFloatingBubble()
        cleanupBottomSheetView()
        serviceJob.cancel()
    }
}