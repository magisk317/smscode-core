package io.github.magisk317.smscode.verification

import android.content.Intent
import android.provider.Telephony
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
    fun markDispatchHandled_keepsHandlerScopesIndependent() {
        val intent = fakeIntent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)

        assertFalse(SmsIntentHookSupport.markDispatchHandled(intent, intent.action, "sms_handler"))
        assertFalse(SmsIntentHookSupport.markDispatchHandled(intent, intent.action, "sms_forward"))
        assertTrue(SmsIntentHookSupport.markDispatchHandled(intent, intent.action, "sms_handler"))
        assertTrue(SmsIntentHookSupport.markDispatchHandled(intent, intent.action, "sms_forward"))
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
        return FakeIntent(action)
    }

    private class FakeIntent(
        private val actionValue: String? = null,
    ) : Intent() {
        private val extras = linkedMapOf<String, Any?>()

        override fun getAction(): String? = actionValue

        override fun getStringExtra(name: String): String? = extras[name] as? String

        override fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean {
            return extras[name] as? Boolean ?: defaultValue
        }

        override fun putExtra(name: String, value: String?): Intent {
            extras[name] = value
            return this
        }

        override fun putExtra(name: String, value: Boolean): Intent {
            extras[name] = value
            return this
        }
    }
}
