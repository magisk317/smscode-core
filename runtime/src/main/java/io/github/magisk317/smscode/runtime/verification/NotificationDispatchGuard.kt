package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.xposed.logging.MagiskOtel

object NotificationDispatchGuard {
    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
        val key: String? = null,
    )

    fun <M : SmsMessage> claim(
        pluginContext: Context,
        smsMsg: M,
        gateClaimer: (Context, String, List<String>, Long, Int) -> ClaimResult,
    ): Boolean {
        val key = buildNotificationKey(smsMsg)
        if (key.isBlank()) {
            emit(result = "ok", reason = "no_keys")
            return true
        }

        val claim = gateClaimer(
            pluginContext,
            SHARED_NOTIFICATION_DISPATCH_GUARD_FILE_NAME,
            listOf(key),
            NOTIFICATION_DISPATCH_GUARD_WINDOW_MS,
            MAX_NOTIFICATION_DISPATCH_GUARD_ENTRIES,
        )
        if (claim.claimed) {
            emit(result = "ok", reason = "claimed")
            return true
        }

        emit(
            result = "skip",
            reason = "duplicate_window",
            durationMs = claim.ageMs,
        )
        return false
    }

    /**
     * Stable across routing metadata differences such as SIM slot, subscription ID,
     * package name, and company resolution. The date keeps distinct SMS messages
     * with identical text/code from suppressing one another indefinitely.
     */
    fun buildNotificationKey(message: SmsMessage): String {
        val sender = message.sender.orEmpty()
        val body = message.body.orEmpty()
        val code = message.smsCode.orEmpty().trim()
        if (sender.isBlank() && body.isBlank() && code.isBlank()) return ""
        return "notify:${hashValue(sender)}:${hashValue(body)}:${message.date}:$code"
    }

    fun stableNotificationId(message: SmsMessage): Int = buildNotificationKey(message).hashCode()

    private fun emit(
        result: String,
        reason: String,
        durationMs: Long? = null,
    ) {
        MagiskOtel.event(
            name = "notify.dispatch",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to (durationMs ?: 0L).coerceAtLeast(0L).toString(),
                "process" to "hook",
                "stage" to "claim",
                "reason" to reason,
            ),
            statusOk = true,
        )
    }

    private fun hashValue(value: String): String = Integer.toHexString(value.hashCode())

    private const val SHARED_NOTIFICATION_DISPATCH_GUARD_FILE_NAME = "notification_dispatch_guard"
    private const val NOTIFICATION_DISPATCH_GUARD_WINDOW_MS = 8_000L
    private const val MAX_NOTIFICATION_DISPATCH_GUARD_ENTRIES = 128
}
