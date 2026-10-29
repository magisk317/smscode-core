package io.github.magisk317.forward.capture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallNotificationMatcherTest {

    @Test
    fun `telecom framework package counts as a call source`() {
        assertTrue(CallNotificationMatcher.isCallSourcePackage("com.android.server.telecom"))
        assertTrue(CallNotificationMatcher.isCallSourcePackage("com.android.incallui"))
        assertTrue(CallNotificationMatcher.isCallSourcePackage("com.google.android.dialer"))
    }

    @Test
    fun `vendor dialer tokens match without an allowlist entry`() {
        assertTrue(CallNotificationMatcher.isCallSourcePackage("com.sec.android.dialer"))
        assertTrue(CallNotificationMatcher.isCallSourcePackage("com.example.InCallUI"))
    }

    @Test
    fun `contacts app is not a call source`() {
        assertFalse(CallNotificationMatcher.isCallSourcePackage("com.android.contacts"))
        assertFalse(CallNotificationMatcher.isCallSourcePackage("com.android.providers.contacts"))
    }

    @Test
    fun `telecom channels are recognized case sensitively`() {
        assertTrue(CallNotificationMatcher.isTelecomCallChannel("TelecomIncomingCalls"))
        assertTrue(CallNotificationMatcher.isTelecomCallChannel("MiuiTelecomMissedCalls"))
        assertFalse(CallNotificationMatcher.isTelecomCallChannel("telecomIncomingCalls"))
        assertFalse(CallNotificationMatcher.isTelecomCallChannel(""))
    }

    @Test
    fun `telecom package is a call notification when category is call`() {
        assertTrue(
            CallNotificationMatcher.isCallNotification(
                packageName = "com.android.server.telecom",
                category = "call",
                channelId = "TelecomIncomingCalls",
                hasCallKeyword = false,
            ),
        )
    }

    @Test
    fun `telecom package needs a call signal when category is absent`() {
        assertFalse(
            CallNotificationMatcher.isCallNotification(
                packageName = "com.android.server.telecom",
                category = null,
                channelId = "misc",
                hasCallKeyword = false,
            ),
        )
        assertTrue(
            CallNotificationMatcher.isCallNotification(
                packageName = "com.android.server.telecom",
                category = null,
                channelId = "misc",
                hasCallKeyword = true,
            ),
        )
    }

    @Test
    fun `telecom channel alone is not enough for an unrelated package`() {
        assertFalse(
            CallNotificationMatcher.isCallNotification(
                packageName = "com.example.notes",
                category = "call",
                channelId = "misc",
                hasCallKeyword = false,
            ),
        )
    }

    @Test
    fun `telecom channel carries a call signal from any package`() {
        assertTrue(
            CallNotificationMatcher.isCallNotification(
                packageName = "com.example.notes",
                category = null,
                channelId = "MiuiTelecomMissedCalls",
                hasCallKeyword = true,
            ),
        )
    }

    @Test
    fun `looksLikeCall is broader than the forwarding predicate`() {
        assertTrue(CallNotificationMatcher.looksLikeCall(null, "misc", hasCallKeyword = true))
        assertTrue(CallNotificationMatcher.looksLikeCall("call", "misc", hasCallKeyword = false))
        assertFalse(CallNotificationMatcher.looksLikeCall(null, "misc", hasCallKeyword = false))
    }
}
