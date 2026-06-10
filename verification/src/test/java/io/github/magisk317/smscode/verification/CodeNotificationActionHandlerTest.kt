package io.github.magisk317.smscode.verification

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodeNotificationActionHandlerTest {
    @Test
    fun handleCopyCodeIntent_ignoresDifferentAction() {
        val cancelledIds = mutableListOf<Int>()
        val copiedCodes = mutableListOf<String>()

        val result = CodeNotificationActionHandler.handleCopyCodeIntent(
            intent = FakeIntent().apply {
                action = "com.example.OTHER"
                putExtra(CodeNotificationActionPayload.EXTRA_KEY_CODE, "123456")
                putExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID, 8)
            },
            expectedAction = "com.example.ACTION_COPY_CODE",
            cancelNotification = cancelledIds::add,
            copyCode = copiedCodes::add,
        )

        assertEquals(
            CodeNotificationActionHandler.CopyCodeResult(
                handled = false,
                notificationCancelled = false,
                copied = false,
            ),
            result,
        )
        assertEquals(emptyList<Int>(), cancelledIds)
        assertEquals(emptyList<String>(), copiedCodes)
    }

    @Test
    fun handleCopyCodeIntent_cancelsNotificationAndCopiesCode() {
        val cancelledIds = mutableListOf<Int>()
        val copiedCodes = mutableListOf<String>()

        val result = CodeNotificationActionHandler.handleCopyCodeIntent(
            intent = FakeIntent().apply {
                action = "com.example.ACTION_COPY_CODE"
                putExtra(CodeNotificationActionPayload.EXTRA_KEY_CODE, "123456")
                putExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID, 8)
            },
            expectedAction = "com.example.ACTION_COPY_CODE",
            cancelNotification = cancelledIds::add,
            copyCode = copiedCodes::add,
        )

        assertEquals(
            CodeNotificationActionHandler.CopyCodeResult(
                handled = true,
                notificationCancelled = true,
                copied = true,
            ),
            result,
        )
        assertEquals(listOf(8), cancelledIds)
        assertEquals(listOf("123456"), copiedCodes)
    }

    @Test
    fun handleCopyCodeIntent_allowsMissingCodeAndNotificationId() {
        val cancelledIds = mutableListOf<Int>()
        val copiedCodes = mutableListOf<String>()

        val result = CodeNotificationActionHandler.handleCopyCodeIntent(
            intent = FakeIntent().apply {
                action = "com.example.ACTION_COPY_CODE"
            },
            expectedAction = "com.example.ACTION_COPY_CODE",
            cancelNotification = cancelledIds::add,
            copyCode = copiedCodes::add,
        )

        assertEquals(
            CodeNotificationActionHandler.CopyCodeResult(
                handled = true,
                notificationCancelled = false,
                copied = false,
            ),
            result,
        )
        assertEquals(emptyList<Int>(), cancelledIds)
        assertEquals(emptyList<String>(), copiedCodes)
    }

    @Test
    fun handleAutoCancelIntent_requiresNotificationId() {
        val cancelledIds = mutableListOf<Int>()

        val missingResult = CodeNotificationActionHandler.handleAutoCancelIntent(
            intent = FakeIntent(),
            cancelNotification = cancelledIds::add,
        )

        assertEquals(
            CodeNotificationActionHandler.AutoCancelResult(
                handled = false,
                notificationCancelled = false,
            ),
            missingResult,
        )

        val result = CodeNotificationActionHandler.handleAutoCancelIntent(
            intent = FakeIntent().apply {
                putExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID, 9)
            },
            cancelNotification = cancelledIds::add,
        )

        assertEquals(
            CodeNotificationActionHandler.AutoCancelResult(
                handled = true,
                notificationCancelled = true,
            ),
            result,
        )
        assertEquals(listOf(9), cancelledIds)
    }

    private class FakeIntent : Intent() {
        private val extras = linkedMapOf<String, Any?>()
        private var actionValue: String? = null

        override fun getAction(): String? = actionValue

        override fun setAction(action: String?): Intent {
            actionValue = action
            return this
        }

        override fun putExtra(name: String, value: String?): Intent {
            extras[name] = value
            return this
        }

        override fun putExtra(name: String, value: Int): Intent {
            extras[name] = value
            return this
        }

        override fun getStringExtra(name: String?): String? = extras[name] as? String

        override fun getIntExtra(name: String?, defaultValue: Int): Int {
            return extras[name] as? Int ?: defaultValue
        }
    }
}
