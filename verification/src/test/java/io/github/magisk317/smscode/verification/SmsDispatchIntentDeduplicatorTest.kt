package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SmsDispatchIntentDeduplicatorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun shouldSkipByFile_ignoresBlankEventOrAction() {
        val file = tempDir.resolve("dedup").toFile()
        val deduplicator = SmsDispatchIntentDeduplicator()

        val blankEvent = deduplicator.shouldSkipByFile(file, "", "sms")
        val blankAction = deduplicator.shouldSkipByFile(file, "event-1", "")

        assertFalse(blankEvent.shouldSkip)
        assertNull(blankEvent.key)
        assertFalse(blankAction.shouldSkip)
        assertNull(blankAction.key)
        assertFalse(file.exists())
    }

    @Test
    fun shouldSkipByFile_rejectsDuplicateWithinWindow() {
        var now = 1_000L
        val file = tempDir.resolve("dedup").toFile()
        val deduplicator = SmsDispatchIntentDeduplicator(timeProvider = { now })

        assertFalse(deduplicator.shouldSkipByFile(file, "event-1", "sms").shouldSkip)

        now += 250L
        val duplicate = deduplicator.shouldSkipByFile(file, "event-1", "sms")

        assertTrue(duplicate.shouldSkip)
        assertEquals(250L, duplicate.ageMs)
        assertEquals("event-1|sms", duplicate.key)
    }

    @Test
    fun shouldSkipByFile_allowsSameEventAfterWindow() {
        var now = 1_000L
        val file = tempDir.resolve("dedup").toFile()
        val deduplicator = SmsDispatchIntentDeduplicator(
            windowMs = 500L,
            timeProvider = { now },
        )

        assertFalse(deduplicator.shouldSkipByFile(file, "event-1", "sms").shouldSkip)

        now += 501L
        val result = deduplicator.shouldSkipByFile(file, "event-1", "sms")

        assertFalse(result.shouldSkip)
        assertNull(result.ageMs)
    }

    @Test
    fun shouldSkipInMemory_rejectsDuplicateWithinWindow() {
        var now = 1_000L
        val deduplicator = SmsDispatchIntentDeduplicator(timeProvider = { now })

        val first = deduplicator.shouldSkipInMemory("event-1", "sms")

        assertFalse(first.shouldSkip)
        assertEquals("event-1|sms", first.key)
        assertEquals(1_000L, first.timestampMs)

        now += 100L
        val duplicate = deduplicator.shouldSkipInMemory("event-1", "sms")

        assertTrue(duplicate.shouldSkip)
        assertEquals(100L, duplicate.ageMs)
    }

    @Test
    fun shouldSkipByFile_allowsEventAfterClockRollback() {
        var now = 10_000L
        val file = tempDir.resolve("rollback-dedup").toFile()
        val deduplicator = SmsDispatchIntentDeduplicator(timeProvider = { now })

        assertFalse(deduplicator.shouldSkipByFile(file, "event-1", "sms").shouldSkip)
        now = 9_000L

        assertFalse(deduplicator.shouldSkipByFile(file, "event-1", "sms").shouldSkip)
    }

    @Test
    fun syncFileIfLockAvailable_writesMemoryClaimForLaterFileClaim() {
        var now = 1_000L
        val file = tempDir.resolve("dedup").toFile()
        val deduplicator = SmsDispatchIntentDeduplicator(timeProvider = { now })
        val first = deduplicator.shouldSkipInMemory("event-1", "sms")

        deduplicator.syncFileIfLockAvailable(file, first.key!!, first.timestampMs!!)

        now += 250L
        val duplicate = deduplicator.shouldSkipByFile(file, "event-1", "sms")

        assertTrue(duplicate.shouldSkip)
        assertEquals(250L, duplicate.ageMs)
    }

    @Test
    fun shouldSkipByFile_evictionsKeepCapacityBounded() {
        var now = 1_000L
        val file = tempDir.resolve("dedup").toFile()
        val deduplicator = SmsDispatchIntentDeduplicator(
            maxEntries = 2,
            timeProvider = { now },
        )

        assertFalse(deduplicator.shouldSkipByFile(file, "event-1", "sms").shouldSkip)
        now += 1L
        assertFalse(deduplicator.shouldSkipByFile(file, "event-2", "sms").shouldSkip)
        now += 1L
        assertFalse(deduplicator.shouldSkipByFile(file, "event-3", "sms").shouldSkip)
        now += 1L

        assertFalse(deduplicator.shouldSkipByFile(file, "event-1", "sms").shouldSkip)
    }
}
