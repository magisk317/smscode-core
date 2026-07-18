package io.github.magisk317.smscode.runtime.common.autoinput

import android.content.Intent
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract
import java.io.Serializable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoInputResultProcessorTest {
    @Test
    fun `validation rejects mismatched tokens without exposing token values`() = runBlocking {
        val intent = resultIntent(token = "received")

        assertEquals(
            AutoInputResultProcessor.ValidationResult.RejectedToken(
                result = RESULT,
                expectedTokenEmpty = false,
                receivedTokenEmpty = false,
            ),
            AutoInputResultProcessor.validate(intent, ACTION) { "expected" },
        )
    }

    @Test
    fun `validation accepts a matching nonblank token`() = runBlocking {
        val intent = resultIntent(token = "token")

        assertEquals(
            AutoInputResultProcessor.ValidationResult.Accepted(RESULT),
            AutoInputResultProcessor.validate(intent, ACTION) { "token" },
        )
    }

    @Test
    fun `persistence updates first and only upserts missing rows`() = runBlocking {
        var upsertCalls = 0
        assertEquals(
            AutoInputResultProcessor.PersistenceOutcome.UPDATED,
            AutoInputResultProcessor.persist(
                result = RESULT,
                update = { 1L },
                upsert = {
                    upsertCalls += 1
                    1L
                },
            ),
        )
        assertEquals(0, upsertCalls)

        assertEquals(
            AutoInputResultProcessor.PersistenceOutcome.UPSERTED,
            AutoInputResultProcessor.persist(RESULT, update = { 0L }, upsert = { 1L }),
        )
        assertEquals(
            AutoInputResultProcessor.PersistenceOutcome.STALE,
            AutoInputResultProcessor.persist(RESULT, update = { 0L }, upsert = { 0L }),
        )
    }

    private fun resultIntent(token: String): Intent = FakeIntent(ACTION).apply {
        putExtra(AutoInputResultBroadcastContract.EXTRA_ATTEMPT_ID, RESULT.attemptId)
        putExtra(AutoInputResultBroadcastContract.EXTRA_SUCCESS, RESULT.success)
        putExtra(AutoInputResultBroadcastContract.EXTRA_REASON, RESULT.reason)
        putExtra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN, token)
    }

    private class FakeIntent(
        private val actionValue: String,
    ) : Intent(actionValue) {
        private val extras = linkedMapOf<String, Any?>()

        override fun getAction(): String = actionValue

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
    }

    private companion object {
        const val ACTION = "com.example.AUTO_INPUT_RESULT"
        val RESULT = AutoInputResultBroadcastContract.Result(
            attemptId = 42L,
            success = false,
            reason = "no_focus",
        )
    }
}
