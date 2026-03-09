package com.noxquill.rewordium.service

import android.content.Context
import android.content.SharedPreferences

class CreditManager(context: Context) {

    companion object {
        const val USER_STATUS_PREFS_NAME = "rewordium_user_status"
        const val KEY_IS_PRO = "is_pro_user"
        const val KEY_CREDITS = "user_credits"
        // <-- ADDED: New key to differentiate between a guest and a logged-in free user.
        const val KEY_IS_LOGGED_IN = "is_logged_in_user"
        private const val DEFAULT_CREDITS = 0
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(USER_STATUS_PREFS_NAME, Context.MODE_PRIVATE)
        
    // <-- ADDED: New public function to check the user's login status. -->
    /**
     * Checks if a user is currently logged into the app.
     * @return `true` if the user is logged in, `false` otherwise (guest user).
     */
    fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun isProUser(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PRO, false)
    }

    fun getCredits(): Int {
        return sharedPreferences.getInt(KEY_CREDITS, DEFAULT_CREDITS)
    }

    fun canPerformAction(): Boolean {
        return isProUser() || getCredits() > 0
    }

    fun consumeCredit() {
        // NOTE: Credit consumption is now handled by Flutter/Firebase.
        // This method is kept for compatibility but doesn't actually consume credits.
        // The actual consumption happens in Flutter and gets synced back to Android via broadcast.
    }
}