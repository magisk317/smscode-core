package io.github.magisk317.smscode.verification

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.Serializable

class CodeNotificationPayloadTest {
    @Test
    fun createBroadcastIntent_setsActionReceiverAndPayload() {
        val intent = CodeNotificationPayload.createBroadcastIntent(
            applicationId = "com.example.app",
            receiverClassName = "com.example.app.CodeNotificationReceiver",
            payload = CodeNotificationPayload.Payload(
                sender = "1068",
                company = "Bank",
                smsCode = "123456",
                notificationId = 42,
                autoCancelEnabled = true,
                retentionTimeMs = 3000L,
                token = "token",
            ),
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertEquals("com.example.app.ACTION_SHOW_CODE_NOTIFICATION", intent.action)
        assertEquals("com.example.app", intent.targetPackage)
        assertEquals("com.example.app.CodeNotificationReceiver", intent.targetClass)
        assertEquals("1068", intent.extra(CodeNotificationPayload.EXTRA_SENDER))
        assertEquals("Bank", intent.extra(CodeNotificationPayload.EXTRA_COMPANY))
        assertEquals("123456", intent.extra(CodeNotificationPayload.EXTRA_SMS_CODE))
        assertEquals(42, intent.extra(CodeNotificationPayload.EXTRA_NOTIFICATION_ID))
        assertEquals(true, intent.extra(CodeNotificationPayload.EXTRA_AUTO_CANCEL_ENABLED))
        assertEquals(3000L, intent.extra(CodeNotificationPayload.EXTRA_RETENTION_TIME_MS))
        assertEquals("token", intent.extra(CodeNotificationPayload.EXTRA_IPC_TOKEN))
    }

    @Test
    fun broadcastContract_createsBroadcastIntent() {
        val contract = CodeNotificationPayload.BroadcastContract(
            applicationId = "com.example.app",
            receiverClassName = "com.example.app.CodeNotificationReceiver",
        )
        val intent = contract.createIntent(
            sender = "1068",
            company = "Bank",
            smsCode = "123456",
            notificationId = 42,
            autoCancelEnabled = true,
            retentionTimeMs = 3000L,
            token = "token",
            intentFactory = ::FakeIntent,
        ) as FakeIntent

        assertEquals("com.example.app.ACTION_SHOW_CODE_NOTIFICATION", contract.actionShowCodeNotification)
        assertEquals("com.example.app.ACTION_SHOW_CODE_NOTIFICATION", intent.action)
        assertEquals("com.example.app", intent.targetPackage)
        assertEquals("com.example.app.CodeNotificationReceiver", intent.targetClass)
        assertEquals("1068", intent.extra(CodeNotificationPayload.EXTRA_SENDER))
        assertEquals("Bank", intent.extra(CodeNotificationPayload.EXTRA_COMPANY))
        assertEquals("123456", intent.extra(CodeNotificationPayload.EXTRA_SMS_CODE))
        assertEquals(42, intent.extra(CodeNotificationPayload.EXTRA_NOTIFICATION_ID))
        assertEquals(true, intent.extra(CodeNotificationPayload.EXTRA_AUTO_CANCEL_ENABLED))
        assertEquals(3000L, intent.extra(CodeNotificationPayload.EXTRA_RETENTION_TIME_MS))
        assertEquals("token", intent.extra(CodeNotificationPayload.EXTRA_IPC_TOKEN))
    }

    @Test
    fun readReceiverRequest_ignoresDifferentAction() {
        val request = CodeNotificationPayload.readReceiverRequest(
            intent = FakeIntent("com.example.OTHER"),
            expectedAction = "com.example.ACTION_SHOW_CODE_NOTIFICATION",
            expectedToken = "token",
            sentFromUid = null,
        )

        assertEquals(CodeNotificationPayload.ReceiverRequest.Ignored, request)
    }

    @Test
    fun readReceiverRequest_rejectsBlankCode() {
        val intent = CodeNotificationPayload.fillIntent(
            FakeIntent("com.example.ACTION_SHOW_CODE_NOTIFICATION"),
            CodeNotificationPayload.Payload(
                sender = "1068",
                company = "Bank",
                smsCode = "",
                notificationId = 42,
                autoCancelEnabled = true,
                retentionTimeMs = 3000L,
                token = "token",
            ),
        )

        val request = CodeNotificationPayload.readReceiverRequest(
            intent = intent,
            expectedAction = "com.example.ACTION_SHOW_CODE_NOTIFICATION",
            expectedToken = "token",
            sentFromUid = null,
        )

        assertEquals(
            CodeNotificationPayload.ReceiverRequest.Rejected(
                resultData = CodeNotificationPayload.RESULT_DATA_BLANK_CODE,
                expectedTokenEmpty = false,
                receivedTokenEmpty = false,
                sentFromUid = null,
            ),
            request,
        )
    }

    @Test
    fun readReceiverRequest_rejectsInvalidToken() {
        val intent = CodeNotificationPayload.fillIntent(
            FakeIntent("com.example.ACTION_SHOW_CODE_NOTIFICATION"),
            CodeNotificationPayload.Payload(
                sender = "1068",
                company = "Bank",
                smsCode = "123456",
                notificationId = 42,
                autoCancelEnabled = true,
                retentionTimeMs = 3000L,
                token = "wrong",
            ),
        )

        val request = CodeNotificationPayload.readReceiverRequest(
            intent = intent,
            expectedAction = "com.example.ACTION_SHOW_CODE_NOTIFICATION",
            expectedToken = "token",
            sentFromUid = 2000,
        )

        assertEquals(
            CodeNotificationPayload.ReceiverRequest.Rejected(
                resultData = CodeNotificationPayload.RESULT_DATA_REJECTED_TOKEN,
                expectedTokenEmpty = false,
                receivedTokenEmpty = false,
                sentFromUid = 2000,
            ),
            request,
        )
    }

    @Test
    fun readReceiverRequest_acceptsMatchingToken() {
        val payload = CodeNotificationPayload.Payload(
            sender = "1068",
            company = "Bank",
            smsCode = "123456",
            notificationId = 42,
            autoCancelEnabled = true,
            retentionTimeMs = 3000L,
            token = "token",
        )
        val intent = CodeNotificationPayload.fillIntent(
            FakeIntent("com.example.ACTION_SHOW_CODE_NOTIFICATION"),
            payload,
        )

        val request = CodeNotificationPayload.readReceiverRequest(
            intent = intent,
            expectedAction = "com.example.ACTION_SHOW_CODE_NOTIFICATION",
            expectedToken = "token",
            sentFromUid = null,
        )

        assertEquals(
            CodeNotificationPayload.ReceiverRequest.Accepted(
                payload = payload,
                smsCode = "123456",
            ),
            request,
        )
    }

    @Test
    fun readReceiverRequest_acceptsSystemBypassWhenExpectedTokenEmpty() {
        val payload = CodeNotificationPayload.Payload(
            sender = "1068",
            company = "Bank",
            smsCode = "123456",
            notificationId = 42,
            autoCancelEnabled = true,
            retentionTimeMs = 3000L,
            token = null,
        )
        val intent = CodeNotificationPayload.fillIntent(
            FakeIntent("com.example.ACTION_SHOW_CODE_NOTIFICATION"),
            payload,
        )

        val request = CodeNotificationPayload.readReceiverRequest(
            intent = intent,
            expectedAction = "com.example.ACTION_SHOW_CODE_NOTIFICATION",
            expectedToken = "",
            sentFromUid = 1001,
        )

        assertEquals(
            CodeNotificationPayload.ReceiverRequest.Accepted(
                payload = payload,
                smsCode = "123456",
            ),
            request,
        )
    }

    @Test
    fun resolveTitle_prefersCompanyThenSenderThenFallback() {
        assertEquals("Bank", CodeNotificationPayload.resolveTitle("Bank", "1068", "App"))
        assertEquals("1068", CodeNotificationPayload.resolveTitle("", "1068", "App"))
        assertEquals("App", CodeNotificationPayload.resolveTitle(null, "", "App"))
    }

    @Test
    fun resolveTitle_readsSmsMessageFields() {
        assertEquals(
            "Bank",
            CodeNotificationPayload.resolveTitle(
                message = FakeSmsMessage(company = "Bank", sender = "1068"),
                fallbackTitle = "App",
            ),
        )
    }

    @Test
    fun shouldAllowSmsHookTokenBypass_acceptsSystemAndPhone() {
        assertTrue(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(1000, sdkInt = 34))
        assertTrue(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(1001, sdkInt = 34))
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(2000, sdkInt = 34))
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(null, sdkInt = 33))
    }

    @Test
    fun shouldAllowSmsHookTokenBypass_requiresEmptyExpectedTokenForCompatPath() {
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass("", null, sdkInt = 33))
        assertTrue(CodeNotificationPayload.shouldAllowSmsHookTokenBypass(null, 1001, sdkInt = 34))
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass("initialized", null, sdkInt = 33))
        assertFalse(CodeNotificationPayload.shouldAllowSmsHookTokenBypass("initialized", 1001, sdkInt = 34))
    }

    @Test
    fun deliveryResultFromBroadcast_mapsPostedAndFailureResults() {
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = true,
                reason = CodeNotificationPayload.RESULT_DATA_POSTED,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_POSTED,
                null,
            ),
        )
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = CodeNotificationPayload.RESULT_DATA_REJECTED_TOKEN,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_FAILED,
                CodeNotificationPayload.RESULT_DATA_REJECTED_TOKEN,
            ),
        )
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = CodeNotificationPayload.RESULT_DATA_NO_RECEIVER,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_UNDELIVERED,
                null,
            ),
        )
        assertEquals(
            CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = CodeNotificationPayload.RESULT_DATA_NOTIFY_FAILED,
            ),
            CodeNotificationPayload.deliveryResultFromBroadcast(
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_FAILED,
                null,
            ),
        )
    }

    @Test
    fun resultCodeFor_mapsSuccessFlagToBroadcastResultCode() {
        assertEquals(
            CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_POSTED,
            CodeNotificationPayload.resultCodeFor(success = true),
        )
        assertEquals(
            CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_FAILED,
            CodeNotificationPayload.resultCodeFor(success = false),
        )
    }

    @Test
    fun resolveSentFromUidCompat_ignoresApisBeforeShareIdentitySupport() {
        var called = false

        val uid = CodeNotificationPayload.resolveSentFromUidCompat(sdkInt = 33) {
            called = true
            1000
        }

        assertEquals(null, uid)
        assertFalse(called)
    }

    @Test
    fun resolveSentFromUidCompat_readsUidOnApi34AndAbove() {
        assertEquals(
            1001,
            CodeNotificationPayload.resolveSentFromUidCompat(sdkInt = 34) { 1001 },
        )
    }

    @Test
    fun resolveSentFromUidCompat_returnsNullWhenUidReadFails() {
        assertEquals(
            null,
            CodeNotificationPayload.resolveSentFromUidCompat(sdkInt = 34) {
                throw IllegalStateException("unavailable")
            },
        )
    }

    private class FakeIntent(
        private val actionValue: String? = null,
    ) : Intent() {
        private val extras = linkedMapOf<String, Any?>()
        var targetPackage: String? = null
            private set
        var targetClass: String? = null
            private set

        override fun getAction(): String? = actionValue

        override fun setClassName(packageName: String, className: String): Intent {
            targetPackage = packageName
            targetClass = className
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

        override fun getIntExtra(name: String?, defaultValue: Int): Int =
            extras[name] as? Int ?: defaultValue

        override fun getBooleanExtra(name: String?, defaultValue: Boolean): Boolean =
            extras[name] as? Boolean ?: defaultValue

        override fun getLongExtra(name: String?, defaultValue: Long): Long =
            extras[name] as? Long ?: defaultValue

        fun extra(name: String): Any? = extras[name]
    }

    private data class FakeSmsMessage(
        override val sender: String? = null,
        override val company: String? = null,
    ) : SmsMessage {
        override val body: String? = null
        override val date: Long = 0L
        override val smsCode: String? = null
        override val packageName: String? = null
    }
}
