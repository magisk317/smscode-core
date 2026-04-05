package io.github.magisk317.smscode.runtime.common.sms

import android.content.ContextWrapper
import io.github.magisk317.smscode.domain.model.SmsBlacklistConfig
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeSmsAdaptersTest {

    private val context = ContextWrapper(null)

    @Test
    fun runtimeSmsCodeAdapter_usesProvidedKeywordRuleAndResolver() {
        val adapter = RuntimeSmsCodeAdapter(
            keywordProvider = SmsKeywordProvider { _, override ->
                override ?: "验证码|code"
            },
            ruleProvider = SmsCodeRuleProvider {
                listOf(SmsCodeRuleSpec(company = "ACME", codeKeyword = "验证码", codeRegex = "\\d{6}"))
            },
            labelResolver = SmsPackageLabelResolver { _, label ->
                if (label == "ACME") "com.acme.app" else null
            },
        )

        val result = kotlinx.coroutines.runBlocking {
            adapter.parseSmsCodeResultIfExists(context, "【ACME】您的验证码是123456")
        }

        assertEquals("123456", result.code)
        assertEquals("com.acme.app", adapter.findPackageNameByLabel(context, "ACME"))
        assertTrue(adapter.parseCompanyCandidates("【ACME】您的验证码是123456").contains("ACME"))
    }

    @Test
    fun runtimeSmsBlacklistAdapter_usesProvidedConfig() {
        val adapter = RuntimeSmsBlacklistAdapter(
            configProvider = SmsBlacklistConfigProvider {
                SmsBlacklistConfig(
                    enabled = true,
                    actionDelete = true,
                    actionBlock = false,
                    numbers = "10086",
                    prefixes = "",
                    content = "",
                    regex = "",
                )
            },
        )

        val result = adapter.match(context, "10086", "hello")

        assertTrue(result.matched)
        assertTrue(result.actionDelete)
        assertFalse(result.actionBlock)
    }
}
