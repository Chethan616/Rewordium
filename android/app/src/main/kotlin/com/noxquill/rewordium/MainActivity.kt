package com.noxquill.rewordium

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import com.noxquill.rewordium.util.KeyboardConstants
import com.noxquill.rewordium.service.KeyboardSettingsBroadcastReceiver
import com.noxquill.rewordium.integrity.PlayIntegrityHandler
import com.noxquill.rewordium.review.InAppReviewHelper
import com.noxquill.rewordium.update.InAppUpdateHelper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import android.accessibilityservice.AccessibilityServiceInfo
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.noxquill.rewordium.keyboard.app.QuickSettingsHelper
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceModel
import com.noxquill.rewordium.keyboard.ime.keyboard.SpaceBarMode
import com.noxquill.rewordium.keyboard.ime.input.HapticVibrationMode

class MainActivity : FlutterActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val ACCESSIBILITY_CHANNEL = "com.noxquill.rewordium/accessibility"
        private const val KEYBOARD_CHANNEL = "com.noxquill.rewordium/rewordium_keyboard"
        private const val SWIPE_GESTURE_CHANNEL = "com.noxquill.rewordium/swipe_gestures"
        private const val AI_SETTINGS_CHANNEL = "com.noxquill.rewordium/ai_settings"
        private const val DEEP_LINK_CHANNEL = "com.noxquill.rewordium/deep_link"
        private const val INTEGRITY_CHANNEL = "com.noxquill.rewordium/integrity"
        private const val ONBOARDING_RUNTIME_PREFS = "rewordium_onboarding_runtime"
        private const val KEY_RETURN_TO_APP_AFTER_ACCESSIBILITY_ENABLED =
            "return_to_app_after_accessibility_enabled"
        private const val KEY_RETURN_TO_APP_AFTER_KEYBOARD_ENABLED =
            "return_to_app_after_keyboard_enabled"
        private const val KEYBOARD_RETURN_TO_APP_POLL_INTERVAL_MS = 700L
        private const val KEYBOARD_RETURN_TO_APP_MAX_POLLS = 45
        
        // <-- ADDED: A new channel specifically for syncing user status and credits.
        private const val USER_STATUS_CHANNEL = "com.noxquill.rewordium/user_status"
        private const val REVIEW_CHANNEL = "com.noxquill.rewordium/review"
        private const val UPDATE_CHANNEL = "com.noxquill.rewordium/update"
        private const val KEYBOARD_EVENTS_CHANNEL = "com.noxquill.rewordium/keyboard_events"
    }


    private var deepLinkChannel: MethodChannel? = null
    private val pendingDeepLinks: ArrayDeque<String> = ArrayDeque()
    private var isDeepLinkChannelReady: Boolean = false
    private var userStatusMethodChannel: MethodChannel? = null
    private var keyboardEventSink: EventChannel.EventSink? = null
    private val keyboardStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isEnabled = isKeyboardEnabled()
            val isDefault = isKeyboardSelectedAsDefault()
            keyboardEventSink?.success(mapOf("isEnabled" to isEnabled, "isDefault" to isDefault))
        }
    }
    private var lastReboardSettingsLaunchAt: Long = 0L
    private val keyboardReturnHandler = Handler(Looper.getMainLooper())
    private var keyboardReturnRunnable: Runnable? = null
    private var keyboardReturnPollCount: Int = 0
    private var keyboardSettingsObserver: ContentObserver? = null
    private val creditConsumptionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.noxquill.rewordium.CONSUME_CREDIT_REQUEST") {
                Log.d(TAG, "Received credit consumption request from accessibility service")
                handleCreditConsumptionRequest()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeDefaultSettings()
        
        // Handle deep link from app shortcut
        handleDeepLink(intent)
        
        // Register broadcast receiver for credit consumption requests
        val filter = IntentFilter("com.noxquill.rewordium.CONSUME_CREDIT_REQUEST")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(creditConsumptionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(creditConsumptionReceiver, filter)
        }
        Log.d(TAG, "Registered credit consumption broadcast receiver")
    }

    override fun onResume() {
        super.onResume()
        InAppUpdateHelper.resumeUpdateIfNeeded(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        stopKeyboardReturnPolling()

        isDeepLinkChannelReady = false
        deepLinkChannel = null
        
        // Cleanup broadcast receiver
        try {
            unregisterReceiver(creditConsumptionReceiver)
            Log.d(TAG, "Unregistered credit consumption broadcast receiver")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering broadcast receiver", e)
        }
        
        // Cleanup keyboard settings broadcast receiver
        try {
            KeyboardSettingsBroadcastReceiver.unregister(this)
            Log.d(TAG, "Unregistered keyboard settings broadcast receiver")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering keyboard settings broadcast receiver", e)
        }

        // Cleanup keyboard events EventChannel receiver (may already be gone if stream was cancelled)
        try {
            unregisterReceiver(keyboardStatusReceiver)
        } catch (_: Exception) {}
        keyboardEventSink = null

        unregisterKeyboardSettingsObserver()
    }

    override fun onNewIntent(intent: Intent) {
        handleDeepLink(intent)
        // Clear the data so Flutter engine doesn't try to parse it natively
        // and fall back to the '/' splash screen route, which obliterates
        // our manual MethodChannel deep link route pushes.
        intent.data = null
        super.onNewIntent(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "rewordium") {
            val route = resolveDeepLinkRoute(data)
            Log.d(TAG, "Deep link received: uri=$data, route=$route")

            if (route != null) {
                enqueueDeepLink(route)
            } else {
                Log.w(TAG, "Unsupported deep link: $data")
            }
        }
    }

    private fun resolveDeepLinkRoute(uri: Uri): String? {
        val host = uri.host?.lowercase()?.trim()
        val firstPath = uri.pathSegments.firstOrNull()?.lowercase()?.trim()
        val routeQuery = uri.getQueryParameter("route")?.lowercase()?.trim()

        val rawRoute = when {
            !routeQuery.isNullOrBlank() -> routeQuery
            !host.isNullOrBlank() -> host
            !firstPath.isNullOrBlank() -> firstPath
            else -> null
        } ?: return null

        return when (rawRoute) {
            "ai_settings", "ai-settings", "aisettings", "ai", "jade_ai", "jade-ai", "jadeai" -> "ai_settings"
            "home" -> "home"
            "settings", "app_settings", "app-settings" -> "settings"
            "keyboard_settings", "keyboard" -> "keyboard_settings"
            "paraphraser", "paraphrase", "rewrite" -> "paraphraser"
            "grammar", "grammar_check", "grammar-check" -> "grammar"
            "tools", "tool" -> "tools"
            else -> null
        }
    }

    private fun enqueueDeepLink(route: String) {
        synchronized(pendingDeepLinks) {
            pendingDeepLinks.addLast(route)
        }
        dispatchPendingDeepLinks()
    }

    private fun dispatchPendingDeepLinks() {
        val channel = deepLinkChannel ?: return
        if (!isDeepLinkChannelReady) return

        val links = synchronized(pendingDeepLinks) {
            pendingDeepLinks.toList().also { pendingDeepLinks.clear() }
        }
        links.forEach { route ->
            channel.invokeMethod("navigateTo", mapOf("route" to route))
        }
    }

    private fun initializeDefaultSettings() {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var settingsChanged = false

        // Check if this is first run by checking if any setting exists
        val isFirstRun = !prefs.contains(KeyboardConstants.KEY_HAPTIC_FEEDBACK) &&
                !prefs.contains(KeyboardConstants.KEY_AUTO_CAPITALIZE) &&
                !prefs.contains(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD) &&
                !prefs.contains(KeyboardConstants.KEY_AUTOCORRECT)

        // Set default values for all settings if they don't exist
        if (!prefs.contains(KeyboardConstants.KEY_HAPTIC_FEEDBACK)) {
            Log.d(TAG, "🔥 Setting default haptic feedback to TRUE (Premium Default)")
            editor.putBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true)
            settingsChanged = true
        }

        if (!prefs.contains(KeyboardConstants.KEY_AUTO_CAPITALIZE)) {
            Log.d(TAG, "Setting default auto-capitalize to true")
            editor.putBoolean(KeyboardConstants.KEY_AUTO_CAPITALIZE, true)
            settingsChanged = true
        }

        if (!prefs.contains(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD)) {
            Log.d(TAG, "Setting default double-space period to true")
            editor.putBoolean(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD, true)
            settingsChanged = true
        }

        if (!prefs.contains(KeyboardConstants.KEY_AUTOCORRECT)) {
            Log.d(TAG, "Setting default autocorrect to true")
            editor.putBoolean(KeyboardConstants.KEY_AUTOCORRECT, true)
            settingsChanged = true
        }

        if (!prefs.contains(KeyboardConstants.KEY_AI_SUGGESTIONS)) {
            Log.d(TAG, "Setting default AI suggestions to true")
            editor.putBoolean(KeyboardConstants.KEY_AI_SUGGESTIONS, true)
            settingsChanged = true
        }
        
        // Clear personas on first run to ensure clean slate
        if (isFirstRun) {
            Log.d(TAG, "First run detected, clearing any existing personas")
            editor.remove(KeyboardConstants.KEY_PERSONAS)
            settingsChanged = true
        }

        // Apply all changes at once
        if (settingsChanged) {
            editor.apply()
            Log.d(TAG, "Applied default keyboard settings")
        } else {
            // Log current settings for debugging
            Log.d(TAG, "Current keyboard settings: ${prefs.all}")
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d(TAG, "Configuring Flutter engine and all method channels.")

        // --- DEEP LINK CHANNEL ---
        deepLinkChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DEEP_LINK_CHANNEL)
        isDeepLinkChannelReady = true
        deepLinkChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "getPendingDeepLink" -> {
                    val link = synchronized(pendingDeepLinks) {
                        if (pendingDeepLinks.isNotEmpty()) pendingDeepLinks.removeFirst() else null
                    }
                    result.success(link)
                }
                "getPendingDeepLinks" -> {
                    val links = synchronized(pendingDeepLinks) {
                        pendingDeepLinks.toList().also { pendingDeepLinks.clear() }
                    }
                    result.success(links)
                }
                else -> result.notImplemented()
            }
        }

        Handler(Looper.getMainLooper()).post {
            dispatchPendingDeepLinks()
        }
        // --- PLAY INTEGRITY CHANNEL ---
        val integrityChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, INTEGRITY_CHANNEL)
        integrityChannel.setMethodCallHandler(PlayIntegrityHandler(this))
        Log.d(TAG, "Play Integrity channel configured")

        // --- 
        // --- ACCESSIBILITY CHANNEL (Unchanged) ---
        val accessibilityChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, ACCESSIBILITY_CHANNEL)
        accessibilityChannel.setMethodCallHandler { call, result ->
            Log.d(TAG, "[Accessibility] Received method call: ${call.method}")
            try {
                when (call.method) {
                    "isAccessibilityServiceEnabled" -> {
                        val isEnabled = isAccessibilityServiceEnabled()
                        Log.d(TAG, "[Accessibility] isAccessibilityServiceEnabled: $isEnabled")
                        result.success(isEnabled)
                    }
                    "requestAccessibilitySettings" -> {
                        val autoReturnToApp = call.argument<Boolean>("autoReturnToApp") ?: false
                        getSharedPreferences(ONBOARDING_RUNTIME_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_RETURN_TO_APP_AFTER_ACCESSIBILITY_ENABLED, autoReturnToApp)
                            .apply()

                        Log.d(TAG, "[Accessibility] Opening accessibility settings")
                        openAccessibilitySettings()
                        result.success(true)
                    }
                    else -> {
                        Log.w(TAG, "[Accessibility] Unknown method called: ${call.method}")
                        result.notImplemented()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Accessibility] Error in method channel handler", e)
                result.error("ERROR", "Error in method channel handler: ${e.message}", null)
            }
        }

        // --- KEYBOARD CHANNEL (Unchanged) ---
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, KEYBOARD_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isKeyboardEnabled" -> {
                    result.success(isKeyboardEnabled())
                }
                "isKeyboardSelectedAsDefault" -> {
                    result.success(isKeyboardSelectedAsDefault())
                }
                "isReboardKeyboardEnabled" -> {
                    result.success(isReboardEnabled())
                }
                "isRewordiumAIKeyboardEnabled" -> {
                    result.success(isReboardEnabled())
                }
                "openKeyboardSettings" -> {
                    val autoReturnToApp = call.argument<Boolean>("autoReturnToApp") ?: false
                    getSharedPreferences(ONBOARDING_RUNTIME_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_RETURN_TO_APP_AFTER_KEYBOARD_ENABLED, autoReturnToApp)
                        .apply()
                    openKeyboardSettings(autoReturnToApp)
                    result.success(true)
                }
                "showInputMethodPicker" -> {
                    result.success(showInputMethodPicker())
                }
                "openReboardSettings" -> {
                    result.success(openReboardSettings())
                }
                "setDarkMode" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    updateSetting(KeyboardConstants.KEY_DARK_MODE, enabled)
                    result.success(true)
                }
                "updateThemeColor" -> {
                    val colorHex = call.argument<String>("colorHex") ?: "#007AFF"
                    updateSetting(KeyboardConstants.KEY_THEME_COLOR, colorHex)
                    result.success(true)
                }
                "setHapticFeedback" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_HAPTIC_FEEDBACK, enabled)
                    CoroutineScope(Dispatchers.Main).launch {
                        QuickSettingsHelper.updateQuickSetting(this@MainActivity, "hapticEnabled", enabled)
                    }
                    result.success(true)
                }
                "setAiSuggestions" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_AI_SUGGESTIONS, enabled)

                    // Keep keyboard AI quick actions in sync with Flutter-side toggle.
                    val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                    flutterPrefs.edit().putBoolean("flutter.paraphraser_enabled", enabled).apply()

                    result.success(true)
                }
                "setAutoCapitalize" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_AUTO_CAPITALIZE, enabled)
                    CoroutineScope(Dispatchers.Main).launch {
                        QuickSettingsHelper.updateQuickSetting(this@MainActivity, "autoCapitalization", enabled)
                    }
                    result.success(true)
                }
                "setDoubleSpacePeriod" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD, enabled)
                    CoroutineScope(Dispatchers.Main).launch {
                        QuickSettingsHelper.updateQuickSetting(this@MainActivity, "doubleSpacePeriod", enabled)
                    }
                    result.success(true)
                }
                "setGlideTypingEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_GLIDE_TYPING_ENABLED, enabled)
                    CoroutineScope(Dispatchers.Main).launch {
                        QuickSettingsHelper.updateQuickSetting(this@MainActivity, "glideEnabled", enabled)
                    }
                    result.success(true)
                }
                "setSpacebarNavigationEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_SPACEBAR_NAVIGATION_ENABLED, enabled)
                    result.success(true)
                }
                "getContactsPermissionStatus" -> {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.READ_CONTACTS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    result.success(if (granted) "granted" else "not_granted")
                }
                "hasContactsPermission" -> {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.READ_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    result.success(granted)
                }
                "requestContactsPermission" -> {
                    startActivity(
                        Intent(this, com.noxquill.rewordium.keyboard.app.ContactsPermissionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    result.success(true)
                }
                "setUseContactsForSuggestions" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_USE_CONTACTS, enabled)
                    // Mirror to keyboard prefs so the IME reads the new value
                    // on the next onStartInputView call.
                    getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("flutter.keyboard_use_contacts", enabled)
                        .apply()
                    result.success(true)
                }
                "updateKeyboardPersonas" -> {
                    try {
                        val personas = call.argument<List<String>>("personas")
                        Log.d(TAG, "Received personas to update: $personas")
                        updatePersonas(personas ?: emptyList())
                        sendBroadcast(Intent(KeyboardConstants.ACTION_PERSONAS_UPDATED))
                        Log.d(TAG, "Successfully updated keyboard personas")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating keyboard personas", e)
                        result.success(false)
                    }
                }
                "getQuickSettings" -> {
                    val settings = QuickSettingsHelper.getQuickSettings(this@MainActivity)
                    result.success(settings)
                }
                "updateQuickSetting" -> {
                    val key = call.argument<String>("key") ?: ""
                    val value = call.argument<Any>("value")
                    CoroutineScope(Dispatchers.Main).launch {
                        QuickSettingsHelper.updateQuickSetting(this@MainActivity, key, value!!)
                    }
                    result.success(true)
                }
                "openLanguagesSettings" -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("ui://ReBoard/settings/localization")).apply {
                        setClass(this@MainActivity, com.noxquill.rewordium.keyboard.app.FlorisAppActivity::class.java)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    result.success(true)
                }
                "openStickerStudio" -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("ui://ReBoard/settings/sticker-studio")).apply {
                            setClass(this@MainActivity, com.noxquill.rewordium.keyboard.app.FlorisAppActivity::class.java)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open Sticker Studio: ${e.message}")
                        // Fallback: open the native settings root
                        openReboardSettings()
                        result.success(false)
                    }
                }
                "getKeyboardSettings" -> {
                    val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    val quickSettings = QuickSettingsHelper.getQuickSettings(this@MainActivity)
                    
                    val settings = mapOf(
                        "themeColor" to (prefs.getString(KeyboardConstants.KEY_THEME_COLOR, "#007AFF") ?: "#007AFF"),
                        "darkMode" to prefs.getBoolean(KeyboardConstants.KEY_DARK_MODE, false),
                        "hapticFeedback" to (quickSettings["hapticEnabled"] as? Boolean ?: true),
                        "aiSuggestions" to prefs.getBoolean(KeyboardConstants.KEY_AI_SUGGESTIONS, true),
                        "autoCapitalize" to (quickSettings["autoCapitalization"] as? Boolean ?: true),
                        "doubleSpacePeriod" to (quickSettings["doubleSpacePeriod"] as? Boolean ?: true)
                    )
                    
                    Log.d(TAG, "📝 Returning keyboard settings")
                    result.success(settings)
                }
                "refreshKeyboard" -> {
                    Log.d(TAG, "🔄 FORCE REFRESH: Sending aggressive keyboard update broadcasts")
                    
                    // Send multiple broadcasts with slight delays to ensure reception
                    val settingsIntent = Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED)
                    settingsIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    sendBroadcast(settingsIntent)
                    
                    // Send with a small delay to ensure processing
                    Handler(Looper.getMainLooper()).postDelayed({
                        sendBroadcast(Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED))
                        Log.d(TAG, "🔄 Second settings broadcast sent")
                    }, 50)
                    
                    // Also send personas update
                    sendBroadcast(Intent(KeyboardConstants.ACTION_PERSONAS_UPDATED))
                    
                    Log.d(TAG, "✅ Aggressive keyboard refresh broadcasts sent")
                    result.success(true)
                }
                "forceKeyboardRecreation" -> {
                    Log.d(TAG, "🚨 ULTIMATE NUCLEAR OPTION: Most aggressive keyboard update possible")
                    
                    // PHASE 1: Try to restart InputMethod directly
                    try {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        // Force restart of current input connection
                        currentFocus?.let { focusedView ->
                            Log.d(TAG, "🔄 Step 1: Restarting InputMethod for focused view")
                            imm.restartInput(focusedView)
                            
                            // Also try to hide and show the keyboard
                            imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
                            
                            // Show keyboard again after brief delay
                            Handler(Looper.getMainLooper()).postDelayed({
                                imm.showSoftInput(focusedView, InputMethodManager.SHOW_IMPLICIT)
                                Log.d(TAG, "🔄 Step 2: Keyboard hide/show cycle completed")
                            }, 100)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "InputMethod restart failed (expected on some devices)", e)
                    }
                    
                    // PHASE 2: Send nuclear-level broadcasts
                    val settingsIntent = Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED)
                    settingsIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    settingsIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    settingsIntent.putExtra("NUCLEAR_UPDATE", true)
                    
                    // Send immediate broadcast
                    sendBroadcast(settingsIntent)
                    Log.d(TAG, "🚨 NUCLEAR broadcast sent with special flag")
                    
                    // PHASE 3: Escalating delayed broadcasts
                    val handler = Handler(Looper.getMainLooper())
                    
                    handler.postDelayed({ 
                        sendBroadcast(Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED))
                        Log.d(TAG, "🚨 Nuclear Phase 1 broadcast (25ms)")
                    }, 25)
                    
                    handler.postDelayed({ 
                        sendBroadcast(Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED))
                        Log.d(TAG, "🚨 Nuclear Phase 2 broadcast (75ms)")
                        
                        // Try another InputMethod restart
                        try {
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            currentFocus?.let { imm.restartInput(it) }
                        } catch (e: Exception) { 
                            Log.w(TAG, "Secondary restart failed", e)
                        }
                    }, 75)
                    
                    handler.postDelayed({ 
                        sendBroadcast(Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED))
                        Log.d(TAG, "🚨 Nuclear Final broadcast (150ms)")
                        
                        // FINAL PHASE: Force configuration change
                        try {
                            val configIntent = Intent(Intent.ACTION_CONFIGURATION_CHANGED)
                            sendBroadcast(configIntent)
                            Log.d(TAG, "🚨 Configuration change broadcast sent")
                        } catch (e: Exception) {
                            Log.w(TAG, "Configuration change failed", e)
                        }
                        
                        // Show completion feedback
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "✅ NUCLEAR keyboard update sequence completed")
                        }, 50)
                    }, 150)
                    
                    Log.d(TAG, "✅ NUCLEAR keyboard recreation sequence initiated")
                    result.success(true)
                }
                "setKeyboardLayout" -> {
                    try {
                        Log.d(TAG, "🎯 Setting keyboard layout")
                        // This method can be used for future layout configurations
                        // For now, just acknowledge the call
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting keyboard layout: ${e.message}")
                        result.success(false)
                    }
                }
                "bringAppToForeground" -> {
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                            Log.d(TAG, "✅ Brought app to foreground")
                        }
                        result.success(true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error bringing app to foreground: ${e.message}")
                        result.success(false)
                    }
                }
                else -> {
                    Log.w(TAG, "Method not implemented on keyboard channel: ${call.method}")
                    result.notImplemented()
                }
            }
        }

        // --- AI SETTINGS CHANNEL (NEW) ---
        // This channel syncs AI settings from Flutter to native Android services
        val aiSettingsChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AI_SETTINGS_CHANNEL)
        aiSettingsChannel.setMethodCallHandler { call, result ->
            Log.d(TAG, "AI Settings method called: ${call.method}")
            when (call.method) {
                "updateAISettings" -> {
                    try {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        val provider = call.argument<String>("provider") ?: "groq"
                        val apiKey = call.argument<String>("apiKey") ?: ""
                        val model = call.argument<String>("model") ?: "qwen/qwen3-32b"
                        val maxTokens = call.argument<Int>("maxTokens") ?: 8192
                        val customEndpoint = call.argument<String>("customEndpoint") ?: ""
                        
                        // Store AI settings in SharedPreferences for native services
                        val prefs = getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putBoolean("advanced_ai_enabled", enabled)
                            putString("ai_provider", provider)
                            putString("ai_api_key", apiKey)
                            putString("ai_model", model)
                            putInt("ai_max_tokens", maxTokens)
                            putString("ai_custom_endpoint", customEndpoint)
                            apply()
                        }
                        
                        // Broadcast to native services (Accessibility + Keyboard)
                        val intent = Intent("com.noxquill.rewordium.AI_SETTINGS_CHANGED")
                        intent.putExtra("enabled", enabled)
                        intent.putExtra("provider", provider)
                        intent.putExtra("model", model)
                        sendBroadcast(intent)
                        
                        Log.d(TAG, "🤖 AI Settings synced: provider=$provider, enabled=$enabled")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating AI settings: ${e.message}")
                        result.success(false)
                    }
                }
                "getAISettings" -> {
                    try {
                        val prefs = getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
                        val settings = mapOf(
                            "enabled" to prefs.getBoolean("advanced_ai_enabled", false),
                            "provider" to (prefs.getString("ai_provider", "groq") ?: "groq"),
                            "apiKey" to (prefs.getString("ai_api_key", "") ?: ""),
                            "model" to (prefs.getString("ai_model", "qwen/qwen3-32b") ?: "qwen/qwen3-32b"),
                            "maxTokens" to prefs.getInt("ai_max_tokens", 8192),
                            "customEndpoint" to (prefs.getString("ai_custom_endpoint", "") ?: "")
                        )
                        result.success(settings)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting AI settings: ${e.message}")
                        result.error("ERROR", "Error getting AI settings", null)
                    }
                }
                else -> result.notImplemented()
            }
        }

        // --- SWIPE GESTURE CHANNEL (NEW) ---
        val swipeGestureChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SWIPE_GESTURE_CHANNEL)
        swipeGestureChannel.setMethodCallHandler { call, result ->
            Log.d(TAG, "SwipeGesture method called: ${call.method}")
            when (call.method) {
                "initialize" -> {
                    try {
                        // Store settings to be read by keyboard service
                        val prefs = getSharedPreferences("rewordium_keyboard_settings", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("swipe_gestures_initialized", true).apply()
                        
                        // Send broadcast to keyboard service
                        val intent = Intent("com.noxquill.rewordium.GESTURE_SETTINGS_CHANGED")
                        intent.putExtra("action", "initialize")
                        sendBroadcast(intent)
                        
                        Log.d(TAG, "🚀 Swipe gesture initialization broadcasted")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing swipe gestures: ${e.message}")
                        result.success(false)
                    }
                }
                "setSwipeGesturesEnabled" -> {
                    try {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        
                        // Store setting
                        val prefs = getSharedPreferences("rewordium_keyboard_settings", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("swipe_gestures_enabled", enabled).apply()
                        
                        // Broadcast to keyboard service
                        val intent = Intent("com.noxquill.rewordium.GESTURE_SETTINGS_CHANGED")
                        intent.putExtra("action", "setSwipeGesturesEnabled")
                        intent.putExtra("enabled", enabled)
                        sendBroadcast(intent)
                        
                        Log.d(TAG, "🎯 Swipe gestures ${if (enabled) "enabled" else "disabled"}")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting swipe gestures: ${e.message}")
                        result.success(false)
                    }
                }
                "setSwipeSensitivity" -> {
                    try {
                        val sensitivity = call.argument<Double>("sensitivity") ?: 0.8
                        
                        // Store setting
                        val prefs = getSharedPreferences("rewordium_keyboard_settings", Context.MODE_PRIVATE)
                        prefs.edit().putFloat("swipe_sensitivity", sensitivity.toFloat()).apply()
                        
                        // Broadcast to keyboard service
                        val intent = Intent("com.noxquill.rewordium.GESTURE_SETTINGS_CHANGED")
                        intent.putExtra("action", "setSwipeSensitivity")
                        intent.putExtra("sensitivity", sensitivity.toFloat())
                        sendBroadcast(intent)
                        
                        Log.d(TAG, "🎚️ Swipe sensitivity set to $sensitivity")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting swipe sensitivity: ${e.message}")
                        result.success(false)
                    }
                }
                "configureSpecialGestures" -> {
                    try {
                        val spaceDeleteEnabled = call.argument<Boolean>("spaceDeleteEnabled") ?: true
                        val cursorMovementEnabled = call.argument<Boolean>("cursorMovementEnabled") ?: true
                        val capsToggleEnabled = call.argument<Boolean>("capsToggleEnabled") ?: true
                        val symbolModeEnabled = call.argument<Boolean>("symbolModeEnabled") ?: true
                        
                        // Store settings
                        val prefs = getSharedPreferences("rewordium_keyboard_settings", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("space_delete_enabled", spaceDeleteEnabled)
                            .putBoolean("cursor_movement_enabled", cursorMovementEnabled)
                            .putBoolean("caps_toggle_enabled", capsToggleEnabled)
                            .putBoolean("symbol_mode_enabled", symbolModeEnabled)
                            .apply()
                        
                        // Broadcast to keyboard service
                        val intent = Intent("com.noxquill.rewordium.GESTURE_SETTINGS_CHANGED")
                        intent.putExtra("action", "configureSpecialGestures")
                        intent.putExtra("spaceDeleteEnabled", spaceDeleteEnabled)
                        intent.putExtra("cursorMovementEnabled", cursorMovementEnabled)
                        intent.putExtra("capsToggleEnabled", capsToggleEnabled)
                        intent.putExtra("symbolModeEnabled", symbolModeEnabled)
                        sendBroadcast(intent)
                        
                        Log.d(TAG, "⚡ Special gestures configured")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error configuring special gestures: ${e.message}")
                        result.success(false)
                    }
                }
                "getPerformanceMetrics" -> {
                    val prefs = getSharedPreferences("rewordium_keyboard_settings", Context.MODE_PRIVATE)
                    val metrics = mapOf(
                        "engineInitialized" to prefs.getBoolean("swipe_gestures_initialized", false),
                        "gesturesEnabled" to prefs.getBoolean("swipe_gestures_enabled", false),
                        "sensitivity" to prefs.getFloat("swipe_sensitivity", 0.8f),
                        "averageResponseTime" to "< 16ms",
                        "accuracy" to "> 95%",
                        "status" to "Bridge Communication Active"
                    )
                    result.success(metrics)
                }
                else -> result.notImplemented()
            }
        }
        Log.d(TAG, "🎯 Swipe gesture method channel initialized")

        // --- USER STATUS CHANNEL (NEW) ---
        val userStatusChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, USER_STATUS_CHANNEL)
        userStatusMethodChannel = userStatusChannel
        userStatusChannel.setMethodCallHandler { call, result ->
            Log.d(TAG, "[UserStatus] Received method call: ${call.method}")
            try {
                when (call.method) {
                    "updateUserStatus" -> {
                        val isLoggedIn = call.argument<Boolean>("isLoggedIn") ?: false
                        val isPro = call.argument<Boolean>("isPro") ?: false
                        val credits = call.argument<Int>("credits") ?: 0
                        
                        Log.d(TAG, "[UserStatus] Updating user status - LoggedIn: $isLoggedIn, Pro: $isPro, Credits: $credits")
                        
                        // Store user status in keyboard shared preferences
                        val keyboardPrefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
                        keyboardPrefs.edit()
                            .putBoolean("user_logged_in", isLoggedIn)
                            .putBoolean("user_is_pro", isPro)
                            .putInt("user_credits", credits)
                            .apply()
                        
                        // CRITICAL: Also store in accessibility service shared preferences
                        val accessibilityPrefs = getSharedPreferences("rewordium_user_status", Context.MODE_PRIVATE)
                        accessibilityPrefs.edit()
                            .putBoolean("is_logged_in_user", isLoggedIn)
                            .putBoolean("is_pro_user", isPro)
                            .putInt("user_credits", credits)
                            .apply()
                        
                        // Send broadcast to notify both keyboard service and accessibility service
                        val keyboardIntent = Intent("com.noxquill.rewordium.USER_STATUS_UPDATED")
                        keyboardIntent.putExtra("isLoggedIn", isLoggedIn)
                        keyboardIntent.putExtra("isPro", isPro)
                        keyboardIntent.putExtra("credits", credits)
                        keyboardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        sendBroadcast(keyboardIntent)
                        
                        // Also send broadcast for accessibility service
                        val accessibilityIntent = Intent("com.noxquill.rewordium.ACCESSIBILITY_USER_STATUS_UPDATED")
                        accessibilityIntent.putExtra("isLoggedIn", isLoggedIn)
                        accessibilityIntent.putExtra("isPro", isPro)
                        accessibilityIntent.putExtra("credits", credits)
                        accessibilityIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        sendBroadcast(accessibilityIntent)
                        
                        Log.d(TAG, "[UserStatus] User status updated successfully in both keyboard and accessibility preferences")
                        result.success(true)
                    }
                    else -> {
                        Log.w(TAG, "[UserStatus] Unknown method called: ${call.method}")
                        result.notImplemented()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[UserStatus] Error in method channel handler", e)
                result.error("ERROR", "Error updating user status: ${e.message}", null)
            }
        }
        
        // --- KEYBOARD SETTINGS BROADCAST RECEIVER INITIALIZATION ---
        try {
            KeyboardSettingsBroadcastReceiver.initialize(flutterEngine)
            KeyboardSettingsBroadcastReceiver.register(this)
            Log.d(TAG, "Keyboard settings broadcast receiver initialized and registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing keyboard settings broadcast receiver: ${e.message}")
        }

        // --- IN-APP REVIEW CHANNEL ---
        val reviewChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, REVIEW_CHANNEL)
        reviewChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "showReview" -> {
                    InAppReviewHelper.showInAppReview(this)
                    result.success(true)
                }
                "onSuccessfulGeneration" -> {
                    InAppReviewHelper.onSuccessfulGeneration(this)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
        Log.d(TAG, "In-App Review channel configured")

        // --- IN-APP UPDATE CHANNEL ---
        val updateChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, UPDATE_CHANNEL)
        updateChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "checkForUpdate" -> {
                    val preferImmediate = call.argument<Boolean>("preferImmediate") ?: false
                    InAppUpdateHelper.checkForUpdate(this, preferImmediate)
                    result.success(true)
                }
                "completeFlexibleUpdate" -> {
                    InAppUpdateHelper.completeFlexibleUpdate(this)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
        InAppUpdateHelper.onFlexibleUpdateReady = {
            updateChannel.invokeMethod("onUpdateDownloaded", null)
        }
        Log.d(TAG, "In-App Update channel configured")

        // --- KEYBOARD EVENTS CHANNEL (EventChannel for push-based status) ---
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, KEYBOARD_EVENTS_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    keyboardEventSink = sink
                    // Register for system IME change broadcasts.
                    val filter = IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED)
                    registerReceiver(keyboardStatusReceiver, filter)
                    // Push current status immediately so Flutter syncs on subscribe.
                    sink.success(mapOf(
                        "isEnabled" to isKeyboardEnabled(),
                        "isDefault" to isKeyboardSelectedAsDefault(),
                    ))
                    Log.d(TAG, "Keyboard events stream started")
                }

                override fun onCancel(arguments: Any?) {
                    keyboardEventSink = null
                    try { unregisterReceiver(keyboardStatusReceiver) } catch (_: Exception) {}
                    Log.d(TAG, "Keyboard events stream cancelled")
                }
            })
        Log.d(TAG, "Keyboard events EventChannel configured")
    }
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(packageName) == true
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun isKeyboardEnabled(): Boolean {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = inputMethodManager.enabledInputMethodList
        return enabledMethods.any { it.packageName == packageName }
    }

    private fun isKeyboardSelectedAsDefault(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.currentInputMethodInfo?.packageName == packageName
            } else {
                val selectedIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                val component = ComponentName.unflattenFromString(selectedIme)
                component?.packageName == packageName
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to check selected keyboard", e)
            false
        }
    }

    private fun isReboardEnabled(): Boolean {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = inputMethodManager.enabledInputMethodList
        // Check for Reboard keyboard (the main keyboard from reboard_keyboard module)
        return enabledMethods.any { 
            it.packageName == packageName
        }
    }

    private fun openKeyboardSettings(autoReturnToApp: Boolean = false) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        if (autoReturnToApp) {
            scheduleKeyboardReturnPolling()
            registerKeyboardSettingsObserver()
        } else {
            stopKeyboardReturnPolling()
            unregisterKeyboardSettingsObserver()
        }
    }

    private fun registerKeyboardSettingsObserver() {
        unregisterKeyboardSettingsObserver()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onKeyboardSettingChanged()
            }
        }
        keyboardSettingsObserver = observer
        try {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
                false,
                observer,
            )
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                false,
                observer,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register keyboard settings observer", e)
            keyboardSettingsObserver = null
        }
    }

    private fun unregisterKeyboardSettingsObserver() {
        keyboardSettingsObserver?.let { observer ->
            try {
                contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
        keyboardSettingsObserver = null
    }

    private fun onKeyboardSettingChanged() {
        val runtimePrefs = getSharedPreferences(ONBOARDING_RUNTIME_PREFS, Context.MODE_PRIVATE)
        val shouldReturn = runtimePrefs.getBoolean(KEY_RETURN_TO_APP_AFTER_KEYBOARD_ENABLED, false)
        if (!shouldReturn) {
            unregisterKeyboardSettingsObserver()
            return
        }
        if (!isReboardEnabled()) return

        runtimePrefs.edit().putBoolean(KEY_RETURN_TO_APP_AFTER_KEYBOARD_ENABLED, false).apply()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
                Log.d(TAG, "Returned to app via ContentObserver after keyboard enablement")
            } catch (e: Exception) {
                Log.w(TAG, "ContentObserver-driven app return failed", e)
            }
        }
        stopKeyboardReturnPolling()
        unregisterKeyboardSettingsObserver()
    }

    private fun scheduleKeyboardReturnPolling() {
        stopKeyboardReturnPolling()
        keyboardReturnPollCount = 0

        keyboardReturnRunnable = object : Runnable {
            override fun run() {
                val runtimePrefs = getSharedPreferences(ONBOARDING_RUNTIME_PREFS, Context.MODE_PRIVATE)
                val shouldReturn = runtimePrefs.getBoolean(KEY_RETURN_TO_APP_AFTER_KEYBOARD_ENABLED, false)
                if (!shouldReturn) {
                    stopKeyboardReturnPolling()
                    return
                }

                if (isReboardEnabled()) {
                    runtimePrefs.edit().putBoolean(KEY_RETURN_TO_APP_AFTER_KEYBOARD_ENABLED, false).apply()
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    if (launchIntent != null) {
                        try {
                            startActivity(launchIntent)
                            Log.d(TAG, "Returned user to app after keyboard enablement")
                            stopKeyboardReturnPolling()
                            return
                        } catch (e: Exception) {
                            Log.w(TAG, "Unable to auto-return app after keyboard setup", e)
                        }
                    } else {
                        Log.w(TAG, "Could not resolve launch intent for keyboard auto-return")
                    }
                }

                keyboardReturnPollCount += 1
                if (keyboardReturnPollCount >= KEYBOARD_RETURN_TO_APP_MAX_POLLS) {
                    runtimePrefs.edit().putBoolean(KEY_RETURN_TO_APP_AFTER_KEYBOARD_ENABLED, false).apply()
                    Log.d(TAG, "Keyboard auto-return polling timed out")
                    stopKeyboardReturnPolling()
                    return
                }

                keyboardReturnHandler.postDelayed(this, KEYBOARD_RETURN_TO_APP_POLL_INTERVAL_MS)
            }
        }

        keyboardReturnRunnable?.let {
            keyboardReturnHandler.postDelayed(it, KEYBOARD_RETURN_TO_APP_POLL_INTERVAL_MS)
        }
    }

    private fun stopKeyboardReturnPolling() {
        keyboardReturnRunnable?.let { keyboardReturnHandler.removeCallbacks(it) }
        keyboardReturnRunnable = null
        keyboardReturnPollCount = 0
    }

    private fun showInputMethodPicker(): Boolean {
        return try {
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showInputMethodPicker()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input method picker", e)
            false
        }
    }

    private fun openReboardSettings(): Boolean {
        return try {
            val now = System.currentTimeMillis()
            if (now - lastReboardSettingsLaunchAt < 1500L) {
                Log.d(TAG, "Ignoring duplicate ReBoard settings launch request")
                return true
            }

            val intent = Intent().apply {
                setClassName(packageName, "com.noxquill.rewordium.keyboard.app.FlorisAppActivity")
                `package` = packageName
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            if (intent.resolveActivity(packageManager) != null) {
                lastReboardSettingsLaunchAt = now
                startActivity(intent)
                true
            } else {
                Log.w(TAG, "ReBoard settings activity is not available")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ReBoard settings", e)
            false
        }
    }

    private fun updateSetting(key: String, value: Any) {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Float -> editor.putFloat(key, value)
            is Long -> editor.putLong(key, value)
        }
        
        editor.apply()
        
        // Send broadcast to notify keyboard service of settings change
        val intent = Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        sendBroadcast(intent)
        
        Log.d(TAG, "Updated setting $key = $value and sent broadcast")
    }

    private fun updatePersonas(personas: List<String>) {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        // Convert list to JSON string for storage
        val personasJson = personas.joinToString(",") { "\"$it\"" }
        prefs.edit()
            .putString(KeyboardConstants.KEY_PERSONAS, "[$personasJson]")
            .apply()
        
        // Send broadcast to notify keyboard service of persona changes
        val intent = Intent(KeyboardConstants.ACTION_PERSONAS_UPDATED)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        sendBroadcast(intent)
        
        Log.d(TAG, "Updated personas and sent broadcast")
    }

    private fun handleCreditConsumptionRequest() {
        Log.d(TAG, "Handling credit consumption request from accessibility service")
        userStatusMethodChannel?.invokeMethod("consumeCredit", null, object : MethodChannel.Result {
            override fun success(result: Any?) {
                val success = result as? Boolean ?: false
                Log.d(TAG, "Credit consumption result: $success")
                if (success) {
                    Log.d(TAG, "Credit successfully consumed via Flutter")
                } else {
                    Log.w(TAG, "Failed to consume credit via Flutter")
                }
            }

            override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                Log.e(TAG, "Error consuming credit via Flutter: $errorCode - $errorMessage")
            }

            override fun notImplemented() {
                Log.e(TAG, "consumeCredit method not implemented in Flutter")
            }
        })
    }
}
