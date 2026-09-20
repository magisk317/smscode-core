package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage

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
    /**
     * Explicit deliverability decision. When supplied it always wins, because the caller is the
     * only layer that knows which package the notification is posted as.
     */
    private val diagnostics: ((Context) -> DeliveryDiagnostics)? = null,
    /**
     * Context the bridge-based inspection resolves against.
     *
     * Both the permission lookup and the channel lookup resolve against the package of the posting
     * process, so a bridge inspection for a notification posted by another package must be given
     * that package's context. Defaulting to [pluginContext] is only correct for the app-owned path;
     * the phone-owned path has to pass the phone context, otherwise the inspection reports the
     * module app's channel state for a notification that package never posts.
     */
    private val diagnosticsContext: Context? = null,
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

        // Precedence matters here. An explicit diagnostics lambda is authoritative because the
        // caller resolves it against the package it actually posts as. The bridge path is a
        // convenience that has to be told which package to inspect; without that it would silently
        // inspect the module app while the phone-owned path posts as the phone app.
        val explicitDiagnostics = diagnostics
        val diag = when {
            explicitDiagnostics != null -> explicitDiagnostics(pluginContext)
            notificationBridge != null && channelId.isNotEmpty() -> {
                val bridgeDiag = notificationBridge.inspectDelivery(
                    diagnosticsContext ?: pluginContext,
                    channelId,
                )
                DeliveryDiagnostics(
                    canPost = bridgeDiag.canPost,
                    summary = bridgeDiag.summary(),
                )
            }
            else -> DeliveryDiagnostics(canPost = true)
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
                notificationId = NotificationDispatchGuard.stableNotificationId(smsMsg),
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
