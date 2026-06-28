package io.github.magisk317.smscode.verification

import android.content.Intent
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.Serializable

class AutoInputAccessibilityResultPublisherTest {
    @Test
    fun buildTerminalResultIntent_setsNormalizedIpcToken() {
        val intent = AutoInputAccessibilityResultPublisher.buildTerminalResultIntent(
            action = "com.example.AUTO_INPUT_RESULT",
            packageName = "com.example.app",
            attemptId = 42L,
            result = AutoInputAccessibilityNodeHelper.Result(
                success = true,
                strategy = "focused_node",
                reason = "ok",
                windowPackage = "com.example.target",
            ),
            token = " token ",
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertEquals("com.example.AUTO_INPUT_RESULT", intent.action)
        assertEquals("com.example.app", intent.`package`)
        assertEquals(42L, intent.extra(AutoInputResultBroadcastContract.EXTRA_ATTEMPT_ID))
        assertEquals(true, intent.extra(AutoInputResultBroadcastContract.EXTRA_SUCCESS))
        assertEquals("token", intent.extra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN))
    }

    @Test
    fun buildTerminalResultIntent_omitsBlankIpcToken() {
        val intent = AutoInputAccessibilityResultPublisher.buildTerminalResultIntent(
            action = "com.example.AUTO_INPUT_RESULT",
            packageName = "com.example.app",
            attemptId = 42L,
            result = AutoInputAccessibilityNodeHelper.Result(
                success = false,
                strategy = "focused_node",
                reason = "no_focus",
                windowPackage = "",
            ),
            token = " ",
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertNull(intent.extra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN))
        assertEquals("no_focus", intent.extra(AutoInputResultBroadcastContract.EXTRA_REASON))
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

        fun extra(name: String): Any? = extras[name]
    }
}
