package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsDispatchChainBlockDeduplicatorTest {

    @Test
    fun shouldSkip_ignoresMissingMessageOrAction() {
        var now = 1_000L
        val deduplicator = SmsDispatchChainBlockDeduplicator(timeProvider = { now })

        assertFalse(deduplicator.shouldSkip(null, "sms", "pref_block_sms").shouldSkip)
        assertFalse(deduplicator.shouldSkip(message(), "", "pref_block_sms").shouldSkip)

        now += 100L
        assertFalse(deduplicator.shouldSkip(message(), "sms", "pref_block_sms").shouldSkip)
    }

    @Test
    fun shouldSkip_rejectsDuplicateWithinWindow() {
        var now = 1_000L
        val deduplicator = SmsDispatchChainBlockDeduplicator(timeProvider = { now })
        val message = message()

        assertFalse(deduplicator.shouldSkip(message, "sms", "pref_block_sms").shouldSkip)

        now += 250L
        val duplicate = deduplicator.shouldSkip(message, "sms", "pref_block_sms")

        assertTrue(duplicate.shouldSkip)
        assertEquals(250L, duplicate.ageMs)
    }

    @Test
    fun shouldSkip_allowsSameMessageAfterWindow() {
        var now = 1_000L
        val deduplicator = SmsDispatchChainBlockDeduplicator(
            windowMs = 500L,
            timeProvider = { now },
        )
        val message = message()

        assertFalse(deduplicator.shouldSkip(message, "sms", "pref_block_sms").shouldSkip)

        now += 501L
        val result = deduplicator.shouldSkip(message, "sms", "pref_block_sms")

        assertFalse(result.shouldSkip)
        assertNull(result.ageMs)
    }

    @Test
    fun shouldSkip_allowsMessageAfterClockRollback() {
        var now = 10_000L
        val deduplicator = SmsDispatchChainBlockDeduplicator(timeProvider = { now })
        val message = message()

        assertFalse(deduplicator.shouldSkip(message, "sms", "pref_block_sms").shouldSkip)
        now = 9_000L

        assertFalse(deduplicator.shouldSkip(message, "sms", "pref_block_sms").shouldSkip)
    }

    @Test
    fun shouldSkip_separatesReasonActionAndMessageFields() {
        var now = 1_000L
        val deduplicator = SmsDispatchChainBlockDeduplicator(timeProvider = { now })
        val message = message()

        assertFalse(deduplicator.shouldSkip(message, "sms", "pref_block_sms").shouldSkip)
        now += 100L

        assertFalse(deduplicator.shouldSkip(message, "mms", "pref_block_sms").shouldSkip)
        assertFalse(deduplicator.shouldSkip(message, "sms", "blacklist_block").shouldSkip)
        assertFalse(deduplicator.shouldSkip(message(body = "other"), "sms", "pref_block_sms").shouldSkip)
        assertFalse(deduplicator.shouldSkip(message(date = 200L), "sms", "pref_block_sms").shouldSkip)
    }

    @Test
    fun shouldSkip_evictionsKeepCapacityBounded() {
        var now = 1_000L
        val deduplicator = SmsDispatchChainBlockDeduplicator(
            maxEntries = 2,
            timeProvider = { now },
        )

        assertFalse(deduplicator.shouldSkip(message(body = "first"), "sms", "pref_block_sms").shouldSkip)
        now += 1L
        assertFalse(deduplicator.shouldSkip(message(body = "second"), "sms", "pref_block_sms").shouldSkip)
        now += 1L
        assertFalse(deduplicator.shouldSkip(message(body = "third"), "sms", "pref_block_sms").shouldSkip)
        now += 1L

        assertFalse(deduplicator.shouldSkip(message(body = "first"), "sms", "pref_block_sms").shouldSkip)
    }

    private fun message(
        sender: String? = "1068",
        body: String? = "code 123456",
        date: Long = 100L,
    ): TestSmsMessage {
        return TestSmsMessage(
            sender = sender,
            body = body,
            date = date,
        )
    }
}
