package io.github.magisk317.smscode.xposed.hook.notification

import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `resolves ipc token from core prefs`() {
        installCoreRuntime(debug = false)
        installCorePrefs(
            stringValues = mapOf(NotificationHookConst.KEY_IPC_TOKEN to "ipc-token-from-core"),
        )

        val hook = NotificationManagerHook()
        val method = NotificationManagerHook::class.java.getDeclaredMethod(
            "resolveIpcToken",
            String::class.java,
        )
        method.isAccessible = true

        assertEquals("ipc-token-from-core", method.invoke(hook, "caller.pkg") as String)
    }

    @Test
    fun `uses core prefs for debug and recovery booleans`() {
        installCoreRuntime(debug = true)
        installCorePrefs(
            booleanValues = mapOf(
                NotificationHookConst.KEY_SENSITIVE_DEBUG_LOG_MODE to true,
                NotificationHookConst.KEY_FORCE_STOP_RECOVERY to true,
            ),
        )

        val hook = NotificationManagerHook()
        val debugMethod = NotificationManagerHook::class.java.getDeclaredMethod(
            "isSensitiveDebugLogEnabled",
            String::class.java,
        )
        debugMethod.isAccessible = true
        val recoveryMethod = NotificationManagerHook::class.java.getDeclaredMethod(
            "isForceStopRecoveryEnabled",
            String::class.java,
        )
        recoveryMethod.isAccessible = true

        assertTrue(debugMethod.invoke(hook, "caller.pkg") as Boolean)
        assertTrue(recoveryMethod.invoke(hook, "caller.pkg") as Boolean)
    }

    @Test
    fun `falls back to defaults when core prefs is unset`() {
        installCoreRuntime(debug = false)
        installCorePrefs()

        val hook = NotificationManagerHook()
        val tokenMethod = NotificationManagerHook::class.java.getDeclaredMethod(
            "resolveIpcToken",
            String::class.java,
        )
        tokenMethod.isAccessible = true
        val debugMethod = NotificationManagerHook::class.java.getDeclaredMethod(
            "isSensitiveDebugLogEnabled",
            String::class.java,
        )
        debugMethod.isAccessible = true

        assertEquals("", tokenMethod.invoke(hook, "caller.pkg") as String)
        assertFalse(debugMethod.invoke(hook, "caller.pkg") as Boolean)
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

    private fun installCorePrefs(
        booleanValues: Map<String, Boolean> = emptyMap(),
        stringValues: Map<String, String> = emptyMap(),
        intValues: Map<String, Int> = emptyMap(),
    ) {
        CorePrefs.install(
            object : CorePrefsAccess {
                override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
                    booleanValues[key] ?: defaultValue

                override fun getString(key: String, defaultValue: String): String =
                    stringValues[key] ?: defaultValue

                override fun getInt(key: String, defaultValue: Int): Int =
                    intValues[key] ?: defaultValue
            },
        )
    }

    private fun installCoreRuntime(debug: Boolean) {
        CoreRuntime.install(
            object : CoreRuntimeAccess {
                override val logTag: String = "test"
                override val logLevel: Int = android.util.Log.INFO
                override val logToXposed: Boolean = false
                override val debug: Boolean = debug
                override val applicationId: String = "io.github.magisk317.relay"
                override val actionNamespace: String = "io.github.magisk317.relay"
            },
        )
    }
}
