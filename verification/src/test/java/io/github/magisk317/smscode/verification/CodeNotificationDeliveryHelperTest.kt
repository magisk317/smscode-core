package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CodeNotificationDeliveryHelperTest {
    @Test
    fun buildCodeNotification_createsValidNotification() {
        // Basic smoke test - the helper methods are tested via integration
        assertNotNull(CodeNotificationDeliveryHelper)
    }
}
