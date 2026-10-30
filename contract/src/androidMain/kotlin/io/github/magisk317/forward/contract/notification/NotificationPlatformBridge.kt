package io.github.magisk317.forward.contract.notification

import android.content.Context
import io.github.magisk317.xposed.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.xposed.runtime.contract.notification.NotificationPlatformBridge

/**
 * Host-app notification bridge marker on top of the shared kit contract.
 * The marker exists so install points can require the product-specific bridge
 * without accepting a bare [NotificationPlatformBridge].
 */
interface RelayNotificationPlatformBridge : NotificationPlatformBridge

/** No-op bridge used until the real Android bridge is installed. */
object NoopRelayNotificationPlatformBridge : RelayNotificationPlatformBridge {
    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) = Unit

    override fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics =
        NotificationDeliveryDiagnostics(
            notificationsEnabled = false,
            postNotificationsGranted = false,
            channelImportance = null,
        )

    override fun hasPostNotificationsPermission(context: Context): Boolean = false
}
