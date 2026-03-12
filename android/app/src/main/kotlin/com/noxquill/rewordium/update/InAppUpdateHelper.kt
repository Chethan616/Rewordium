package com.noxquill.rewordium.update

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Helper for Google Play In-App Updates API.
 * Supports both FLEXIBLE (background download) and IMMEDIATE (blocking) flows.
 */
object InAppUpdateHelper {
    private const val TAG = "InAppUpdateHelper"
    private const val FLEXIBLE_UPDATE_REQUEST_CODE = 1001
    private const val IMMEDIATE_UPDATE_REQUEST_CODE = 1002

    /**
     * Callback for when a flexible update has been downloaded
     * and is ready to install.
     */
    var onFlexibleUpdateReady: (() -> Unit)? = null

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Log.d(TAG, "Flexible update downloaded, ready to install")
            onFlexibleUpdateReady?.invoke()
        }
    }

    /**
     * Check for available updates and start the appropriate flow.
     *
     * @param activity The current activity
     * @param preferImmediate If true, prefer IMMEDIATE update when available.
     *                        Otherwise, use FLEXIBLE for optional updates.
     */
    fun checkForUpdate(activity: Activity, preferImmediate: Boolean = false) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                when (appUpdateInfo.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        if (preferImmediate && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                            startImmediateUpdate(activity, appUpdateInfo)
                        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                            startFlexibleUpdate(activity, appUpdateInfo)
                        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                            startImmediateUpdate(activity, appUpdateInfo)
                        }
                    }
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        // Resume a stalled immediate update
                        if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                            startImmediateUpdate(activity, appUpdateInfo)
                        }
                    }
                    else -> {
                        Log.d(TAG, "No update available")
                    }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to check for updates", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
    }

    /**
     * Complete a flexible update that has been downloaded.
     * Call this when the user agrees to restart the app.
     */
    fun completeFlexibleUpdate(activity: Activity) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error completing flexible update", e)
        }
    }

    /**
     * Resume an immediate update if one was in progress (e.g., after onResume).
     */
    fun resumeUpdateIfNeeded(activity: Activity) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                // Resume stalled immediate update
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        startImmediateUpdate(activity, appUpdateInfo)
                    }
                }
                // Complete downloaded flexible update
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    onFlexibleUpdateReady?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming update", e)
        }
    }

    private fun startFlexibleUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            appUpdateManager.registerListener(installStateListener)
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                FLEXIBLE_UPDATE_REQUEST_CODE
            )
            Log.d(TAG, "Started flexible update flow")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting flexible update", e)
        }
    }

    private fun startImmediateUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                IMMEDIATE_UPDATE_REQUEST_CODE
            )
            Log.d(TAG, "Started immediate update flow")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting immediate update", e)
        }
    }
}
