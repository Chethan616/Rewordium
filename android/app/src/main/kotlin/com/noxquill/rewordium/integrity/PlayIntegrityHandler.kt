package com.noxquill.rewordium.integrity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Base64

/**
 * Handler for Google Play Integrity API
 * 
 * Since we're on Firebase Spark (free) plan with no backend,
 * we use CLIENT-SIDE checks to block sideloaded/modded APKs:
 * 
 * 1. INSTALLER CHECK: Was the app installed from Play Store?
 *    - Play Store installer = "com.android.vending"
 *    - WhatsApp/ADB/sideload = other values or null
 * 
 * 2. SIGNATURE CHECK: Is the APK signed with our release key?
 *    - Modded APKs will have a different signature
 * 
 * 3. PLAY INTEGRITY TOKEN: Can we get a valid token?
 *    - Additional signal from Google Play services
 * 
 * This effectively blocks:
 * ❌ APKs shared via WhatsApp
 * ❌ APKs installed via ADB
 * ❌ APKs downloaded from random websites
 * ❌ Re-signed/modded APKs
 */
class PlayIntegrityHandler(private val context: Context) : MethodChannel.MethodCallHandler {
    
    companion object {
        private const val TAG = "PlayIntegrityHandler"
        private const val CHANNEL_NAME = "com.noxquill.rewordium/integrity"
        private const val PACKAGE_NAME = "com.noxquill.rewordium"
        
        // Your Cloud Project Number from Google Cloud Console
        private const val CLOUD_PROJECT_NUMBER = 1046215732414L
        
        // Play Store installer package name
        private const val PLAY_STORE_INSTALLER = "com.android.vending"
        // Google Play app (alternative installer name)
        private const val GOOGLE_PLAY_INSTALLER = "com.google.android.packageinstaller"
        
        // SHA-256 fingerprint of your RELEASE signing certificate
        // To get this, run: 
        //   keytool -list -v -keystore upload-keystore.jks -alias upload
        // Then copy the SHA-256 fingerprint (remove colons, lowercase)
        // Or check Google Play Console > Release > Setup > App Integrity > App signing tab
        //
        // IMPORTANT: Replace this with YOUR actual release certificate SHA-256 fingerprint!
        // Format: lowercase hex without colons
        // Example: "a1b2c3d4e5f6..." 
        // Leave empty to skip signature check (NOT recommended for production)
        private const val RELEASE_CERT_SHA256 = ""
    }
    
    private val integrityManager: IntegrityManager by lazy {
        IntegrityManagerFactory.create(context)
    }
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialize" -> {
                initialize(result)
            }
            "checkIntegrity" -> {
                checkIntegrity(result)
            }
            "getIntegrityVerdict" -> {
                getIntegrityVerdict(result)
            }
            "isDeviceSecure" -> {
                isDeviceSecure(result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }
    
    /**
     * Initialize the Play Integrity API
     */
    private fun initialize(result: MethodChannel.Result) {
        try {
            Log.d(TAG, "Initializing Play Integrity API (client-side mode)")
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Play Integrity", e)
            result.error("INIT_ERROR", "Failed to initialize: ${e.message}", null)
        }
    }
    
    /**
     * Perform comprehensive integrity check (client-side)
     * Checks: installer source + signature + Play Integrity token
     */
    private fun checkIntegrity(result: MethodChannel.Result) {
        scope.launch {
            try {
                Log.d(TAG, "=== Starting Client-Side Integrity Check ===")
                
                // CHECK 1: Installer Source (most important for blocking sideloads)
                val installerCheck = checkInstallerSource()
                Log.d(TAG, "Installer check: ${if (installerCheck) "✅ PASS" else "❌ FAIL"}")
                
                if (!installerCheck) {
                    Log.w(TAG, "⛔ APP NOT INSTALLED FROM PLAY STORE - BLOCKING")
                    result.success(false)
                    return@launch
                }
                
                // CHECK 2: App Signature (blocks modded/re-signed APKs)
                val signatureCheck = checkAppSignature()
                Log.d(TAG, "Signature check: ${if (signatureCheck) "✅ PASS" else "❌ FAIL"}")
                
                if (!signatureCheck) {
                    Log.w(TAG, "⛔ APP SIGNATURE MISMATCH - BLOCKING (possible modded APK)")
                    result.success(false)
                    return@launch
                }
                
                // CHECK 3: Play Integrity Token (additional verification)
                val tokenCheck = checkPlayIntegrityToken()
                Log.d(TAG, "Play Integrity token check: ${if (tokenCheck) "✅ PASS" else "⚠️ FAIL (non-blocking)"}")
                
                // Token check is a bonus - installer + signature are the main gates
                // We don't block on token failure alone since it can fail for legitimate
                // reasons (no internet, Google Play services issue, etc.)
                
                Log.d(TAG, "=== All critical checks passed ✅ ===")
                result.success(true)
                
            } catch (e: Exception) {
                Log.e(TAG, "Integrity check error", e)
                // If we can't even check, block to be safe
                result.success(false)
            }
        }
    }
    
    /**
     * Check if the app was installed from Play Store
     * This is the PRIMARY defense against sideloading
     */
    private fun checkInstallerSource(): Boolean {
        try {
            val packageManager = context.packageManager
            val installerPackageName: String?
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                val installSourceInfo = packageManager.getInstallSourceInfo(PACKAGE_NAME)
                installerPackageName = installSourceInfo.installingPackageName
                Log.d(TAG, "Install source (API 30+):")
                Log.d(TAG, "  Initiating: ${installSourceInfo.initiatingPackageName}")
                Log.d(TAG, "  Installing: ${installSourceInfo.installingPackageName}")
                Log.d(TAG, "  Originating: ${installSourceInfo.originatingPackageName}")
            } else {
                // Android 10 and below
                @Suppress("DEPRECATION")
                installerPackageName = packageManager.getInstallerPackageName(PACKAGE_NAME)
                Log.d(TAG, "Installer package (legacy): $installerPackageName")
            }
            
            // Check if installed from Play Store
            val isFromPlayStore = installerPackageName == PLAY_STORE_INSTALLER ||
                                   installerPackageName == GOOGLE_PLAY_INSTALLER
            
            if (!isFromPlayStore) {
                Log.w(TAG, "⛔ Not installed from Play Store! Installer: '$installerPackageName'")
                Log.w(TAG, "  Expected: '$PLAY_STORE_INSTALLER'")
                
                when (installerPackageName) {
                    null -> Log.w(TAG, "  → Likely ADB install or direct APK install")
                    "com.whatsapp" -> Log.w(TAG, "  → Installed via WhatsApp")
                    "com.google.android.apps.nbu.files" -> Log.w(TAG, "  → Installed via Files by Google")
                    "com.android.shell" -> Log.w(TAG, "  → Installed via ADB shell")
                    else -> Log.w(TAG, "  → Installed via unknown source: $installerPackageName")
                }
            }
            
            return isFromPlayStore
            
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found: $PACKAGE_NAME", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking installer source", e)
            return false
        }
    }
    
    /**
     * Check if the app is signed with our release certificate
     * This blocks modded APKs that were re-signed with a different key
     */
    private fun checkAppSignature(): Boolean {
        // Skip check if no fingerprint configured
        if (RELEASE_CERT_SHA256.isEmpty()) {
            Log.d(TAG, "Signature check skipped (no fingerprint configured)")
            Log.d(TAG, "⚠️ Set RELEASE_CERT_SHA256 for production!")
            
            // Log the current signature for the developer to copy
            logCurrentSignature()
            return true
        }
        
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    PACKAGE_NAME,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    PACKAGE_NAME,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            if (signatures.isNullOrEmpty()) {
                Log.e(TAG, "No signatures found!")
                return false
            }
            
            for (signature in signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signature.toByteArray())
                val hexString = digest.joinToString("") { "%02x".format(it) }
                
                Log.d(TAG, "App certificate SHA-256: $hexString")
                
                if (hexString == RELEASE_CERT_SHA256) {
                    Log.d(TAG, "✅ Signature matches release certificate")
                    return true
                }
            }
            
            Log.w(TAG, "⛔ Signature does NOT match expected release certificate!")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app signature", e)
            return false
        }
    }
    
    /**
     * Log the current signing certificate for developer reference
     */
    private fun logCurrentSignature() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    PACKAGE_NAME,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    PACKAGE_NAME,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signature.toByteArray())
                val hexString = digest.joinToString("") { "%02x".format(it) }
                Log.d(TAG, "📋 Current cert SHA-256 (copy this to RELEASE_CERT_SHA256): $hexString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging signature", e)
        }
    }
    
    /**
     * Request a Play Integrity token as an additional signal
     * This verifies that Google Play Services are present and functional
     */
    private suspend fun checkPlayIntegrityToken(): Boolean {
        return try {
            val nonce = generateNonce()
            
            val integrityTokenRequest = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .build()
            
            val integrityTokenResponse = integrityManager
                .requestIntegrityToken(integrityTokenRequest)
                .await()
            
            val token = integrityTokenResponse.token()
            val hasToken = token.isNotEmpty()
            
            Log.d(TAG, "Play Integrity token received: ${if (hasToken) "yes (${token.length} chars)" else "empty"}")
            hasToken
            
        } catch (e: Exception) {
            Log.w(TAG, "Play Integrity token request failed: ${e.message}")
            false
        }
    }
    
    /**
     * Get detailed integrity verdict (client-side version)
     */
    private fun getIntegrityVerdict(result: MethodChannel.Result) {
        scope.launch {
            try {
                val installerSource = getInstallerPackageName()
                val isFromPlayStore = installerSource == PLAY_STORE_INSTALLER || 
                                      installerSource == GOOGLE_PLAY_INSTALLER
                val signatureValid = checkAppSignature()
                val hasToken = checkPlayIntegrityToken()
                
                val isAllowed = isFromPlayStore && signatureValid
                
                val reasons = mutableListOf<String>()
                if (!isFromPlayStore) reasons.add("Not installed from Play Store (installer: $installerSource)")
                if (!signatureValid) reasons.add("App signature mismatch (possible modded APK)")
                if (!hasToken) reasons.add("Play Integrity token unavailable")
                
                val verdict = hashMapOf(
                    "allowed" to isAllowed,
                    "installerPackage" to (installerSource ?: "unknown"),
                    "isFromPlayStore" to isFromPlayStore,
                    "signatureValid" to signatureValid,
                    "playIntegrityToken" to hasToken,
                    "reason" to if (reasons.isEmpty()) "All checks passed" else reasons.joinToString("; "),
                    "timestamp" to System.currentTimeMillis()
                )
                
                result.success(verdict)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting verdict", e)
                result.success(hashMapOf(
                    "allowed" to false,
                    "reason" to "Error: ${e.message}",
                    "timestamp" to System.currentTimeMillis()
                ))
            }
        }
    }
    
    /**
     * Check if device is secure
     */
    private fun isDeviceSecure(result: MethodChannel.Result) {
        checkIntegrity(result)
    }
    
    /**
     * Get the installer package name
     */
    private fun getInstallerPackageName(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(PACKAGE_NAME).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(PACKAGE_NAME)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installer package name", e)
            null
        }
    }
    
    /**
     * Generate a nonce for the integrity request
     */
    private fun generateNonce(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(randomBytes)
        } else {
            android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
        }
    }
}
