package io.github.magisk317.smscode.runtime.verification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

object CodeNotificationReceiverHandler {
    data class DeliveryDiagnostics(
        val canPost: Boolean,
        val summary: String,
    )

    data class Config(
        val source: String,
        val expectedAction: String,
        val expectedTokenProvider: (Context) -> String?,
        val sentFromUidProvider: () -> Int,
        val channelName: String,
        val visualConfig: CodeNotificationDeliveryHelper.VisualConfig,
        val fallbackTitleProvider: (Context) -> String,
        val contentTextProvider: (Context, String?) -> String,
        val createNotificationChannel: (Context, String, String, Int) -> Unit,
        val createCopyIntent: (Context, String, Int) -> Intent,
        val createAutoCancelIntent: (Context, Int) -> Intent,
        val inspectDelivery: (Context, String) -> DeliveryDiagnostics,
    )

    fun handleBroadcast(
        receiver: BroadcastReceiver,
        context: Context,
        intent: Intent,
        config: Config,
    ) {
        if (intent.action != config.expectedAction) {
            return
        }

        val request = CodeNotificationPayload.readReceiverRequest(
            intent = intent,
            expectedAction = config.expectedAction,
            expectedToken = config.expectedTokenProvider(context),
            sentFromUid = CodeNotificationPayload.resolveSentFromUidCompat(
                sentFromUidProvider = config.sentFromUidProvider,
            ),
        )
        when (request) {
            CodeNotificationPayload.ReceiverRequest.Ignored -> return
            is CodeNotificationPayload.ReceiverRequest.Rejected -> {
                CodeNotificationDeliveryHelper.logReceiverRejected(config.source, request)
                CodeNotificationPayload.finishOrderedResult(receiver, request.resultData)
            }
            is CodeNotificationPayload.ReceiverRequest.Accepted -> handleAcceptedRequest(
                receiver = receiver,
                context = context,
                request = request,
                config = config,
            )
        }
    }

    fun handleAcceptedRequest(
        receiver: BroadcastReceiver,
        context: Context,
        request: CodeNotificationPayload.ReceiverRequest.Accepted,
        config: Config,
    ) {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
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

        val payload = request.payload
        val smsCode = request.smsCode
        val appContext = context.applicationContext
        val notificationId = payload.notificationId
        val autoCancelEnabled = payload.autoCancelEnabled
        val retentionTimeMs = payload.retentionTimeMs
        val title = CodeNotificationPayload.resolveTitle(
            company = payload.company,
            sender = payload.sender,
            fallbackTitle = config.fallbackTitleProvider(appContext),
        )

        config.createNotificationChannel(
            appContext,
            config.visualConfig.channelId,
            config.channelName,
            NotificationManager.IMPORTANCE_HIGH,
        )

        val copyIntent = config.createCopyIntent(appContext, smsCode, notificationId)
        val contentIntent = CodeNotificationActionPayload.createBroadcastPendingIntent(
            context = appContext,
            requestCode = notificationId,
            intent = copyIntent,
        )

        val notification = CodeNotificationDeliveryHelper.buildCodeNotification(
            context = appContext,
            visualConfig = config.visualConfig,
            title = title,
            smsCode = smsCode,
            contentIntent = contentIntent,
            contentTextProvider = { code ->
                config.contentTextProvider(appContext, code)
            },
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
        )

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) {
            XLog.w(message = "%s missing NotificationManager", config.source)
            CodeNotificationPayload.finishOrderedResult(
                receiver,
                CodeNotificationPayload.RESULT_DATA_MISSING_NOTIFICATION_MANAGER,
            )
            emit(result = "error", statusOk = false, reason = "missing_notification_manager")
            return
        }

        val diagnostics = config.inspectDelivery(appContext, config.visualConfig.channelId)
        XLog.i(message = "%s delivery diagnostics: %s", config.source, diagnostics.summary)
        if (!diagnostics.canPost) {
            XLog.w(
                message = "%s posting while app-owned notifications are unavailable: %s",
                config.source,
                diagnostics.summary,
            )
        }

        runCatching {
            CodeNotificationDeliveryHelper.showNotification(manager, notificationId, notification)
        }.onFailure { throwable ->
            XLog.w(
                message = "%s notify failed: id=%d err=%s",
                config.source,
                notificationId,
                throwable.message ?: throwable.javaClass.simpleName,
            )
            CodeNotificationPayload.finishOrderedResult(
                receiver,
                CodeNotificationPayload.RESULT_DATA_NOTIFY_FAILED,
            )
            emit(result = "error", statusOk = false, reason = "notify_failed")
            return
        }
        XLog.i(message = "%s posted app-owned notification id=%d", config.source, notificationId)
        CodeNotificationPayload.finishOrderedResult(
            receiver,
            CodeNotificationPayload.RESULT_DATA_POSTED,
            success = true,
        )
        emit(result = "ok")

        if (autoCancelEnabled && retentionTimeMs > 0L) {
            CodeNotificationAutoCancelScheduler.scheduleSafely(
                request = CodeNotificationAutoCancelScheduler.Request(
                    context = appContext,
                    notificationId = notificationId,
                    autoCancelEnabled = autoCancelEnabled,
                    retentionTimeMs = retentionTimeMs,
                    intentFactory = config.createAutoCancelIntent,
                ),
                source = config.source,
            )
        }
    }
}
