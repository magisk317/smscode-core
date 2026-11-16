package io.github.magisk317.smscode.xposed.hook.telephony

/**
 * One inbound SMS row about to be written through the sms provider.
 *
 * Exists because Xiaomi network SMS is inserted straight into `content://sms`
 * and never passes the telephony dispatch chain, so the only choke point left
 * is the provider write itself.
 */
data class SmsProviderDraft(
    val address: String?,
    val body: String?,
    val date: Long?,
    val type: Int?,
)

/** Host decision for an inbound provider write; the shared layer only executes it. */
sealed interface SmsProviderWriteDecision {
    data object Allow : SmsProviderWriteDecision

    /** Cancels the provider write; [reason] flows into diagnostics and hit records. */
    data class Block(val reason: String) : SmsProviderWriteDecision
}
