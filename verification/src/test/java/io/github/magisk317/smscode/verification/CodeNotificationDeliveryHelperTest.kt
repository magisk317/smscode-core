package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodeNotificationDeliveryHelperTest {
    @Test
    fun createPhoneOwnedNotificationPlan_resolvesMessageFields() {
        val request = NotifyActionHelper.PhoneOwnedNotificationRequest(
            smsMsg = TestSmsMessage(
                sender = "1068",
                company = "Bank",
                smsCode = "123456",
            ),
            notificationId = 42,
            autoCancelEnabled = true,
            retentionTimeMs = 3000L,
        )

        val plan = CodeNotificationDeliveryHelper.createPhoneOwnedNotificationPlan(
            request = request,
            fallbackTitle = "SmsCode",
        )

        assertEquals("Bank", plan.title)
        assertEquals("123456", plan.smsCode)
        assertEquals(42, plan.notificationId)
        assertEquals(true, plan.autoCancelEnabled)
        assertEquals(3000L, plan.retentionTimeMs)
    }
}
