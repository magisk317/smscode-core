package io.github.magisk317.smscode.domain.utils

import io.github.magisk317.smscode.domain.model.SmsBlacklistConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsBlacklistUtilsTest {

    @Test
    fun `match returns configured action for prefix rule`() {
        val result = SmsBlacklistUtils.match(
            config = SmsBlacklistConfig(
                enabled = true,
                actionDelete = true,
                prefixes = "+86 10",
            ),
            sender = "+86-10086",
            body = "hello",
        )

        assertTrue(result.matched)
        assertEquals("prefix", result.matchType)
        assertTrue(result.actionDelete)
        assertFalse(result.actionBlock)
    }

    @Test
    fun `match supports regex across sender and body`() {
        val result = SmsBlacklistUtils.match(
            config = SmsBlacklistConfig(
                enabled = true,
                actionBlock = true,
                regex = "(?s)bank.*code",
            ),
            sender = "BANK",
            body = "your code is 123456",
        )

        assertTrue(result.matched)
        assertEquals("regex", result.matchType)
        assertTrue(result.actionBlock)
    }
}
