package io.github.magisk317.smscode.verification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import io.github.magisk317.xposed.logging.MagiskOtel

object CodeNotificationActionHandler {
    data class CopyCodeResult(
        val handled: Boolean,
        val notificationCancelled: Boolean,
        val copied: Boolean,
    )

    data class AutoCancelResult(
        val handled: Boolean,
        val notificationCancelled: Boolean,
    )

    fun handleCopyCodeIntent(
        intent: Intent,
        expectedAction: String,
        cancelNotification: (Int) -> Unit,
        copyCode: (String) -> Unit,
    ): CopyCodeResult {
        val startedAt = System.nanoTime()
        val request = CodeNotificationActionPayload.readCopyCodeRequest(intent, expectedAction)
        if (request == null) {
            MagiskOtel.event(
                name = "sms.copy",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "reason" to "ignored_intent",
                    "stage" to "notification_action",
                ),
                statusOk = true,
            )
            return CopyCodeResult(handled = false, notificationCancelled = false, copied = false)
        }
        var notificationCancelled = false
        request.notificationIdToCancel?.let { notificationId ->
            cancelNotification(notificationId)
            notificationCancelled = true
        }
        var copied = false
        request.smsCode?.let { smsCode ->
            copyCode(smsCode)
            copied = true
        }
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "sms.copy",
            attributes = mapOf(
                "result" to if (copied) "ok" else "skip",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "reason" to when {
                    copied && notificationCancelled -> "copied_and_cancelled"
                    copied -> "copied"
                    notificationCancelled -> "cancelled_only"
                    else -> "no_code"
                },
                "stage" to "notification_action",
                "code_length" to (request.smsCode?.length ?: 0).toString(),
            ),
            statusOk = true,
        )
        return CopyCodeResult(
            handled = true,
            notificationCancelled = notificationCancelled,
            copied = copied,
        )
    }

    fun handleCopyCodeReceiverIntent(
        context: Context,
        intent: Intent,
        expectedAction: String,
        copyCode: (String) -> Unit,
    ): CopyCodeResult {
        return handleCopyCodeIntent(
            intent = intent,
            expectedAction = expectedAction,
            cancelNotification = { notificationId ->
                cancelNotification(context, notificationId)
            },
            copyCode = copyCode,
        )
    }

    fun handleAutoCancelIntent(
        intent: Intent,
        cancelNotification: (Int) -> Unit,
    ): AutoCancelResult {
        val startedAt = System.nanoTime()
        val request = CodeNotificationActionPayload.readAutoCancelRequest(intent)
        if (request == null) {
            MagiskOtel.event(
                name = "notify.cancel",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "reason" to "ignored_intent",
                ),
                statusOk = true,
            )
            return AutoCancelResult(handled = false, notificationCancelled = false)
        }
        cancelNotification(request.notificationId)
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "notify.cancel",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "reason" to "auto_cancel",
            ),
            statusOk = true,
        )
        return AutoCancelResult(handled = true, notificationCancelled = true)
    }

    fun handleAutoCancelReceiverIntent(
        context: Context,
        intent: Intent,
    ): AutoCancelResult {
        return handleAutoCancelIntent(intent) { notificationId ->
            cancelNotification(context, notificationId)
            XLog.i("Notification auto cancelled by alarm, id=%d", notificationId)
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        manager?.cancel(notificationId)
    }
}
