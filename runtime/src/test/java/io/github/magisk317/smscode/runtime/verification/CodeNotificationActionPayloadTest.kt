package io.github.magisk317.smscode.runtime.verification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeNotificationActionPayloadTest {
    @Test
    fun fillAutoCancelIntent_writesNotificationId() {
        val intent = CodeNotificationActionPayload.fillAutoCancelIntent(
            FakeIntent(),
            notificationId = 42,
        ) as FakeIntent

        assertEquals(42, intent.intExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID))
    }

    @Test
    fun fillCopyCodeIntent_setsActionAndPayload() {
        val intent = CodeNotificationActionPayload.fillCopyCodeIntent(
            FakeIntent(),
            action = "com.example.ACTION_COPY_CODE",
            smsCode = "123456",
            notificationId = 7,
        ) as FakeIntent

        assertEquals("com.example.ACTION_COPY_CODE", intent.action)
        assertEquals("123456", intent.stringExtra(CodeNotificationActionPayload.EXTRA_KEY_CODE))
        assertEquals(7, intent.intExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID))
    }

    @Test
    fun createAutoCancelIntent_targetsReceiverAndWritesNotificationId() {
        val context = mockk<Context>(relaxed = true)
        var receiverClass: Class<*>? = null

        val intent = CodeNotificationActionPayload.createAutoCancelIntent(
            context = context,
            receiverClass = DummyReceiver::class.java,
            notificationId = 42,
            intentFactory = { _, targetClass ->
                receiverClass = targetClass
                FakeIntent()
            },
        ) as FakeIntent

        assertEquals(DummyReceiver::class.java, receiverClass)
        assertEquals(42, intent.intExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID))
    }

    @Test
    fun createCopyCodeIntent_targetsReceiverAndWritesPayload() {
        val context = mockk<Context>(relaxed = true)
        var receiverClass: Class<*>? = null

        val intent = CodeNotificationActionPayload.createCopyCodeIntent(
            context = context,
            receiverClass = DummyReceiver::class.java,
            action = "com.example.ACTION_COPY_CODE",
            smsCode = "123456",
            notificationId = 7,
            intentFactory = { _, targetClass ->
                receiverClass = targetClass
                FakeIntent()
            },
        ) as FakeIntent

        assertEquals(DummyReceiver::class.java, receiverClass)
        assertEquals("com.example.ACTION_COPY_CODE", intent.action)
        assertEquals("123456", intent.stringExtra(CodeNotificationActionPayload.EXTRA_KEY_CODE))
        assertEquals(7, intent.intExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID))
    }

    @Test
    fun createBroadcastPendingIntent_usesUpdateCurrentAndImmutableFlag() {
        val context = mockk<Context>(relaxed = true)
        val sourceIntent = FakeIntent()
        val pendingIntent = mockk<PendingIntent>()
        var requestCode: Int? = null
        var receivedIntent: Intent? = null
        var flags: Int? = null

        val result = CodeNotificationActionPayload.createBroadcastPendingIntent(
            context = context,
            requestCode = 7,
            intent = sourceIntent,
            pendingIntentFactory = { _, pendingRequestCode, pendingSourceIntent, pendingFlags ->
                requestCode = pendingRequestCode
                receivedIntent = pendingSourceIntent
                flags = pendingFlags
                pendingIntent
            },
        )

        assertEquals(pendingIntent, result)
        assertEquals(7, requestCode)
        assertEquals(sourceIntent, receivedIntent)
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
            flags,
        )
    }

    @Test
    fun readNotificationId_returnsDefaultMissingValue() {
        assertEquals(
            CodeNotificationActionPayload.NOTIFICATION_ID_MISSING,
            CodeNotificationActionPayload.readNotificationId(FakeIntent()),
        )
    }

    @Test
    fun readNotificationId_supportsCustomMissingValue() {
        assertEquals(
            CodeNotificationActionPayload.AUTO_CANCEL_NOTIFICATION_ID_MISSING,
            CodeNotificationActionPayload.readNotificationId(
                FakeIntent(),
                CodeNotificationActionPayload.AUTO_CANCEL_NOTIFICATION_ID_MISSING,
            ),
        )
    }

    @Test
    fun hasNotificationId_honorsMissingValue() {
        assertFalse(CodeNotificationActionPayload.hasNotificationId(-1))
        assertTrue(CodeNotificationActionPayload.hasNotificationId(5))
        assertFalse(
            CodeNotificationActionPayload.hasNotificationId(
                CodeNotificationActionPayload.AUTO_CANCEL_NOTIFICATION_ID_MISSING,
                CodeNotificationActionPayload.AUTO_CANCEL_NOTIFICATION_ID_MISSING,
            ),
        )
    }

    @Test
    fun readSmsCode_returnsCodeExtra() {
        val intent = FakeIntent().apply {
            putExtra(CodeNotificationActionPayload.EXTRA_KEY_CODE, "654321")
        }

        assertEquals("654321", CodeNotificationActionPayload.readSmsCode(intent))
    }

    @Test
    fun readCopyCodeRequest_ignoresDifferentAction() {
        val intent = FakeIntent().apply {
            action = "com.example.OTHER"
            putExtra(CodeNotificationActionPayload.EXTRA_KEY_CODE, "123456")
            putExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID, 8)
        }

        assertEquals(null, CodeNotificationActionPayload.readCopyCodeRequest(intent, "com.example.ACTION_COPY_CODE"))
    }

    @Test
    fun readCopyCodeRequest_readsCodeAndNotificationId() {
        val intent = FakeIntent().apply {
            action = "com.example.ACTION_COPY_CODE"
            putExtra(CodeNotificationActionPayload.EXTRA_KEY_CODE, "123456")
            putExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID, 8)
        }

        val request = CodeNotificationActionPayload.readCopyCodeRequest(intent, "com.example.ACTION_COPY_CODE")

        assertEquals(
            CodeNotificationActionPayload.CopyCodeRequest(
                smsCode = "123456",
                notificationId = 8,
            ),
            request,
        )
        assertEquals(8, request?.notificationIdToCancel)
    }

    @Test
    fun readCopyCodeRequest_usesNullCancelableIdWhenMissing() {
        val intent = FakeIntent().apply {
            action = "com.example.ACTION_COPY_CODE"
        }

        val request = CodeNotificationActionPayload.readCopyCodeRequest(intent, "com.example.ACTION_COPY_CODE")

        assertEquals(CodeNotificationActionPayload.NOTIFICATION_ID_MISSING, request?.notificationId)
        assertEquals(null, request?.notificationIdToCancel)
    }

    @Test
    fun readAutoCancelRequest_requiresNotificationId() {
        assertEquals(null, CodeNotificationActionPayload.readAutoCancelRequest(FakeIntent()))

        val intent = FakeIntent().apply {
            putExtra(CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID, 9)
        }

        assertEquals(
            CodeNotificationActionPayload.AutoCancelRequest(notificationId = 9),
            CodeNotificationActionPayload.readAutoCancelRequest(intent),
        )
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

        fun stringExtra(name: String): String? = extras[name] as? String

        fun intExtra(name: String): Int? = extras[name] as? Int
    }

    private class DummyReceiver
}
