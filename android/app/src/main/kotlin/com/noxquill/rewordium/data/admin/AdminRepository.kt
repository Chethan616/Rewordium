package com.noxquill.rewordium.data.admin

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.noxquill.rewordium.data.RewordiumRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Date
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Everything the owner's admin panel reads and changes. The panel is only a window: what it may actually
 * do is decided by the Firestore rules and the `sendNotification` function, which both check the signed-in
 * account's verified email on the server. The password is an extra lock on the device, kept here only as a
 * salted PBKDF2 hash.
 */
object AdminRepository {
    const val OWNER_EMAIL = "chethankrishna2022@gmail.com"

    // From local.properties at build time, so the hash isn't in the public repo. Blank = the panel stays locked.
    private val PASSWORD_SALT = com.noxquill.rewordium.BuildConfig.ADMIN_PASSWORD_SALT
    private val PASSWORD_HASH = com.noxquill.rewordium.BuildConfig.ADMIN_PASSWORD_HASH
    private const val PASSWORD_ITERATIONS = 210_000
    private const val FUNCTIONS_BASE = "https://us-central1-rewordium.cloudfunctions.net"

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    /** The hidden entry only answers to the owner's own account, and only once Google has verified its email. */
    fun isOwnerSignedIn(): Boolean {
        val user = RewordiumRepository.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(OWNER_EMAIL, ignoreCase = true)
    }

    sealed interface Unlock {
        data object Granted : Unlock
        data class Wrong(val attemptsLeft: Int) : Unlock
        data class LockedOut(val seconds: Long) : Unlock
    }

    @Volatile private var unlockedUntil = 0L
    @Volatile private var failures = 0
    @Volatile private var lockedUntil = 0L

    /** Open for a while after the right password, and only while the owner is still the one signed in. */
    fun isUnlocked(): Boolean = isOwnerSignedIn() && System.currentTimeMillis() < unlockedUntil

    fun lock() {
        unlockedUntil = 0L
    }

    suspend fun unlock(password: String): Unlock {
        val now = System.currentTimeMillis()
        if (!isOwnerSignedIn()) return Unlock.Wrong(0)
        if (now < lockedUntil) return Unlock.LockedOut((lockedUntil - now + 999) / 1000)
        if (checkPassword(password)) {
            failures = 0
            unlockedUntil = System.currentTimeMillis() + SESSION_MILLIS
            return Unlock.Granted
        }
        failures++
        if (failures >= MAX_ATTEMPTS) {
            failures = 0
            lockedUntil = System.currentTimeMillis() + LOCKOUT_MILLIS
            return Unlock.LockedOut(LOCKOUT_MILLIS / 1000)
        }
        return Unlock.Wrong(MAX_ATTEMPTS - failures)
    }

    private suspend fun checkPassword(input: String): Boolean = withContext(Dispatchers.Default) {
        if (PASSWORD_SALT.isBlank() || PASSWORD_HASH.isBlank()) return@withContext false
        val spec = PBEKeySpec(input.toCharArray(), PASSWORD_SALT.hexToBytes(), PASSWORD_ITERATIONS, 256)
        val derived = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        MessageDigest.isEqual(derived, PASSWORD_HASH.hexToBytes())
    }

    private const val SESSION_MILLIS = 20 * 60_000L
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MILLIS = 60_000L

    // ── Overview ────────────────────────────────────────────────────────────

    data class Overview(
        val totalUsers: Long,
        val proUsers: Long,
        val monthlySubscribers: Long,
        val yearlySubscribers: Long,
        val grantedPro: Long,
        val bannedUsers: Long,
        val newUsers7d: Long,
        val installs: Long,
        val active24h: Long,
        val active7d: Long,
        val openReports: Long,
        val communityThemes: Long,
    )

    suspend fun overview(): Overview = coroutineScope {
        val users = firestore.collection("users")
        val installs = firestore.collection("app_installs")
        val now = System.currentTimeMillis()
        fun ago(days: Double) = Timestamp(Date(now - (days * 86_400_000).toLong()))
        val total = async { count(users) }
        val pro = async { count(users.whereEqualTo("isPro", true)) }
        val monthly = async { count(users.whereEqualTo("isPro", true).whereEqualTo("subscription.planType", "monthly")) }
        val yearly = async { count(users.whereEqualTo("isPro", true).whereEqualTo("subscription.planType", "yearly")) }
        val granted = async { count(users.whereEqualTo("isPro", true).whereEqualTo("planType", GRANTED_PLAN)) }
        val banned = async { count(users.whereEqualTo("banned", true)) }
        val new7 = async { count(users.whereGreaterThanOrEqualTo("createdAt", ago(7.0))) }
        val allInstalls = async { count(installs) }
        val day = async { count(installs.whereGreaterThanOrEqualTo("lastSeenAt", ago(1.0))) }
        val week = async { count(installs.whereGreaterThanOrEqualTo("lastSeenAt", ago(7.0))) }
        val reports = async { count(firestore.collection("usage_events").whereEqualTo("type", REPORT_TYPE)) }
        val themes = async { count(firestore.collection("keyboard_themes")) }
        Overview(
            totalUsers = total.await(),
            proUsers = pro.await(),
            monthlySubscribers = monthly.await(),
            yearlySubscribers = yearly.await(),
            grantedPro = granted.await(),
            bannedUsers = banned.await(),
            newUsers7d = new7.await(),
            installs = allInstalls.await(),
            active24h = day.await(),
            active7d = week.await(),
            openReports = reports.await(),
            communityThemes = themes.await(),
        )
    }

    /** A count that never fails the whole overview: one unreadable query shows as 0. */
    private suspend fun count(query: Query): Long = io {
        runCatching { Tasks.await(query.count().get(AggregateSource.SERVER)).count }.getOrDefault(0L)
    }

    // ── Users ───────────────────────────────────────────────────────────────

    data class AdminUser(
        val uid: String,
        val name: String,
        val email: String,
        val credits: Long,
        val isPro: Boolean,
        val plan: String,
        val subscriptionStatus: String?,
        val expiresAt: Date?,
        val banned: Boolean,
        val bannedReason: String?,
        val createdAt: Date?,
        val signInMethod: String?,
        /** Where a push to just this person goes; saved by their app. */
        val pushToken: String?,
    )

    suspend fun recentUsers(limit: Long = 40): List<AdminUser> = io {
        Tasks.await(
            firestore.collection("users").orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).get(),
        ).documents.map(::toUser)
    }

    /** By uid, or by the start of an email address. */
    suspend fun searchUsers(query: String): List<AdminUser> = io {
        val q = query.trim()
        if (q.isEmpty()) return@io emptyList()
        val byUid = runCatching { Tasks.await(firestore.collection("users").document(q).get()) }.getOrNull()
            ?.takeIf { it.exists() }?.let(::toUser)
        val byEmail = Tasks.await(
            firestore.collection("users").orderBy("email").startAt(q.lowercase()).endAt(q.lowercase() + "").limit(25).get(),
        ).documents.map(::toUser)
        listOfNotNull(byUid) + byEmail.filter { it.uid != byUid?.uid }
    }

    suspend fun user(uid: String): AdminUser? = io {
        Tasks.await(firestore.collection("users").document(uid).get()).takeIf { it.exists() }?.let(::toUser)
    }

    /** When any of the user's installs last opened the app. */
    suspend fun lastSeen(uid: String): Date? = io {
        runCatching {
            Tasks.await(firestore.collection("app_installs").whereEqualTo("uid", uid).limit(10).get())
                .documents.mapNotNull { it.getTimestamp("lastSeenAt")?.toDate() }.maxOrNull()
        }.getOrNull()
    }

    suspend fun setBanned(uid: String, banned: Boolean, reason: String?) {
        val update = if (banned) {
            mapOf("banned" to true, "bannedReason" to reason?.trim()?.take(200).orEmpty(), "bannedAt" to FieldValue.serverTimestamp())
        } else {
            mapOf("banned" to false, "bannedReason" to FieldValue.delete(), "bannedAt" to FieldValue.delete())
        }
        updateUser(uid, update)
        audit(if (banned) "ban" else "unban", uid, reason)
    }

    /** Pro the owner hands out, kept apart from Play subscriptions so revenue counts stay honest. */
    suspend fun setPro(uid: String, pro: Boolean) {
        val update = if (pro) {
            mapOf("isPro" to true, "planType" to GRANTED_PLAN, "proGrantedAt" to FieldValue.serverTimestamp())
        } else {
            mapOf("isPro" to false, "planType" to "free", "proGrantedAt" to FieldValue.delete())
        }
        updateUser(uid, update)
        audit(if (pro) "grant_pro" else "revoke_pro", uid, null)
    }

    suspend fun addCredits(uid: String, amount: Long) {
        updateUser(uid, mapOf("credits" to FieldValue.increment(amount)))
        audit("add_credits", uid, amount.toString())
    }

    private suspend fun updateUser(uid: String, fields: Map<String, Any>) = io {
        Tasks.await(
            firestore.collection("users").document(uid)
                .set(fields + ("lastUpdated" to FieldValue.serverTimestamp()), SetOptions.merge()),
        )
        Unit
    }

    private fun toUser(doc: DocumentSnapshot): AdminUser {
        val subscription = doc.get("subscription") as? Map<*, *>
        val isPro = doc.getBoolean("isPro") == true
        return AdminUser(
            uid = doc.id,
            name = doc.getString("name").orEmpty(),
            email = doc.getString("email").orEmpty(),
            credits = doc.getLong("credits") ?: 0,
            isPro = isPro,
            plan = (subscription?.get("planType") as? String)?.takeIf { isPro && doc.getString("planType") != GRANTED_PLAN }
                ?: doc.getString("planType")
                ?: if (isPro) "pro" else "free",
            subscriptionStatus = subscription?.get("status") as? String,
            expiresAt = (subscription?.get("expiryDate") as? Timestamp)?.toDate(),
            banned = doc.getBoolean("banned") == true,
            bannedReason = doc.getString("bannedReason")?.takeIf { it.isNotBlank() },
            createdAt = doc.getTimestamp("createdAt")?.toDate(),
            signInMethod = doc.getString("signInMethod"),
            pushToken = doc.getString("fcmToken")?.takeIf { it.isNotBlank() },
        )
    }

    // ── In-app messages, campaigns and release notes ────────────────────────

    data class Message(
        val id: String,
        val title: String,
        val body: String,
        val type: String,
        val target: String,
        val version: String?,
        val publishedAt: Date?,
    )

    suspend fun messages(): List<Message> = io {
        Tasks.await(firestore.collection("in_app_messages").orderBy("publishedAt", Query.Direction.DESCENDING).limit(30).get())
            .documents.map { doc ->
                Message(
                    id = doc.id,
                    title = doc.getString("title").orEmpty(),
                    body = doc.getString("body").orEmpty(),
                    type = doc.getString("type").orEmpty().ifBlank { "card" },
                    target = doc.getString("target").orEmpty().ifBlank { "all" },
                    version = doc.getString("version"),
                    publishedAt = doc.getTimestamp("publishedAt")?.toDate(),
                )
            }
    }

    suspend fun publishMessage(
        title: String,
        body: String,
        type: String,
        buttonText: String?,
        actionUrl: String?,
        imageUrl: String?,
        version: String?,
        notes: List<String>,
    ) {
        val data = buildMap<String, Any> {
            put("title", title.trim().take(80))
            put("body", body.trim().take(600))
            put("type", type)
            put("target", "all")
            put("publishedAt", FieldValue.serverTimestamp())
            buttonText?.trim()?.takeIf { it.isNotEmpty() }?.let { put("buttonText", it.take(30)) }
            actionUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { put("actionUrl", it) }
            imageUrl?.trim()?.takeIf { it.startsWith("https://") }?.let { put("imageUrl", it) }
            version?.trim()?.takeIf { it.isNotEmpty() }?.let { put("version", it) }
            notes.map(String::trim).filter(String::isNotEmpty).take(10).takeIf { it.isNotEmpty() }?.let { put("notes", it) }
        }
        val id = io { Tasks.await(firestore.collection("in_app_messages").add(data)).id }
        audit("publish_message", null, "$type · $id")
    }

    suspend fun deleteMessage(id: String) {
        io { Tasks.await(firestore.collection("in_app_messages").document(id).delete()) }
        audit("delete_message", null, id)
    }

    // ── Push notifications ──────────────────────────────────────────────────

    data class PushLog(val title: String, val body: String, val target: String, val sentAt: Date?, val success: Boolean, val error: String?)

    suspend fun pushHistory(): List<PushLog> = io {
        Tasks.await(firestore.collection("notifications").orderBy("sentAt", Query.Direction.DESCENDING).limit(20).get())
            .documents.map { doc ->
                PushLog(
                    title = doc.getString("title").orEmpty(),
                    body = doc.getString("body").orEmpty(),
                    target = doc.getString("target").orEmpty(),
                    sentAt = doc.getTimestamp("sentAt")?.toDate() ?: doc.getDate("sentAt"),
                    success = doc.getBoolean("success") != false,
                    error = doc.getString("error"),
                )
            }
    }

    /**
     * Sends a push through the `sendNotification` callable (FCM needs a server; the function checks the caller
     * is an admin). [route] opens that screen when tapped, e.g. `theme_shop` or `pro`.
     */
    suspend fun sendPush(title: String, body: String, topic: String?, route: String?, token: String? = null) = io {
        val user = requireNotNull(RewordiumRepository.currentUser) { "Sign in first." }
        val token = Tasks.await(user.getIdToken(false)).token ?: error("Couldn’t get a sign-in token.")
        val payload = JSONObject().put(
            "data",
            JSONObject()
                .put("title", title.trim())
                .put("body", body.trim())
                .apply { if (token != null) put("token", token) else put("topic", topic) }
                .put("data", JSONObject().apply { route?.trim()?.takeIf { it.isNotEmpty() }?.let { put("route", it) } }),
        )
        val connection = (URL("$FUNCTIONS_BASE/sendNotification").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 404) error(PUSH_NOT_SET_UP)
            if (code !in 200..299) {
                val message = runCatching { JSONObject(response).getJSONObject("error").getString("message") }.getOrNull()
                error(message ?: "The push wasn’t sent (HTTP $code).")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A message for one person: it lands in their private inbox and shows in their app (live if it's open,
     * else on their next visit). With [alsoPush] it also goes out as a push to their phone — that part needs
     * the push function (Blaze plan); the inbox copy is sent either way.
     */
    suspend fun messageUser(user: AdminUser, title: String, body: String, type: String, buttonText: String?, actionUrl: String?, alsoPush: Boolean) {
        val data = buildMap<String, Any> {
            put("title", title.trim().take(80))
            put("body", body.trim().take(600))
            put("type", type)
            put("publishedAt", FieldValue.serverTimestamp())
            put("from", RewordiumRepository.currentUser?.email.orEmpty())
            buttonText?.trim()?.takeIf { it.isNotEmpty() }?.let { put("buttonText", it.take(30)) }
            actionUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { put("actionUrl", it) }
        }
        io { Tasks.await(firestore.collection("users").document(user.uid).collection("inbox").add(data)) }
        audit("message_user", user.uid, title.take(80))
        if (alsoPush) {
            val token = user.pushToken ?: error("Saved to their inbox. No push: their phone hasn’t registered for notifications yet.")
            runCatching { sendPush(title, body, topic = null, route = null, token = token) }
                .onFailure { throw IllegalStateException("Saved to their inbox. The push didn’t go: ${it.message}") }
        }
    }

    // ── AI content reports ──────────────────────────────────────────────────

    data class Report(
        val id: String,
        val uid: String,
        val reason: String,
        val details: String,
        val reply: String,
        val prompt: String?,
        val createdAt: Date?,
    )

    suspend fun reports(): List<Report> = io {
        Tasks.await(firestore.collection("usage_events").whereEqualTo("type", REPORT_TYPE).limit(50).get())
            .documents.map { doc ->
                Report(
                    id = doc.id,
                    uid = doc.getString("uid").orEmpty(),
                    reason = doc.getString("reason").orEmpty(),
                    details = doc.getString("details").orEmpty(),
                    reply = doc.getString("reply").orEmpty(),
                    prompt = doc.getString("prompt"),
                    createdAt = doc.getTimestamp("createdAt")?.toDate(),
                )
            }
            .sortedByDescending { it.createdAt }
    }

    suspend fun resolveReport(id: String) {
        io { Tasks.await(firestore.collection("usage_events").document(id).delete()) }
        audit("resolve_report", null, id)
    }

    // ── Audit trail ─────────────────────────────────────────────────────────

    /** Every change the panel makes, so there's a record of who did what (best effort). */
    private suspend fun audit(action: String, targetUid: String?, detail: String?) {
        io {
            runCatching {
                Tasks.await(
                    firestore.collection("admin_audit").add(
                        buildMap<String, Any> {
                            put("action", action)
                            put("by", RewordiumRepository.currentUser?.email.orEmpty())
                            put("at", FieldValue.serverTimestamp())
                            targetUid?.let { put("targetUid", it) }
                            detail?.let { put("detail", it.take(300)) }
                        },
                    ),
                )
            }
        }
    }

    /** Firebase Console's free notification composer, which works on any plan. */
    const val CONSOLE_MESSAGING_URL = "https://console.firebase.google.com/project/rewordium/messaging"

    private const val PUSH_NOT_SET_UP =
        "Sending from here needs the sendNotification function, which needs the Blaze plan. Use Firebase Console › Messaging for now."

    private const val GRANTED_PLAN = "admin_grant"
    private const val REPORT_TYPE = "ai_content_report"

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
