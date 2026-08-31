package io.github.magisk317.smscode.runtime.common.sms

import android.content.ContextWrapper
import io.github.magisk317.smscode.rule.model.SmsBlacklistConfig
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
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

    @Test
    fun runtimeSmsCodeAdapter_forwardsSourceToRuleParser() {
        val adapter = RuntimeSmsCodeAdapter(
            keywordProvider = SmsKeywordProvider { _, override ->
                override ?: "验证码"
            },
            ruleProvider = SmsCodeRuleProvider {
                listOf(
                    SmsCodeRuleSpec(
                        company = "Google Voice",
                        codeKeyword = "verification code",
                        codeRegex = "\\d{6}",
                        packageNameHint = "com.google.android.apps.googlevoice",
                    ),
                )
            },
            labelResolver = SmsPackageLabelResolver { _, _ -> null },
        )

        val result = kotlinx.coroutines.runBlocking {
            adapter.parseSmsCodeResultIfExists(
                context = context,
                content = "Your Google Voice verification code is 864210",
                source = SmsCodeParseSource(
                    packageName = "com.google.android.apps.googlevoice",
                    title = "Google Voice",
                    sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
                ),
            )
        }

        assertEquals("864210", result.code)
    }
}
