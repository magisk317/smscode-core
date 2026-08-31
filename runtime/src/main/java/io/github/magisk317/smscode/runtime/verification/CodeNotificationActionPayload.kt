package io.github.magisk317.smscode.runtime.verification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object CodeNotificationActionPayload {
    const val EXTRA_KEY_CODE = "extra_key_code"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val NOTIFICATION_ID_MISSING = -1
    const val AUTO_CANCEL_NOTIFICATION_ID_MISSING = -0xff

    data class CopyCodeRequest(
        val smsCode: String?,
        val notificationId: Int,
    ) {
        val notificationIdToCancel: Int?
            get() = notificationId.takeIf { hasNotificationId(it) }
    }

    data class AutoCancelRequest(
        val notificationId: Int,
    )

    fun fillAutoCancelIntent(intent: Intent, notificationId: Int): Intent {
        return intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    }

    fun fillCopyCodeIntent(
        intent: Intent,
        action: String,
        smsCode: String?,
        notificationId: Int,
    ): Intent {
        return intent.apply {
            this.action = action
            putExtra(EXTRA_KEY_CODE, smsCode)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
    }

    fun createAutoCancelIntent(
        context: Context,
        receiverClass: Class<*>,
        notificationId: Int,
        intentFactory: (Context, Class<*>) -> Intent = ::receiverIntent,
    ): Intent {
        return fillAutoCancelIntent(
            intentFactory(context, receiverClass),
            notificationId,
        )
    }

    fun createCopyCodeIntent(
        context: Context,
        receiverClass: Class<*>,
        action: String,
        smsCode: String?,
        notificationId: Int,
        intentFactory: (Context, Class<*>) -> Intent = ::receiverIntent,
    ): Intent {
        return fillCopyCodeIntent(
            intentFactory(context, receiverClass),
            action,
            smsCode,
            notificationId,
        )
    }

    fun createBroadcastPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        pendingIntentFactory: (Context, Int, Intent, Int) -> PendingIntent = ::getBroadcastPendingIntent,
    ): PendingIntent {
        return pendingIntentFactory(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )
    }

    fun readNotificationId(
        intent: Intent,
        missingValue: Int = NOTIFICATION_ID_MISSING,
    ): Int {
        return intent.getIntExtra(EXTRA_NOTIFICATION_ID, missingValue)
    }

    fun hasNotificationId(
        notificationId: Int,
        missingValue: Int = NOTIFICATION_ID_MISSING,
    ): Boolean {
        return notificationId != missingValue
    }

    fun readSmsCode(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_KEY_CODE)
    }

    fun readCopyCodeRequest(intent: Intent, expectedAction: String): CopyCodeRequest? {
        if (intent.action != expectedAction) return null
        return CopyCodeRequest(
            smsCode = readSmsCode(intent),
            notificationId = readNotificationId(intent),
        )
    }

    fun readAutoCancelRequest(intent: Intent): AutoCancelRequest? {
        val notificationId = readNotificationId(intent, AUTO_CANCEL_NOTIFICATION_ID_MISSING)
        if (!hasNotificationId(notificationId, AUTO_CANCEL_NOTIFICATION_ID_MISSING)) return null
        return AutoCancelRequest(notificationId)
    }

    private fun getBroadcastPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun receiverIntent(context: Context, receiverClass: Class<*>): Intent {
        return Intent(context, receiverClass)
    }
}
