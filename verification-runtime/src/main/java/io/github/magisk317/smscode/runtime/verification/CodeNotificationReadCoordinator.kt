package io.github.magisk317.smscode.runtime.verification

import android.app.Notification
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

/**
 * App-side backup of the mark-as-read pipeline: when the DB-based path (hook process, telephony
 * provider) fails or the vendor SMS app keeps its own unread state, the SMS app still posts a
 * notification. This coordinator inspects that notification, and when it belongs to an allowed
 * SMS poster and carries a verification code, sends the app's own "mark as read" action so the
 * source app clears the notification with its own logic.
 *
 * Primary/backup contract with [OperateSmsActionHelper]: the hook-side DB write runs first and
 * usually clears the notification together with the unread state. When it fully succeeded there
 * is no notification left for the listener, so this coordinator naturally no-ops. Duplicate
 * triggering is safe — mark-as-read is idempotent on both paths.
 *
 * Runs inside the host app's [android.service.notification.NotificationListenerService]; the
 * host owns the notification-listener grant, extra/text extraction, and pref gating.
 */
class CodeNotificationReadCoordinator(
    private val smsPackageAllowlist: Set<String> = TelephonySmsNotificationPackages.POSTERS,
    /**
     * Tags owned by this module. A notification carrying one of them was posted by the
     * module itself, not by the SMS app, so it is checked before the allowlist: the
     * phone-owned path posts under a host package that is on that list (issue #275).
     */
    private val selfTags: Set<String> = setOf(PhoneOwnedNotificationDispatcher.SELF_POST_TAG),
    /** Host-supplied verification-code parser (e.g. shared SmsCodeFacade with APP_NOTIFICATION source). */
    private val codeParser: suspend (String) -> String?,
    /** Host-supplied sender; typically `action.actionIntent.send()` wrapped in runCatching. */
    private val actionSender: (Notification.Action) -> Unit,
    /** Injected for tests; defaults to the shared finder. */
    private val readActionLookup: (Notification) -> Notification.Action? = NotificationReadActionFinder::find,
) {

    enum class Decision {
        /** Posted by this module itself; never a target, never cancelled (issue #275). */
        SELF_POSTED,

        /** Notification matched an allowed SMS poster carrying a code; the read action was sent. */
        MARK_AS_READ_SENT,

        /** Poster package not in the SMS allowlist; untouched. */
        REJECTED_PACKAGE,

        /** Body/title contained no verification code; untouched. */
        CODE_NOT_DETECTED,

        /** Code detected but the poster exposed no mark-as-read action (vendor apps); logged for vendor work. */
        NO_READ_ACTION,
    }

    suspend fun maybeMarkAsRead(
        packageName: String,
        title: CharSequence?,
        text: CharSequence?,
        notification: Notification,
        tag: String? = null,
    ): Decision {
        if (tag != null && tag in selfTags) {
            return emit(Decision.SELF_POSTED, packageName)
        }
        if (packageName !in smsPackageAllowlist) {
            return emit(Decision.REJECTED_PACKAGE, packageName)
        }
        val parsedCode = codeParser(joinText(title, text))
        if (parsedCode.isNullOrBlank()) {
            return emit(Decision.CODE_NOT_DETECTED, packageName)
        }
        val action = readActionLookup(notification)
        if (action == null) {
            // Evidence sink for the per-vendor adaptation backlog (see docs/vendor-sms-apps.md).
            return emit(Decision.NO_READ_ACTION, packageName, codeLength = parsedCode.length)
        }
        return runCatching { actionSender(action) }
            .map { emit(Decision.MARK_AS_READ_SENT, packageName, codeLength = parsedCode.length) }
            .getOrElse { error ->
                XLog.w(
                    message = "Notification mark-as-read send failed: package=%s",
                    packageName,
                    error,
                )
                emit(Decision.NO_READ_ACTION, packageName, codeLength = parsedCode.length)
            }
    }

    private fun emit(decision: Decision, packageName: String, codeLength: Int? = null): Decision {
        val attrs = mutableMapOf(
            "result" to decision.name,
            "package" to packageName,
            "process" to "app",
        )
        if (codeLength != null) attrs["code_length"] = codeLength.toString()
        MagiskOtel.event(name = "sms.notify_read", attributes = attrs, statusOk = true)
        XLog.d(message = "Notification mark-as-read decision: result=%s package=%s", decision.name, packageName)
        return decision
    }

    private fun joinText(title: CharSequence?, text: CharSequence?): String {
        val normalizedTitle = title?.toString()?.trim().orEmpty()
        val normalizedText = text?.toString()?.trim().orEmpty()
        return listOf(normalizedTitle, normalizedText).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
