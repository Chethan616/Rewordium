package com.noxquill.rewordium.notifications

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Announcements the admin panel publishes to the `in_app_messages` collection: campaigns shown as a
 * card, a full modal or a top banner (`type`: card, modal, banner), and release / patch notes
 * (`type`: release_notes or patch_notes, with an optional `version` and a `notes` list). Fields as the
 * admin panel writes them: title, body, type, imageUrl, buttonText, actionUrl, target, publishedAt.
 * Each message is shown once; after an update the app also shows its own "What's new".
 */
object InAppMessages {
    enum class Type { CARD, MODAL, BANNER, RELEASE_NOTES }

    data class Message(
        val id: String,
        val title: String,
        val body: String,
        val type: Type,
        val imageUrl: String? = null,
        val buttonText: String? = null,
        val actionUrl: String? = null,
        val version: String? = null,
        val notes: List<String> = emptyList(),
    )

    private const val COLLECTION = "in_app_messages"
    private const val PREFS = "rewordium_in_app"
    private const val KEY_SEEN = "seen_ids"
    private const val KEY_LAST_VERSION = "last_version_code"
    private val MAX_AGE_MS = TimeUnit.DAYS.toMillis(30)
    private val TARGETS = setOf("", "all", "all_users", "android")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markSeen(context: Context, id: String) {
        val p = prefs(context)
        val seen = p.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
        seen += id
        // Keep the set small: ids of the newest few hundred messages are plenty.
        p.edit().putStringSet(KEY_SEEN, seen.toList().takeLast(300).toSet()).apply()
    }

    private fun seen(context: Context) = prefs(context).getStringSet(KEY_SEEN, emptySet()).orEmpty()

    /**
     * "What's new" when this is the first launch after an update (never on a fresh install): the admin
     * panel's release notes for this version if there are any, else the notes built into the app.
     */
    suspend fun releaseNotesAfterUpdate(context: Context, versionCode: Long, versionName: String): Message? {
        val p = prefs(context)
        val last = p.getLong(KEY_LAST_VERSION, -1L)
        p.edit().putLong(KEY_LAST_VERSION, versionCode).apply()
        if (last < 0 || last >= versionCode) return null
        val remote = runCatching { latest(context) }.getOrDefault(emptyList())
            .firstOrNull { it.type == Type.RELEASE_NOTES && (it.version == null || it.version == versionName) }
        remote?.let { markSeen(context, it.id) }
        return remote ?: Message(
            id = "local-$versionCode",
            title = "What’s new in $versionName",
            body = "A big one for your keyboard.",
            type = Type.RELEASE_NOTES,
            version = versionName,
            notes = BUILT_IN_NOTES,
        )
    }

    /**
     * The newest campaign the user hasn't seen yet, or null. Release notes aren't campaigns: they only
     * show after an update ([releaseNotesAfterUpdate]), never on a fresh install.
     */
    suspend fun nextUnseen(context: Context): Message? {
        val seen = seen(context)
        return runCatching { latest(context) }.getOrDefault(emptyList())
            .firstOrNull { it.id !in seen && it.type != Type.RELEASE_NOTES }
    }

    /** Calls [onMessage] for every message published while the app is open; remove the listener when done. */
    fun listen(context: Context, since: Date, onMessage: (Message) -> Unit): ListenerRegistration? = runCatching {
        FirebaseFirestore.getInstance().collection(COLLECTION)
            .whereGreaterThan("publishedAt", Timestamp(since))
            .addSnapshotListener { snapshot, _ ->
                val seen = seen(context)
                snapshot?.documents?.mapNotNull(::parse)
                    ?.filter { it.id !in seen && it.type != Type.RELEASE_NOTES }
                    ?.lastOrNull()?.let(onMessage)
            }
    }.getOrNull()

    private suspend fun latest(context: Context): List<Message> = withContext(Dispatchers.IO) {
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance().collection(COLLECTION).orderBy("publishedAt", Query.Direction.DESCENDING).limit(10).get(),
            10, TimeUnit.SECONDS,
        )
        val now = System.currentTimeMillis()
        snapshot.documents.filter { doc ->
            val published = doc.getTimestamp("publishedAt")?.toDate()?.time ?: return@filter false
            now - published <= MAX_AGE_MS
        }.mapNotNull(::parse)
    }

    private fun parse(doc: DocumentSnapshot): Message? {
        val target = doc.getString("target").orEmpty().lowercase()
        if (target !in TARGETS) return null
        val title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: return null
        val type = when (doc.getString("type")?.lowercase()) {
            "modal" -> Type.MODAL
            "banner" -> Type.BANNER
            "release_notes", "patch_notes", "whats_new" -> Type.RELEASE_NOTES
            else -> Type.CARD
        }
        @Suppress("UNCHECKED_CAST")
        val notes = (doc.get("notes") as? List<*>)?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }.orEmpty()
        return Message(
            id = doc.id,
            title = title.take(80),
            body = doc.getString("body").orEmpty().take(600),
            type = type,
            imageUrl = doc.getString("imageUrl")?.takeIf { it.startsWith("https://") },
            buttonText = doc.getString("buttonText")?.takeIf { it.isNotBlank() }?.take(30),
            actionUrl = doc.getString("actionUrl")?.takeIf { it.isNotBlank() },
            version = doc.getString("version")?.takeIf { it.isNotBlank() },
            notes = notes.take(10),
        )
    }

    /** Shown after updating to this version when the admin panel has no notes for it. */
    private val BUILT_IN_NOTES = listOf(
        "Theme shop: playful, expressive, professional and clean Android looks",
        "Build your own keyboard theme, or describe a vibe and let Jade design it",
        "Bouncy, squishy, wobbly keys and a mascot on your space bar",
        "Playful new emoji, GIF, sticker, clipboard and AI panels, with Gboard-style search",
        "Next-word predictions that keep up as you write whole sentences",
        "Sharper autocorrect that leaves names and slang alone",
        "Forget learned words any time from the Keys tab",
    )
}
