package io.github.magisk317.smscode.runtime.contract.logging

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun sanitizeUnbounded_handlesEscapedQuotesInSerializedJsonLines() {
        // 模拟 LogBundleExporter 对已序列化 .jsonl 行的二次脱敏：message 值内含转义引号
        val jsonlLine =
            "{\"time\":\"10:43:42\",\"message\":\"Matrix failed: 401  {\\\"errcode\\\":\\\"M_UNKNOWN_TOKEN\\\"," +
                "\\\"error\\\":\\\"Token is not active\\\"}\",\"route\":\"sender\"}"
        val sanitized = DefaultLogSanitizer.sanitizeUnbounded(jsonlLine)

        // 整个 message 值（含转义内容）应被完整替换，不得截断后泄漏剩余部分
        assertFalse(sanitized.contains("M_UNKNOWN_TOKEN"))
        assertFalse(sanitized.contains("Token is not active"))
        assertTrue(sanitized.contains("\"message\":\"payload[len="))
        // 替换后该行仍是合法 JSON（占位符后紧跟收尾引号和下一个字段）
        assertTrue(Regex("\"message\":\"payload\\[len=\\d+ hash=\\w+]\",\"route\"").containsMatchIn(sanitized))
    }

    @Test
    fun sanitizeUnbounded_masksWithoutTruncatingLongLogs() {
        val longTail = "x".repeat(1_200)
        val sanitized = DefaultLogSanitizer.sanitizeUnbounded("token=secret123 body=验证码123456 $longTail")

        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("验证码123456"))
        assertFalse(sanitized.contains("...(len="))
        assertEquals(longTail.length, sanitized.takeLast(longTail.length).length)
    }
}
