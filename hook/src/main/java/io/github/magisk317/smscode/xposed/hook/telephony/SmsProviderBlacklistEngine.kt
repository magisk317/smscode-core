package io.github.magisk317.smscode.xposed.hook.telephony

import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.xposed.logging.XLog

/**
 * Shared decision engine for the provider-level blacklist gate.
 *
 * Xiaomi network SMS is inserted straight into `content://sms` and never passes
 * the telephony dispatch chain, so the provider write is the only choke point
 * that sees it. Hosts (XSC / xinyi-relay) read their own gate booleans and
 * supply their own blacklist matcher — prefs bridges and config resolution are
 * host-owned infrastructure — and this engine owns everything that must not
 * drift between hosts: the gate ordering, the hit semantics and the diag
 * attribution.
 *
 * Gate ordering mirrors the dispatch chain: module switch, then mobile
 * entitlement, then relay-conflict arbitration, then the blacklist.
 */
object SmsProviderBlacklistEngine {
    const val BLOCK_REASON = "blacklist_block"
    const val SOURCE = "sms_provider"

    /** Pure decision plus the match that triggered it, for host-side attribution needs. */
    data class Decision(
        val write: SmsProviderWriteDecision,
        val matched: BlacklistMatchResult?,
    )

    /**
     * Android-side entry: matches every inbound draft through the host matcher
     * and applies [decide]. A matcher failure degrades that draft to "no match"
     * so a rule-store problem never blocks an allowed notification.
     */
    fun evaluate(
        match: (sender: String?, body: String?) -> BlacklistMatchResult,
        enabled: Boolean,
        entitlementAllowed: Boolean,
        conflictSuppressed: Boolean,
        drafts: List<SmsProviderDraft>,
    ): Decision {
        val matches = if (!enabled || !entitlementAllowed || conflictSuppressed) {
            emptyList()
        } else {
            drafts.map { draft ->
                runCatching { match(draft.address, draft.body) }
                    .getOrDefault(BlacklistMatchResult(matched = false))
            }
        }
        return decide(enabled, entitlementAllowed, conflictSuppressed, matches)
    }

    /**
     * Pure decision core. A match counts when its entry asks for delete or
     * block; delete-only entries cancel the provider write too because the row
     * never lands, which is the stronger form of the dispatch path's
     * post-write raw-table cleanup. Mixed bulk drafts attribute the first
     * hitting entry and cancel the whole atomic call.
     */
    fun decide(
        enabled: Boolean,
        entitlementAllowed: Boolean,
        conflictSuppressed: Boolean,
        matches: List<BlacklistMatchResult>,
    ): Decision {
        if (!enabled || !entitlementAllowed || conflictSuppressed) {
            return Decision(SmsProviderWriteDecision.Allow, null)
        }
        matches.forEach { result ->
            if (result.matched && (result.actionBlock || result.actionDelete)) {
                XLog.w(
                    message = "Diag %s blacklist matched: pattern=%s delete=%s block=%s",
                    SOURCE,
                    result.pattern,
                    result.actionDelete,
                    result.actionBlock,
                )
                return Decision(SmsProviderWriteDecision.Block(BLOCK_REASON), result)
            }
        }
        return Decision(SmsProviderWriteDecision.Allow, null)
    }
}
