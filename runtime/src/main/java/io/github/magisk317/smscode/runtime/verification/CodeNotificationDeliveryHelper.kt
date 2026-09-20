package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage

import android.annotation.SuppressLint
import android.app.BroadcastOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import io.github.magisk317.xposed.logging.MagiskOtel

object CodeNotificationDeliveryHelper {
    data class VisualConfig(
        val channelId: String,
        val groupKey: String,
        val smallIconResId: Int,
        val largeIconResId: Int,
        val accentColorResId: Int,
    )

    fun <M : SmsMessage> requestAppOwnedNotification(
        context: Context,
        smsMsg: M,
        notificationId: Int,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
        token: String?,
        intentFactory: (
            sender: String?,
            company: String?,
            smsCode: String?,
            notificationId: Int,
            autoCancelEnabled: Boolean,
            retentionTimeMs: Long,
            token: String?,
        ) -> Intent,
        ackTimeoutMs: Long = APP_OWNED_ACK_TIMEOUT_MS,
    ): CodeNotificationPayload.DeliveryResult {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "stage" to "request",
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "notify.owned", attributes = attrs, statusOk = statusOk)
        }

        if (token.isNullOrBlank()) {
            XLog.w(
                "Skip app-owned code notification request: ipc token missing, id=%d",
                notificationId,
            )
            return CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = CodeNotificationPayload.RESULT_DATA_MISSING_TOKEN,
            )
        }
        val intent = intentFactory(
            smsMsg.sender,
            smsMsg.company,
            smsMsg.smsCode,
            notificationId,
            autoCancelEnabled,
            retentionTimeMs,
            token,
        ).apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val result = sendOrderedNotificationRequest(context, intent, ackTimeoutMs)
        XLog.i(
            "Requested app-owned code notification id=%d autoCancel=%s retentionMs=%d tokenPresent=%s ack=%s reason=%s",
            notificationId,
            autoCancelEnabled,
            retentionTimeMs,
            token.isNotBlank(),
            result.success,
            result.reason,
        )
        emit(
            result = if (result.success) "ok" else "error",
            statusOk = result.success,
            reason = result.reason.ifBlank { "unknown" },
        )
        return result
    }

    fun buildCodeNotification(
        context: Context,
        visualConfig: VisualConfig,
        title: String,
        smsCode: String?,
        contentIntent: PendingIntent,
        contentTextProvider: (String?) -> String,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
    ): Notification {
        val builder = NotificationCompat.Builder(context, visualConfig.channelId)
            .setSmallIcon(visualConfig.smallIconResId)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, visualConfig.largeIconResId))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentTitle(title)
            .setContentText(contentTextProvider(smsCode))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(ContextCompat.getColor(context, visualConfig.accentColorResId))
        // No setGroup(): the module never posts a group summary, and a grouped child without a
        // summary can be collapsed by the system into an empty group header, which the user
        // reads as "no notification". visualConfig.groupKey is kept for callers but is
        // deliberately not applied to the notification.
        if (autoCancelEnabled && retentionTimeMs > 0L) {
            builder.setTimeoutAfter(retentionTimeMs)
        }
        return builder.build()
    }

    @SuppressLint("NotificationPermission")
    fun showNotification(
        manager: NotificationManager,
        notificationId: Int,
        notification: Notification,
    ) {
        manager.notify(notificationId, notification)
    }

    fun logReceiverRejected(
        source: String,
        request: CodeNotificationPayload.ReceiverRequest.Rejected,
    ) {
        if (request.resultData == CodeNotificationPayload.RESULT_DATA_BLANK_CODE) {
            XLog.w("%s ignored blank smsCode", source)
            return
        }
        XLog.w(
            "%s rejected token. expectedEmpty=%s receivedEmpty=%s sentFromUid=%d",
            source,
            request.expectedTokenEmpty,
            request.receivedTokenEmpty,
            request.sentFromUid ?: -1,
        )
    }

    private fun sendOrderedNotificationRequest(
        context: Context,
        intent: Intent,
        ackTimeoutMs: Long,
    ): CodeNotificationPayload.DeliveryResult {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<CodeNotificationPayload.DeliveryResult>()
        val callbackThread = HandlerThread("SmsCodeNotificationAck").apply { start() }
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                resultRef.set(
                    CodeNotificationPayload.deliveryResultFromBroadcast(
                        resultCode = resultCode,
                        resultData = resultData,
                    ),
                )
                latch.countDown()
            }
        }
        return try {
            sendOrderedBroadcastWithIdentity(
                context = context,
                intent = intent,
                resultReceiver = resultReceiver,
                scheduler = Handler(callbackThread.looper),
            )
            if (latch.await(ackTimeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)) {
                resultRef.get() ?: CodeNotificationPayload.DeliveryResult(
                    success = false,
                    reason = CodeNotificationPayload.RESULT_DATA_NO_RECEIVER,
                )
            } else {
                CodeNotificationPayload.DeliveryResult(
                    success = false,
                    reason = CodeNotificationPayload.RESULT_DATA_TIMEOUT,
                )
            }
        } catch (error: SecurityException) {
            broadcastFailureResult(error)
        } catch (error: IllegalArgumentException) {
            broadcastFailureResult(error)
        } finally {
            callbackThread.quitSafely()
        }
    }

    private fun broadcastFailureResult(error: RuntimeException): CodeNotificationPayload.DeliveryResult {
        XLog.w(
            "App-owned code notification broadcast failed: %s",
            error.message ?: error.javaClass.simpleName,
        )
        return CodeNotificationPayload.DeliveryResult(
            success = false,
            reason = CodeNotificationPayload.RESULT_DATA_SEND_FAILED,
        )
    }

    private fun sendOrderedBroadcastWithIdentity(
        context: Context,
        intent: Intent,
        resultReceiver: BroadcastReceiver,
        scheduler: Handler,
    ) {
        val options: Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            BroadcastOptions.makeBasic()
                .setShareIdentityEnabled(true)
                .toBundle()
        } else {
            null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.sendOrderedBroadcast(
                intent,
                null,
                options,
                resultReceiver,
                scheduler,
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_UNDELIVERED,
                CodeNotificationPayload.RESULT_DATA_NO_RECEIVER,
                null,
            )
        } else {
            context.sendOrderedBroadcast(
                intent,
                null,
                resultReceiver,
                scheduler,
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_UNDELIVERED,
                CodeNotificationPayload.RESULT_DATA_NO_RECEIVER,
                null,
            )
        }
    }

    private const val APP_OWNED_ACK_TIMEOUT_MS = 1500L
}
