package com.noxquill.rewordium.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.noxquill.rewordium.MainActivity
import com.noxquill.rewordium.R

/**
 * Push notifications from the admin panel (FCM). Shown only while notifications are on in Settings.
 * `data.url` (or `actionUrl`) opens a web page; `data.route` opens an app screen (`rewordium://<route>`).
 */
class RewordiumMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        getSharedPreferences("rewordium_push", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!RewordiumNotifications.isEnabled(this) || !RewordiumNotifications.hasPermission(this)) return
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        RewordiumNotifications.init(this)

        val target = message.data["url"] ?: message.data["actionUrl"] ?: message.notification?.link?.toString()
        val route = message.data["route"]
        val intent = when {
            !target.isNullOrBlank() && (target.startsWith("https://") || target.startsWith("http://")) ->
                Intent(Intent.ACTION_VIEW, Uri.parse(target))
            !route.isNullOrBlank() -> Intent(Intent.ACTION_VIEW, Uri.parse("rewordium://${route.removePrefix("/")}"), this, MainActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val launch = PendingIntent.getActivity(
            this,
            message.messageId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, RewordiumNotifications.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_rewordium)
            .setColor(0xFF7A2BE0.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(launch)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(message.messageId?.hashCode() ?: 1, notification)
    }
}
