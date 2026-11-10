package io.github.magisk317.smscode.runtime.verification

import android.os.Build
import android.util.Log
import android.telephony.SubscriptionManager
import io.github.magisk317.xposed.runtime.contract.logging.LogRoute
import io.github.magisk317.xposed.logging.XLog

/** A routing row already persisted for a stored SMS. */
data class SmsRoutingCandidate(
    val id: Long,
    val sender: String,
    val body: String,
    val date: Long,
    val simSlot: Int,
    val subId: Int,
)

/** The persisted row the backfill decided to repair, with how it was matched. */
data class SmsRoutingMatch(
    val recordId: Long,
    val simSlot: Int,
    val subId: Int,
    val timestampDeltaMs: Long,
    val matchMode: String,
)

/** Host transport: how to read recent rows and patch routing on one row. */
interface SmsRoutingStore {
    fun findRecentSms(limit: Int): List<SmsRoutingCandidate>
    fun updateRouting(recordId: Long, simSlot: Int, subId: Int): Int
}

/**
 * Conservative SIM-routing backfill for observed inbox records.
 *
 * Policy (historically XSC's, now shared):
 *  - skip records that carry no routing hint at all;
 *  - prefer the closest exact sender+body match inside the window;
 *  - fall back to a single time-close row only when unambiguous;
 *  - patch only the missing pieces of the matched row.
 */
object SmsRoutingBackfill {
    const val MATCH_EXACT = "exact"
    const val MATCH_TIME_FALLBACK = "time_fallback"
    const val DEFAULT_WINDOW_MS = 30 * 60 * 1000L
    const val DEFAULT_QUERY_LIMIT = 64

    /** SubscriptionManager-backed slot recovery with the subId 1→0 / 2→1 fallback. */
    fun resolveBackfillSimSlot(simSlot: Int, subId: Int): Int {
        if (simSlot >= 0) return simSlot
        if (subId <= 0) return -1
        val platformSlot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { SubscriptionManager.getSlotIndex(subId) }.getOrDefault(-1)
        } else {
            -1
        }
        if (platformSlot >= 0) return platformSlot
        return when (subId) {
            1 -> 0
            2 -> 1
            else -> -1
        }
    }

    fun findMatch(
        recordSender: String,
        recordBody: String,
        timestampMs: Long,
        candidates: List<SmsRoutingCandidate>,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): SmsRoutingMatch? {
        var bestExactMatch: SmsRoutingMatch? = null
        val timeFallbackMatches = mutableListOf<SmsRoutingCandidate>()
        for (candidate in candidates) {
            val delta = kotlin.math.abs(candidate.date - timestampMs)
            if (delta > windowMs) continue
            val matchMode = if (candidate.sender == recordSender && candidate.body == recordBody) {
                MATCH_EXACT
            } else {
                MATCH_TIME_FALLBACK
            }
            if (matchMode == MATCH_EXACT) {
                val current = bestExactMatch
                if (current == null || delta < current.timestampDeltaMs) {
                    bestExactMatch = SmsRoutingMatch(
                        recordId = candidate.id,
                        simSlot = candidate.simSlot,
                        subId = candidate.subId,
                        timestampDeltaMs = delta,
                        matchMode = matchMode,
                    )
                }
            } else if (candidate.simSlot < 0 || candidate.subId <= 0) {
                timeFallbackMatches += candidate
            }
        }
        bestExactMatch?.let { return it }
        if (timeFallbackMatches.size == 1) {
            val single = timeFallbackMatches.single()
            return SmsRoutingMatch(
                recordId = single.id,
                simSlot = single.simSlot,
                subId = single.subId,
                timestampDeltaMs = kotlin.math.abs(single.date - timestampMs),
                matchMode = MATCH_TIME_FALLBACK,
            )
        }
        if (timeFallbackMatches.size > 1) {
            XLog.d(
                message = "Diag SMS routing backfill skip: reason=ambiguous_time_fallback candidates=%d",
                timeFallbackMatches.size,
            )
        }
        return null
    }

    /** Full backfill for one observed record; returns true when a row was patched. */
    fun backfill(
        smsId: Long,
        sender: String,
        body: String,
        recordSimSlot: Int,
        recordSubId: Int,
        timestampMs: Long,
        store: SmsRoutingStore,
        windowMs: Long = DEFAULT_WINDOW_MS,
        queryLimit: Int = DEFAULT_QUERY_LIMIT,
    ): Boolean {
        val backfillSimSlot = resolveBackfillSimSlot(recordSimSlot, recordSubId)
        if (backfillSimSlot < 0 && recordSubId <= 0) {
            XLog.d(message = "Diag SMS routing backfill skip: sms_id=%d reason=no_routing", smsId)
            return false
        }
        val candidates = store.findRecentSms(queryLimit)
        val existing = findMatch(sender, body, timestampMs, candidates, windowMs) ?: run {
            XLog.d(
                message = "Diag SMS routing backfill skip: sms_id=%d reason=no_existing timestamp=%d simSlot=%d subId=%d",
                smsId,
                timestampMs,
                backfillSimSlot,
                recordSubId,
            )
            return false
        }
        val shouldUpdateSimSlot = existing.simSlot < 0 && backfillSimSlot >= 0
        val shouldUpdateSubId = existing.subId <= 0 && recordSubId > 0
        if (!shouldUpdateSimSlot && !shouldUpdateSubId) {
            XLog.d(
                message = "Diag SMS routing backfill skip: sms_id=%d reason=existing_has_routing oldSlot=%d oldSubId=%d",
                smsId,
                existing.simSlot,
                existing.subId,
            )
            return false
        }
        val newSimSlot = if (shouldUpdateSimSlot) backfillSimSlot else existing.simSlot
        val newSubId = if (shouldUpdateSubId) recordSubId else existing.subId
        val rows = store.updateRouting(existing.recordId, newSimSlot, newSubId)
        XLog.log(
            Log.INFO,
            LogRoute.SMS_HOOK.id,
            false,
            false,
            "Diag SMS routing backfill: sms_id=%d record_id=%d rows=%d oldSlot=%d oldSubId=%d newSlot=%d newSubId=%d",
            smsId,
            existing.recordId,
            rows,
            existing.simSlot,
            existing.subId,
            newSimSlot,
            newSubId,
        )
        XLog.d(
            message = "Diag SMS routing backfill match: sms_id=%d record_id=%d mode=%s deltaMs=%d rawSlot=%d rawSubId=%d",
            smsId,
            existing.recordId,
            existing.matchMode,
            existing.timestampDeltaMs,
            recordSimSlot,
            recordSubId,
        )
        return rows > 0
    }
}
