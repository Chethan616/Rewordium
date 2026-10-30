package com.noxquill.rewordium.data

/** Rewordium – Smart Keyboard's public web pages and support contact, in one place. */
object RewordiumLinks {
    // This app's own pages, kept apart from the earlier Rewordium app's pages on the same site.
    private const val SITE = "https://chethan616.github.io/Rewordium/keyboard"

    const val WEBSITE = "$SITE/index.html"
    const val PRIVACY_POLICY = "$SITE/privacy-policy.html"
    const val TERMS = "$SITE/terms.html"
    const val DATA_SAFETY = "$SITE/data-safety.html"
    /** Google Play requires a web route to account deletion for people who can't use the app. */
    const val DELETE_ACCOUNT = "$SITE/delete-data.html"
    const val SUPPORT_EMAIL = "noxquilltech@gmail.com"
}
