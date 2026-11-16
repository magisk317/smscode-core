package io.github.magisk317.smscode.verification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsProcessingClaimsTest {
    private var now = 0L

    private fun claims(maxEntries: Int = 4): SmsProcessingClaims = SmsProcessingClaims(
        pendingTtlMs = PENDING_TTL_MS,
        completedTtlMs = COMPLETED_TTL_MS,
        maxEntries = maxEntries,
        clock = { now },
    )

    private fun acquire(
        claims: SmsProcessingClaims,
        sender: String = SENDER,
        body: String = BODY,
        date: Long = DATE,
    ): SmsProcessingClaims.Lease =
        (claims.claim(sender, body, date) as SmsProcessingClaims.ClaimResult.Acquired).lease

    @Test
    fun releasedWorkCanRetryAndCompletedWorkIsSuppressed() {
        val claims = claims()
        val first = acquire(claims)
        assertEquals(SmsProcessingClaims.ClaimResult.InFlight, claims.claim(SENDER, BODY, DATE))
        assertTrue(claims.release(first))
        val retry = acquire(claims)
        assertTrue(claims.complete(retry))
        assertEquals(SmsProcessingClaims.ClaimResult.Completed, claims.claim(SENDER, BODY, DATE))
        assertFalse(claims.release(retry))
        assertFalse(claims.complete(retry))
    }

    @Test
    fun pendingLeaseExpiresAtBoundaryAndOldOwnerCannotAffectItsReplacement() {
        val claims = claims()
        val expired = acquire(claims)
        now = PENDING_TTL_MS - 1L
        assertEquals(SmsProcessingClaims.ClaimResult.InFlight, claims.claim(SENDER, BODY, DATE))
        now = PENDING_TTL_MS
        val replacement = acquire(claims)
        assertNotEquals(expired.id, replacement.id)
        assertFalse(claims.complete(expired))
        assertFalse(claims.release(expired))
        assertEquals(SmsProcessingClaims.ClaimResult.InFlight, claims.claim(SENDER, BODY, DATE))
        assertTrue(claims.complete(replacement))
    }

    @Test
    fun completeRejectsAnExpiredLeaseWithoutAnotherClaim() {
        val claims = claims()
        val lease = acquire(claims)
        now = PENDING_TTL_MS
        assertFalse(claims.complete(lease))
        acquire(claims)
    }

    @Test
    fun completedTtlStartsAtCompletionAndExpiresAtBoundary() {
        val claims = claims()
        val lease = acquire(claims)
        now = PENDING_TTL_MS - 1L
        assertTrue(claims.complete(lease))
        now += COMPLETED_TTL_MS - 1L
        assertEquals(SmsProcessingClaims.ClaimResult.Completed, claims.claim(SENDER, BODY, DATE))
        now += 1L
        acquire(claims)
    }

    @Test
    fun capacityNeverEvictsPendingOrCompletedMessages() {
        val claims = claims(maxEntries = 2)
        val pending = acquire(claims, date = DATE)
        val completed = acquire(claims, date = DATE + 1L)
        assertTrue(claims.complete(completed))
        assertEquals(SmsProcessingClaims.ClaimResult.CapacityExceeded, claims.claim(SENDER, BODY, DATE + 2L))
        assertEquals(SmsProcessingClaims.ClaimResult.InFlight, claims.claim(SENDER, BODY, DATE))
        assertEquals(SmsProcessingClaims.ClaimResult.Completed, claims.claim(SENDER, BODY, DATE + 1L))
        assertTrue(claims.release(pending))
        acquire(claims, date = DATE + 2L)
    }

    @Test
    fun expiredEntriesFreeCapacity() {
        val claims = claims(maxEntries = 1)
        acquire(claims)
        now = PENDING_TTL_MS
        acquire(claims, date = DATE + 1L)
    }

    @Test
    fun differentDatesAndFieldBoundariesRemainDistinct() {
        val claims = claims()
        acquire(claims, sender = "ab", body = "c", date = DATE)
        acquire(claims, sender = "a", body = "bc", date = DATE)
        acquire(claims, sender = "ab", body = "c", date = DATE + 1L)
        // These strings collide under String.hashCode(), so that is not an adequate fingerprint.
        val collisions = claims()
        acquire(collisions, body = "Aa")
        acquire(collisions, body = "BB")
    }

    @Test
    fun clearInvalidatesLeasesWithoutReusingTheirGeneration() {
        val claims = claims()
        val old = acquire(claims)
        claims.clear()
        val fresh = acquire(claims)
        assertNotEquals(old.id, fresh.id)
        assertFalse(claims.release(old))
        assertFalse(claims.complete(old))
        assertTrue(claims.complete(fresh))
    }

    @Test
    fun aLeaseCannotReleaseOrCompleteAnotherRegistry() {
        val first = claims()
        val second = claims()
        val firstLease = acquire(first)
        val secondLease = acquire(second)
        assertFalse(second.release(firstLease))
        assertFalse(second.complete(firstLease))
        assertTrue(second.complete(secondLease))
    }

    @Test
    fun ownershipSurvivesProviderAvailabilityTransitions() {
        val claims = claims()
        // Every ingress uses the same registry, irrespective of whether its provider answered.
        val healthy = acquire(claims, date = DATE)
        assertTrue(claims.complete(healthy))
        assertEquals(SmsProcessingClaims.ClaimResult.Completed, claims.claim(SENDER, BODY, DATE))
        val degraded = acquire(claims, date = DATE + 1L)
        assertTrue(claims.complete(degraded))
        assertEquals(SmsProcessingClaims.ClaimResult.Completed, claims.claim(SENDER, BODY, DATE + 1L))
    }

    @Test
    fun simultaneousIngressesAcquireExactlyOneLease() {
        val claims = claims()
        val executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS)
        val ready = CountDownLatch(CONCURRENT_CALLERS)
        val start = CountDownLatch(1)
        try {
            val futures = (1..CONCURRENT_CALLERS).map {
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    claims.claim(SENDER, BODY, DATE)
                })
            }
            assertTrue(ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is SmsProcessingClaims.ClaimResult.Acquired })
            assertEquals(CONCURRENT_CALLERS - 1, results.count { it == SmsProcessingClaims.ClaimResult.InFlight })
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun publicLeaseDescriptionContainsNoMessageContent() {
        val claims = claims()
        val result = claims.claim(SENDER, BODY, DATE)
        val lease = (result as SmsProcessingClaims.ClaimResult.Acquired).lease
        assertFalse(lease.toString().contains(SENDER))
        assertFalse(lease.toString().contains(BODY))
        assertFalse(result.toString().contains(SENDER))
        assertFalse(result.toString().contains(BODY))
    }

    private companion object {
        const val PENDING_TTL_MS = 30_000L
        const val COMPLETED_TTL_MS = 600_000L
        const val DATE = 1_700_000_000_000L
        const val SENDER = "synthetic-sender"
        const val BODY = "synthetic-message"
        const val CONCURRENT_CALLERS = 8
        const val TEST_TIMEOUT_SECONDS = 5L
    }
}
