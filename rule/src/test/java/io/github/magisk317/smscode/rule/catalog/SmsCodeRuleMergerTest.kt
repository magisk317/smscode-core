package io.github.magisk317.smscode.rule.catalog

import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.rule.utils.SmsCodeUtils
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsCodeRuleMergerTest {
    @Test
    fun `merger keeps user rules first and sorts official rules by priority`() = runBlocking {
        val userRule = SmsCodeRuleSpec(
            company = "Acme",
            codeKeyword = "登录",
            codeRegex = "(?<=登录码:)\\d{4}",
        )
        val officialLow = officialRule(
            id = "low",
            codeKeyword = "验证码",
            codeRegex = "(?<=验证码:)\\d{6}",
            priority = 1,
        )
        val officialHigh = officialRule(
            id = "high",
            codeKeyword = "验证码",
            codeRegex = "(?<=验证码:)\\d{5}",
            priority = 100,
        )

        val merged = SmsCodeRuleMerger.merge(
            userRules = listOf(userRule),
            officialRules = listOf(officialLow, officialHigh),
        )
        val userResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】登录码:4321 验证码:12345",
            keywordsRegex = "验证码|登录码",
            rules = merged,
        )
        val officialResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】验证码:12345",
            keywordsRegex = "验证码",
            rules = merged,
        )

        assertEquals("4321", userResult.code)
        assertEquals(SmsCodeMatchedRuleSource.CUSTOM, userResult.matchedRule?.source)
        assertEquals("12345", officialResult.code)
        assertEquals(SmsCodeMatchedRuleSource.OFFICIAL, officialResult.matchedRule?.source)
    }

    private fun officialRule(
        id: String,
        codeKeyword: String,
        codeRegex: String,
        priority: Int,
    ): OfficialSmsCodeRule = OfficialSmsCodeRule(
        setId = "set",
        setName = "Set",
        setLocale = "zh-CN",
        setUpdatedAt = "2026-05-01T00:00:00+08:00",
        sourcePath = "rules/set.json",
        id = id,
        company = "Acme",
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        priority = priority,
    )
}
