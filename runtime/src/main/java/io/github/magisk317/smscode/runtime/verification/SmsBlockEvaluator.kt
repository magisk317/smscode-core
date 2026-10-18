package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsMessage

import android.content.Context
import android.content.Intent
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.runBlocking
import io.github.magisk317.xposed.logging.MagiskOtel

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
        val blacklistResult: BlacklistMatchResult,
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
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null, codeLength: Int? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "source" to source,
                "event_id_present" to eventId.isNotBlank().toString(),
            )
            if (reason != null) attrs["reason"] = reason
            if (codeLength != null) attrs["code_length"] = codeLength.toString()
            MagiskOtel.event(name = "sms.block", attributes = attrs, statusOk = statusOk)
        }

        if (!SmsIntentHookSupport.isSmsAction(intent.action)) {
            return null
        }

        val smsMsg = runCatching { incomingSmsParser(intent) }.getOrNull()
        val blacklistResult = blacklistMatcher(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                message = "Diag %s blacklist matched: event_id=%s type=%s pattern=%s delete=%s block=%s",
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
                    message = "Diag %s blacklist block contains code: event_id=%s codeLength=%d",
                    source,
                    eventId,
                    smsCode.length,
                )
                emit(result = "ok", reason = "pref_block", codeLength = smsCode.length)
                return Result(
                    smsMsg = smsMsg,
                    blockReason = SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK,
                    blacklistDeleteOnly = false,
                    blacklistResult = blacklistResult,
                )
            }
            emit(result = "ok", reason = "blacklist_block")
            return Result(
                smsMsg = smsMsg,
                blockReason = SmsHandlerDispatchDecision.BlockReason.BLACKLIST,
                blacklistDeleteOnly = false,
                blacklistResult = blacklistResult,
            )
        }

        if (!shouldParseCode) {
            emit(
                result = "skip",
                reason = if (deleteOnlyBlacklist) "delete_only" else "no_block",
            )
            return Result(
                smsMsg = smsMsg,
                blockReason = null,
                blacklistDeleteOnly = deleteOnlyBlacklist,
                blacklistResult = blacklistResult,
            )
        }

        if (smsCode.isBlank()) {
            emit(result = "skip", reason = "no_code")
            return Result(
                smsMsg = smsMsg,
                blockReason = null,
                blacklistDeleteOnly = deleteOnlyBlacklist,
                blacklistResult = blacklistResult,
            )
        }

        XLog.w(
            message = "Diag %s early block candidate: event_id=%s codeLength=%d",
            source,
            eventId,
            smsCode.length,
        )
        emit(result = "ok", reason = "pref_block", codeLength = smsCode.length)
        return Result(
            smsMsg = smsMsg,
            blockReason = SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK,
            blacklistDeleteOnly = deleteOnlyBlacklist,
            blacklistResult = blacklistResult,
        )
    }
}
