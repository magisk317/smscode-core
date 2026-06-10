package io.github.magisk317.smscode.verification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object ConflictNotificationHelper {
    data class VisualConfig(
        val channelId: String,
        val notificationId: Int,
        val smallIconResId: Int,
        val largeIconResId: Int,
        val accentColorResId: Int,
    )

    data class Request(
        val phoneContext: Context,
        val pluginContext: Context,
        val applicationId: String,
        val visualConfig: VisualConfig,
        val title: String,
        val content: String,
        val activityPendingIntentImmutableMinSdk: Int = Build.VERSION_CODES.M,
        val nowProvider: () -> Long = System::currentTimeMillis,
    )

    @SuppressLint("NotificationPermission", "UnspecifiedImmutableFlag")
    fun showConflictNotification(request: Request): Boolean {
        val manager = request.phoneContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager? ?: return false
        val contentIntent = createLaunchActivityPendingIntent(
            phoneContext = request.phoneContext,
            pluginContext = request.pluginContext,
            applicationId = request.applicationId,
            immutableMinSdk = request.activityPendingIntentImmutableMinSdk,
        )
        val builder = NotificationCompat.Builder(request.pluginContext, request.visualConfig.channelId)
            .setSmallIcon(request.visualConfig.smallIconResId)
            .setLargeIcon(BitmapFactory.decodeResource(request.pluginContext.resources, request.visualConfig.largeIconResId))
            .setWhen(request.nowProvider())
            .setContentTitle(request.title)
            .setContentText(request.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.content))
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(request.pluginContext, request.visualConfig.accentColorResId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }
        manager.notify(request.visualConfig.notificationId, builder.build())
        return true
    }

    fun activityPendingIntentFlags(
        immutableMinSdk: Int,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Int {
        val immutableFlag = if (sdkInt >= immutableMinSdk) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
    }

    private fun createLaunchActivityPendingIntent(
        phoneContext: Context,
        pluginContext: Context,
        applicationId: String,
        immutableMinSdk: Int,
    ): PendingIntent? {
        val launchIntent = pluginContext.packageManager.getLaunchIntentForPackage(applicationId) ?: return null
        return PendingIntent.getActivity(
            phoneContext,
            0,
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            activityPendingIntentFlags(immutableMinSdk),
        )
    }
}
