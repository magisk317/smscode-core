package io.github.magisk317.smscode.verification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build

object CodeNotificationPayload {
    const val EXTRA_SENDER = "sender"
    const val EXTRA_COMPANY = "company"
    const val EXTRA_SMS_CODE = "sms_code"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_AUTO_CANCEL_ENABLED = "auto_cancel_enabled"
    const val EXTRA_RETENTION_TIME_MS = "retention_time_ms"
    const val EXTRA_IPC_TOKEN = "ipc_token"
    const val RESULT_CODE_APP_NOTIFICATION_POSTED = 1
    const val RESULT_CODE_APP_NOTIFICATION_FAILED = -1
    const val RESULT_CODE_APP_NOTIFICATION_UNDELIVERED = -2
    const val RESULT_DATA_POSTED = "posted"
    const val RESULT_DATA_BLANK_CODE = "blank_code"
    const val RESULT_DATA_REJECTED_TOKEN = "rejected_token"
    const val RESULT_DATA_MISSING_NOTIFICATION_MANAGER = "missing_notification_manager"
    const val RESULT_DATA_NOTIFY_FAILED = "notify_failed"
    const val RESULT_DATA_NO_RECEIVER = "no_receiver"
    const val RESULT_DATA_TIMEOUT = "timeout"
    const val RESULT_DATA_SEND_FAILED = "send_failed"
    const val RESULT_DATA_MISSING_TOKEN = "missing_token"

    data class DeliveryResult(
        val success: Boolean,
        val reason: String,
    )

    data class Payload(
        val sender: String?,
        val company: String?,
        val smsCode: String?,
        val notificationId: Int,
        val autoCancelEnabled: Boolean,
        val retentionTimeMs: Long,
        val token: String?,
    )

    data class BroadcastContract(
        val applicationId: String,
        val receiverClassName: String,
    ) {
        val actionShowCodeNotification: String =
            showNotificationAction(applicationId)

        fun createIntent(
            sender: String?,
            company: String?,
            smsCode: String?,
            notificationId: Int,
            autoCancelEnabled: Boolean,
            retentionTimeMs: Long,
            token: String?,
            intentFactory: (String) -> Intent = ::Intent,
        ): Intent =
            createBroadcastIntent(
                applicationId = applicationId,
                receiverClassName = receiverClassName,
                payload = Payload(
                    sender = sender,
                    company = company,
                    smsCode = smsCode,
                    notificationId = notificationId,
                    autoCancelEnabled = autoCancelEnabled,
                    retentionTimeMs = retentionTimeMs,
                    token = token,
                ),
                intentFactory = intentFactory,
            )
    }

    sealed class ReceiverRequest {
        object Ignored : ReceiverRequest()

        data class Rejected(
            val resultData: String,
            val expectedTokenEmpty: Boolean,
            val receivedTokenEmpty: Boolean,
            val sentFromUid: Int?,
        ) : ReceiverRequest()

        data class Accepted(
            val payload: Payload,
            val smsCode: String,
        ) : ReceiverRequest()
    }

    fun fillIntent(intent: Intent, payload: Payload): Intent =
        intent.apply {
            putExtra(EXTRA_SENDER, payload.sender)
            putExtra(EXTRA_COMPANY, payload.company)
            putExtra(EXTRA_SMS_CODE, payload.smsCode)
            putExtra(EXTRA_NOTIFICATION_ID, payload.notificationId)
            putExtra(EXTRA_AUTO_CANCEL_ENABLED, payload.autoCancelEnabled)
            putExtra(EXTRA_RETENTION_TIME_MS, payload.retentionTimeMs)
            if (!payload.token.isNullOrBlank()) {
                putExtra(EXTRA_IPC_TOKEN, payload.token)
            }
        }

    fun showNotificationAction(applicationId: String): String {
        return "$applicationId.ACTION_SHOW_CODE_NOTIFICATION"
    }

    fun createBroadcastIntent(
        applicationId: String,
        receiverClassName: String,
        payload: Payload,
        intentFactory: (String) -> Intent = ::Intent,
    ): Intent {
        return fillIntent(
            intentFactory(showNotificationAction(applicationId)).apply {
                setClassName(applicationId, receiverClassName)
            },
            payload,
        )
    }

    fun readPayload(intent: Intent): Payload {
        val smsCode = intent.getStringExtra(EXTRA_SMS_CODE).orEmpty()
        return Payload(
            sender = intent.getStringExtra(EXTRA_SENDER),
            company = intent.getStringExtra(EXTRA_COMPANY),
            smsCode = smsCode,
            notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, smsCode.hashCode()),
            autoCancelEnabled = intent.getBooleanExtra(EXTRA_AUTO_CANCEL_ENABLED, false),
            retentionTimeMs = intent.getLongExtra(EXTRA_RETENTION_TIME_MS, 0L).coerceAtLeast(0L),
            token = intent.getStringExtra(EXTRA_IPC_TOKEN),
        )
    }

    fun readReceiverRequest(
        intent: Intent,
        expectedAction: String,
        expectedToken: String?,
        sentFromUid: Int?,
    ): ReceiverRequest {
        if (intent.action != expectedAction) return ReceiverRequest.Ignored
        val payload = readPayload(intent)
        val smsCode = payload.smsCode.orEmpty()
        if (smsCode.isBlank()) {
            return ReceiverRequest.Rejected(
                resultData = RESULT_DATA_BLANK_CODE,
                expectedTokenEmpty = expectedToken.isNullOrBlank(),
                receivedTokenEmpty = payload.token.isNullOrBlank(),
                sentFromUid = sentFromUid,
            )
        }

        val tokenMatched = !expectedToken.isNullOrBlank() && payload.token == expectedToken
        val allowSystemBypass = shouldAllowSmsHookTokenBypass(
            expectedToken = expectedToken,
            sentFromUid = sentFromUid,
        )
        if (!tokenMatched && !allowSystemBypass) {
            return ReceiverRequest.Rejected(
                resultData = RESULT_DATA_REJECTED_TOKEN,
                expectedTokenEmpty = expectedToken.isNullOrBlank(),
                receivedTokenEmpty = payload.token.isNullOrBlank(),
                sentFromUid = sentFromUid,
            )
        }
        return ReceiverRequest.Accepted(
            payload = payload,
            smsCode = smsCode,
        )
    }

    fun resolveTitle(
        company: String?,
        sender: String?,
        fallbackTitle: String,
    ): String {
        return company?.takeIf { it.isNotBlank() }
            ?: sender?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
    }

    fun resolveTitle(
        message: SmsMessage,
        fallbackTitle: String,
    ): String {
        return resolveTitle(
            company = message.company,
            sender = message.sender,
            fallbackTitle = fallbackTitle,
        )
    }

    fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    fun deliveryResultFromBroadcast(
        resultCode: Int,
        resultData: String?,
    ): DeliveryResult {
        val reason = resultData?.takeIf { it.isNotBlank() } ?: when (resultCode) {
            RESULT_CODE_APP_NOTIFICATION_POSTED -> RESULT_DATA_POSTED
            RESULT_CODE_APP_NOTIFICATION_UNDELIVERED -> RESULT_DATA_NO_RECEIVER
            else -> RESULT_DATA_NOTIFY_FAILED
        }
        return DeliveryResult(
            success = resultCode == RESULT_CODE_APP_NOTIFICATION_POSTED,
            reason = reason,
        )
    }

    fun finishOrderedResult(
        receiver: BroadcastReceiver,
        reason: String,
        success: Boolean = false,
    ): Boolean {
        if (!receiver.isOrderedBroadcast) return false
        receiver.setResult(resultCodeFor(success), reason, null)
        return true
    }

    fun resultCodeFor(success: Boolean): Int {
        return if (success) {
            RESULT_CODE_APP_NOTIFICATION_POSTED
        } else {
            RESULT_CODE_APP_NOTIFICATION_FAILED
        }
    }

    fun resolveSentFromUidCompat(
        sdkInt: Int = Build.VERSION.SDK_INT,
        sentFromUidProvider: () -> Int,
    ): Int? {
        if (sdkInt < API_LEVEL_34) return null
        return runCatching(sentFromUidProvider).getOrNull()
    }

    fun shouldAllowSmsHookTokenBypass(
        sentFromUid: Int?,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        return sentFromUid == SYSTEM_UID || sentFromUid == PHONE_UID
    }

    fun shouldAllowSmsHookTokenBypass(
        expectedToken: String?,
        sentFromUid: Int?,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        return expectedToken.isNullOrBlank() &&
            shouldAllowSmsHookTokenBypass(sentFromUid = sentFromUid, sdkInt = sdkInt)
    }

    private const val API_LEVEL_34 = 34
    private const val SYSTEM_UID = 1000
    private const val PHONE_UID = 1001
}
