package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

class SmsBlockEvaluator<M : SmsMessage>(
    private val incomingSmsParser: (Intent) -> M?,
    private val blacklistMatcher: (Context, String?, String?) -> BlacklistMatchResult,
    private val blockSmsEnabledReader: (Context) -> Boolean,
    private val smsCodeParser: suspend (Context, String) -> String?,
) {
    data class Result<M : SmsMessage>(
        val smsMsg: M?,
        val blockReason: SmsHandlerDispatchDecision.BlockReason?,
        val blacklistDeleteOnly: Boolean,
    ) {
        val blockReasonWireValue: String?
            get() = blockReason?.wireValue
    }

    fun evaluate(
        pluginContext: Context,
        intent: Intent,
        eventId: String,
        source: String,
    ): Result<M>? {
        if (!SmsIntentHookSupport.isSmsAction(intent.action)) {
            return null
        }

        val smsMsg = runCatching { incomingSmsParser(intent) }.getOrNull()
        val blacklistResult = blacklistMatcher(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag %s blacklist matched: event_id=%s type=%s pattern=%s delete=%s block=%s",
                source,
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
        }
        val deleteOnlyBlacklist = blacklistResult.matched && blacklistResult.actionDelete
        val body = smsMsg?.body.orEmpty()
        val shouldParseCode =
            body.isNotBlank() && (blacklistResult.actionBlock || blockSmsEnabledReader(pluginContext))
        val smsCode = if (shouldParseCode) {
            runBlocking { smsCodeParser(pluginContext, body) }.orEmpty()
        } else {
            ""
        }
        if (blacklistResult.actionBlock) {
            if (smsCode.isNotBlank()) {
                XLog.w(
                    "Diag %s blacklist block contains code: event_id=%s codeLength=%d",
                    source,
                    eventId,
                    smsCode.length,
                )
                return Result(
                    smsMsg = smsMsg,
                    blockReason = SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK,
                    blacklistDeleteOnly = false,
                )
            }
            return Result(
                smsMsg = smsMsg,
                blockReason = SmsHandlerDispatchDecision.BlockReason.BLACKLIST,
                blacklistDeleteOnly = false,
            )
        }

        if (!shouldParseCode) {
            return Result(
                smsMsg = smsMsg,
                blockReason = null,
                blacklistDeleteOnly = deleteOnlyBlacklist,
            )
        }

        if (smsCode.isBlank()) {
            return Result(
                smsMsg = smsMsg,
                blockReason = null,
                blacklistDeleteOnly = deleteOnlyBlacklist,
            )
        }

        XLog.w(
            "Diag %s early block candidate: event_id=%s codeLength=%d",
            source,
            eventId,
            smsCode.length,
        )
        return Result(
            smsMsg = smsMsg,
            blockReason = SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK,
            blacklistDeleteOnly = deleteOnlyBlacklist,
        )
    }
}
