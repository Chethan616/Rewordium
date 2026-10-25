package com.noxquill.rewordium

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.noxquill.rewordium.app.RewordiumRoot
import com.noxquill.rewordium.app.ui.RewordiumTheme
import com.noxquill.rewordium.keyboard.app.FlorisAppActivity
import com.noxquill.rewordium.review.InAppReviewHelper
import com.noxquill.rewordium.service.KeyboardSettingsBroadcastReceiver
import com.noxquill.rewordium.update.InAppUpdateHelper
import com.noxquill.rewordium.util.KeyboardConstants

data class PlatformSetupStatus(
    val accessibilityEnabled: Boolean,
    val keyboardEnabled: Boolean,
    val keyboardSelected: Boolean,
)

class MainActivity : ComponentActivity() {
    private var routeRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this) }
        migrateLegacyKeyboardAppPreferences()
        initializeKeyboardDefaults()
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

    fun openKeyboardPreferences() {
        startActivity(Intent(this, FlorisAppActivity::class.java))
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
            "paraphrase", "paraphraser", "rewrite" -> "paraphraser"
            "grammar", "grammar_check" -> "grammar"
            "tools", "tool" -> "tools"
            "summarizer", "summary" -> "summarizer"
            "translator", "translate" -> "translator"
            "tone", "tone_editor" -> "tone"
            "detector", "ai_detector" -> "detector"
            else -> null
        }
    }
}
