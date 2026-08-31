package io.github.magisk317.smscode.runtime.verification

import android.content.Intent
import android.provider.Telephony
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.Serializable

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

    @Test
    fun getPduCount_returnsExtractorSizeAndHandlesNull() {
        val intent = fakeIntent()

        assertEquals(
            2,
            SmsIntentHookSupport.getPduCount(intent, messageExtractor = { arrayOf("first", "second") }),
        )
        assertEquals(
            -1,
            SmsIntentHookSupport.getPduCount(intent, messageExtractor = { null }),
        )
    }

    @Test
    fun getPduCount_reportsExtractorFailure() {
        val intent = fakeIntent()
        var failureMessage: String? = null

        val count = SmsIntentHookSupport.getPduCount(
            intent = intent,
            messageExtractor = { error("boom") },
            onFailure = { failureMessage = it.message },
        )

        assertEquals(-1, count)
        assertEquals("boom", failureMessage)
    }

    @Test
    fun extractIntentAction_returnsFirstIntentAction() {
        val intent = fakeIntent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)

        assertEquals(
            Telephony.Sms.Intents.SMS_DELIVER_ACTION,
            SmsIntentHookSupport.extractIntentAction(arrayOf("ignored", intent)),
        )
        assertNull(SmsIntentHookSupport.extractIntentAction(arrayOf("ignored")))
    }

    @Test
    fun extractOrBuildSmsIntent_prefersExistingIntent() {
        val existing = fakeIntent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        val result = SmsIntentHookSupport.extractOrBuildSmsIntent(
            args = arrayOf(existing, arrayOf(byteArrayOf(1))),
            fallbackAction = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
            intentFactory = ::FakeIntent,
        )

        assertSame(existing, result)
    }

    @Test
    fun extractOrBuildSmsIntent_buildsFallbackFromPdusAndFormat() {
        val pdus = arrayOf(byteArrayOf(1, 2), byteArrayOf(3))

        val result = SmsIntentHookSupport.extractOrBuildSmsIntent(
            args = arrayOf("3gpp2", pdus),
            fallbackAction = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION, result.action)
        assertEquals("3gpp2", result.extra("format"))
        val storedPdus = result.extra("pdus") as Array<*>
        assertTrue((storedPdus[0] as ByteArray).contentEquals(byteArrayOf(1, 2)))
        assertTrue((storedPdus[1] as ByteArray).contentEquals(byteArrayOf(3)))
    }

    @Test
    fun extractOrBuildSmsIntent_returnsNullForMissingOrMixedPdus() {
        assertNull(
            SmsIntentHookSupport.extractOrBuildSmsIntent(
                args = null,
                fallbackAction = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentFactory = ::FakeIntent,
            ),
        )
        assertNull(
            SmsIntentHookSupport.extractOrBuildSmsIntent(
                args = arrayOf(arrayOf(byteArrayOf(1), "bad")),
                fallbackAction = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentFactory = ::FakeIntent,
            ),
        )
    }

    @Test
    fun findRawTableReceiver_matchesRequiredFieldsAcrossClassHierarchy() {
        val receiver = RawTableReceiver()

        assertSame(
            receiver,
            SmsIntentHookSupport.findRawTableReceiver(arrayOf("ignored", receiver)),
        )
        assertNull(SmsIntentHookSupport.findRawTableReceiver(arrayOf(IncompleteRawTableReceiver())))
    }

    @Test
    fun defaultResultForType_returnsPrimitiveDefaults() {
        assertNull(SmsIntentHookSupport.defaultResultForType(null))
        assertNull(SmsIntentHookSupport.defaultResultForType(Void.TYPE))
        assertEquals(false, SmsIntentHookSupport.defaultResultForType(Boolean::class.javaPrimitiveType))
        assertEquals(0, SmsIntentHookSupport.defaultResultForType(Int::class.javaPrimitiveType))
        assertEquals(0L, SmsIntentHookSupport.defaultResultForType(Long::class.javaPrimitiveType))
        assertEquals(0f, SmsIntentHookSupport.defaultResultForType(Float::class.javaPrimitiveType))
        assertEquals(0.0, SmsIntentHookSupport.defaultResultForType(Double::class.javaPrimitiveType))
        assertEquals(0.toShort(), SmsIntentHookSupport.defaultResultForType(Short::class.javaPrimitiveType))
        assertEquals(0.toByte(), SmsIntentHookSupport.defaultResultForType(Byte::class.javaPrimitiveType))
        assertEquals(0.toChar(), SmsIntentHookSupport.defaultResultForType(Char::class.javaPrimitiveType))
        assertNull(SmsIntentHookSupport.defaultResultForType(String::class.java))
    }

    private fun fakeIntent(action: String? = null): Intent {
        return FakeIntent(action)
    }

    private open class RawTableReceiverBase {
        @Suppress("unused")
        private val mDeleteWhere: String? = null
    }

    private class RawTableReceiver : RawTableReceiverBase() {
        @Suppress("unused")
        private val mDeleteWhereArgs: Array<String>? = null
    }

    private class IncompleteRawTableReceiver {
        @Suppress("unused")
        private val mDeleteWhere: String? = null
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

        override fun putExtra(name: String?, value: Serializable?): Intent {
            extras[name.orEmpty()] = value
            return this
        }

        fun extra(name: String): Any? = extras[name]
    }
}
