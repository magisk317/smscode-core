package io.github.magisk317.smscode.verification

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotifyActionHelperTest {

    @Test
    fun run_usesPhoneOwnedNotificationWhenOwnerIsNotSelected() {
        val result = runHelper(owner = "")

        assertEquals("phone", result)
    }

    @Test
    fun run_preservesExplicitPhoneOwnedSelection() {
        val result = runHelper(owner = OWNER_PHONE)

        assertEquals("phone", result)
    }

    @Test
    fun run_preservesExplicitAppOwnedSelection() {
        val result = runHelper(owner = OWNER_APP)

        assertEquals("app", result)
    }

    private fun runHelper(owner: String): String? {
        return NotifyActionHelper<TestSmsMessage, String>(
            pluginContext = context,
            smsMsg = TestSmsMessage,
            enabled = true,
            ownerReader = { owner },
            appOwnedValue = OWNER_APP,
            phoneOwnedValue = OWNER_PHONE,
            autoCancelEnabledProvider = { false },
            retentionTimeMsProvider = { 0L },
            appOwnedNotifier = { "app" },
            phoneOwnedNotifier = { "phone" },
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
        const val OWNER_APP = "app"
        const val OWNER_PHONE = "phone"
        val context: Context = mockk(relaxed = true)
    }
}
