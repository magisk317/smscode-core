package io.github.magisk317.smscode.runtime.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordSmsActionTelemetryTest {

    @Test
    fun insertFailureIncludesBoundedErrorMessageAndPreservesExistingFields() {
        val attributes = recordSmsInsertFailureAttributes(
            RecordSmsActionHelper.InsertResult(
                success = false,
                error = "provider failed\nwith details",
                errorClass = "IllegalStateException",
            ),
        )

        assertEquals("provider failed\nwith details", attributes["error"])
        assertEquals("provider failed with details", attributes["error_msg"])
        assertEquals("IllegalStateException", attributes["error_class"])
    }

    @Test
    fun insertFailureTruncatesLongErrorMessage() {
        val attributes = recordSmsInsertFailureAttributes(
            RecordSmsActionHelper.InsertResult(
                success = false,
                detail = "x".repeat(200),
            ),
        )

        assertEquals(160, attributes.getValue("error_msg").length)
        assertTrue(attributes.getValue("error_msg").all { it == 'x' })
    }

    @Test
    fun insertFailureUsesUnknownWhenNoErrorOrDetailExists() {
        val attributes = recordSmsInsertFailureAttributes(
            RecordSmsActionHelper.InsertResult(success = false),
        )

        assertEquals("unknown", attributes["error"])
        assertEquals("unknown", attributes["error_msg"])
        assertEquals("unknown", attributes["error_class"])
    }
}
