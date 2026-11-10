package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult
import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.smscode.verification.SmsProcessingClaims
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservedSmsHandlerRecoveryTest {
    private var clock = 0L
    private val claims = SmsProcessingClaims(clock = { clock })
    private var expireDuringLookup = false
    private var providerResult = SmsDuplicateCheckResult.NOT_DUPLICATE
    private var dispatchCount = 0
    private var gateCount = 0
    private var failDispatch = false

    private fun handler(): ObservedSmsHandler<SmsMessage> = ObservedSmsHandler(
        pluginContext = mockk<Context>(relaxed = true),
        phoneContext = mockk<Context>(relaxed = true),
        settingsLoader = { settings() },
        moduleEnabledReader = { true },
        conflictSuppressor = { _, _ -> false },
        sharedGateClaimer = { _, _, _, _, _ ->
            gateCount++
            ObservedSmsHandler.ClaimResult(claimed = gateCount == 1)
        },
        duplicateChecker = { _, _, _, _ ->
            if (expireDuringLookup) clock += SmsProcessingClaims.DEFAULT_PENDING_TTL_MS
            providerResult
        },
        smsEnricher = { _, _ -> mockk<SmsMessage>(relaxed = true) },
        dispatcher = { _, _, _, _, _ ->
            if (failDispatch) error("scheduler rejected synthetic work")
            dispatchCount++
        },
        processingClaims = claims,
    )

    @Test
    fun unavailableFreshMessageDispatchesOnceAndRemainsSuppressedAfterRecovery() {
        providerResult = SmsDuplicateCheckResult.UNAVAILABLE
        val handler = handler()
        assertTrue(handler.handle(record(fresh = true)).dispatched)
        providerResult = SmsDuplicateCheckResult.NOT_DUPLICATE
        val repeat = handler.handle(record(fresh = true))
        assertFalse(repeat.dispatched)
        assertFalse(repeat.retryable)
        assertEquals(1, dispatchCount)
    }

    @Test
    fun unavailableHistoricalMessageRetriesUntilProviderCanVerifyIt() {
        providerResult = SmsDuplicateCheckResult.UNAVAILABLE
        val handler = handler()
        val unavailable = handler.handle(record(fresh = false))
        assertFalse(unavailable.dispatched)
        assertTrue(unavailable.retryable)
        assertEquals(0, gateCount)
        providerResult = SmsDuplicateCheckResult.NOT_DUPLICATE
        assertTrue(handler.handle(record(fresh = false)).dispatched)
        assertEquals(1, dispatchCount)
    }

    @Test
    fun historicalMessageAlreadyDeliveredByLiveIngressNeedsNoProvider() {
        val record = record(fresh = false)
        val lease = (claims.claim(record.sender, record.body, record.date) as SmsProcessingClaims.ClaimResult.Acquired).lease
        assertTrue(claims.complete(lease))
        providerResult = SmsDuplicateCheckResult.UNAVAILABLE
        val outcome = handler().handle(record)
        assertFalse(outcome.dispatched)
        assertFalse(outcome.retryable)
        assertEquals(0, gateCount)
    }

    @Test
    fun liveIngressInFlightMakesObserverRetryWithoutClaimingTheRemoteGate() {
        val record = record(fresh = true)
        val lease = (claims.claim(record.sender, record.body, record.date) as SmsProcessingClaims.ClaimResult.Acquired).lease
        val handler = handler()
        assertTrue(handler.handle(record).retryable)
        assertEquals(0, gateCount)
        assertTrue(claims.release(lease))
        assertTrue(handler.handle(record).dispatched)
    }

    @Test
    fun schedulingFailureReleasesLocalClaimAndReusesItsOwnRemoteClaim() {
        val handler = handler()
        failDispatch = true
        val failed = handler.handle(record(fresh = true))
        assertFalse(failed.dispatched)
        assertTrue(failed.retryable)
        failDispatch = false
        val retried = handler.handle(record(fresh = true))
        assertTrue(retried.dispatched)
        assertFalse(retried.retryable)
        assertEquals(1, gateCount)
        assertEquals(1, dispatchCount)
    }

    @Test
    fun abandonedLookupCannotDispatchAfterItsLeaseExpired() {
        expireDuringLookup = true
        val outcome = handler().handle(record(fresh = true))
        assertFalse(outcome.dispatched)
        assertTrue(outcome.retryable)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun providerConfirmedDuplicateIsTerminalWithoutActions() {
        providerResult = SmsDuplicateCheckResult.DUPLICATE
        val outcome = handler().handle(record(fresh = true))
        assertFalse(outcome.dispatched)
        assertFalse(outcome.retryable)
        assertEquals(0, gateCount)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun alreadyReadMessageNeverRunsEvenWhenItWasFresh() {
        providerResult = SmsDuplicateCheckResult.UNAVAILABLE
        val outcome = handler().handle(record(fresh = true).copy(read = true))
        assertFalse(outcome.dispatched)
        assertFalse(outcome.retryable)
        assertEquals(0, gateCount)
    }

    private fun record(fresh: Boolean) = ObservedInboxScanRecord(
        smsId = 1L,
        triggerUri = "content://sms/1",
        sender = "synthetic-sender",
        body = "synthetic-message",
        date = DATE,
        read = false,
        code = "synthetic-code",
        receivedDuringObservation = fresh,
    )

    private fun settings() = SmsCodePostParseCoordinator.Settings(
        showNotification = false,
        autoCancelNotification = false,
        notificationRetentionMs = 0L,
        autoInputEnabled = true,
        autoInputDelayMs = 0L,
        inputIntervalMs = 0L,
        copyToClipboardEnabled = false,
        showToast = false,
        recordSmsEnabled = true,
        blockSmsEnabled = false,
        markAsReadEnabled = false,
        deleteSmsEnabled = false,
        deduplicateSmsEnabled = true,
    )

    private companion object {
        const val DATE = 1_700_000_000_000L
    }
}
