package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.xposed.utils.XLog
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecordSmsDedupHelperTest {
    @BeforeEach
    fun setUp() {
        XLog.setTestSink { _, _ -> }
    }

    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun shouldSkipByWindow_skipsOnFingerprintDuplicate() {
        val sms = FakeSmsMessage(
            sender = "10086",
            body = "code 1234",
            date = 1_000L,
            smsCode = "1234",
            packageName = "com.android.phone",
            company = "carrier",
        )

        val skipped = RecordSmsDedupHelper.shouldSkipByWindow(
            smsMsg = sms,
            eventLabel = "evt-1",
            hasFingerprintDuplicate = { sender, body, from, to ->
                sender == "10086" && body == "code 1234" && from == 0L && to == 21_000L
            },
            hasCodeDuplicateInWindow = { _, _, _ -> false },
            hasCodeDuplicateByPackage = { _, _, _, _ -> false },
            hasCodeDuplicateByCompany = { _, _, _, _ -> false },
        )

        assertTrue(skipped)
    }

    @Test
    fun shouldSkipByWindow_skipsOnPackageDuplicate() {
        val sms = FakeSmsMessage(
            sender = "bank",
            body = "Your code is 5678",
            date = 10_000L,
            smsCode = "5678",
            packageName = "com.google.android.apps.messaging",
            company = "bank",
        )

        val skipped = RecordSmsDedupHelper.shouldSkipByWindow(
            smsMsg = sms,
            eventLabel = "evt-2",
            hasFingerprintDuplicate = { _, _, _, _ -> false },
            hasCodeDuplicateInWindow = { _, _, _ -> false },
            hasCodeDuplicateByPackage = { code, pkg, from, to ->
                code == "5678" &&
                    pkg == "com.google.android.apps.messaging" &&
                    from == 0L &&
                    to == 30_000L
            },
            hasCodeDuplicateByCompany = { _, _, _, _ -> false },
        )

        assertTrue(skipped)
    }

    @Test
    fun shouldSkipByWindow_allowsFreshMessage() {
        val sms = FakeSmsMessage(
            sender = "bank",
            body = "Your code is 5678",
            date = 10_000L,
            smsCode = "5678",
            packageName = "com.google.android.apps.messaging",
            company = "bank",
        )

        val skipped = RecordSmsDedupHelper.shouldSkipByWindow(
            smsMsg = sms,
            eventLabel = "evt-3",
            hasFingerprintDuplicate = { _, _, _, _ -> false },
            hasCodeDuplicateInWindow = { _, _, _ -> false },
            hasCodeDuplicateByPackage = { _, _, _, _ -> false },
            hasCodeDuplicateByCompany = { _, _, _, _ -> false },
        )

        assertFalse(skipped)
    }

    @Test
    fun shouldSkipByWindow_skipsOnCodeWindowBeforeFingerprint() {
        val sms = FakeSmsMessage(
            sender = "中国移动",
            body = "【验证码】888686，尊敬的用户，您正在登录中国移动 APP",
            date = 27_000L,
            smsCode = "888686",
            packageName = "com.android.mms",
            company = "中国移动",
        )

        val skipped = RecordSmsDedupHelper.shouldSkipByWindow(
            smsMsg = sms,
            eventLabel = "evt-4",
            hasFingerprintDuplicate = { _, _, _, _ -> false },
            hasCodeDuplicateInWindow = { code, from, to ->
                code == "888686" && from == 7_000L && to == 47_000L
            },
            hasCodeDuplicateByPackage = { _, _, _, _ -> false },
            hasCodeDuplicateByCompany = { _, _, _, _ -> false },
        )

        assertTrue(skipped)
    }

    private data class FakeSmsMessage(
        override val sender: String?,
        override val body: String?,
        override val date: Long,
        override val smsCode: String?,
        override val packageName: String?,
        override val company: String?,
    ) : SmsMessage
}
