package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeNotificationPayloadTest {
    @Test
    fun resolveTitle_prefersCompanyThenSenderThenFallback() {
        assertEquals("Bank", CodeNotificationPayload.resolveTitle("Bank", "1068", "App"))
        assertEquals("1068", CodeNotificationPayload.resolveTitle("", "1068", "App"))
        assertEquals("App", CodeNotificationPayload.resolveTitle(null, "", "App"))
    }

    @Test
    fun shouldAllowSmsHookTokenBypass_acceptsSystemAndPhone() {
        assertTrue(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(1000, sdkInt = 34))
        assertTrue(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(1001, sdkInt = 34))
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(2000, sdkInt = 34))
        assertTrue(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(null, sdkInt = 33))
    }
}
