package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import android.os.Bundle
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SmsCodeCallableActionTest {
    @Test
    fun call_returnsActionResult() {
        val result = mockk<Bundle>()
        val action = TestAction(
            pluginContext = mockk(relaxed = true),
            phoneContext = mockk(relaxed = true),
            smsMsg = result,
            throwOnAction = false,
        )

        assertSame(result, action.call())
    }

    @Test
    fun call_returnsNullWhenActionThrows() {
        val action = TestAction(
            pluginContext = mockk(relaxed = true),
            phoneContext = mockk(relaxed = true),
            smsMsg = mockk(),
            throwOnAction = true,
        )

        assertNull(action.call())
    }

    private class TestAction(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: Bundle,
        private val throwOnAction: Boolean,
    ) : SmsCodeCallableAction<Bundle>(pluginContext, phoneContext, smsMsg) {
        override fun action(): Bundle? {
            if (throwOnAction) {
                error("boom")
            }
            return mSmsMsg
        }
    }
}
