package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecentEventIdTrackerTest {
    @Test
    fun mark_rejectsDuplicateIds() {
        val tracker = RecentEventIdTracker(maxSize = 2)

        assertTrue(tracker.mark("event-1"))
        assertFalse(tracker.mark("event-1"))
    }

    @Test
    fun mark_evictsOldestIdWhenCapacityExceeded() {
        val tracker = RecentEventIdTracker(maxSize = 2)

        assertTrue(tracker.mark("event-1"))
        assertTrue(tracker.mark("event-2"))
        assertTrue(tracker.mark("event-3"))

        assertTrue(tracker.mark("event-1"))
        assertFalse(tracker.mark("event-3"))
    }
}
