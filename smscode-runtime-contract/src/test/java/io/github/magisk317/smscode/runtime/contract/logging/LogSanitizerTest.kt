package io.github.magisk317.smscode.runtime.contract.logging

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogSanitizerTest {

    @Test
    fun defaultSanitizer_masksTokensUrlUserInfoAndMessagePayloads() {
        val sanitized = DefaultLogSanitizer.sanitize(
            """url=https://user:pass@example.com token=secret123 body=验证码123456 "smsCode":"123456"""",
        )

        assertFalse(sanitized.contains("user:pass"))
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("验证码123456"))
        assertFalse(sanitized.contains("\":\"123456\""))
        assertTrue(sanitized.contains("token=***"))
        assertTrue(sanitized.contains("payload[len="))
        assertTrue(sanitized.contains("code[len="))
    }

    @Test
    fun defaultSanitizer_masksLikelyPhoneNumbers() {
        val sanitized = DefaultLogSanitizer.sanitize("sender=13800138000 from 13900139000")

        assertFalse(sanitized.contains("13800138000"))
        assertFalse(sanitized.contains("13900139000"))
        assertTrue(sanitized.contains("sender[len="))
        assertTrue(sanitized.contains("139******00"))
    }
}
