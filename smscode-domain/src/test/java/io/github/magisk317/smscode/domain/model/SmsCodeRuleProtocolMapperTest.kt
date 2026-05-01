package io.github.magisk317.smscode.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsCodeRuleProtocolMapperTest {

    @Test
    fun `fromProtocol maps extended rule fields`() {
        val protocol = SmsCodeRuleProtocol(
            company = "ACME",
            codeKeyword = "code",
            codeRegex = "\\d{6}",
            senderRegex = "ACME.*",
            packageNameHint = "com.acme.app",
            priority = 7,
        )

        val spec = SmsCodeRuleProtocolMapper.fromProtocol(protocol)

        assertEquals("ACME", spec.company)
        assertEquals("code", spec.codeKeyword)
        assertEquals("\\d{6}", spec.codeRegex)
        assertEquals("ACME.*", spec.senderRegex)
        assertEquals("com.acme.app", spec.packageNameHint)
        assertEquals(7, spec.priority)
    }

    @Test
    fun `toProtocol preserves legacy-compatible defaults`() {
        val spec = SmsCodeRuleSpec(
            company = "Bank",
            codeKeyword = "otp",
            codeRegex = "[A-Z0-9]{4}",
        )

        val protocol = SmsCodeRuleProtocolMapper.toProtocol(spec)

        assertEquals("Bank", protocol.company)
        assertEquals("otp", protocol.codeKeyword)
        assertEquals("[A-Z0-9]{4}", protocol.codeRegex)
        assertEquals(null, protocol.senderRegex)
        assertEquals(null, protocol.packageNameHint)
        assertEquals(0, protocol.priority)
    }
}
