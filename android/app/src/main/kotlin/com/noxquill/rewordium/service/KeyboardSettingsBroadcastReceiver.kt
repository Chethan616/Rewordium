package com.noxquill.rewordium.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.noxquill.rewordium.keyboard.app.FlorisAppActivity

/** Opens the full ReBoard settings surface when the keyboard asks for its settings. */
class KeyboardSettingsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SHOW_SETTINGS) return
        runCatching {
            context.startActivity(
                Intent(context, FlorisAppActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }.onFailure { Log.w(TAG, "Unable to open keyboard settings", it) }
    }

    companion object {
        private const val TAG = "KeyboardSettingsReceiver"
        private const val ACTION_SHOW_SETTINGS = "com.noxquill.rewordium.SHOW_KEYBOARD_SETTINGS"
        private val receiver = KeyboardSettingsBroadcastReceiver()
        private var registered = false

        @Synchronized
        fun register(context: Context) {
            if (registered) return
            val filter = IntentFilter(ACTION_SHOW_SETTINGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        }

        @Synchronized
        fun unregister(context: Context) {
            if (!registered) return
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
    }
}
