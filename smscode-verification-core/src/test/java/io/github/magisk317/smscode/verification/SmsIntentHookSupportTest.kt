package io.github.magisk317.smscode.verification

import android.content.Intent
import android.provider.Telephony
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsIntentHookSupportTest {

    @Test
    fun markDispatchHandled_marksActionSpecificFlag() {
        val intent = fakeIntent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)

        assertFalse(SmsIntentHookSupport.markDispatchHandled(intent, intent.action))
        assertTrue(SmsIntentHookSupport.markDispatchHandled(intent, intent.action))
    }

    @Test
    fun ensureEventId_reusesExistingExtra() {
        val intent = fakeIntent().apply {
            putExtra("event_id", "evt-1")
        }

        assertTrue(SmsIntentHookSupport.ensureEventId(intent) == "evt-1")
    }

    @Test
    fun ensureEventId_generatesStableNonBlankValue() {
        val intentA = fakeIntent()
        val intentB = fakeIntent()

        val eventA = SmsIntentHookSupport.ensureEventId(intentA)
        val eventB = SmsIntentHookSupport.ensureEventId(intentB)

        assertTrue(eventA.isNotBlank())
        assertTrue(eventB.isNotBlank())
        assertNotEquals(eventA, eventB)
    }

    private fun fakeIntent(action: String? = null): Intent {
        val extras = linkedMapOf<String, Any?>()
        return mockk(relaxed = true) {
            every { this@mockk.action } returns action
            every { getStringExtra(any()) } answers { extras[firstArg<String>()] as? String }
            every { getBooleanExtra(any(), any()) } answers {
                extras[firstArg<String>()] as? Boolean ?: secondArg()
            }
            every { putExtra(any<String>(), any<String>()) } answers {
                extras[firstArg()] = secondArg<String>()
                this@mockk
            }
            every { putExtra(any<String>(), any<Boolean>()) } answers {
                extras[firstArg()] = secondArg<Boolean>()
                this@mockk
            }
        }
    }
}
