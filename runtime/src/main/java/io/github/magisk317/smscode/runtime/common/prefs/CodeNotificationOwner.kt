package io.github.magisk317.smscode.runtime.common.prefs

/**
 * Normalizes the code-notification owner preference to `app` or `phone`;
 * anything else falls back to [DEFAULT].
 */
object CodeNotificationOwner {
    const val APP = "app"
    const val PHONE = "phone"
    const val DEFAULT = PHONE

    fun normalize(value: String?): String {
        return when (value) {
            APP -> APP
            PHONE -> PHONE
            else -> DEFAULT
        }
    }
}
