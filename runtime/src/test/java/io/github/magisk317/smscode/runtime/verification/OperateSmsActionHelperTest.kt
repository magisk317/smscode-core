package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.runtime.verification.OperateSmsActionHelper.Operation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperateSmsActionHelperTest {

    @Test
    fun resolveOperation_prefersForcedOperation() {
        assertEquals(
            Operation.MARK_AS_READ,
            OperateSmsActionHelper.resolveOperation(
                forcedOperation = Operation.MARK_AS_READ,
                deleteSmsEnabled = true,
                markAsReadEnabled = false,
            ),
        )
    }

    @Test
    fun resolveOperation_prefersDeleteOverMarkAsRead() {
        assertEquals(
            Operation.DELETE,
            OperateSmsActionHelper.resolveOperation(
                forcedOperation = null,
                deleteSmsEnabled = true,
                markAsReadEnabled = true,
            ),
        )
    }

    @Test
    fun resolveOperation_usesMarkAsReadWhenDeleteDisabled() {
        assertEquals(
            Operation.MARK_AS_READ,
            OperateSmsActionHelper.resolveOperation(
                forcedOperation = null,
                deleteSmsEnabled = false,
                markAsReadEnabled = true,
            ),
        )
    }

    @Test
    fun resolveOperation_returnsNullWhenNoOperationEnabled() {
        assertNull(
            OperateSmsActionHelper.resolveOperation(
                forcedOperation = null,
                deleteSmsEnabled = false,
                markAsReadEnabled = false,
            ),
        )
    }

    @Test
    fun resolveForcedOperation_mapsLegacyValues() {
        assertEquals(
            Operation.DELETE,
            OperateSmsActionHelper.resolveForcedOperation(
                forcedOperation = 0,
                deleteValue = 0,
                markAsReadValue = 1,
            ),
        )
        assertEquals(
            Operation.MARK_AS_READ,
            OperateSmsActionHelper.resolveForcedOperation(
                forcedOperation = 1,
                deleteValue = 0,
                markAsReadValue = 1,
            ),
        )
        assertNull(
            OperateSmsActionHelper.resolveForcedOperation(
                forcedOperation = 2,
                deleteValue = 0,
                markAsReadValue = 1,
            ),
        )
    }

    @Test
    fun isAddressMatched_acceptsExactAndSuffixMatches() {
        assertTrue(OperateSmsActionHelper.isAddressMatched("106900", "106900"))
        assertTrue(OperateSmsActionHelper.isAddressMatched("+86 106900", "106900"))
        assertTrue(OperateSmsActionHelper.isAddressMatched("106900", "+86 106900"))
    }

    @Test
    fun isAddressMatched_rejectsBlankOrUnrelatedAddresses() {
        assertFalse(OperateSmsActionHelper.isAddressMatched(null, "106900"))
        assertFalse(OperateSmsActionHelper.isAddressMatched("service", "106900"))
        assertFalse(OperateSmsActionHelper.isAddressMatched("106900", "95555"))
    }

    @Test
    fun isBodyMatched_acceptsPrefixWhitespaceAndCodeMatches() {
        assertTrue(OperateSmsActionHelper.isBodyMatched("code 123456", "code", null))
        assertTrue(OperateSmsActionHelper.isBodyMatched("code 123456", "code123456", null))
        assertTrue(OperateSmsActionHelper.isBodyMatched("Your code is 123456", "验证码 123456", "123456"))
    }

    @Test
    fun isBodyMatched_rejectsBlankOrUnrelatedBodies() {
        assertFalse(OperateSmsActionHelper.isBodyMatched(null, "code", "123456"))
        assertFalse(OperateSmsActionHelper.isBodyMatched("code", "", "123456"))
        assertFalse(OperateSmsActionHelper.isBodyMatched("hello", "world", "123456"))
        assertFalse(OperateSmsActionHelper.isBodyMatched("Your code is 123", "验证码 123", "123"))
    }
}
