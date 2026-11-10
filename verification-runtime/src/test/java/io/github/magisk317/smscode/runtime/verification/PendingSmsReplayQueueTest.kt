package io.github.magisk317.smscode.runtime.verification

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingSmsReplayQueueTest {

    private fun entry(eventId: String) = PendingSmsReplayQueue.Entry(
        intent = Intent(),
        eventId = eventId,
        source = "dispatch_intent",
        hookArgs = null,
        receiver = null,
        inboundSmsHandler = null,
        enqueuedAt = 0L,
    )

    @Test
    fun drainReady_returnsEntriesOldestFirst() {
        var now = 1_000L
        val queue = PendingSmsReplayQueue(ttlMs = 5_000L, clock = { now })
        assertTrue(queue.enqueue(Intent(), "e1", "dispatch_intent"))
        assertTrue(queue.enqueue(Intent(), "e2", "dispatch_intent"))

        val (ready, expired) = queue.drainReady()
        assertEquals(listOf("e1", "e2"), ready.map { it.eventId })
        assertEquals(0, expired)
        assertEquals(0, queue.size)
    }

    @Test
    fun drainReady_dropsExpiredEntries() {
        var now = 1_000L
        val queue = PendingSmsReplayQueue(ttlMs = 5_000L, maxEntries = 10, clock = { now })
        queue.enqueue(Intent(), "old", "dispatch_intent")
        now += 5_001L
        // enqueue() prunes on entry, so add the fresh one without disturbing "old".
        queue.enqueue(Intent(), "fresh", "dispatch_intent")

        // "old" was already pruned at enqueue time, so nothing is left to expire here.
        val (ready, expired) = queue.drainReady()
        assertEquals(0, expired)
        assertEquals(listOf("fresh"), ready.map { it.eventId })
    }

    @Test
    fun drainReady_reportsExpiryWhenClockMovesWithoutNewArrival() {
        var now = 1_000L
        val queue = PendingSmsReplayQueue(ttlMs = 5_000L, clock = { now })
        queue.enqueue(Intent(), "old", "dispatch_intent")
        now += 5_001L
        val (ready, expired) = queue.drainReady()
        assertEquals(1, expired)
        assertTrue(ready.isEmpty())
    }

    @Test
    fun enqueue_respectsMaxEntries() {
        val queue = PendingSmsReplayQueue(maxEntries = 2, clock = { 0L })
        assertTrue(queue.enqueue(Intent(), "e1", "s"))
        assertTrue(queue.enqueue(Intent(), "e2", "s"))
        assertFalse(queue.enqueue(Intent(), "e3", "s"))
        assertEquals(2, queue.size)
    }

    @Test
    fun prune_freesExpiredSlotsForNewArrivals() {
        var now = 0L
        val queue = PendingSmsReplayQueue(ttlMs = 1_000L, maxEntries = 1, clock = { now })
        assertTrue(queue.enqueue(Intent(), "e1", "s"))
        now += 1_001L
        // enqueue prunes first, so the expired entry must not block the new one.
        assertTrue(queue.enqueue(Intent(), "e2", "s"))
        assertEquals(1, queue.size)
    }

    @Test
    fun entry_keepsHookArgsForReplay() {
        var now = 0L
        val queue = PendingSmsReplayQueue(clock = { now })
        val args = arrayOf<Any?>("a", 1)
        queue.enqueue(Intent(), "e1", "dispatch_intent", hookArgs = args, receiver = "r")
        val (ready, _) = queue.drainReady()
        assertNotNull(ready.single().hookArgs)
        assertEquals("r", ready.single().receiver)
    }
}
