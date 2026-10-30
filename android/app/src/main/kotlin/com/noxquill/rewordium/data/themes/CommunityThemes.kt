package com.noxquill.rewordium.data.themes

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.noxquill.rewordium.data.RewordiumRepository
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keyboard themes people made and shared: the `keyboard_themes` Firestore collection. Each document
 * holds the theme's spec as JSON (a few hundred bytes), so browsing downloads no images and a theme
 * previews and installs from that alone. Rules: anyone can browse (no account needed); publishing needs
 * a session, authors write their own; downloads only ever go up by one.
 */
object CommunityThemes {
    private const val COLLECTION = "keyboard_themes"
    private const val PAGE = 30L

    enum class Sort { Popular, New }

    data class Shared(
        val id: String,
        val spec: KeyboardThemeSpec,
        val author: String,
        val downloads: Long,
        val mine: Boolean,
    )

    class CommunityException(message: String) : Exception(message)

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    suspend fun list(sort: Sort): Result<List<Shared>> = io {
        val uid = RewordiumRepository.currentUser?.uid
        val field = if (sort == Sort.Popular) "downloads" else "createdAt"
        val snapshot = Tasks.await(firestore.collection(COLLECTION).orderBy(field, Query.Direction.DESCENDING).limit(PAGE).get())
        snapshot.documents.mapNotNull { doc ->
            val spec = KeyboardThemeSpec.fromJson(doc.getString("spec").orEmpty()) ?: return@mapNotNull null
            Shared(
                id = doc.id,
                spec = spec,
                author = doc.getString("authorName").orEmpty().ifBlank { "Someone" },
                downloads = doc.getLong("downloads") ?: 0L,
                mine = uid != null && doc.getString("authorUid") == uid,
            )
        }
    }

    /** Shares [spec] under [authorName]; returns the new theme's id. */
    suspend fun publish(spec: KeyboardThemeSpec, authorName: String): Result<String> = io {
        val uid = RewordiumRepository.currentUser?.uid ?: throw CommunityException("Sign in to share themes.")
        val clean = spec.readable().copy(author = authorName.take(40))
        val data = hashMapOf<String, Any>(
            "name" to clean.name,
            "spec" to clean.toJson(),
            "night" to clean.night,
            "style" to clean.style,
            "authorName" to authorName.take(40),
            "authorUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "downloads" to 0,
        )
        Tasks.await(firestore.collection(COLLECTION).add(data)).id
    }

    /** Counts one download of a shared theme (best effort: a miss never blocks installing it). */
    suspend fun countDownload(id: String) {
        io { Tasks.await(firestore.collection(COLLECTION).document(id).update("downloads", FieldValue.increment(1))) }
    }

    suspend fun delete(id: String): Result<Unit> = io {
        Tasks.await(firestore.collection(COLLECTION).document(id).delete())
        Unit
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching(block).recoverCatching { error ->
            val cause = (error as? java.util.concurrent.ExecutionException)?.cause ?: error
            throw when {
                cause is CommunityException -> cause
                cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    CommunityException("Community themes aren’t open yet. Check back soon.")
                cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                    CommunityException("You’re offline. Community themes need a connection.")
                else -> CommunityException("Couldn’t reach community themes. Try again.")
            }
        }
    }
}
