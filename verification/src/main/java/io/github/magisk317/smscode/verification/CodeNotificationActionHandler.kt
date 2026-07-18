package io.github.magisk317.smscode.verification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent

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
        val request = CodeNotificationActionPayload.readCopyCodeRequest(intent, expectedAction)
            ?: return CopyCodeResult(handled = false, notificationCancelled = false, copied = false)
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
        val request = CodeNotificationActionPayload.readAutoCancelRequest(intent)
            ?: return AutoCancelResult(handled = false, notificationCancelled = false)
        cancelNotification(request.notificationId)
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
