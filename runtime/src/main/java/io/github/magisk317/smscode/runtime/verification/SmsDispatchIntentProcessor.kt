package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.smscode.verification.SmsParseResult

import android.content.Context
import android.content.Intent
import io.github.magisk317.xposed.logging.MagiskOtel

class SmsDispatchIntentProcessor<M : SmsMessage>(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val incomingSmsParser: (Intent) -> M?,
    private val blacklistMatcher: (Context, String?, String?) -> BlacklistMatchResult,
    private val codeParser: (Context, Context, Intent, String) -> SmsParseResult?,
    private val decisionEvaluator: (BlacklistMatchResult, Boolean, SmsParseResult?) -> SmsHandlerDispatchDecision.Decision =
        { blacklistResult, smsMsgAvailable, parseResult ->
            SmsHandlerDispatchDecision.evaluate(
                blacklistMatched = blacklistResult.matched,
                blacklistActionDelete = blacklistResult.actionDelete,
                blacklistActionBlock = blacklistResult.actionBlock,
                smsMsgAvailable = smsMsgAvailable,
                parseResultBlockSms = parseResult?.isBlockSms,
            )
        },
) {
    data class Outcome<M : SmsMessage>(
        val smsMsg: M?,
        val blacklistResult: BlacklistMatchResult,
        val parseResult: SmsParseResult?,
        val decision: SmsHandlerDispatchDecision.Decision,
    )

    fun handle(intent: Intent, eventId: String): Outcome<M> {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "event_id_present" to eventId.isNotBlank().toString(),
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "sms.process", attributes = attrs, statusOk = statusOk)
        }

        val smsMsg = incomingSmsParser(intent)
        val blacklistResult = blacklistMatcher(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag sms blacklist matched: event_id=%s type=%s, pattern=%s, delete=%s, block=%s",
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
        }

        val parseResult = codeParser(pluginContext, phoneContext, intent, eventId)
        if (parseResult == null) {
            XLog.d("Diag parse result is null: event_id=%s no code matched or parse failed", eventId)
        } else {
            XLog.i("Diag parse result: event_id=%s blockSms=%s", eventId, parseResult.isBlockSms)
        }

        val decision = decisionEvaluator(
            blacklistResult,
            smsMsg != null,
            parseResult,
        )
        val reason = when {
            blacklistResult.matched && decision.blockReason != null -> "blacklist_block"
            blacklistResult.matched -> "blacklist_hit"
            parseResult == null -> "no_code"
            else -> "processed"
        }
        emit(
            result = if (parseResult == null && !blacklistResult.matched) "skip" else "ok",
            reason = reason,
        )
        return Outcome(
            smsMsg = smsMsg,
            blacklistResult = blacklistResult,
            parseResult = parseResult,
            decision = decision,
        )
    }
}
