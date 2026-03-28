package io.github.magisk317.smscode.verification

import android.content.Context
import io.github.magisk317.smscode.xposed.utils.XLog

class NotifyActionHelper<M : SmsMessage, R>(
    private val pluginContext: Context,
    private val smsMsg: M,
    private val enabled: Boolean,
    private val ownerReader: (Context) -> String,
    private val appOwnedValue: String,
    private val phoneOwnedValue: String,
    private val autoCancelEnabledProvider: (Context) -> Boolean,
    private val retentionTimeMsProvider: (Context) -> Long,
    private val tokenProvider: (Context) -> String? = { null },
    private val appOwnedChannelInitializer: (Context) -> Unit = {},
    private val phoneOwnedChannelInitializer: (Context) -> Unit = {},
    private val appOwnedDiagnostics: (Context) -> DeliveryDiagnostics = { DeliveryDiagnostics(canPost = true) },
    private val appOwnedNotifier: (AppOwnedNotificationRequest<M>) -> R?,
    private val phoneOwnedNotifier: (PhoneOwnedNotificationRequest<M>) -> R?,
) {
    data class DeliveryDiagnostics(
        val canPost: Boolean,
        val summary: String = "",
    )

    data class AppOwnedNotificationRequest<M : SmsMessage>(
        val smsMsg: M,
        val notificationId: Int,
        val autoCancelEnabled: Boolean,
        val retentionTimeMs: Long,
        val token: String?,
    )

    data class PhoneOwnedNotificationRequest<M : SmsMessage>(
        val smsMsg: M,
        val notificationId: Int,
        val autoCancelEnabled: Boolean,
        val retentionTimeMs: Long,
    )

    fun run(): R? {
        if (!enabled) return null
        return when (ownerReader(pluginContext)) {
            phoneOwnedValue -> showPhoneOwnedNotification()
            appOwnedValue -> showAppOwnedNotification()
            else -> {
                XLog.w("Skip code notification: owner not selected")
                null
            }
        }
    }

    private fun showAppOwnedNotification(): R? {
        appOwnedChannelInitializer(pluginContext)
        val diagnostics = appOwnedDiagnostics(pluginContext)
        if (!diagnostics.canPost) {
            XLog.w(
                "App-owned code notification unavailable, fallback to phone-owned: %s",
                diagnostics.summary,
            )
            return showPhoneOwnedNotification()
        }
        return appOwnedNotifier(
            AppOwnedNotificationRequest(
                smsMsg = smsMsg,
                notificationId = smsMsg.hashCode(),
                autoCancelEnabled = autoCancelEnabledProvider(pluginContext),
                retentionTimeMs = retentionTimeMsProvider(pluginContext),
                token = tokenProvider(pluginContext),
            ),
        )
    }

    private fun showPhoneOwnedNotification(): R? {
        phoneOwnedChannelInitializer(pluginContext)
        return phoneOwnedNotifier(
            PhoneOwnedNotificationRequest(
                smsMsg = smsMsg,
                notificationId = smsMsg.hashCode(),
                autoCancelEnabled = autoCancelEnabledProvider(pluginContext),
                retentionTimeMs = retentionTimeMsProvider(pluginContext),
            ),
        )
    }
}
