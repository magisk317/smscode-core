package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.smscode.verification.SmsMessageDedupKeys
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult
import io.github.magisk317.smscode.verification.SmsProcessingClaims

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

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
    private val duplicateChecker: (SmsCodePostParseCoordinator.Settings, String, String, Long) -> SmsDuplicateCheckResult,
    private val smsEnricher: (Context, ObservedInboxScanRecord) -> M,
    private val dispatcher: (
        Context,
        Context,
        M,
        String,
        SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) -> Unit,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val processingClaims: SmsProcessingClaims = SmsProcessingState.claims,
) {
    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
    )

    data class Outcome(
        val eventId: String,
        val decision: SmsInboxObserverDecision.Decision,
        val dispatched: Boolean,
        val retryable: Boolean = false,
    )

    // Retain an acquired provider gate across a local scheduling failure. The provider has no
    // release operation; reacquiring immediately would mistake our own reservation for delivery.
    // Entries are bounded and live no longer than that provider gate's 30-second window.
    private val acceptedSharedClaims = LinkedHashMap<String, Long>()

    fun handle(record: ObservedInboxScanRecord): Outcome {
        val settings = settingsLoader(pluginContext)
        val plan = planFactory(settings)
        val eventId = buildObservedEventId(record.smsId, record.date)
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = moduleEnabledReader(pluginContext),
            suppressedByRelay = conflictSuppressor(phoneContext, OBSERVER_CONFLICT_SOURCE),
            duplicateCheckResult = SmsDuplicateCheckResult.NOT_DUPLICATE,
            autoInputEnabled = plan.autoInputEnabled,
            shouldRecord = plan.shouldRecord,
            deduplicateSmsEnabled = plan.deduplicateSmsEnabled,
        )
        if (!decision.shouldProceed || record.read) {
            return Outcome(eventId, decision, dispatched = false)
        }
        val claim = processingClaims.claim(record.sender, record.body, record.date)
        val lease = when (claim) {
            is SmsProcessingClaims.ClaimResult.Acquired -> claim.lease
            SmsProcessingClaims.ClaimResult.Completed -> return Outcome(
                eventId, decision.copy(skipReason = SmsInboxObserverDecision.SkipReason.DUPLICATED), false,
            )
            SmsProcessingClaims.ClaimResult.InFlight,
            SmsProcessingClaims.ClaimResult.CapacityExceeded,
            -> return Outcome(eventId, decision, dispatched = false, retryable = true)
        }
        return handleClaimed(record, eventId, settings, plan, decision, lease)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleClaimed(
        record: ObservedInboxScanRecord,
        eventId: String,
        settings: SmsCodePostParseCoordinator.Settings,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
        decision: SmsInboxObserverDecision.Decision,
        lease: SmsProcessingClaims.Lease,
    ): Outcome {
        try {
            val duplicate = if (plan.deduplicateSmsEnabled) {
                runCatching { duplicateChecker(settings, record.sender, record.body, record.date) }
                    .getOrElse { error ->
                        XLog.w("Observer duplicate lookup failed: type=%s", error.javaClass.simpleName)
                        SmsDuplicateCheckResult.UNAVAILABLE
                    }
            } else {
                SmsDuplicateCheckResult.NOT_DUPLICATE
            }
            if (duplicate == SmsDuplicateCheckResult.DUPLICATE) {
                processingClaims.complete(lease)
                return Outcome(
                    eventId, decision.copy(skipReason = SmsInboxObserverDecision.SkipReason.DUPLICATED), false,
                )
            }
            if (duplicate == SmsDuplicateCheckResult.UNAVAILABLE && !record.receivedDuringObservation) {
                processingClaims.release(lease)
                emit("skip", "historical_lookup_unavailable")
                return Outcome(
                    eventId,
                    decision.copy(skipReason = SmsInboxObserverDecision.SkipReason.DUPLICATE_CHECK_UNAVAILABLE),
                    dispatched = false,
                    retryable = true,
                )
            }
            val normalizedRecord = if (record.date > 0) record else record.copy(date = currentTimeMillis())
            val smsMsg = smsEnricher(phoneContext, normalizedRecord)
            if (!claimObservedSms(record)) {
                processingClaims.release(lease)
                return Outcome(eventId, decision, dispatched = false)
            }
            if (!processingClaims.isActive(lease)) {
                emit("skip", "stale_processing_lease")
                return Outcome(eventId, decision, dispatched = false, retryable = true)
            }
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Observer attempt abandoned")
            }
            roleStateLogger(eventId)
            dispatcher(pluginContext, phoneContext, smsMsg, eventId, plan)
            processingClaims.complete(lease)
            synchronized(acceptedSharedClaims) { acceptedSharedClaims.remove(claimKey(record)) }
            emit("ok", if (duplicate == SmsDuplicateCheckResult.UNAVAILABLE) "degraded_dispatch" else "dispatch")
            return Outcome(eventId, decision, dispatched = true)
        } catch (error: Exception) {
            processingClaims.release(lease)
            XLog.w("Observer dispatch not accepted: type=%s", error.javaClass.simpleName)
            emit("skip", "dispatch_retry")
            return Outcome(eventId, decision, dispatched = false, retryable = true)
        }
    }

    private fun emit(result: String, reason: String) {
        MagiskOtel.event(
            name = "sms.observe",
            attributes = mapOf("result" to result, "reason" to reason, "process" to "hook"),
            statusOk = true,
        )
    }

    private fun buildObservedEventId(smsId: Long, date: Long): String {
        val ts = if (date > 0) date else currentTimeMillis()
        return "sms_observed_${ts.toString(EVENT_ID_RADIX)}_${smsId.toString(EVENT_ID_RADIX)}"
    }

    private fun claimObservedSms(record: ObservedInboxScanRecord): Boolean {
        val reservationKey = claimKey(record)
        val now = System.nanoTime() / NANOS_PER_MILLISECOND
        synchronized(acceptedSharedClaims) {
            acceptedSharedClaims.entries.removeAll { now - it.value >= OBSERVED_SMS_DEDUP_WINDOW_MS }
            if (acceptedSharedClaims.containsKey(reservationKey)) return true
            check(acceptedSharedClaims.size < MAX_TRACKED_SMS_IDS) { "Observer claim capacity exceeded" }
        }
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
            synchronized(acceptedSharedClaims) { acceptedSharedClaims[reservationKey] = now }
        }
        return claim.claimed
    }

    private fun claimKey(record: ObservedInboxScanRecord): String = "${record.smsId}:${record.date}"

    private companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val EVENT_ID_RADIX = 36
        private const val OBSERVED_SMS_DEDUP_WINDOW_MS = 30_000L
        private const val MAX_TRACKED_SMS_IDS = 128
        private const val OBSERVER_CONFLICT_SOURCE = "SmsInboxObserver#handleObservedCode"
        private const val SHARED_OBSERVED_SMS_FILE_NAME = "observed_sms_dedup"
    }
}
