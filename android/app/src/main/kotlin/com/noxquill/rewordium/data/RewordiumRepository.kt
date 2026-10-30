package com.noxquill.rewordium.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.noxquill.rewordium.service.CreditManager
import com.noxquill.rewordium.util.KeyboardConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Date

const val FREE_CREDITS = 20

data class AccountInfo(
    val uid: String,
    val name: String,
    val email: String,
    val credits: Int,
    val isPro: Boolean,
    val planType: String = "free",
)

object RewordiumRepository {
    private const val TAG = "RewordiumRepository"
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    val currentUser: FirebaseUser? get() = runCatching { auth.currentUser }.getOrNull()

    private val _suspension = MutableStateFlow<String?>(null)

    /** Set when an admin has banned the account that was signed in: the reason (possibly blank), until shown. */
    val suspension: StateFlow<String?> = _suspension

    fun dismissSuspension() {
        _suspension.value = null
    }

    suspend fun signIn(email: String, password: String): FirebaseUser = io {
        val result = Tasks.await(auth.signInWithEmailAndPassword(email.trim(), password))
        requireNotNull(result.user) { "Sign in did not return an account." }
    }

    suspend fun signUp(name: String, email: String, password: String, newsOptIn: Boolean): FirebaseUser = io {
        val result = Tasks.await(auth.createUserWithEmailAndPassword(email.trim(), password))
        val user = requireNotNull(result.user) { "Account creation did not return an account." }
        val cleanName = name.trim().ifBlank { user.email?.substringBefore('@') ?: "Rewordium user" }
        Tasks.await(firestore.collection("users").document(user.uid).set(mapOf(
            "name" to cleanName,
            "email" to user.email,
            "isPro" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "credits" to FREE_CREDITS,
            "lastCreditRefresh" to FieldValue.serverTimestamp(),
            "signInMethod" to "email",
            "subscribedToNews" to newsOptIn,
        )))
        user
    }

    suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser = io {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = Tasks.await(auth.signInWithCredential(credential))
        val user = requireNotNull(result.user) { "Google sign in did not return an account." }
        val userRef = firestore.collection("users").document(user.uid)
        val snapshot = Tasks.await(userRef.get())
        if (!snapshot.exists()) {
            Tasks.await(userRef.set(mapOf(
                "name" to (user.displayName ?: user.email?.substringBefore('@') ?: "Google user"),
                "email" to user.email,
                "isPro" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "credits" to FREE_CREDITS,
                "lastCreditRefresh" to FieldValue.serverTimestamp(),
                "signInMethod" to "google",
                "subscribedToNews" to false,
            )))
        }
        user
    }

    suspend fun sendPasswordReset(email: String) = io {
        Tasks.await(auth.sendPasswordResetEmail(email.trim()))
    }

    fun signOut(context: Context) {
        auth.signOut()
        syncNativeStatus(context, null)
    }

    suspend fun refreshAccount(context: Context): AccountInfo? {
        val user = currentUser ?: run {
            syncNativeStatus(context, null)
            return null
        }
        return try {
            val snapshot = io { Tasks.await(firestore.collection("users").document(user.uid).get()) }
            val data = snapshot.data.orEmpty()
            if (data["banned"] == true) {
                _suspension.value = (data["bannedReason"] as? String).orEmpty()
                signOut(context)
                return null
            }
            val isPro = data["isPro"] as? Boolean ?: false
            val info = AccountInfo(
                uid = user.uid,
                name = (data["name"] as? String)?.takeIf(String::isNotBlank)
                    ?: user.displayName?.takeIf(String::isNotBlank)
                    ?: user.email?.substringBefore('@').orEmpty(),
                email = user.email.orEmpty(),
                credits = (data["credits"] as? Number)?.toInt() ?: 0,
                isPro = isPro,
                planType = when {
                    // Pro handed out from the admin panel, not bought.
                    data["planType"] == "admin_grant" -> "gifted"
                    else -> (data["subscription"] as? Map<*, *>)?.get("planType") as? String
                        ?: data["planType"] as? String
                        ?: if (isPro) "pro" else "free"
                },
            )
            syncNativeStatus(context, info)
            savePushToken(user.uid, data["fcmToken"] as? String)
            info
        } catch (error: Exception) {
            Log.w(TAG, "Could not refresh the account profile", error)
            val cached = AccountInfo(user.uid, user.displayName ?: "", user.email.orEmpty(), 0, false)
            syncNativeStatus(context, cached)
            cached
        }
    }

    /** Keeps this phone's push token on the profile, so the admin can reach just this person. */
    private suspend fun savePushToken(uid: String, saved: String?) {
        runCatching {
            val token = io { Tasks.await(com.google.firebase.messaging.FirebaseMessaging.getInstance().token) }
            if (token.isNotBlank() && token != saved) {
                io { Tasks.await(firestore.collection("users").document(uid).set(mapOf("fcmToken" to token), SetOptions.merge())) }
            }
        }.onFailure { Log.w(TAG, "Could not save the push token", it) }
    }

    suspend fun consumeCredit(context: Context, source: String): Boolean {
        val user = currentUser ?: return false
        val allowed = io {
            val userRef = firestore.collection("users").document(user.uid)
            Tasks.await(firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val data = snapshot.data ?: return@runTransaction false
                if (data["isPro"] as? Boolean == true) return@runTransaction true
                val credits = (data["credits"] as? Number)?.toInt() ?: 0
                if (credits <= 0) return@runTransaction false
                transaction.update(userRef, mapOf(
                    "credits" to credits - 1,
                    "lastUpdated" to FieldValue.serverTimestamp(),
                ))
                true
            })
        }
        if (allowed) {
            runCatching {
                FirebaseAnalytics.getInstance(context).logEvent("credit_used", android.os.Bundle().apply {
                    putString(FirebaseAnalytics.Param.ITEM_NAME, source)
                })
            }
            refreshAccount(context)
        }
        return allowed
    }

    suspend fun activateProFromPlay(
        productId: String,
        purchaseToken: String,
        purchaseTimeMillis: Long,
        autoRenewing: Boolean,
        restored: Boolean,
    ): Boolean {
        val user = currentUser ?: return false
        if (purchaseToken.isBlank()) return false
        return runCatching {
            val plan = if (productId.contains("year", ignoreCase = true)) "yearly" else "monthly"
            val days = if (plan == "yearly") 365L else 30L
            val now = Date()
            val purchasedAt = Date(purchaseTimeMillis.takeIf { it > 0 } ?: now.time)
            val expiry = when {
                !restored -> Date(maxOf(purchasedAt.time, now.time) + days * 24L * 60L * 60L * 1000L)
                autoRenewing -> Date(now.time + days * 24L * 60L * 60L * 1000L)
                else -> Date(now.time + 3L * 24L * 60L * 60L * 1000L)
            }
            val ref = firestore.collection("users").document(user.uid)
            val existing = withContext(Dispatchers.IO) { Tasks.await(ref.get()).data?.get("subscription") as? Map<*, *> }
            val oldToken = existing?.get("purchaseToken") as? String
            val oldExpiry = (existing?.get("expiryDate") as? Timestamp)?.toDate()
            val resolvedExpiry = if (autoRenewing && oldToken == purchaseToken && oldExpiry?.after(expiry) == true) oldExpiry else expiry
            withContext(Dispatchers.IO) {
                Tasks.await(ref.set(mapOf(
                    "isPro" to true,
                    "planType" to plan,
                    "upgradedAt" to FieldValue.serverTimestamp(),
                    "lastUpdated" to FieldValue.serverTimestamp(),
                    "lastPurchaseAt" to FieldValue.serverTimestamp(),
                    "subscription" to mapOf(
                        "planType" to plan,
                        "status" to if (autoRenewing) "active" else "cancelled_active",
                        "autoRenewing" to autoRenewing,
                        "platform" to "google_play",
                        "productId" to productId,
                        "purchaseToken" to purchaseToken,
                        "purchaseDate" to Timestamp(purchasedAt),
                        "startDate" to FieldValue.serverTimestamp(),
                        "lastVerifiedAt" to FieldValue.serverTimestamp(),
                        "verificationSource" to "client_firestore_only",
                        "expiryDate" to Timestamp(resolvedExpiry),
                    ),
                ), SetOptions.merge()))
            }
            true
        }.onFailure { Log.w(TAG, "Could not save the Play purchase", it) }.getOrDefault(false)
    }

    /**
     * Sends a report about a Jade reply (Google Play's AI-generated content
     * policy). Stored as an admin-only usage event, so no other user can read it.
     */
    suspend fun reportAiContent(context: Context, reason: String, details: String, reply: String, prompt: String?) {
        val user = requireNotNull(currentUser) { "Sign in to send a report." }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        io {
            Tasks.await(firestore.collection("usage_events").add(mapOf(
                "uid" to user.uid,
                "type" to "ai_content_report",
                "surface" to "jade",
                "reason" to reason,
                "details" to details.take(1_000),
                "reply" to reply.take(4_000),
                "prompt" to prompt?.take(2_000),
                "appVersion" to version,
                "createdAt" to FieldValue.serverTimestamp(),
            )))
        }
    }

    /** Whether the signed-in account uses Google rather than an email password. */
    val signedInWithGoogle: Boolean
        get() = currentUser?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    /** Firebase only deletes accounts that signed in within the last few minutes. */
    fun needsFreshSignIn(): Boolean {
        val signedIn = currentUser?.metadata?.lastSignInTimestamp ?: return true
        val last = maxOf(signedIn, lastReauthenticatedAt)
        return System.currentTimeMillis() - last > 4 * 60_000L
    }

    // Re-authenticating refreshes the token's sign-in time but not the user metadata.
    @Volatile private var lastReauthenticatedAt = 0L

    suspend fun reauthenticateWithPassword(password: String) {
        val user = requireNotNull(currentUser) { "No account is signed in." }
        val email = requireNotNull(user.email) { "This account has no email address." }
        io { Tasks.await(user.reauthenticate(com.google.firebase.auth.EmailAuthProvider.getCredential(email, password))) }
        lastReauthenticatedAt = System.currentTimeMillis()
    }

    suspend fun reauthenticateWithGoogle(idToken: String) {
        val user = requireNotNull(currentUser) { "No account is signed in." }
        io { Tasks.await(user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null))) }
        lastReauthenticatedAt = System.currentTimeMillis()
    }

    /** Deletes the profile, then the sign-in account itself. Call after a fresh sign-in. */
    suspend fun deleteAccount() {
        val user = requireNotNull(currentUser) { "No account is signed in." }
        check(!needsFreshSignIn()) { "For your security, confirm it's you before deleting your account." }
        io { Tasks.await(firestore.collection("users").document(user.uid).delete()) }
        io { Tasks.await(user.delete()) }
    }

    private fun syncNativeStatus(context: Context, account: AccountInfo?) {
        val loggedIn = account != null
        val isPro = account?.isPro == true
        val credits = account?.credits ?: 0
        context.getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("user_logged_in", loggedIn)
            .putBoolean("user_is_pro", isPro)
            .putInt("user_credits", credits)
            .apply()
        context.getSharedPreferences(CreditManager.USER_STATUS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(CreditManager.KEY_IS_LOGGED_IN, loggedIn)
            .putBoolean(CreditManager.KEY_IS_PRO, isPro)
            .putInt(CreditManager.KEY_CREDITS, credits)
            .apply()

        context.sendBroadcast(Intent("com.noxquill.rewordium.USER_STATUS_UPDATED").setPackage(context.packageName).apply {
            putExtra("isLoggedIn", loggedIn); putExtra("isPro", isPro); putExtra("credits", credits)
        })
        context.sendBroadcast(Intent("com.noxquill.rewordium.ACCESSIBILITY_USER_STATUS_UPDATED").setPackage(context.packageName).apply {
            putExtra("isLoggedIn", loggedIn); putExtra("isPro", isPro); putExtra("credits", credits)
        })
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
