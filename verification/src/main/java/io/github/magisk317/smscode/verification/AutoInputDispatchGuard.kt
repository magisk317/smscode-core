package io.github.magisk317.smscode.verification

import android.content.Context

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
        if (keys.isEmpty()) return true
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
            return true
        }
        XLog.w(
            "Auto input dispatch skipped: key=%s ageMs=%d delayMs=%d windowMs=%d",
            claim.key ?: keys.first(),
            claim.ageMs ?: -1L,
            delayMs,
            windowMs,
        )
        return false
    }

    private const val SHARED_AUTO_INPUT_DISPATCH_GUARD_FILE_NAME = "auto_input_dispatch_guard"
    private const val AUTO_INPUT_DISPATCH_GUARD_EXTRA_MS = 5_000L
    private const val AUTO_INPUT_DISPATCH_GUARD_MIN_WINDOW_MS = 8_000L
    private const val MAX_AUTO_INPUT_DISPATCH_GUARD_ENTRIES = 128
}
