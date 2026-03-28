package io.github.magisk317.smscode.verification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.smscode.xposed.utils.XLog

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
    ) {
        val intent = intentFactory(
            smsMsg.sender,
            smsMsg.company,
            smsMsg.smsCode,
            notificationId,
            autoCancelEnabled,
            retentionTimeMs,
            token,
        )
        context.sendBroadcast(intent)
        XLog.i(
            "Requested app-owned code notification id=%d autoCancel=%s retentionMs=%d tokenPresent=%s",
            notificationId,
            autoCancelEnabled,
            retentionTimeMs,
            token != null,
        )
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
            .setContentTitle(title)
            .setContentText(contentTextProvider(smsCode))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, visualConfig.accentColorResId))
            .setGroup(visualConfig.groupKey)
        if (autoCancelEnabled && retentionTimeMs > 0L) {
            builder.setTimeoutAfter(retentionTimeMs)
        }
        return builder.build()
    }
}
