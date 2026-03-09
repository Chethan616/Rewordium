package com.noxquill.rewordium.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class KeyboardSettingsBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL = "keyboard_settings_service"
        private const val TAG = "KeyboardSettingsReceiver"
        private var methodChannel: MethodChannel? = null
        private var instance: KeyboardSettingsBroadcastReceiver? = null
        
        fun getInstance(): KeyboardSettingsBroadcastReceiver {
            if (instance == null) {
                instance = KeyboardSettingsBroadcastReceiver()
            }
            return instance!!
        }
        
        fun initialize(flutterEngine: FlutterEngine) {
            methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            Log.d(TAG, "KeyboardSettingsBroadcastReceiver initialized")
        }
        
        fun register(context: Context) {
            val receiver = getInstance()
            val filter = IntentFilter("com.noxquill.rewordium.SHOW_KEYBOARD_SETTINGS")
            try {
                context.registerReceiver(receiver, filter)
                Log.d(TAG, "BroadcastReceiver registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering BroadcastReceiver: ${e.message}")
            }
        }
        
        fun unregister(context: Context) {
            try {
                val receiver = getInstance()
                context.unregisterReceiver(receiver)
                Log.d(TAG, "BroadcastReceiver unregistered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering BroadcastReceiver: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.noxquill.rewordium.SHOW_KEYBOARD_SETTINGS") {
            Log.d(TAG, "Received broadcast to show keyboard settings")
            
            val isDarkMode = intent.getBooleanExtra("isDarkMode", false)
            
            methodChannel?.invokeMethod("showKeyboardSettings", mapOf(
                "isDarkMode" to isDarkMode
            ))
            
            Log.d(TAG, "Method channel invoked with isDarkMode: $isDarkMode")
        }
    }
}
