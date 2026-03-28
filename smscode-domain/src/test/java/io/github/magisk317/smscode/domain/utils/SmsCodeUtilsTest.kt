package io.github.magisk317.smscode.domain.utils

import io.github.magisk317.smscode.domain.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SmsCodeUtilsTest {

    @Test
    fun `parse prefers custom rule when available`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】login code is Z9X8, valid for five minutes",
            keywordsRegex = "code",
            rules = listOf(
                SmsCodeRuleSpec(
                    company = "Acme",
                    codeKeyword = "login",
                    codeRegex = "[A-Z0-9]{4}",
                ),
            ),
        )

        assertEquals("Z9X8", result.code)
        assertNotNull(result.matchedRule)
        assertEquals(SmsCodeMatchedRuleSource.CUSTOM, result.matchedRule?.source)
    }

    @Test
    fun `parse ignores date like tokens and keeps nearby verification code`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】2026年03月28日验证码123456，请勿泄露。",
            keywordsRegex = "验证码",
        )

        assertEquals("123456", result.code)
        assertEquals(SmsCodeMatchedRuleSource.BUILTIN, result.matchedRule?.source)
    }
}
