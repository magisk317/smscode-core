package io.github.magisk317.smscode.xposed.hook.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationManagerHookTest {

    @Test
    fun `does not treat domain token as verification code for telephony sms notifications`() {
        val content =
            "【平安银行】您是我行优质客户，阅读业务条款后回复260404确认办理，可申请取现金额50000.00元，" +
                "快至实时划转到储蓄卡1822。日利率0.050%，年化利率18.25%（单利），且手续费一次性收取0.000%。" +
                "请务必点击 br.pingan.com/zYR 阅读业务条款，回复短信即视为您同意办理，04月07日前有效，以审批为准。拒收请回复R"

        assertEquals("", extractLikelyVerificationCode(content))
    }

    @Test
    fun `extracts numeric verification code when real keyword is present`() {
        val content = "【平安银行】您的验证码123456，请勿泄露给他人。"

        assertEquals("123456", extractLikelyVerificationCode(content))
    }

    @Test
    fun `extracts alphanumeric code for standalone english keyword`() {
        val content = "Verification code: AB12, valid for 5 minutes."

        assertEquals("AB12", extractLikelyVerificationCode(content))
    }

    private fun extractLikelyVerificationCode(content: String): String {
        val hook = NotificationManagerHook()
        val method = NotificationManagerHook::class.java.getDeclaredMethod(
            "extractLikelyVerificationCode",
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(hook, content) as String
    }
}
