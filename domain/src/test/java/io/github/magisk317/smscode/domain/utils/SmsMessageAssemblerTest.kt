package io.github.magisk317.smscode.domain.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsMessageAssemblerTest {
    @Test
    fun `returns an empty body for no parts`() {
        assertEquals("", SmsMessageAssembler.getMessageBody(emptyList()))
    }

    @Test
    fun `returns the single part body unchanged`() {
        assertEquals(
            "verification code 123456",
            SmsMessageAssembler.getMessageBody(listOf(SmsMessagePart("verification code 123456"))),
        )
    }

    @Test
    fun `joins multipart SMS bodies in delivery order`() {
        assertEquals(
            "verification code 123456",
            SmsMessageAssembler.getMessageBody(
                listOf(
                    SmsMessagePart("verification code "),
                    SmsMessagePart("123456"),
                ),
            ),
        )
    }
}
