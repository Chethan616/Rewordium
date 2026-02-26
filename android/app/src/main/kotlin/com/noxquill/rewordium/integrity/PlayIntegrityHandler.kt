package com.noxquill.rewordium.integrity

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.model.IntegrityErrorCode
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Base64
import org.json.JSONObject

/**
 * Handler for Google Play Integrity API
 * Provides app and device integrity verification
 */
class PlayIntegrityHandler(private val context: Context) : MethodChannel.MethodCallHandler {
    
    companion object {
        private const val TAG = "PlayIntegrityHandler"
        private const val CHANNEL_NAME = "com.noxquill.rewordium/integrity"
        
        // Your Cloud Project Number (Project ID number from Google Cloud Console)
        // You can find this in Google Cloud Console > Project Settings
        private const val CLOUD_PROJECT_NUMBER = 1046215732414L
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
            Log.d(TAG, "Initializing Play Integrity API")
            // The manager is initialized lazily, so just return success
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Play Integrity", e)
            result.error("INIT_ERROR", "Failed to initialize: ${e.message}", null)
        }
    }
    
    /**
     * Perform a basic integrity check
     */
    private fun checkIntegrity(result: MethodChannel.Result) {
        scope.launch {
            try {
                Log.d(TAG, "Starting integrity check")
                
                // Generate a nonce (a random string) for this request
                // In production, you should generate this server-side and pass it to the app
                val nonce = generateNonce()
                
                // Create the integrity token request
                val integrityTokenRequest = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                    .build()
                
                // Request the integrity token
                val integrityTokenResponse = integrityManager
                    .requestIntegrityToken(integrityTokenRequest)
                    .await()
                
                val token = integrityTokenResponse.token()
                Log.d(TAG, "Integrity token received (length: ${token.length})")
                
                // For basic check, just verify we got a token
                // In production, you should send this token to your backend for verification
                val isValid = token.isNotEmpty()
                
                result.success(isValid)
            } catch (e: Exception) {
                Log.e(TAG, "Integrity check failed", e)
                handleIntegrityError(e, result)
            }
        }
    }
    
    /**
     * Get detailed integrity verdict
     * This should ideally be done on your backend server
     */
    private fun getIntegrityVerdict(result: MethodChannel.Result) {
        scope.launch {
            try {
                Log.d(TAG, "Getting integrity verdict")
                
                val nonce = generateNonce()
                
                val integrityTokenRequest = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                    .build()
                
                val integrityTokenResponse = integrityManager
                    .requestIntegrityToken(integrityTokenRequest)
                    .await()
                
                val token = integrityTokenResponse.token()
                
                // IMPORTANT: In production, send this token to your backend
                // Your backend should verify it with Google's API
                // For now, we just return basic info
                
                val verdict = hashMapOf(
                    "hasToken" to true,
                    "tokenLength" to token.length,
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "Token received. Verify on backend server."
                )
                
                result.success(verdict)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get integrity verdict", e)
                handleIntegrityError(e, result)
            }
        }
    }
    
    /**
     * Check if device meets basic security requirements
     */
    private fun isDeviceSecure(result: MethodChannel.Result) {
        scope.launch {
            try {
                // Perform a quick integrity check
                val nonce = generateNonce()
                
                val integrityTokenRequest = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                    .build()
                
                val integrityTokenResponse = integrityManager
                    .requestIntegrityToken(integrityTokenRequest)
                    .await()
                
                val isSecure = integrityTokenResponse.token().isNotEmpty()
                result.success(isSecure)
            } catch (e: Exception) {
                Log.e(TAG, "Device security check failed", e)
                // Return false for security check failures
                result.success(false)
            }
        }
    }
    
    /**
     * Generate a nonce for the integrity request
     * In production, this should come from your backend server
     */
    private fun generateNonce(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(randomBytes)
        } else {
            android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
        }
    }
    
    /**
     * Handle integrity API errors
     */
    private fun handleIntegrityError(e: Exception, result: MethodChannel.Result) {
        val errorMessage = when {
            e.message?.contains("API_NOT_AVAILABLE") == true -> 
                "Play Integrity API not available on this device"
            e.message?.contains("NETWORK_ERROR") == true -> 
                "Network error during integrity check"
            e.message?.contains("INVALID_REQUEST") == true -> 
                "Invalid integrity request. Check configuration."
            else -> 
                "Integrity check error: ${e.message}"
        }
        
        result.error("INTEGRITY_ERROR", errorMessage, null)
    }
}
