package io.github.magisk317.smscode.runtime.contract.autoinput

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.Serializable

class AutoInputResultBroadcastContractTest {
    @Test
    fun buildResultIntent_setsPayloadPackageAndNormalizedToken() {
        val intent = AutoInputResultBroadcastContract.buildResultIntent(
            action = "com.example.AUTO_INPUT_RESULT",
            packageName = "com.example.app",
            result = AutoInputResultBroadcastContract.Result(
                attemptId = 42L,
                success = false,
                reason = "no_focus",
            ),
            token = " token ",
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertEquals("com.example.AUTO_INPUT_RESULT", intent.action)
        assertEquals("com.example.app", intent.`package`)
        assertEquals(42L, intent.extra(AutoInputResultBroadcastContract.EXTRA_ATTEMPT_ID))
        assertEquals(false, intent.extra(AutoInputResultBroadcastContract.EXTRA_SUCCESS))
        assertEquals("no_focus", intent.extra(AutoInputResultBroadcastContract.EXTRA_REASON))
        assertEquals("token", intent.extra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN))
    }

    @Test
    fun buildResultIntent_omitsReasonForSuccess() {
        val intent = AutoInputResultBroadcastContract.buildResultIntent(
            action = "com.example.AUTO_INPUT_RESULT",
            packageName = null,
            result = AutoInputResultBroadcastContract.Result(
                attemptId = 42L,
                success = true,
                reason = "filled",
            ),
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertEquals(true, intent.extra(AutoInputResultBroadcastContract.EXTRA_SUCCESS))
        assertNull(intent.extra(AutoInputResultBroadcastContract.EXTRA_REASON))
        assertNull(intent.`package`)
    }

    @Test
    fun readResult_ignoresDifferentAction() {
        val result = AutoInputResultBroadcastContract.readResult(
            intent = FakeIntent("com.example.OTHER"),
            expectedAction = "com.example.AUTO_INPUT_RESULT",
        )

        assertEquals(AutoInputResultBroadcastContract.ReceiverResult.Ignored, result)
    }

    @Test
    fun readResult_rejectsMissingAttemptId() {
        val result = AutoInputResultBroadcastContract.readResult(
            intent = FakeIntent("com.example.AUTO_INPUT_RESULT"),
            expectedAction = "com.example.AUTO_INPUT_RESULT",
        )

        assertEquals(AutoInputResultBroadcastContract.ReceiverResult.MissingAttemptId, result)
    }

    @Test
    fun readResult_acceptsPayload() {
        val intent = FakeIntent("com.example.AUTO_INPUT_RESULT").apply {
            putExtra(AutoInputResultBroadcastContract.EXTRA_ATTEMPT_ID, 42L)
            putExtra(AutoInputResultBroadcastContract.EXTRA_SUCCESS, false)
            putExtra(AutoInputResultBroadcastContract.EXTRA_REASON, "no_focus")
        }

        val result = AutoInputResultBroadcastContract.readResult(
            intent = intent,
            expectedAction = "com.example.AUTO_INPUT_RESULT",
        )

        assertEquals(
            AutoInputResultBroadcastContract.ReceiverResult.Accepted(
                AutoInputResultBroadcastContract.Result(
                    attemptId = 42L,
                    success = false,
                    reason = "no_focus",
                ),
            ),
            result,
        )
    }

    private class FakeIntent(
        private val actionValue: String? = null,
    ) : Intent(actionValue) {
        private val extras = linkedMapOf<String, Any?>()
        private var packageValue: String? = null

        override fun getAction(): String? = actionValue

        override fun setPackage(packageName: String?): Intent {
            packageValue = packageName
            return this
        }

        override fun getPackage(): String? = packageValue

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
