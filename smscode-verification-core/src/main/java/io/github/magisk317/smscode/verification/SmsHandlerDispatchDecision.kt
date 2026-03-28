package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision.BlockReason

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
        if (blacklistMatched) {
            if (blacklistActionBlock) {
                return Decision(
                    shouldDeleteByBlacklist = false,
                    blockReason = BlockReason.BLACKLIST,
                )
            }
            if (blacklistActionDelete && smsMsgAvailable) {
                return Decision(
                    shouldDeleteByBlacklist = true,
                    blockReason = null,
                )
            }
        }

        if (parseResultBlockSms == true) {
            return Decision(
                shouldDeleteByBlacklist = false,
                blockReason = BlockReason.PREF_BLOCK,
            )
        }

        return Decision(
            shouldDeleteByBlacklist = false,
            blockReason = null,
            shouldAllowSystemPersist = parseResultBlockSms != null,
        )
    }
}
