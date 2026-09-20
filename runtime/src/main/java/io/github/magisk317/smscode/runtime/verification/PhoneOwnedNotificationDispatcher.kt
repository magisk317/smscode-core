package io.github.magisk317.smscode.runtime.verification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.smscode.runtime.contract.notification.NotificationPlatformBridge
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Posts the verification-code notification with the **phone** application identity.
 *
 * This is the path that has to survive a frozen module app, so it refuses to repeat the app-owned
 * mistake of trusting `notify()`: deliverability is inspected before the post, and the system is
 * asked what it actually accepted afterwards. Three properties are load bearing:
 *
 * 1. **Every inspection resolves against the posting package.** Both the permission lookup and the
 *    channel lookup resolve against the package of the process that posts, so the module app's
 *    context must never be passed here - it answers for the wrong package and reports a channel
 *    state that has nothing to do with the notification being posted.
 * 2. **A channel set to `IMPORTANCE_NONE` is never posted into.** Android does not let an app raise
 *    the importance of an existing channel, so posting into such a channel is a guaranteed silent
 *    no-op. The dispatcher rotates to [fallbackChannelId], which the system creates fresh.
 * 3. **Acceptance is verified, not assumed.** `notify()` returning without throwing proves nothing
 *    on Android 13+, where a missing channel, a denied permission or a switched-off channel all
 *    make it a silent no-op. The outcome distinguishes "the system lists it as active" from "the
 *    system dropped it" from "the system did not answer".
 *
 * The notification *content* stays with the caller, because icons and strings are per-module
 * resources; the caller supplies it through the [post] builder.
 */
class PhoneOwnedNotificationDispatcher(
    private val phoneContext: Context,
    private val bridge: NotificationPlatformBridge?,
    private val modulePackageName: String,
    private val primaryChannelId: String,
    private val fallbackChannelId: String,
    private val channelName: String,
) {
    /** The result of a phone-owned delivery attempt. Reported to logs and telemetry only. */
    enum class Outcome(val id: String) {
        /** The system lists the notification as active for the posting package. */
        ACCEPTED("accepted"),

        /** `notify()` returned but the system did not answer the verification query. */
        UNVERIFIED("unverified"),

        /** The system accepted the call but does not list the notification as active. */
        DROPPED("dropped"),

        /** Pre-post inspection says the notification could not reach the user. */
        NOT_DELIVERABLE("not_deliverable"),

        /** No [NotificationManager] is reachable from the phone context. */
        NO_MANAGER("no_manager"),

        /** Building or posting the notification threw. */
        NOTIFY_FAILED("notify_failed");

        /** Whether the notification is believed to be visible to the user. */
        val delivered: Boolean get() = this == ACCEPTED || this == UNVERIFIED
    }

    /** What the channel preparation decided, so the caller can log and reuse the id. */
    data class ChannelResolution(val channelId: String, val rotated: Boolean)

    /** The action [decideChannelAction] selected for the primary channel. */
    enum class ChannelAction { CREATE_PRIMARY, USE_PRIMARY, ROTATE_TO_FALLBACK }

    /**
     * Resolves the channel the phone-owned notification is posted into, creating it when missing
     * and rotating when the user switched the primary channel off.
     */
    fun resolveChannelId(): ChannelResolution {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ChannelResolution(primaryChannelId, rotated = false)
        }
        val manager = notificationManager() ?: return ChannelResolution(primaryChannelId, rotated = false)
        return runCatching { ensureDeliverableChannel(manager) }
            .getOrElse { error ->
                XLog.w("Phone-owned channel preparation failed: %s", error.message)
                ChannelResolution(primaryChannelId, rotated = false)
            }
    }

    /**
     * Whether posting is worth attempting at all.
     *
     * The phone-owned path is evaluated against the phone package, but a non-deliverable phone path
     * never becomes the sole reason to skip: the app-owned attempt runs in the module app process
     * with its own package, channel and permission, so it must stay possible. The two are
     * combined with an OR for that reason.
     */
    fun deliveryDiagnostics(channelId: String): NotifyActionHelper.DeliveryDiagnostics {
        val phone = inspect(channelId)
        val appPermissionGranted = isPackageAllowedToPost(phoneContext, modulePackageName)
        val phoneSummary = phone?.summary() ?: INSPECTION_UNAVAILABLE
        return NotifyActionHelper.DeliveryDiagnostics(
            canPost = (phone?.canPost ?: true) || appPermissionGranted,
            summary = "phone[$channelId]=<$phoneSummary> appPermissionGranted=$appPermissionGranted",
        )
    }

    /**
     * Posts and verifies.
     *
     * [build] receives the resolved channel id so the caller can construct its own notification
     * (icon, title, text and content intent are module resources), then the dispatcher posts it and
     * asks the system whether it landed.
     */
    fun post(
        notificationId: Int,
        channelId: String,
        build: (String) -> Notification,
    ): Outcome {
        val startedAt = System.nanoTime()
        val manager = notificationManager()
        if (manager == null) {
            XLog.w("Phone-owned notification skipped: no NotificationManager on %s", packageLabel(phoneContext))
            return emit(Outcome.NO_MANAGER, notificationId, channelId, startedAt)
        }

        val deliverability = inspect(channelId)
        if (deliverability != null && !deliverability.canPost) {
            XLog.w(
                "Phone-owned notification not deliverable: pkg=%s channel=%s %s",
                packageLabel(phoneContext),
                channelId,
                deliverability.summary(),
            )
            return emit(Outcome.NOT_DELIVERABLE, notificationId, channelId, startedAt)
        }

        val notification = runCatching { build(channelId) }.getOrElse { error ->
            XLog.w(
                "Phone-owned notification could not be built: id=%d pkg=%s channel=%s err=%s",
                notificationId,
                packageLabel(phoneContext),
                channelId,
                error.message,
            )
            return emit(Outcome.NOTIFY_FAILED, notificationId, channelId, startedAt)
        }

        val posted = try {
            manager.notify(notificationId, notification)
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            XLog.w(
                "Phone-owned notification failed: id=%d pkg=%s channel=%s err=%s",
                notificationId,
                packageLabel(phoneContext),
                channelId,
                e.message,
            )
            false
        }
        if (!posted) return emit(Outcome.NOTIFY_FAILED, notificationId, channelId, startedAt)

        val outcome = when (verifyAccepted(manager, notificationId)) {
            true -> Outcome.ACCEPTED
            false -> Outcome.DROPPED
            null -> Outcome.UNVERIFIED
        }
        when (outcome) {
            Outcome.ACCEPTED -> XLog.i(
                "Phone-owned code notification posted and accepted by the system: id=%d pkg=%s channel=%s",
                notificationId,
                packageLabel(phoneContext),
                channelId,
            )

            Outcome.DROPPED -> XLog.w(
                "Phone-owned code notification was dropped by the system: id=%d pkg=%s channel=%s " +
                    "- the notification is not visible in the shade",
                notificationId,
                packageLabel(phoneContext),
                channelId,
            )

            else -> XLog.i(
                "Phone-owned code notification submitted: id=%d pkg=%s channel=%s (acceptance unverified)",
                notificationId,
                packageLabel(phoneContext),
                channelId,
            )
        }
        return emit(outcome, notificationId, channelId, startedAt)
    }

    /**
     * Ensures the primary channel exists at a usable importance and returns the id to post into.
     *
     * Android refuses to raise the importance of an existing channel. If a channel has been lowered
     * below [NotificationManager.IMPORTANCE_HIGH] (e.g. NONE, MIN, LOW, or DEFAULT without heads-up),
     * verification code notifications can be silently collapsed or muted. In that case, the dispatcher
     * rotates to [fallbackChannelId] which is created fresh with [NotificationManager.IMPORTANCE_HIGH].
     */
    private fun ensureDeliverableChannel(manager: NotificationManager): ChannelResolution {
        val primary = runCatching { manager.getNotificationChannel(primaryChannelId) }.getOrNull()
        return when (decideChannelAction(exists = primary != null, importance = primary?.importance)) {
            ChannelAction.CREATE_PRIMARY -> {
                manager.createNotificationChannel(createHighPriorityChannel(primaryChannelId))
                XLog.i("Phone-owned channel created: id=%s importance=high", primaryChannelId)
                ChannelResolution(primaryChannelId, rotated = false)
            }

            ChannelAction.USE_PRIMARY -> {
                XLog.i(
                    "Phone-owned channel ready: id=%s importance=%d",
                    primaryChannelId,
                    primary?.importance ?: NotificationManager.IMPORTANCE_NONE,
                )
                ChannelResolution(primaryChannelId, rotated = false)
            }

            ChannelAction.ROTATE_TO_FALLBACK -> {
                XLog.w(
                    "Phone-owned channel %s importance (%d) is below HIGH, rotating to %s",
                    primaryChannelId,
                    primary?.importance ?: NotificationManager.IMPORTANCE_NONE,
                    fallbackChannelId,
                )
                val rotatedExisting = runCatching { manager.getNotificationChannel(fallbackChannelId) }.getOrNull()
                if (rotatedExisting == null) {
                    manager.createNotificationChannel(createHighPriorityChannel(fallbackChannelId))
                } else if (rotatedExisting.importance < NotificationManager.IMPORTANCE_HIGH) {
                    XLog.w(
                        "Phone-owned fallback channel %s importance (%d) is below HIGH as well; delivery cannot be " +
                            "guaranteed on %s",
                        fallbackChannelId,
                        rotatedExisting.importance,
                        packageLabel(phoneContext),
                    )
                }
                ChannelResolution(fallbackChannelId, rotated = true)
            }
        }
    }

    private fun createHighPriorityChannel(channelId: String): NotificationChannel {
        return NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            enableLights(true)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
    }

    private fun notificationManager(): NotificationManager? = runCatching {
        phoneContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }.getOrNull()

    /** `null` means the inspection was unavailable, which must not be read as either answer. */
    private fun inspect(channelId: String): NotificationDeliveryDiagnostics? =
        bridge?.let { runCatching { it.inspectDelivery(phoneContext, channelId) }.getOrNull() }

    /**
     * Asks the system for this package' active notifications to confirm the post landed.
     * Returns `null` when the answer is unavailable, which must not be reported as a failure.
     *
     * In Android NotificationManagerService, notification enqueuing and posting is asynchronous
     * (often deferred by NMS worker threads or OEM assistants like ColorOS MCS Assistant).
     * Querying [NotificationManager.getActiveNotifications] immediately after `notify()` causes
     * false "dropped" diagnoses due to race conditions. A short polling loop gives NMS time to post.
     */
    private fun verifyAccepted(manager: NotificationManager, notificationId: Int): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        repeat(ACCEPTANCE_VERIFY_MAX_ATTEMPTS) { attempt ->
            val active = runCatching { manager.activeNotifications }.getOrNull() ?: return null
            if (active.any { it.id == notificationId }) return true
            if (attempt < ACCEPTANCE_VERIFY_MAX_ATTEMPTS - 1) {
                try {
                    Thread.sleep(ACCEPTANCE_VERIFY_DELAY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        return false
    }

    private fun emit(
        outcome: Outcome,
        notificationId: Int,
        channelId: String,
        startedAt: Long,
    ): Outcome {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "notify.phone_owned",
            attributes = mapOf(
                "result" to outcome.id,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "stage" to "post",
                "notification_id" to notificationId.toString(),
                "channel" to channelId,
                "package" to packageLabel(phoneContext),
            ),
            statusOk = outcome.delivered,
        )
        return outcome
    }

    companion object {
        const val INSPECTION_UNAVAILABLE = "inspection_unavailable"
        private const val ACCEPTANCE_VERIFY_MAX_ATTEMPTS = 4
        private const val ACCEPTANCE_VERIFY_DELAY_MS = 60L

        /**
         * Decides what has to happen to the channel before posting.
         *
         * Kept pure and separate from [ensureDeliverableChannel] so the rotation rule
         * is directly unit testable without a `NotificationManager`.
         * If the channel exists but has importance lower than [NotificationManager.IMPORTANCE_HIGH],
         * we rotate to fallback so that verification code notifications are guaranteed to pop up.
         */
        fun decideChannelAction(exists: Boolean, importance: Int?): ChannelAction {
            if (!exists) return ChannelAction.CREATE_PRIMARY
            return if (importance != null && importance < NotificationManager.IMPORTANCE_HIGH) {
                ChannelAction.ROTATE_TO_FALLBACK
            } else {
                ChannelAction.USE_PRIMARY
            }
        }

        /**
         * Whether [packageName] may post notifications, asked as a package-targeted permission
         * check so it works from a hook process that runs as another application.
         */
        fun isPackageAllowedToPost(context: Context, packageName: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return runCatching {
                context.packageManager.checkPermission(
                    Manifest.permission.POST_NOTIFICATIONS,
                    packageName,
                ) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(true)
        }

        private fun packageLabel(context: Context): String =
            runCatching { context.packageName }.getOrDefault("unknown")
    }
}
