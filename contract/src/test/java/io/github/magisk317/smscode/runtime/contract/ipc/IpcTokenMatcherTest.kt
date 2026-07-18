package io.github.magisk317.smscode.runtime.contract.ipc

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IpcTokenMatcherTest {
    @Test
    fun `accepts matching nonblank tokens`() {
        assertTrue(IpcTokenMatcher.matches("token", "token"))
    }

    @Test
    fun `rejects blank mismatched and oversized tokens`() {
        assertFalse(IpcTokenMatcher.matches("", ""))
        assertFalse(IpcTokenMatcher.matches("token", "other"))
        assertFalse(IpcTokenMatcher.matches("a".repeat(4097), "a".repeat(4097)))
    }
}
