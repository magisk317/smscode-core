package io.github.magisk317.smscode.runtime.contract.autoinput

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.Serializable

class AutoInputBroadcastContractTest {
    @Test
    fun normalizeIpcToken_trimsAndDropsBlankValues() {
        assertEquals("token", AutoInputBroadcastContract.normalizeIpcToken(" token "))
        assertNull(AutoInputBroadcastContract.normalizeIpcToken(""))
        assertNull(AutoInputBroadcastContract.normalizeIpcToken("   "))
        assertNull(AutoInputBroadcastContract.normalizeIpcToken(null))
    }

    @Test
    fun buildRequestIntent_setsPayloadAndNormalizedToken() {
        val intent = AutoInputBroadcastContract.buildRequestIntent(
            action = "com.example.AUTO_INPUT",
            request = AutoInputBroadcastContract.Request(
                code = "123456",
                autoEnter = true,
                inputIntervalMs = 200L,
                attemptId = 42L,
            ),
            token = " token ",
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertEquals("com.example.AUTO_INPUT", intent.action)
        assertEquals("123456", intent.extra(AutoInputBroadcastContract.EXTRA_CODE))
        assertEquals(true, intent.extra(AutoInputBroadcastContract.EXTRA_AUTO_ENTER))
        assertEquals(200L, intent.extra(AutoInputBroadcastContract.EXTRA_INPUT_INTERVAL_MS))
        assertEquals(42L, intent.extra(AutoInputBroadcastContract.EXTRA_ATTEMPT_ID))
        assertEquals("token", intent.extra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN))
    }

    @Test
    fun readReceiverRequest_ignoresDifferentAction() {
        val request = AutoInputBroadcastContract.readReceiverRequest(
            intent = FakeIntent("com.example.OTHER"),
            expectedAction = "com.example.AUTO_INPUT",
        )

        assertEquals(AutoInputBroadcastContract.ReceiverRequest.Ignored, request)
    }

    @Test
    fun readReceiverRequest_rejectsBlankCode() {
        val intent = FakeIntent("com.example.AUTO_INPUT").apply {
            putExtra(AutoInputBroadcastContract.EXTRA_CODE, " ")
            putExtra(AutoInputBroadcastContract.EXTRA_AUTO_ENTER, true)
            putExtra(AutoInputBroadcastContract.EXTRA_INPUT_INTERVAL_MS, -100L)
            putExtra(AutoInputBroadcastContract.EXTRA_ATTEMPT_ID, -1L)
        }

        val request = AutoInputBroadcastContract.readReceiverRequest(
            intent = intent,
            expectedAction = "com.example.AUTO_INPUT",
        )

        assertEquals(
            AutoInputBroadcastContract.ReceiverRequest.BlankCode(
                AutoInputBroadcastContract.Request(
                    code = " ",
                    autoEnter = true,
                    inputIntervalMs = 0L,
                    attemptId = null,
                ),
            ),
            request,
        )
    }

    @Test
    fun readReceiverRequest_acceptsPayload() {
        val intent = FakeIntent("com.example.AUTO_INPUT").apply {
            putExtra(AutoInputBroadcastContract.EXTRA_CODE, "123456")
            putExtra(AutoInputBroadcastContract.EXTRA_AUTO_ENTER, true)
            putExtra(AutoInputBroadcastContract.EXTRA_INPUT_INTERVAL_MS, 200L)
            putExtra(AutoInputBroadcastContract.EXTRA_ATTEMPT_ID, 42L)
        }

        val request = AutoInputBroadcastContract.readReceiverRequest(
            intent = intent,
            expectedAction = "com.example.AUTO_INPUT",
        )

        assertEquals(
            AutoInputBroadcastContract.ReceiverRequest.Accepted(
                AutoInputBroadcastContract.Request(
                    code = "123456",
                    autoEnter = true,
                    inputIntervalMs = 200L,
                    attemptId = 42L,
                ),
            ),
            request,
        )
    }

    private class FakeIntent(
        private val actionValue: String? = null,
    ) : Intent(actionValue) {
        private val extras = linkedMapOf<String, Any?>()

        override fun getAction(): String? = actionValue

        override fun putExtra(name: String, value: String?): Intent {
            extras[name] = value
            return this
        }

        override fun putExtra(name: String, value: Boolean): Intent {
            extras[name] = value
            return this
        }

        override fun putExtra(name: String, value: Long): Intent {
            extras[name] = value
            return this
        }

        override fun putExtra(name: String?, value: Serializable?): Intent {
            extras[name.orEmpty()] = value
            return this
        }

        override fun getStringExtra(name: String?): String? = extras[name] as? String

        override fun getBooleanExtra(name: String?, defaultValue: Boolean): Boolean =
            extras[name] as? Boolean ?: defaultValue

        override fun getLongExtra(name: String?, defaultValue: Long): Long =
            extras[name] as? Long ?: defaultValue

        fun extra(name: String): Any? = extras[name]
    }
}
