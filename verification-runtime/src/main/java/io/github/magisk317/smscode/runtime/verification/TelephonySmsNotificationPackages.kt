package io.github.magisk317.smscode.runtime.verification

/**
 * Packages that legitimately post "new SMS" notifications on behalf of the default SMS app.
 *
 * The notification-listener based mark-as-read path must only act on notifications posted by
 * telephony/SMS components; matching anything else would click a "mark as read" button inside
 * an unrelated conversation app. The list is intentionally conservative: add a package only
 * when a device dump or a user report proves that package posts SMS notifications.
 *
 * Vendor-specific SMS apps that are NOT listed here fall through to the generic DB-based
 * mark-as-read path; extend this list per [docs/vendor-sms-apps.md] as evidence arrives.
 */
object TelephonySmsNotificationPackages {

    /** AOSP/Google Messages — the only family whose notifications carry the mark-as-read semantic action so far. */
    const val GOOGLE_MESSAGING = "com.google.android.apps.messaging"

    /** Stock AOSP Messaging. */
    const val AOSP_MESSAGING = "com.android.messaging"

    /** MIUI/HyperOS SMS (bugle-derived, own conversation DB, no semantic read action). */
    const val MIUI_MMS = "com.android.mms"

    /** Samsung One UI messages. */
    const val SAMSUNG_MESSAGING = "com.samsung.android.messaging"

    /** Telephony components that may post fallback notifications on some ROMs. */
    const val AOSP_PHONE = "com.android.phone"
    const val AOSP_TELEPHONY_PROVIDER = "com.android.providers.telephony"
    const val MIUI_PHONE = "com.xiaomi.phone"

    val POSTERS: Set<String> = setOf(
        GOOGLE_MESSAGING,
        AOSP_MESSAGING,
        MIUI_MMS,
        SAMSUNG_MESSAGING,
        AOSP_PHONE,
        AOSP_TELEPHONY_PROVIDER,
        MIUI_PHONE,
    )
}
