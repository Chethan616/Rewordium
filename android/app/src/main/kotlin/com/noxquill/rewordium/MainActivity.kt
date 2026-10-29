package com.noxquill.rewordium

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.noxquill.rewordium.app.RewordiumRoot
import com.noxquill.rewordium.app.ui.RewordiumTheme
import com.noxquill.rewordium.data.AppSettings
import com.noxquill.rewordium.keyboard.app.FlorisAppActivity
import com.noxquill.rewordium.review.InAppReviewHelper
import com.noxquill.rewordium.service.KeyboardSettingsBroadcastReceiver
import com.noxquill.rewordium.update.InAppUpdateHelper
import com.noxquill.rewordium.util.KeyboardConstants

/** How long a fresh launch keeps the splash up, so Jade's spring-in and blink can play. */
private const val SPLASH_HOLD_MS = 600L

data class PlatformSetupStatus(
    val accessibilityEnabled: Boolean,
    val keyboardEnabled: Boolean,
    val keyboardSelected: Boolean,
)

class MainActivity : ComponentActivity() {
    private var routeRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installLaunchScreen(coldStart = savedInstanceState == null)
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars on every Android version (15+ forces it anyway),
        // so the app's own background shows there instead of the window theme colour.
        enableEdgeToEdge()
        runCatching { if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this) }
        migrateLegacyKeyboardAppPreferences()
        initializeKeyboardDefaults()
        AppSettings.init(this)
        routeRequest = routeFromIntent(intent)
        KeyboardSettingsBroadcastReceiver.register(this)
        setContent {
            RewordiumTheme {
                RewordiumRoot(
                    activity = this,
                    routeRequest = routeRequest,
                    onRouteConsumed = { routeRequest = null },
                )
            }
        }
        InAppUpdateHelper.checkForUpdate(this, false)
    }

    /**
     * The launch screen: Jade pops onto a Material shape (see splash_jade.xml).
     * On a fresh launch it stays up just long enough for her hello to land, then
     * the shape swells and fades into the app instead of cutting away.
     */
    private fun installLaunchScreen(coldStart: Boolean) {
        val splash = installSplashScreen()
        if (coldStart) {
            val shownAt = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - shownAt < SPLASH_HOLD_MS }
        }
        splash.setOnExitAnimationListener { provider ->
            runCatching {
                val grow = ObjectAnimator.ofPropertyValuesHolder(
                    provider.iconView,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.3f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.3f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                )
                val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
                AnimatorSet().apply {
                    playTogether(grow, fade)
                    duration = 300L
                    interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = provider.remove()
                    })
                    start()
                }
            }.onFailure { provider.remove() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeRequest = routeFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        InAppUpdateHelper.resumeUpdateIfNeeded(this)
    }

    override fun onDestroy() {
        KeyboardSettingsBroadcastReceiver.unregister(this)
        super.onDestroy()
    }

    fun setupStatus(): PlatformSetupStatus {
        val enabledServices = Secure.getString(contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
        val accessibilityEnabled = enabledServices.any {
            it.packageName == packageName && it.className ==
                "com.noxquill.rewordium.service.MyAccessibilityService"
        }

        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val rewordiumKeyboard = inputMethodManager.enabledInputMethodList.firstOrNull {
            it.packageName == packageName && it.serviceName.endsWith("FlorisImeService")
        }
        val selectedInputMethod = Secure.getString(contentResolver, Secure.DEFAULT_INPUT_METHOD)
        return PlatformSetupStatus(
            accessibilityEnabled = accessibilityEnabled,
            keyboardEnabled = rewordiumKeyboard != null,
            keyboardSelected = rewordiumKeyboard?.id == selectedInputMethod,
        )
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openKeyboardActivationSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    fun chooseKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    fun openKeyboardPreferences(section: String? = null) {
        openReboardPath(if (section.isNullOrBlank()) null else "settings/$section")
    }

    /** Opens a ReBoard settings screen by its deep-link path (e.g. `settings/theme`), or ReBoard's home. */
    fun openReboardPath(path: String?) {
        val intent = if (path.isNullOrBlank()) {
            Intent(this, FlorisAppActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("ui://ReBoard/$path"))
                .setClass(this, FlorisAppActivity::class.java)
        }
        startActivity(intent)
    }

    fun onAiGenerationSuccess() {
        InAppReviewHelper.onSuccessfulGeneration(this)
    }

    private fun initializeKeyboardDefaults() {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        if (!prefs.contains(KeyboardConstants.KEY_HAPTIC_FEEDBACK)) editor.putBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true)
        if (!prefs.contains(KeyboardConstants.KEY_AUTO_CAPITALIZE)) editor.putBoolean(KeyboardConstants.KEY_AUTO_CAPITALIZE, true)
        if (!prefs.contains(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD)) editor.putBoolean(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD, true)
        if (!prefs.contains(KeyboardConstants.KEY_AUTOCORRECT)) editor.putBoolean(KeyboardConstants.KEY_AUTOCORRECT, true)
        if (!prefs.contains(KeyboardConstants.KEY_AI_SUGGESTIONS)) editor.putBoolean(KeyboardConstants.KEY_AI_SUGGESTIONS, true)
        editor.apply()
    }

    /** Copies saved settings from the former Flutter host into the native app store once. */
    private fun migrateLegacyKeyboardAppPreferences() {
        val legacy = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val native = getSharedPreferences(KeyboardConstants.PREFS_NAME, MODE_PRIVATE)
        val edit = native.edit()
        val migratedKeys = mutableListOf<String>()

        fun copyBoolean(oldKey: String, newKey: String) {
            if (legacy.contains(oldKey)) {
                if (!native.contains(newKey)) edit.putBoolean(newKey, legacy.getBoolean(oldKey, false))
                migratedKeys += oldKey
            }
        }

        fun copyString(oldKey: String, newKey: String) {
            if (legacy.contains(oldKey)) {
                if (!native.contains(newKey)) legacy.getString(oldKey, null)?.let { edit.putString(newKey, it) }
                migratedKeys += oldKey
            }
        }

        copyBoolean("flutter.paraphraser_enabled", "ai_rewrite_enabled")
        copyString("flutter.active_persona", "active_persona")
        copyString("flutter.custom_personas", "custom_personas")
        if (legacy.contains("flutter.keyboard_personas") && !native.contains("selected_keyboard_personas")) {
            val selected = legacy.getStringSet("flutter.keyboard_personas", emptySet()).orEmpty()
            edit.putStringSet("selected_keyboard_personas", selected)
            migratedKeys += "flutter.keyboard_personas"
        }

        if (migratedKeys.isNotEmpty()) {
            edit.apply()
            legacy.edit().also { old -> migratedKeys.forEach(old::remove) }.apply()
        }
    }

    private fun routeFromIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val raw = uri.getQueryParameter("route") ?: uri.host ?: uri.lastPathSegment ?: return null
        return when (raw.lowercase().replace('-', '_')) {
            "home" -> "home"
            "settings", "app_settings" -> "settings"
            "keyboard", "keyboard_settings" -> "keyboard_settings"
            "ai", "jade", "jade_ai" -> "jade"
            "ai_settings" -> "ai_settings"
            "pro", "premium", "subscribe" -> "pro"
            "privacy" -> "privacy"
            "terms" -> "terms"
            // The old writing-tool screens now live inside Jade as skills.
            "paraphrase", "paraphraser", "rewrite" -> "paraphraser"
            "grammar", "grammar_check" -> "grammar"
            "tools", "tool" -> "tools"
            "summarizer", "summary" -> "summarizer"
            "translator", "translate" -> "translator"
            "tone", "tone_editor" -> "tone"
            "detector", "ai_detector" -> "detector"
            "humanizer", "humanize" -> "humanizer"
            "document" -> "document"
            else -> null
        }
    }
}
