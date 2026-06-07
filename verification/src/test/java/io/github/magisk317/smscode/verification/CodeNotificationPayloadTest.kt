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
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(null, sdkInt = 33))
    }

    @Test
    fun shouldAllowSmsHookTokenBypass_requiresEmptyExpectedTokenForCompatPath() {
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass("", null, sdkInt = 33))
        assertTrue(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(null, 1001, sdkInt = 34))
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass("initialized", null, sdkInt = 33))
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass("initialized", 1001, sdkInt = 34))
    }

    @Test
    fun deliveryResultFromBroadcast_mapsPostedAndFailureResults() {
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = true,
                reason = CodeNotificationPayload.RESULT_DATA_POSTED,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_POSTED,
                null,
            ),
        )
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = CodeNotificationPayload.RESULT_DATA_REJECTED_TOKEN,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_FAILED,
                CodeNotificationPayload.RESULT_DATA_REJECTED_TOKEN,
            ),
        )
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = CodeNotificationPayload.RESULT_DATA_NO_RECEIVER,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_UNDELIVERED,
                null,
            ),
        )
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = CodeNotificationPayload.RESULT_DATA_NOTIFY_FAILED,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_FAILED,
                null,
            ),
        )
    }
}
