package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.SmsInboxSeenTracker

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val NOW = 1_762_000_000_000L
private const val WINDOW_MS = 60_000L
private const val ROUTING_WINDOW_MS = 24 * 60 * 60_000L
private const val CODE = "123456"
private const val ONE_SECOND_MS = 1_000L
private const val THIRTY_MINUTES_MS = 30 * 60 * 1000L
private const val TWO_HUNDRED_FORTY_DAYS_MS = 240L * 24 * 60 * 60 * 1000L

class ObservedInboxScannerTest {

    private fun row(
        smsId: Long,
        date: Long,
        body: String = "otp $CODE",
        sender: String = "1068",
        read: Boolean = false,
    ): ObservedInboxScanner.InboxRow = ObservedInboxScanner.InboxRow(
        smsId = smsId,
        sender = sender,
        body = body,
        date = date,
        read = read,
    )

    private fun scanner(
        smsIdTracker: SmsInboxSeenTracker = SmsInboxSeenTracker(maxTrackedSmsIds = 8),
        codeParser: (String) -> String = { body -> if (body.startsWith("otp")) CODE else "" },
        clock: () -> Long = { NOW },
        rowLoader: (Long, Long?) -> List<ObservedInboxScanner.InboxRow>,
    ): ObservedInboxScanner = ObservedInboxScanner(
        pluginContext = mockk<Context>(relaxed = true),
        phoneContext = mockk<Context>(relaxed = true),
        smsIdTracker = smsIdTracker,
        smsCodeParser = { _, body -> codeParser(body) },
        inboxRowLoader = rowLoader,
        currentTimeMillis = clock,
    )

    @Test
    fun scan_filtersDuplicateAndBlankCodeRows() {
        val scanner = scanner(
            rowLoader = { _, _ ->
                listOf(
                    row(smsId = 10L, date = NOW - ONE_SECOND_MS),
                    row(smsId = 10L, date = NOW - ONE_SECOND_MS),
                    row(smsId = 11L, date = NOW - ONE_SECOND_MS, body = "hello", sender = "10086", read = true),
                )
            },
        )

        val result = scanner.scan(triggerUri = "", recentSmsWindowMs = WINDOW_MS)

        assertEquals(1, result.size)
        assertEquals(10L, result[0].smsId)
        assertEquals("1068", result[0].sender)
        assertEquals(CODE, result[0].code)
        assertEquals("content://sms", result[0].triggerUri)
    }

    @Test
    fun scan_keepsExplicitTriggerUri() {
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 12L, date = NOW - ONE_SECOND_MS, body = "otp", sender = "bank")) },
        )

        val result = scanner.scan(triggerUri = "content://sms/inbox/12", recentSmsWindowMs = WINDOW_MS)

        assertEquals(1, result.size)
        assertEquals("content://sms/inbox/12", result.first().triggerUri)
        assertTrue(result.first().code.isNotBlank())
    }

    @Test
    fun scan_passesTriggeredSmsIdToLoader() {
        var receivedTriggeredSmsId: Long? = null
        val scanner = scanner(
            rowLoader = { _, triggeredSmsId ->
                receivedTriggeredSmsId = triggeredSmsId
                listOf(row(smsId = 66L, date = NOW - ONE_SECOND_MS, sender = "bank"))
            },
        )

        val result = scanner.scan(triggerUri = "content://sms/66", recentSmsWindowMs = WINDOW_MS)

        assertEquals(66L, receivedTriggeredSmsId)
        assertEquals(1, result.size)
        assertEquals(66L, result.first().smsId)
    }

    @Test
    fun scan_rejectsMonthsOldRowReachedThroughRowSpecificTrigger() {
        // A provider change to a stored row is queried by id with no date floor, so
        // the recency window has to be enforced here before any action runs.
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 42L, date = NOW - TWO_HUNDRED_FORTY_DAYS_MS)) },
        )

        val result = scanner.scan(triggerUri = "content://sms/inbox/42", recentSmsWindowMs = WINDOW_MS)

        assertEquals(0, result.size)
    }

    @Test
    fun scan_rejectsStaleRowOnTheGeneralBranchToo() {
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 43L, date = NOW - TWO_HUNDRED_FORTY_DAYS_MS)) },
        )

        val result = scanner.scan(triggerUri = "", recentSmsWindowMs = WINDOW_MS)

        assertEquals(0, result.size)
    }

    @Test
    fun scan_rejectsRowExactlyAtTheWindowBoundary() {
        // Mirrors the general branch's strict `date > cutoff`, so an age equal to the
        // window is already outside it.
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 44L, date = NOW - WINDOW_MS)) },
        )

        val result = scanner.scan(triggerUri = "content://sms/44", recentSmsWindowMs = WINDOW_MS)

        assertEquals(0, result.size)
    }

    @Test
    fun scan_acceptsRowJustInsideTheWindow() {
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 45L, date = NOW - WINDOW_MS + ONE_SECOND_MS)) },
        )

        val result = scanner.scan(triggerUri = "content://sms/45", recentSmsWindowMs = WINDOW_MS)

        assertEquals(1, result.size)
        assertEquals(45L, result.first().smsId)
    }

    @Test
    fun scan_acceptsRowWithUnknownDate() {
        // An unset date is unknown, not expired, matching the `date > 0` fallback this
        // path uses when it builds event ids and records.
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 46L, date = 0L)) },
        )

        val result = scanner.scan(triggerUri = "content://sms/46", recentSmsWindowMs = WINDOW_MS)

        assertEquals(1, result.size)
        assertEquals(0L, result.first().date)
    }

    @Test
    fun scan_acceptsFutureDatedRow() {
        // A device clock stepping backwards makes fresh rows look future-dated; that is
        // not a reason to drop a code that just arrived.
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 47L, date = NOW + THIRTY_MINUTES_MS)) },
        )

        val result = scanner.scan(triggerUri = "content://sms/47", recentSmsWindowMs = WINDOW_MS)

        assertEquals(1, result.size)
    }

    @Test
    fun scanRouting_honoursTheCallerWindowNotTheDeliveryWindow() {
        // The routing repair pass asks for a day; a row older than the delivery window
        // must still reach it.
        val scanner = scanner(
            rowLoader = { _, _ -> listOf(row(smsId = 48L, date = NOW - THIRTY_MINUTES_MS)) },
        )

        val result: List<ObservedInboxScanRecord> =
            scanner.scanRouting(triggerUri = "content://sms", recentSmsWindowMs = ROUTING_WINDOW_MS)

        assertEquals(1, result.size)
        assertEquals("", result.first().code)
    }

    @Test
    fun rejectedStaleRowDoesNotConsumeASeenTrackerSlot() {
        var call = 0
        val scanner = scanner(
            rowLoader = { _, _ ->
                call += 1
                if (call == 1) {
                    listOf(row(smsId = 77L, date = NOW - TWO_HUNDRED_FORTY_DAYS_MS))
                } else {
                    listOf(row(smsId = 77L, date = NOW - ONE_SECOND_MS))
                }
            },
        )

        assertEquals(0, scanner.scan(triggerUri = "content://sms/77", recentSmsWindowMs = WINDOW_MS).size)

        val retried = scanner.scan(triggerUri = "content://sms/77", recentSmsWindowMs = WINDOW_MS)

        assertEquals(1, retried.size)
        assertEquals(77L, retried.first().smsId)
    }
    @Test
    fun scan_marksOnlyRowsReceivedWhileThisObserverWasRunning() {
        var now = NOW
        val scanner = scanner(
            clock = { now },
            rowLoader = { _, _ ->
                listOf(
                    row(1L, NOW - 1L),
                    row(2L, NOW),
                    row(3L, NOW + ONE_SECOND_MS),
                    row(4L, 0L),
                    row(5L, NOW + THIRTY_MINUTES_MS),
                )
            },
        )
        now += ONE_SECOND_MS
        val records = scanner.scan("content://sms", WINDOW_MS).associateBy { it.smsId }
        assertFalse(records.getValue(1L).receivedDuringObservation)
        assertTrue(records.getValue(2L).receivedDuringObservation)
        assertTrue(records.getValue(3L).receivedDuringObservation)
        assertFalse(records.getValue(4L).receivedDuringObservation)
        assertFalse(records.getValue(5L).receivedDuringObservation)
    }

    @Test
    fun parserFailureDoesNotConsumeSeenIdAndOtherRowsStillProceed() {
        var unavailable = true
        val scanner = scanner(
            codeParser = { body ->
                if (body == "first" && unavailable) error("temporary parser failure")
                CODE
            },
            rowLoader = { _, _ -> listOf(row(10L, NOW, body = "first"), row(11L, NOW, body = "second")) },
        )
        assertEquals(listOf(11L), scanner.scan("content://sms", WINDOW_MS).map { it.smsId })
        unavailable = false
        assertEquals(listOf(10L), scanner.scan("content://sms", WINDOW_MS).map { it.smsId })
    }
    @Test
    fun queueOverflowRowsRemainAvailableForTheNextNotification() {
        val scanner = scanner(
            smsIdTracker = SmsInboxSeenTracker(maxTrackedSmsIds = 128),
            rowLoader = { _, _ -> (1L..32L).map { row(it, NOW) } },
        )
        val batch = scanner.scan("content://sms", WINDOW_MS)
        assertEquals(32, batch.size)
        // A scan produces up to 32 rows while the retry queue admits at most 20.
        batch.drop(BoundedSmsRetryQueue.DEFAULT_MAX_PENDING).forEach(scanner::release)
        val revisited = scanner.scan("content://sms", WINDOW_MS)
        assertEquals((21L..32L).toList(), revisited.map { it.smsId })
    }

}
