package io.github.magisk317.smscode.rule.utils

import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsCodeReviewRegressionTest {
    private val genericRule = SmsCodeRuleSpec(
        company = null,
        codeKeyword = "code",
        codeRegex = "[0-9]{6}",
        source = SmsCodeMatchedRuleSource.OFFICIAL,
    )

    private val voiceRule = SmsCodeRuleSpec(
        company = "Google Voice",
        codeKeyword = "code",
        codeRegex = "[0-9]{6}",
        packageNameHint = "com.google.android.apps.googlevoice",
        senderRegex = "Google Voice|Voice",
        priority = 120,
        source = SmsCodeMatchedRuleSource.OFFICIAL,
    )

    @Test
    fun `notification package restrictions do not suppress genuine SMS codes`() {
        val result = SmsCodeUtils.parseSmsCodeResult(
            content = "Your Google Voice verification code is 482910",
            keywordsRegex = "",
            rules = listOf(voiceRule, genericRule),
            source = SmsCodeParseSource(sender = "10690000", sourceKind = SmsCodeParseSourceKind.SMS),
        )
        assertEquals("482910", result.code)
    }

    @Test
    fun `user keyword extras still work alongside the official general catalog`() {
        val result = SmsCodeUtils.parseSmsCodeResult(
            content = "Your access ticket is 482910",
            keywordsRegex = "access ticket",
            rules = listOf(genericRule),
        )
        assertEquals("482910", result.code)
        assertEquals(SmsCodeMatchedRuleSource.BUILTIN, result.matchedRule?.source)
    }

    @Test
    fun `a deferred brand fallback cannot bypass notification source rejection`() {
        val fallbackRule = genericRule.copy(company = "Other service", priority = 200)
        val result = SmsCodeUtils.parseSmsCodeResult(
            content = "Google Voice verification code is 482910",
            keywordsRegex = "code",
            rules = listOf(fallbackRule, voiceRule),
            source = spoofedVoiceSource(),
        )
        assertEquals("", result.code)
    }

    @Test
    fun `generic rule priority cannot bypass notification source rejection`() {
        val result = SmsCodeUtils.parseSmsCodeResult(
            content = "Google Voice verification code is 482910",
            keywordsRegex = "",
            rules = listOf(genericRule.copy(priority = 200), voiceRule),
            source = spoofedVoiceSource(),
        )
        assertEquals("", result.code)
    }

    private fun spoofedVoiceSource() = SmsCodeParseSource(
        packageName = "com.example.chat",
        sender = "Google Voice",
        title = "Google Voice",
        sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
    )
}
