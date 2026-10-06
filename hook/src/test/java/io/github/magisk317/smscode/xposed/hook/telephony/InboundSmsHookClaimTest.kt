package io.github.magisk317.smscode.xposed.hook.telephony

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InboundSmsHookClaimTest {

    @Test
    fun claim_succeedsFirstTime() {
        val store = mutableMapOf<String, String>()
        val claim = InboundSmsHookClaim(
            windowMs = 5_000L,
            clock = { 1_000L },
            propertyReader = { store[it] },
            propertyWriter = { k, v -> store[k] = v },
            pid = 42,
        )
        assertNull(claim.claim("k"))
        assertEquals("1000", store["k"])
    }

    @Test
    fun claim_reportsAgeOfExistingClaimInsideWindow() {
        val store = mutableMapOf<String, String>()
        var now = 1_000L
        val claim = InboundSmsHookClaim(
            windowMs = 5_000L,
            clock = { now },
            propertyReader = { store[it] },
            propertyWriter = { k, v -> store[k] = v },
            pid = 42,
        )
        claim.claim("k")
        now += 1_500L
        // A sibling classloader in the same process must be told the claim is held.
        assertEquals(1_500L, claim.claim("k"))
    }

    @Test
    fun claim_succeedsAgainAfterWindowExpires() {
        val store = mutableMapOf<String, String>()
        var now = 1_000L
        val claim = InboundSmsHookClaim(
            windowMs = 5_000L,
            clock = { now },
            propertyReader = { store[it] },
            propertyWriter = { k, v -> store[k] = v },
            pid = 42,
        )
        claim.claim("k")
        now += 5_001L
        assertNull(claim.claim("k"))
        assertEquals("6001", store["k"])
    }

    @Test
    fun claim_ignoresGarbagePropertyValue() {
        val store = mutableMapOf<String, String>("k" to "not-a-number")
        val claim = InboundSmsHookClaim(
            clock = { 1_000L },
            propertyReader = { store[it] },
            propertyWriter = { k, v -> store[k] = v },
            pid = 42,
        )
        assertNull(claim.claim("k"))
    }

    @Test
    fun key_includesPrefixPackageProcessAndPid() {
        val claim = InboundSmsHookClaim(pid = 1234)
        assertEquals("hook_init|com.android.phone|com.android.phone|pid:1234", claim.key("hook_init", "com.android.phone", ""))
        assertEquals(
            "obs|sms.vendor|vendor.mss|pid:1234",
            claim.key("obs", "sms.vendor", "vendor.mss"),
        )
    }

    @Test
    fun key_fallsBackToPackageWhenProcessNameBlank() {
        val claim = InboundSmsHookClaim(pid = 7)
        val k = claim.key("p", "com.android.phone", "   ")
        assertTrue(k.contains("com.android.phone|com.android.phone"))
    }

    @Test
    fun markInstalled_isFirstSeenWins() {
        val claim = InboundSmsHookClaim(pid = 1)
        assertTrue(claim.markInstalled("a"))
        assertEquals(false, claim.markInstalled("a"))
        assertTrue(claim.markInstalled("b"))
    }

    @Test
    fun claim_doesNotFireBeforeWindowElapses() {
        val store = mutableMapOf<String, String>()
        var now = 0L
        val claim = InboundSmsHookClaim(
            windowMs = 5_000L,
            clock = { now },
            propertyReader = { store[it] },
            propertyWriter = { k, v -> store[k] = v },
            pid = 1,
        )
        claim.claim("k")
        now = 4_999L
        assertNotNull(claim.claim("k"))
    }
}
