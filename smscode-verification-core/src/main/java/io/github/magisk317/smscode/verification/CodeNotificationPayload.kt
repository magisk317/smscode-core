package io.github.magisk317.smscode.verification

import android.app.PendingIntent
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

    data class Payload(
        val sender: String?,
        val company: String?,
        val smsCode: String?,
        val notificationId: Int,
        val autoCancelEnabled: Boolean,
        val retentionTimeMs: Long,
        val token: String?,
    )

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

    fun resolveTitle(
        company: String?,
        sender: String?,
        fallbackTitle: String,
    ): String {
        return company?.takeIf { it.isNotBlank() }
            ?: sender?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
    }

    fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    fun shouldAllowSmsHookTokenBypass(
        sentFromUid: Int?,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        return sentFromUid == SYSTEM_UID ||
            sentFromUid == PHONE_UID ||
            (sdkInt < API_LEVEL_34 && sentFromUid == null)
    }

    private const val API_LEVEL_34 = 34
    private const val SYSTEM_UID = 1000
    private const val PHONE_UID = 1001
}
