package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotifyActionHelperTest {

    @Test
    fun run_returnsNullWhenDisabled() {
        val result = runHelper(enabled = false)

        assertEquals(null, result)
    }

    @Test
    fun run_callsNotifierWhenEnabled() {
        val result = runHelper(enabled = true)

        assertEquals("notified", result)
    }

    private fun runHelper(enabled: Boolean): String? {
        return NotifyActionHelper<TestSmsMessage, String>(
            pluginContext = context,
            smsMsg = TestSmsMessage,
            enabled = enabled,
            autoCancelEnabledProvider = { false },
            retentionTimeMsProvider = { 0L },
            notifier = { "notified" },
        ).run()
    }

    private object TestSmsMessage : SmsMessage {
        override val sender: String? = "sender"
        override val body: String? = "body"
        override val date: Long = 0L
        override val company: String? = "company"
        override val smsCode: String? = "123456"
        override val packageName: String? = null
    }

    private companion object {
        val context: Context = mockk(relaxed = true)
    }
}
