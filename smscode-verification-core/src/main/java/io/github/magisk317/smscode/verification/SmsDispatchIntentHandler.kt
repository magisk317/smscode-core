package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.xposed.utils.XLog

class SmsDispatchIntentHandler<M : SmsMessage>(
    private val runtimeResolver: (String) -> VerificationRuntimeContext?,
    private val moduleEnabledReader: (Context) -> Boolean,
    private val conflictSuppressor: (Context, String) -> Boolean,
    private val dispatchProcessor: (Context, Context, Intent, String) -> SmsDispatchIntentProcessor.Outcome<M>,
    private val conflictNotifier: (Context, Context, String, String) -> Unit,
    private val suppressionLogger: (String) -> Unit = {},
    private val blacklistDeleteScheduler: (Context, Context, M) -> Unit = { _, _, _ -> },
    private val inboundBlocker: (Any, Any, String, String) -> Unit = { _, _, _, _ -> },
    private val gateEvaluator: (Boolean, Boolean) -> DispatchGateDecision = ::defaultGateDecision,
) {
    enum class StopReason {
        RUNTIME_UNAVAILABLE,
        MODULE_DISABLED,
        CONFLICT_SUPPRESSED,
        SMS_BLOCKED,
    }

    data class Outcome(
        val stopReason: StopReason? = null,
        val inboundBlocked: Boolean = false,
    ) {
        val shouldStopDispatch: Boolean = stopReason != null
    }

    fun handle(
        intent: Intent,
        eventId: String,
        inboundSmsHandler: Any?,
        receiver: Any?,
    ): Outcome {
        val runtime = runtimeResolver(DISPATCH_HEARTBEAT_SOURCE)
        if (runtime == null) {
            XLog.e("Context is null, skip parsing. pluginContext: %s, phoneContext: %s", null, null)
            return Outcome(stopReason = StopReason.RUNTIME_UNAVAILABLE)
        }
        val pluginContext = runtime.pluginContext
        val phoneContext = runtime.phoneContext
        when (
            gateEvaluator(
                moduleEnabledReader(pluginContext),
                conflictSuppressor(phoneContext, DISPATCH_CONFLICT_SOURCE),
            ).reason
        ) {
            DispatchGateReason.MODULE_DISABLED -> {
                XLog.w("Diag: module disabled in settings")
                XLog.i("XposedSmsCode disabled, exiting")
                return Outcome(stopReason = StopReason.MODULE_DISABLED)
            }

            DispatchGateReason.CONFLICT_SUPPRESSED -> {
                suppressionLogger(DISPATCH_STAGE)
                conflictNotifier(
                    pluginContext,
                    phoneContext,
                    eventId,
                    DISPATCH_CONFLICT_SOURCE,
                )
                return Outcome(stopReason = StopReason.CONFLICT_SUPPRESSED)
            }

            else -> Unit
        }

        val dispatchOutcome = dispatchProcessor(pluginContext, phoneContext, intent, eventId)
        val smsMsg = dispatchOutcome.smsMsg
        val decision = dispatchOutcome.decision
        if (decision.shouldDeleteByBlacklist && smsMsg != null) {
            blacklistDeleteScheduler(pluginContext, phoneContext, smsMsg)
        }
        decision.blockReason?.let { blockReason ->
            XLog.w("Diag sms block reason=%s event_id=%s", blockReason.wireValue, eventId)
            if (inboundSmsHandler != null && receiver != null) {
                inboundBlocker(
                    inboundSmsHandler,
                    receiver,
                    blockReason.wireValue,
                    eventId,
                )
                return Outcome(
                    stopReason = StopReason.SMS_BLOCKED,
                    inboundBlocked = true,
                )
            }
            return Outcome(stopReason = StopReason.SMS_BLOCKED)
        }
        if (decision.shouldAllowSystemPersist) {
            XLog.w(
                "Diag allow system inbox persist: event_id=%s sender_hash=%s body_len=%d",
                eventId,
                senderHash(smsMsg?.sender),
                smsMsg?.body?.length ?: 0,
            )
        }
        return Outcome()
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    private companion object {
        private const val DISPATCH_HEARTBEAT_SOURCE = "sms_handler_dispatch"
        private const val DISPATCH_CONFLICT_SOURCE = "SmsHandlerHook#dispatchIntent"
        private const val DISPATCH_STAGE = "dispatchIntent"
    }
}

private fun defaultGateDecision(
    moduleEnabled: Boolean,
    suppressedByRelay: Boolean,
): DispatchGateDecision {
    if (!moduleEnabled) {
        return DispatchGateDecision.moduleDisabled()
    }
    if (suppressedByRelay) {
        return DispatchGateDecision.conflictSuppressed()
    }
    return DispatchGateDecision.allow()
}
