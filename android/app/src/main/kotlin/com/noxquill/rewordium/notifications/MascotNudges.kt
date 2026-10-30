package com.noxquill.rewordium.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.noxquill.rewordium.MainActivity
import com.noxquill.rewordium.R
import com.noxquill.rewordium.app.ui.avatar.BOT_TYPES
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.avatar.BotPortrait
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Little notes from the mascots for people who haven't opened Rewordium in a while: "Blob and Pebble are
 * playing tag on your space bar". Gentle by design — only after two quiet days, at most one every three
 * days, only in the daytime, on a silent channel, and they stop after three go unanswered until the app is
 * opened again. Off whenever the notifications switch in Settings is off.
 */
object MascotNudges {
    const val CHANNEL = "rewordium_mascots"

    private const val WORK = "mascot_nudges"
    private const val PREFS = "rewordium_nudges"
    private const val KEY_LAST_OPEN = "last_open"
    private const val KEY_LAST_NUDGE = "last_nudge"
    private const val KEY_UNANSWERED = "unanswered"
    private const val KEY_NEXT_NOTE = "next_note"
    private const val NOTIFICATION_ID = 7_301

    private val QUIET_BEFORE_NUDGE = TimeUnit.DAYS.toMillis(2)
    private val BETWEEN_NUDGES = TimeUnit.DAYS.toMillis(3)
    private const val MAX_UNANSWERED = 3
    private val DAYTIME = 10 until 20

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The app was opened: the clock restarts and any unanswered notes are forgiven. */
    fun onAppOpened(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_OPEN, System.currentTimeMillis()).putInt(KEY_UNANSWERED, 0).apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Follows the notifications switch: checks in every few hours while it's on, nothing at all when off. */
    fun sync(context: Context, enabled: Boolean) {
        val work = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<MascotNudgeWorker>(6, TimeUnit.HOURS).build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    /** Posts a note if now is a good moment for one; true if it did. */
    internal fun maybeNudge(context: Context): Boolean {
        if (!RewordiumNotifications.isEnabled(context) || !RewordiumNotifications.hasPermission(context)) return false
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val lastOpen = p.getLong(KEY_LAST_OPEN, now).also { if (!p.contains(KEY_LAST_OPEN)) p.edit().putLong(KEY_LAST_OPEN, now).apply() }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val due = hour in DAYTIME &&
            now - lastOpen >= QUIET_BEFORE_NUDGE &&
            now - p.getLong(KEY_LAST_NUDGE, 0L) >= BETWEEN_NUDGES &&
            p.getInt(KEY_UNANSWERED, 0) < MAX_UNANSWERED
        if (!due) return false

        val index = p.getInt(KEY_NEXT_NOTE, Random.nextInt(NOTES.size)) % NOTES.size
        val note = NOTES[index]
        val cast = castFor(context, note.mascots)
        post(context, note, cast)
        p.edit()
            .putLong(KEY_LAST_NUDGE, now)
            .putInt(KEY_UNANSWERED, p.getInt(KEY_UNANSWERED, 0) + 1)
            .putInt(KEY_NEXT_NOTE, index + 1)
            .apply()
        return true
    }

    /** The person's own avatar leads; friends are picked at random, never twice. */
    private fun castFor(context: Context, count: Int): List<String> {
        val own = context.getSharedPreferences("rewordium_app", Context.MODE_PRIVATE).getString("mascot_type", null)
            ?.takeIf { it in BOT_TYPES } ?: "flower"
        return listOf(own) + (BOT_TYPES - own).shuffled().take(count - 1)
    }

    private fun post(context: Context, note: Note, cast: List<String>) {
        val names = cast.map { it.replaceFirstChar(Char::uppercase) }
        fun fill(text: String) = text
            .replace("{A}", names[0])
            .replace("{B}", names.getOrElse(1) { names[0] })
            .replace("{C}", names.getOrElse(2) { names[0] })
        val title = fill(note.title)
        val body = fill(note.body)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra("route", note.route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val density = context.resources.displayMetrics.density
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rewordium)
            .setContentTitle(title)
            .setContentText(body)
            .setLargeIcon(runCatching { BotPortrait.single(cast[0], note.mood, (64 * density).toInt()) }.getOrNull())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        // A whole gang gets a picture of them playing when the note is expanded.
        val picture = if (cast.size > 1) {
            runCatching { BotPortrait.group(cast, note.mood, 1024, 512, PASTELS.random()) }.getOrNull()
        } else null
        builder.setStyle(
            if (picture != null) {
                NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as android.graphics.Bitmap?).setSummaryText(body)
            } else {
                NotificationCompat.BigTextStyle().bigText(body)
            },
        )
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    private data class Note(val title: String, val body: String, val route: String, val mascots: Int, val mood: BotMood)

    private val PASTELS = listOf(0xFFF3E8FF.toInt(), 0xFFE3F4FF.toInt(), 0xFFE6F8EC.toInt(), 0xFFFFF1DC.toInt(), 0xFFFFE6EF.toInt())

    private val NOTES = listOf(
        Note("{A} and {B} are playing tag on your space bar", "Nobody’s winning. Come break the tie?", "keyboard_settings", 2, BotMood.Happy),
        Note("{A} misses you", "It’s been practising its best smile all week. Say hi?", "home", 1, BotMood.NeedsYou),
        Note("{A} found a theme you’d love", "It won’t stop bouncing about it. Peek in the Theme shop?", "theme_shop", 1, BotMood.Happy),
        Note("{A}, {B} and {C} are throwing a keyboard party", "Your keys are invited. Snacks are emoji.", "theme_shop", 3, BotMood.Happy),
        Note("{A} wrote you half a poem", "Jade can help with the other half — or any message you’re putting off.", "jade", 1, BotMood.Thinking),
        Note("{A} is napping on the shift key", "Wake it gently with a few words?", "jade", 1, BotMood.Sleeping),
        Note("{A} and {B} built a fort out of your stickers", "It’s very wobbly. Want to make it a sticker pack?", "keyboard_settings", 2, BotMood.Happy),
        Note("Psst, {A} has an idea", "Stuck on a reply? Jade can draft it in your voice in seconds.", "jade", 1, BotMood.NeedsYou),
        Note("{A} tried on every key colour", "It settled on yours. Fancy a new look anyway?", "theme_shop", 1, BotMood.Happy),
        Note("{A} and {B} are racing across your keys", "Swipe-typing practice, they say. Join in?", "keyboard_settings", 2, BotMood.Happy),
    )
}

/** Wakes every few hours; [MascotNudges] decides whether it's a good moment for a note. */
class MascotNudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { MascotNudges.maybeNudge(applicationContext) }
        return Result.success()
    }
}
