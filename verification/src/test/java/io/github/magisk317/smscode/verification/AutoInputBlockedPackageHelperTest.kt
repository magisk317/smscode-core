package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoInputBlockedPackageHelperTest {
    @Test
    fun resolveBlockedState_prefersPrimaryResult() {
        val blocked = AutoInputBlockedPackageHelper.resolveBlockedState(
            packageName = "com.example.app",
            primaryChecker = { true },
            fallbackChecker = { false },
        )

        assertTrue(blocked)
    }

    @Test
    fun resolveBlockedState_usesFallbackWhenPrimaryUnavailable() {
        var fallbackObserved = false
        val blocked = AutoInputBlockedPackageHelper.resolveBlockedState(
            packageName = "com.example.app",
            primaryChecker = { null },
            fallbackChecker = { true },
            fallbackLogger = { _, value -> fallbackObserved = value },
        )

        assertTrue(blocked)
        assertTrue(fallbackObserved)
    }

    @Test
    fun resolveBlockedState_defaultsToFalse() {
        val blocked = AutoInputBlockedPackageHelper.resolveBlockedState(
            packageName = "com.example.app",
            primaryChecker = { null },
        )

        assertFalse(blocked)
    }
}
