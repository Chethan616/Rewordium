package com.noxquill.rewordium.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.inappmessaging.FirebaseInAppMessaging
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether Rewordium may reach the user: push notifications (FCM topic `all_users`, the one the admin panel
 * broadcasts to, plus `android`), Firebase In-App Messaging campaigns, and the in-app announcements and
 * release notes the admin panel publishes ([InAppMessages]). One switch in Settings controls all of them.
 */
object RewordiumNotifications {
    const val CHANNEL_UPDATES = "rewordium_updates"
    private const val PREFS = "rewordium_push"
    private const val KEY_ENABLED = "notifications_enabled"
    private const val KEY_ASKED = "permission_asked"
    private val TOPICS = listOf("all_users", "android")

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the switch, creates the channel and brings topics and in-app messaging in line. Call at start. */
    fun init(context: Context) {
        _enabled.value = prefs(context).getBoolean(KEY_ENABLED, true)
        createChannels(context)
        sync(context)
    }

    fun setEnabled(context: Context, on: Boolean) {
        _enabled.value = on
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        sync(context)
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /** Android 13+ needs the user's permission to post notifications. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True once, the first time the app may ask for the permission without being asked to by the user. */
    fun shouldAskOnce(context: Context): Boolean {
        if (hasPermission(context) || !isEnabled(context)) return false
        val p = prefs(context)
        if (p.getBoolean(KEY_ASKED, false)) return false
        p.edit().putBoolean(KEY_ASKED, true).apply()
        return true
    }

    private fun sync(context: Context) {
        val on = _enabled.value
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            TOPICS.forEach { topic -> if (on) messaging.subscribeToTopic(topic) else messaging.unsubscribeFromTopic(topic) }
        }
        runCatching { FirebaseInAppMessaging.getInstance().setMessagesSuppressed(!on) }
        MascotNudges.sync(context, on)
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Updates and news", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New features, release notes and offers from Rewordium"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(MascotNudges.CHANNEL, "Mascot notes", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Now and then, a quiet note from the mascots when you haven’t visited in a while"
                setShowBadge(false)
            },
        )
    }
}
