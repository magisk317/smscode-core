package io.github.magisk317.smscode.runtime.contract.notification

import android.app.NotificationManager

/**
 * Diagnostics for notification delivery capability.
 * Used by [NotificationPlatformBridge.inspectDelivery] to report whether
 * notifications can be posted on the current device/process.
 */
data class NotificationDeliveryDiagnostics(
    val notificationsEnabled: Boolean,
    val postNotificationsGranted: Boolean,
    val channelImportance: Int?,
) {
    val canPost: Boolean
        get() = notificationsEnabled &&
            postNotificationsGranted &&
            channelImportance != NotificationManager.IMPORTANCE_NONE

    fun summary(): String {
        return "enabled=$notificationsEnabled permission=$postNotificationsGranted channel=${importanceLabel(channelImportance)}"
    }

    companion object {
        fun importanceLabel(importance: Int?): String {
            return when (importance) {
                null -> "missing"
                NotificationManager.IMPORTANCE_NONE -> "none"
                NotificationManager.IMPORTANCE_MIN -> "min"
                NotificationManager.IMPORTANCE_LOW -> "low"
                NotificationManager.IMPORTANCE_DEFAULT -> "default"
                NotificationManager.IMPORTANCE_HIGH -> "high"
                NotificationManager.IMPORTANCE_MAX -> "max"
                else -> importance.toString()
            }
        }
    }
}
