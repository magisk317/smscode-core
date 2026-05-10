package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoInputBroadcastHelperTest {
    @Test
    fun normalizeIpcToken_trimsAndDropsBlankValues() {
        assertEquals("token", AutoInputBroadcastHelper.normalizeIpcToken(" token "))
        assertNull(AutoInputBroadcastHelper.normalizeIpcToken(""))
        assertNull(AutoInputBroadcastHelper.normalizeIpcToken("   "))
        assertNull(AutoInputBroadcastHelper.normalizeIpcToken(null))
    }
}
