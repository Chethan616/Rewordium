package com.example.yc_startup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import com.example.yc_startup.keyboard.util.KeyboardConstants
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.accessibilityservice.AccessibilityServiceInfo

class MainActivity : FlutterActivity() {
    private val KEYBOARD_CHANNEL_NAME = "com.example.yc_startup/rewordium_keyboard"
    private val ACCESSIBILITY_CHANNEL_NAME = "com.example.yc_startup/accessibility"
    private val TAG = "MainActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeDefaultSettings()
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
            Log.d(TAG, "Setting default haptic feedback to true")
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

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, ACCESSIBILITY_CHANNEL_NAME).setMethodCallHandler { call, result ->
            when (call.method) {
                "isAccessibilityServiceEnabled" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "requestAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, KEYBOARD_CHANNEL_NAME).setMethodCallHandler { call, result ->
            when (call.method) {
                "isKeyboardEnabled" -> {
                    result.success(isKeyboardEnabled())
                }
                "isRewordiumAIKeyboardEnabled" -> {
                    result.success(isMyKeyboardEnabled())
                }
                "openKeyboardSettings" -> {
                    openKeyboardSettings()
                    result.success(null)
                }
                "setDarkMode" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    updateSetting(KeyboardConstants.KEY_DARK_MODE, enabled)
                    result.success(null)
                }
                "updateThemeColor" -> {
                    val colorHex = call.argument<String>("colorHex") ?: "#007AFF"
                    updateSetting(KeyboardConstants.KEY_THEME_COLOR, colorHex)
                    result.success(null)
                }
                "setHapticFeedback" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_HAPTIC_FEEDBACK, enabled)
                    result.success(null)
                }
                "setAutoCapitalize" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_AUTO_CAPITALIZE, enabled)
                    result.success(null)
                }
                "setDoubleSpacePeriod" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    updateSetting(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD, enabled)
                    result.success(null)
                }
                "updateKeyboardPersonas" -> {
                    try {
                        val personas = call.argument<List<String>>("personas")
                        Log.d(TAG, "Received personas to update: $personas")
                        
                        // Update personas in shared preferences
                        updatePersonas(personas ?: emptyList())
                        
                        // Send broadcast to notify keyboard service
                        sendBroadcast(Intent(KeyboardConstants.ACTION_PERSONAS_UPDATED))
                        
                        Log.d(TAG, "Successfully updated keyboard personas")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating keyboard personas", e)
                        result.success(false)
                    }
                }
                "getKeyboardSettings" -> {
                    val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    
                    // Ensure defaults are set if they don't exist
                    if (!prefs.contains(KeyboardConstants.KEY_HAPTIC_FEEDBACK)) {
                        prefs.edit().putBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true).apply()
                    }
                    if (!prefs.contains(KeyboardConstants.KEY_AUTO_CAPITALIZE)) {
                        prefs.edit().putBoolean(KeyboardConstants.KEY_AUTO_CAPITALIZE, true).apply()
                    }
                    if (!prefs.contains(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD)) {
                        prefs.edit().putBoolean(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD, true).apply()
                    }
                    
                    val settings = mapOf(
                        "themeColor" to (prefs.getString(KeyboardConstants.KEY_THEME_COLOR, "#007AFF") ?: "#007AFF"),
                        "darkMode" to prefs.getBoolean(KeyboardConstants.KEY_DARK_MODE, false),
                        "hapticFeedback" to prefs.getBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true),
                        "autoCapitalize" to prefs.getBoolean(KeyboardConstants.KEY_AUTO_CAPITALIZE, true),
                        "doubleSpacePeriod" to prefs.getBoolean(KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD, true)
                    )
                    Log.d(TAG, "Returning keyboard settings: $settings")
                    result.success(settings)
                }
                "refreshKeyboard" -> {
                    Log.d(TAG, "Sending settings update broadcast to trigger refresh.")
                    sendBroadcast(Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED))
                    sendBroadcast(Intent(KeyboardConstants.ACTION_PERSONAS_UPDATED))
                    result.success(true)
                }
                else -> {
                    Log.w(TAG, "Method not implemented on keyboard channel: ${call.method}")
                    result.notImplemented()
                }
            }
        }
    }

    private fun <T> updateSetting(key: String, value: T) {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Float -> editor.putFloat(key, value)
            is Long -> editor.putLong(key, value)
            else -> return
        }
        editor.apply()
        sendBroadcast(Intent(KeyboardConstants.ACTION_SETTINGS_UPDATED))
    }

    private fun updatePersonas(personas: List<String>) {
        val prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KeyboardConstants.KEY_PERSONAS, personas.joinToString(",")).apply()
        sendBroadcast(Intent(KeyboardConstants.ACTION_PERSONAS_UPDATED))
    }

    private fun isMyKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val myInputMethodId = "${packageName}/.keyboard.RewordiumAIKeyboardService"
        return imm.enabledInputMethodList.any { it.id == myInputMethodId }
    }

    private fun openKeyboardSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedServiceName = "${packageName}/.service.MyAccessibilityService"
        return enabledServices.any { it.id == expectedServiceName }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
    
    private fun isKeyboardEnabled(): Boolean {
        Log.d(TAG, "Checking if keyboard is enabled...")
        
        // First, try to get the current input method
        val defaultInputMethod = try {
            Settings.Secure.getString(contentResolver, "default_input_method")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default input method", e)
            null
        }
        
        Log.d(TAG, "Default input method: $defaultInputMethod")
        
        // Get our keyboard ID (must match the one in AndroidManifest.xml)
        val myKeyboardId = "${packageName}/.keyboard.RewordiumAIKeyboardService"
        Log.d(TAG, "Our keyboard ID: $myKeyboardId")
        
        // Check if our keyboard is the default one
        val isDefault = defaultInputMethod?.startsWith(myKeyboardId) == true
        Log.d(TAG, "Is our keyboard the default? $isDefault")
        
        // If we're the default, we're definitely enabled
        if (isDefault) {
            Log.d(TAG, "Keyboard is enabled and set as default")
            return true
        }
        
        // If not default, check if we're in the list of enabled keyboards
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val enabledInputMethods = imm.enabledInputMethodList
            
            Log.d(TAG, "Enabled input methods (${enabledInputMethods.size}):")
            enabledInputMethods.forEach { method ->
                Log.d(TAG, "- ${method.id}")
            }
            
            val isEnabled = enabledInputMethods.any { it.id == myKeyboardId }
            Log.d(TAG, "Is our keyboard in the enabled list? $isEnabled")
            
            return isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking enabled input methods", e)
        }
        
        // If we couldn't determine the status, return false to be safe
        return false
    }
}