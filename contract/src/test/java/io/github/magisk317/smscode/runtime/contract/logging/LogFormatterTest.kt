package io.github.magisk317.smscode.runtime.contract.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogFormatterTest {

    @Test
    fun format_appliesStringFormatArguments() {
        assertEquals("hello relay", LogFormatter.format("hello %s", "relay"))
    }

    @Test
    fun format_returnsOriginalMessageWhenFormatIsInvalid() {
        assertEquals("bad %q", LogFormatter.format("bad %q", "value"))
    }

    @Test
    fun format_appendsThrowableStackTraceWithoutAndroidLogStub() {
        val formatted = LogFormatter.format("send failed: %s", "webhook", IllegalStateException("boom"))

        assertTrue(formatted.contains("send failed: webhook"))
        assertTrue(formatted.contains("java.lang.IllegalStateException: boom"))
    }
}
