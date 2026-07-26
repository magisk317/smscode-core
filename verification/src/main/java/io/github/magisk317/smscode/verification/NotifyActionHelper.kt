package io.github.magisk317.smscode.verification

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.smscode.runtime.contract.notification.NotificationPlatformBridge
import io.github.magisk317.xposed.logging.MagiskOtel

class NotifyActionHelper<M : SmsMessage, R>(
    private val pluginContext: Context,
    private val smsMsg: M,
    private val enabled: Boolean,
    private val autoCancelEnabledProvider: (Context) -> Boolean,
    private val retentionTimeMsProvider: (Context) -> Long,
    private val tokenProvider: (Context) -> String? = { null },
    private val channelId: String = "",
    private val channelInitializer: (Context) -> Unit = {},
    private val diagnostics: (Context) -> DeliveryDiagnostics = { DeliveryDiagnostics(canPost = true) },
    private val notificationBridge: NotificationPlatformBridge? = null,
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
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
            )
            if (reason != null) {
                attrs["reason"] = reason
            }
            MagiskOtel.event(
                name = "notify.owned",
                attributes = attrs,
                statusOk = statusOk,
            )
        }

        if (!enabled) {
            emit(result = "skip", reason = "disabled")
            return null
        }

        // Use bridge for channel initialization if available, otherwise fall back to lambda
        if (notificationBridge != null && channelId.isNotEmpty()) {
            // Channel initialization is handled by the bridge's createNotificationChannel
            // which is called by the caller before posting
        } else {
            channelInitializer(pluginContext)
        }

        // Use bridge for diagnostics if available, otherwise fall back to lambda
        val diag = if (notificationBridge != null && channelId.isNotEmpty()) {
            val bridgeDiag = notificationBridge.inspectDelivery(pluginContext, channelId)
            DeliveryDiagnostics(
                canPost = bridgeDiag.canPost,
                summary = bridgeDiag.summary(),
            )
        } else {
            diagnostics(pluginContext)
        }

        if (!diag.canPost) {
            XLog.w(
                "Code notification unavailable: %s",
                diag.summary,
            )
            emit(result = "skip", reason = "cannot_post")
            return null
        }
        val posted = notifier(
            AppOwnedNotificationRequest(
                smsMsg = smsMsg,
                notificationId = smsMsg.hashCode(),
                autoCancelEnabled = autoCancelEnabledProvider(pluginContext),
                retentionTimeMs = retentionTimeMsProvider(pluginContext),
                token = tokenProvider(pluginContext),
            ),
        )
        if (posted == null) {
            emit(result = "skip", reason = "notifier_null")
        } else {
            emit(result = "ok")
        }
        return posted
    }
}
