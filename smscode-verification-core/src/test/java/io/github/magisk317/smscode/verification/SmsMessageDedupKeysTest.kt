package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsMessageDedupKeysTest {

    @Test
    fun buildAutoInputKeys_addsSameCodeWindowKey() {
        val keys = SmsMessageDedupKeys.buildAutoInputKeys(
            sender = "1068",
            body = "otp 123456",
            code = "123456",
            packageName = "com.bank",
            company = "Bank",
        )

        assertEquals(2, keys.size)
        assertTrue(keys.contains("code_window:123456"))
        assertTrue(keys.any { it.contains("code:123456|pkg:com.bank") })
    }

    @Test
    fun buildMessageKey_usesSenderBodyAndChannel() {
        val key = SmsMessageDedupKeys.buildMessageKey(
            sender = "1068",
            body = "otp 123456",
            code = "123456",
            packageName = "com.bank",
            company = "Bank",
        )

        assertTrue(key.contains("fp:"))
        assertTrue(key.contains("code:123456|pkg:com.bank"))
    }

    @Test
    fun buildMessageKey_returnsBlankWhenNothingUsefulPresent() {
        val key = SmsMessageDedupKeys.buildMessageKey(
            sender = null,
            body = "",
            code = null,
            packageName = null,
            company = null,
        )

        assertEquals("", key)
    }

    @Test
    fun buildObservedKey_prefersSmsIdWhenAvailable() {
        val key = SmsMessageDedupKeys.buildObservedKey(
            smsId = 66L,
            date = 100L,
            sender = "1068",
            body = "otp",
            code = "123456",
        )

        assertEquals("id:66|date:100|code:123456", key)
    }
}
