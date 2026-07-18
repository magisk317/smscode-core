package io.github.magisk317.smscode.runtime.common.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.smscode.runtime.contract.notification.NotificationPlatformBridge

/** Shared Android implementation for notification channel and delivery checks. */
class AndroidNotificationPlatformBridge(
    private val recoverDeletedChannels: Boolean,
) : NotificationPlatformBridge {
    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (recoverDeletedChannels) {
            val existing = runCatching { manager.getNotificationChannel(channelId) }.getOrNull()
            if (existing == null) {
                runCatching { manager.deleteNotificationChannel(channelId) }
            }
        }
        manager.createNotificationChannel(NotificationChannel(channelId, channelName, importance))
    }

    override fun inspectDelivery(
        context: Context,
        channelId: String,
    ): NotificationDeliveryDiagnostics {
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationDeliveryDiagnostics(
            notificationsEnabled = runCatching { manager?.areNotificationsEnabled() ?: false }
                .getOrDefault(false),
            postNotificationsGranted = hasPostNotificationsPermission(context),
            channelImportance = runCatching { manager?.getNotificationChannel(channelId)?.importance }
                .getOrNull(),
        )
    }

    override fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
