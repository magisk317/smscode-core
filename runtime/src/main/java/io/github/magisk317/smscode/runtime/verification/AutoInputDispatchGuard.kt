package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.smscode.verification.SmsMessageDedupKeys

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel

object AutoInputDispatchGuard {
    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
        val key: String? = null,
    )

    fun <M : SmsMessage> claim(
        pluginContext: Context,
        smsMsg: M,
        delayMs: Long,
        gateClaimer: (Context, String, List<String>, Long, Int) -> ClaimResult,
    ): Boolean {
        val keys = SmsMessageDedupKeys.buildAutoInputKeys(smsMsg)
        if (keys.isEmpty()) {
            MagiskOtel.event(
                name = "auto.input",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "claim",
                    "reason" to "no_keys",
                    "delay_ms" to delayMs.coerceAtLeast(0L).toString(),
                ),
                statusOk = true,
            )
            return true
        }
        val windowMs = (delayMs + AUTO_INPUT_DISPATCH_GUARD_EXTRA_MS)
            .coerceAtLeast(AUTO_INPUT_DISPATCH_GUARD_MIN_WINDOW_MS)
        val claim = gateClaimer(
            pluginContext,
            SHARED_AUTO_INPUT_DISPATCH_GUARD_FILE_NAME,
            keys,
            windowMs,
            MAX_AUTO_INPUT_DISPATCH_GUARD_ENTRIES,
        )
        if (claim.claimed) {
            MagiskOtel.event(
                name = "auto.input",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "claim",
                    "reason" to "claimed",
                    "delay_ms" to delayMs.coerceAtLeast(0L).toString(),
                ),
                statusOk = true,
            )
            return true
        }
        XLog.w(
            "Auto input dispatch skipped: key=%s ageMs=%d delayMs=%d windowMs=%d",
            claim.key ?: keys.first(),
            claim.ageMs ?: -1L,
            delayMs,
            windowMs,
        )
        MagiskOtel.event(
            name = "auto.input",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to (claim.ageMs ?: 0L).coerceAtLeast(0L).toString(),
                "process" to "hook",
                "stage" to "claim",
                "reason" to "duplicate_window",
                "delay_ms" to delayMs.coerceAtLeast(0L).toString(),
            ),
            statusOk = true,
        )
        return false
    }

    private const val SHARED_AUTO_INPUT_DISPATCH_GUARD_FILE_NAME = "auto_input_dispatch_guard"
    private const val AUTO_INPUT_DISPATCH_GUARD_EXTRA_MS = 5_000L
    private const val AUTO_INPUT_DISPATCH_GUARD_MIN_WINDOW_MS = 8_000L
    private const val MAX_AUTO_INPUT_DISPATCH_GUARD_ENTRIES = 128
}
