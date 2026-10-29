package com.noxquill.rewordium.data

/** Rewordium's public web pages and support contact, in one place. */
object RewordiumLinks {
    private const val SITE = "https://chethan616.github.io/Rewordium"

    const val WEBSITE = "$SITE/"
    const val PRIVACY_POLICY = "$SITE/pages/privacy-policy.html"
    const val TERMS = "$SITE/pages/terms.html"
    const val DATA_SAFETY = "$SITE/pages/data-safety.html"
    /** Google Play requires a web route to account deletion for people who can't use the app. */
    const val DELETE_ACCOUNT = "$SITE/pages/delete-account.html"
    const val SUPPORT_EMAIL = "noxquilltech@gmail.com"
}
