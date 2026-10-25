package com.noxquill.rewordium.integrity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Native Play Integrity checks. The production gate remains disabled, matching the current app policy. */
class PlayIntegrityHandler(private val context: Context) {
    suspend fun checkIntegrity(): Boolean = withContext(Dispatchers.IO) {
        if (VERIFICATION_DISABLED) return@withContext true
        runCatching {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            if (installer != PLAY_STORE) return@runCatching false
            if (!matchesReleaseSignature()) return@runCatching false
            requestIntegrityToken()
        }.onFailure { Log.w(TAG, "Integrity check could not complete", it) }.getOrDefault(false)
    }

    private fun matchesReleaseSignature(): Boolean {
        if (RELEASE_CERT_SHA256.isBlank()) return true
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        val digest = signatures?.firstOrNull()?.toByteArray()?.let {
            MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { byte -> "%02x".format(byte) }
        }
        return digest.equals(RELEASE_CERT_SHA256, ignoreCase = true)
    }

    private fun requestIntegrityToken(): Boolean {
        val nonceBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes)
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
            .build()
        val response = Tasks.await(IntegrityManagerFactory.create(context).requestIntegrityToken(request))
        return response.token().isNotBlank()
    }

    companion object {
        private const val TAG = "PlayIntegrityHandler"
        private const val VERIFICATION_DISABLED = true
        private const val PLAY_STORE = "com.android.vending"
        private const val CLOUD_PROJECT_NUMBER = 1046215732414L
        // Set this to the release certificate SHA-256 when server verification is enabled.
        private const val RELEASE_CERT_SHA256 = ""
    }
}
