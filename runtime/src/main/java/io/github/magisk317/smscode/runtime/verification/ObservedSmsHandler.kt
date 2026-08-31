package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.smscode.verification.SmsMessageDedupKeys

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel

class ObservedSmsHandler<M : SmsMessage>(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val settingsLoader: (Context) -> SmsCodePostParseCoordinator.Settings,
    private val planFactory: (SmsCodePostParseCoordinator.Settings) -> SmsCodePostParseCoordinator.ObservedSmsPlan =
        SmsCodePostParseCoordinator::createObservedSmsPlan,
    private val moduleEnabledReader: (Context) -> Boolean,
    private val conflictSuppressor: (Context, String) -> Boolean,
    private val sharedGateClaimer: (Context, String, String, Long, Int) -> ClaimResult,
    private val roleStateLogger: (String) -> Unit = {},
    private val duplicateChecker: (SmsCodePostParseCoordinator.Settings, String, String, Long) -> Boolean,
    private val smsEnricher: (Context, ObservedInboxScanRecord) -> M,
    private val dispatcher: (
        Context,
        Context,
        M,
        String,
        SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) -> Unit,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
    )

    data class Outcome(
        val eventId: String,
        val decision: SmsInboxObserverDecision.Decision,
        val dispatched: Boolean,
    )

    fun handle(record: ObservedInboxScanRecord): Outcome {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "body_length" to record.body.length.toString(),
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "sms.observe", attributes = attrs, statusOk = statusOk)
        }

        val settings = settingsLoader(pluginContext)
        val plan = planFactory(settings)
        val eventId = buildObservedEventId(record.smsId, record.date)
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = moduleEnabledReader(pluginContext),
            suppressedByRelay = conflictSuppressor(phoneContext, OBSERVER_CONFLICT_SOURCE),
            duplicated = duplicateChecker(
                settings,
                record.sender,
                record.body,
                record.date,
            ),
            autoInputEnabled = plan.autoInputEnabled,
            shouldRecord = plan.shouldRecord,
            deduplicateSmsEnabled = plan.deduplicateSmsEnabled,
        )

        when (decision.skipReason) {
            SmsInboxObserverDecision.SkipReason.CONFLICT_SUPPRESSED -> {
                XLog.w("Diag observer conflict skip: event_id=%s sms_id=%d", eventId, record.smsId)
                emit(result = "skip", reason = "conflict_suppressed")
                return Outcome(eventId = eventId, decision = decision, dispatched = false)
            }

            SmsInboxObserverDecision.SkipReason.MODULE_DISABLED -> {
                XLog.w("Diag observer skip: module disabled event_id=%s", eventId)
                emit(result = "skip", reason = "module_disabled")
                return Outcome(eventId = eventId, decision = decision, dispatched = false)
            }

            SmsInboxObserverDecision.SkipReason.DUPLICATED -> {
                XLog.d("Diag observer duplicate skip: event_id=%s", eventId)
                emit(result = "skip", reason = "duplicate")
                return Outcome(eventId = eventId, decision = decision, dispatched = false)
            }

            null -> Unit
        }

        if (record.read) {
            XLog.d(
                "Diag observer skip: sms already read event_id=%s sms_id=%d uri=%s",
                eventId,
                record.smsId,
                record.triggerUri,
            )
            emit(result = "skip", reason = "already_read")
            return Outcome(eventId = eventId, decision = decision, dispatched = false)
        }
        if (!claimObservedSms(eventId, record)) {
            emit(result = "skip", reason = "claim_failed")
            return Outcome(eventId = eventId, decision = decision, dispatched = false)
        }

        roleStateLogger(eventId)

        val smsMsg = smsEnricher(phoneContext, record)

        if (decision.autoInputEnabled) {
            XLog.i(
                "Diag observer auto-input: event_id=%s sender_hash=%s read=%s uri=%s simSlot=%d subId=%d",
                eventId,
                senderHash(record.sender),
                record.read,
                record.triggerUri,
                record.simSlot,
                record.subId,
            )
        } else {
            XLog.d("Diag observer auto-input disabled: event_id=%s", eventId)
        }

        decision.recordSkipReason?.let { reason ->
            XLog.d("Diag observer record skipped: reason=%s event_id=%s", reason.wireValue, eventId)
        }

        dispatcher(pluginContext, phoneContext, smsMsg, eventId, plan)
        emit(result = "ok")
        return Outcome(eventId = eventId, decision = decision, dispatched = true)
    }

    private fun buildObservedEventId(smsId: Long, date: Long): String {
        val ts = if (date > 0) date else currentTimeMillis()
        return "sms_observed_${ts.toString(EVENT_ID_RADIX)}_${smsId.toString(EVENT_ID_RADIX)}"
    }

    private fun claimObservedSms(
        eventId: String,
        record: ObservedInboxScanRecord,
    ): Boolean {
        val key = SmsMessageDedupKeys.buildObservedKey(
            smsId = record.smsId,
            date = record.date,
            sender = record.sender,
            body = record.body,
            code = record.code,
        )
        val claim = sharedGateClaimer(
            pluginContext,
            SHARED_OBSERVED_SMS_FILE_NAME,
            key,
            OBSERVED_SMS_DEDUP_WINDOW_MS,
            MAX_TRACKED_SMS_IDS,
        )
        if (claim.claimed) {
            return true
        }
        XLog.d(
            "Diag observer dedup skip: event_id=%s key=%s ageMs=%d",
            eventId,
            key,
            claim.ageMs ?: -1L,
        )
        return false
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    private companion object {
        private const val EVENT_ID_RADIX = 36
        private const val OBSERVED_SMS_DEDUP_WINDOW_MS = 30_000L
        private const val MAX_TRACKED_SMS_IDS = 128
        private const val OBSERVER_CONFLICT_SOURCE = "SmsInboxObserver#handleObservedCode"
        private const val SHARED_OBSERVED_SMS_FILE_NAME = "observed_sms_dedup"
    }
}
