package com.noxquill.rewordium.keyboard.app

import com.noxquill.rewordium.keyboard.BuildConfig

/**
 * What this release offers, in one place. Both switches come from `gradle.properties`
 * (`rewordium.requireSignIn`, `rewordium.subscriptionsEnabled`), so the app and the keyboard always agree.
 */
object ReleaseFlags {
    /**
     * Whether people must sign in before using the app. Off: the keyboard and app work straight after
     * install; the app opens a silent per-install session for the built-in AI allowance and community
     * themes, and signing in stays available as an option.
     */
    val requireSignIn: Boolean = BuildConfig.REQUIRE_SIGN_IN

    /**
     * Whether Pro subscriptions are on sale. Off: no plans, paywalls or upgrade prompts, and Google Play
     * billing is never contacted. People who already pay keep what they paid for and can still manage or
     * cancel it in Google Play.
     */
    val subscriptionsEnabled: Boolean = BuildConfig.SUBSCRIPTIONS_ENABLED

    /** The built-in AI allowance each account (or install) starts with; it doesn't refill. */
    const val FREE_AI_USES = 20
}
