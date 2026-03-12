package com.noxquill.rewordium.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Reusable helper for Google Play In-App Review.
 *
 * Best practices followed:
 * - Trigger after a positive user moment (e.g. successful AI text generation).
 * - Counter-gated: only shows after [GENERATION_THRESHOLD] successful generations.
 * - Rate-limited: waits at least [COOLDOWN_MS] between prompts.
 * - Never crashes — all errors are silently caught.
 * - Never blocks UI — launch is fire-and-forget.
 */
object InAppReviewHelper {

    private const val TAG = "InAppReviewHelper"
    private const val PREFS_NAME = "in_app_review_prefs"
    private const val KEY_GENERATION_COUNT = "successful_generations"
    private const val KEY_LAST_PROMPT_TIME = "last_review_prompt_time"

    /** Number of successful AI generations before first review prompt. */
    private const val GENERATION_THRESHOLD = 5

    /** Minimum time between review prompts (30 days in ms). */
    private const val COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Call after every successful AI text generation.
     * Increments the counter and triggers the review flow when conditions are met.
     */
    fun onSuccessfulGeneration(activity: Activity) {
        try {
            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_GENERATION_COUNT, 0) + 1
            prefs.edit().putInt(KEY_GENERATION_COUNT, count).apply()

            if (shouldPrompt(prefs)) {
                showInAppReview(activity)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error tracking generation for review", e)
        }
    }

    /**
     * Directly launch the In-App Review flow (e.g. from a "Rate Us" button).
     * Bypasses counter/cooldown checks — Google still applies its own quotas.
     */
    fun showInAppReview(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()

            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        // Review flow finished (user may or may not have reviewed).
                        // Record the timestamp so we respect the cooldown.
                        Log.d(TAG, "Review flow completed")
                        recordPromptTime(activity)
                    }
                } else {
                    Log.w(TAG, "Review request failed", task.exception)
                }
            }
        } catch (e: Exception) {
            // Never crash — review is non-critical.
            Log.w(TAG, "Failed to launch in-app review", e)
        }
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private fun shouldPrompt(prefs: android.content.SharedPreferences): Boolean {
        val count = prefs.getInt(KEY_GENERATION_COUNT, 0)
        if (count < GENERATION_THRESHOLD) return false

        val lastPrompt = prefs.getLong(KEY_LAST_PROMPT_TIME, 0L)
        val now = System.currentTimeMillis()
        return (now - lastPrompt) >= COOLDOWN_MS
    }

    private fun recordPromptTime(activity: Activity) {
        try {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record review prompt time", e)
        }
    }
}
