package io.github.magisk317.smscode.verification

import android.content.Context
import io.github.magisk317.smscode.xposed.utils.XLog

class NotifyActionHelper<M : SmsMessage, R>(
    private val pluginContext: Context,
    private val smsMsg: M,
    private val enabled: Boolean,
    private val autoCancelEnabledProvider: (Context) -> Boolean,
    private val retentionTimeMsProvider: (Context) -> Long,
    private val tokenProvider: (Context) -> String? = { null },
    private val channelInitializer: (Context) -> Unit = {},
    private val diagnostics: (Context) -> DeliveryDiagnostics = { DeliveryDiagnostics(canPost = true) },
    private val notifier: (AppOwnedNotificationRequest<M>) -> R?,
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

    fun run(): R? {
        if (!enabled) return null
        channelInitializer(pluginContext)
        val diag = diagnostics(pluginContext)
        if (!diag.canPost) {
            XLog.w(
                "Code notification unavailable: %s",
                diag.summary,
            )
            return null
        }
        return notifier(
            AppOwnedNotificationRequest(
                smsMsg = smsMsg,
                notificationId = smsMsg.hashCode(),
                autoCancelEnabled = autoCancelEnabledProvider(pluginContext),
                retentionTimeMs = retentionTimeMsProvider(pluginContext),
                token = tokenProvider(pluginContext),
            ),
        )
    }
}
