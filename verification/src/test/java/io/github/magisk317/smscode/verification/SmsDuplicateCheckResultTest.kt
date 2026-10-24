package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SmsDuplicateCheckResultTest {
    @Test
    fun resolveSmsDuplicateCheck_rejectsMissingTokenWithoutQuerying() {
        var queried = false

        val result = resolveSmsDuplicateCheck(null) {
            queried = true
            false
        }

        assertEquals(SmsDuplicateCheckResult.UNAVAILABLE, result)
        assertFalse(queried)
    }

    @Test
    fun resolveSmsDuplicateCheck_mapsProviderResultsAndFailures() {
        assertEquals(SmsDuplicateCheckResult.DUPLICATE, resolveSmsDuplicateCheck("token") { true })
        assertEquals(SmsDuplicateCheckResult.NOT_DUPLICATE, resolveSmsDuplicateCheck("token") { false })
        assertEquals(SmsDuplicateCheckResult.UNAVAILABLE, resolveSmsDuplicateCheck("token") { null })
        assertEquals(SmsDuplicateCheckResult.UNAVAILABLE, resolveSmsDuplicateCheck("token") { error("provider") })
    }
}
