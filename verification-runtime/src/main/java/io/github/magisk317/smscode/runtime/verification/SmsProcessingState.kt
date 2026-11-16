package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsProcessingClaims

/** Shared by live dispatch and inbox observation in this module ClassLoader. */
object SmsProcessingState {
    // Observer dispatches only auto-input and recording. Keep live-only actions separate so
    // an observer winning the race cannot suppress clipboard or notification delivery.
    val claims = SmsProcessingClaims()
    val liveClaims = SmsProcessingClaims()

    fun shouldResumeLiveActions(sender: String, body: String, date: Long): Boolean =
        claims.hasClaim(sender, body, date) && !liveClaims.isCompleted(sender, body, date)

    /** Release state when the owning hook is unloaded, never between messages. */
    fun clear() {
        claims.clear()
        liveClaims.clear()
    }
}
