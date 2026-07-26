package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision.BlockReason
import io.github.magisk317.xposed.logging.MagiskOtel

object SmsHandlerDispatchDecision {
    enum class BlockReason(val wireValue: String) {
        BLACKLIST("blacklist_block"),
        PREF_BLOCK("pref_block_sms"),
    }

    data class Decision(
        val shouldDeleteByBlacklist: Boolean,
        val blockReason: BlockReason? = null,
        val shouldAllowSystemPersist: Boolean = false,
    )

    fun evaluate(
        blacklistMatched: Boolean,
        blacklistActionDelete: Boolean,
        blacklistActionBlock: Boolean,
        smsMsgAvailable: Boolean,
        parseResultBlockSms: Boolean?,
    ): Decision {
        val decision = when {
            blacklistMatched && blacklistActionBlock -> Decision(
                shouldDeleteByBlacklist = false,
                blockReason = BlockReason.BLACKLIST,
            )
            blacklistMatched && blacklistActionDelete && smsMsgAvailable -> Decision(
                shouldDeleteByBlacklist = true,
                blockReason = null,
            )
            parseResultBlockSms == true -> Decision(
                shouldDeleteByBlacklist = false,
                blockReason = BlockReason.PREF_BLOCK,
            )
            else -> Decision(
                shouldDeleteByBlacklist = false,
                blockReason = null,
                shouldAllowSystemPersist = parseResultBlockSms != null,
            )
        }

        val reason = when {
            decision.blockReason == BlockReason.BLACKLIST -> "blacklist_block"
            decision.shouldDeleteByBlacklist -> "blacklist_delete"
            decision.blockReason == BlockReason.PREF_BLOCK -> "pref_block_sms"
            decision.shouldAllowSystemPersist -> "allow_persist"
            else -> "pass"
        }
        MagiskOtel.event(
            name = "sms.process",
            attributes = mapOf(
                "result" to if (decision.blockReason != null || decision.shouldDeleteByBlacklist) "skip" else "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "dispatch_decision",
                "reason" to reason,
            ),
            statusOk = true,
        )
        return decision
    }
}
