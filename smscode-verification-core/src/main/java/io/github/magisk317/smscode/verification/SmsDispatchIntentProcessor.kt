package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.xposed.utils.XLog

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
            XLog.w("Diag parse result is null: event_id=%s no code matched or parse failed", eventId)
        } else {
            XLog.w("Diag parse result: event_id=%s blockSms=%s", eventId, parseResult.isBlockSms)
        }

        val decision = decisionEvaluator(
            blacklistResult,
            smsMsg != null,
            parseResult,
        )
        return Outcome(
            smsMsg = smsMsg,
            blacklistResult = blacklistResult,
            parseResult = parseResult,
            decision = decision,
        )
    }
}
