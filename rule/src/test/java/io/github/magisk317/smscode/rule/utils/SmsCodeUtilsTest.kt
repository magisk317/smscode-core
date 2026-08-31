package io.github.magisk317.smscode.rule.utils

import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
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

    @Test
    fun `parse ignores url domain token even when custom keyword is too broad`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "请点击 br.pingan.com/zYR 完成后续操作。",
            keywordsRegex = "pin",
        )

        assertEquals("", result.code)
    }

    @Test
    fun `parse accepts verification code followed by sentence period`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content =
                "【优速通】您的 Coinbase 验证代码是：123456. " +
                    "请勿与任何人分享此代码；我们的员工绝不会要求您提供代码。",
            keywordsRegex = "验证代码",
        )

        assertEquals("123456", result.code)
        assertEquals(SmsCodeMatchedRuleSource.BUILTIN, result.matchedRule?.source)
    }

    @Test
    fun `official rule accepts verification code followed by sentence period`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】验证代码：123456. 请勿泄露。",
            keywordsRegex = "验证代码",
            rules = listOf(
                SmsCodeRuleSpec(
                    company = "Acme",
                    codeKeyword = "验证代码",
                    codeRegex = "\\d{6}",
                    source = SmsCodeMatchedRuleSource.OFFICIAL,
                ),
            ),
        )

        assertEquals("123456", result.code)
        assertEquals(SmsCodeMatchedRuleSource.OFFICIAL, result.matchedRule?.source)
    }

    @Test
    fun `parse still rejects numeric token inside bare domain`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "Verification code lookup: example.123456.com",
            keywordsRegex = "code",
        )

        assertEquals("", result.code)
    }

    @Test
    fun `parse skips invalid custom rule and keeps later matching rule`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】验证码:246810",
            keywordsRegex = "验证码",
            rules = listOf(
                SmsCodeRuleSpec(
                    company = "Acme",
                    codeKeyword = "验证码",
                    codeRegex = "[",
                ),
                SmsCodeRuleSpec(
                    company = "Acme",
                    codeKeyword = "验证码",
                    codeRegex = "(?<=验证码:)\\d{6}",
                ),
            ),
        )

        assertEquals("246810", result.code)
        assertEquals(SmsCodeMatchedRuleSource.CUSTOM, result.matchedRule?.source)
    }

    @Test
    fun `parse can fallback to custom rule when company token is missing`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【验证密码】领奖验证码:2108，5分钟有效；请前往移动云盘APP领取。",
            keywordsRegex = "验证码",
            rules = listOf(
                SmsCodeRuleSpec(
                    company = "中国移动云盘",
                    codeKeyword = "领奖验证码",
                    codeRegex = "(?<=领奖验证码[:：])\\d{4,8}",
                ),
            ),
        )

        assertEquals("2108", result.code)
        assertEquals(SmsCodeMatchedRuleSource.CUSTOM, result.matchedRule?.source)
    }

    @Test
    fun `parse marks official rule hit as official source`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】验证码:246810",
            keywordsRegex = "验证码",
            rules = listOf(
                SmsCodeRuleSpec(
                    company = "Acme",
                    codeKeyword = "验证码",
                    codeRegex = "(?<=验证码:)\\d{6}",
                    source = SmsCodeMatchedRuleSource.OFFICIAL,
                ),
            ),
        )

        assertEquals("246810", result.code)
        assertEquals(SmsCodeMatchedRuleSource.OFFICIAL, result.matchedRule?.source)
    }

    @Test
    fun `parse prefers strict company hit over relaxed fallback candidate`() = runBlocking {
        val result = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】验证码:246810",
            keywordsRegex = "验证码",
            rules = listOf(
                SmsCodeRuleSpec(
                    company = "Other",
                    codeKeyword = "验证码",
                    codeRegex = "\\d{4}",
                ),
                SmsCodeRuleSpec(
                    company = "Acme",
                    codeKeyword = "验证码",
                    codeRegex = "(?<=验证码:)\\d{6}",
                ),
            ),
        )

        assertEquals("246810", result.code)
        assertEquals(SmsCodeMatchedRuleSource.CUSTOM, result.matchedRule?.source)
        assertEquals(2, result.matchedRule?.ordinal)
    }

    @Test
    fun `parse applies packageNameHint only when source is present`() = runBlocking {
        val rule = SmsCodeRuleSpec(
            company = "Gmail",
            codeKeyword = "verification code",
            codeRegex = "(?<![A-Z0-9])(?=[A-Z0-9]{4,10}(?![A-Z0-9]))(?=[A-Z0-9]*\\d)[A-Z0-9]{4,10}",
            packageNameHint = "com.google.android.gm",
        )

        val noSourceResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "Your verification code is 731284",
            keywordsRegex = "验证码",
            rules = listOf(rule),
        )
        val matchedPackageResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "Your verification code is 731284",
            keywordsRegex = "验证码",
            rules = listOf(rule),
            source = SmsCodeParseSource(
                packageName = "com.google.android.gm",
                sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
            ),
        )
        val mismatchedPackageResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "Your verification code is 731284",
            keywordsRegex = "验证码",
            rules = listOf(rule),
            source = SmsCodeParseSource(
                packageName = "com.example.mail",
                sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
            ),
        )

        assertEquals("731284", noSourceResult.code)
        assertEquals("731284", matchedPackageResult.code)
        assertEquals("", mismatchedPackageResult.code)
    }

    @Test
    fun `parse applies senderRegex to notification sender source`() = runBlocking {
        val rule = SmsCodeRuleSpec(
            company = "Voice",
            codeKeyword = "verification code",
            codeRegex = "(?<![A-Z0-9])(?=[A-Z0-9]{4,10}(?![A-Z0-9]))(?=[A-Z0-9]*\\d)[A-Z0-9]{4,10}",
            senderRegex = "Google Voice|voice-noreply@example\\.com",
        )

        val matchedSenderResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "Your Google Voice verification code is GV42",
            keywordsRegex = "验证码",
            rules = listOf(rule),
            source = SmsCodeParseSource(
                sender = "Google Voice",
                title = "Google Voice",
                sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
            ),
        )
        val mismatchedSenderResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "Your Google Voice verification code is GV42",
            keywordsRegex = "验证码",
            rules = listOf(rule),
            source = SmsCodeParseSource(
                sender = "Marketing",
                title = "Promotion",
                sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
            ),
        )

        assertEquals("GV42", matchedSenderResult.code)
        assertEquals("", mismatchedSenderResult.code)
    }

    @Test
    fun `parse does not treat url amount date or order numbers as custom-rule codes`() = runBlocking {
        val broadRule = SmsCodeRuleSpec(
            company = "Acme",
            codeKeyword = "code",
            codeRegex = "\\d{4,8}",
        )

        listOf(
            "Open https://example.com/123456?code=reset to continue.",
            "Your amount is 50000.00 USD, code SAVE20 applies at checkout.",
            "Your appointment is 2026年07月06日. use code SAVE20 for parking.",
            "Order 246810 has shipped and tracking details are ready. Promo code SAVE20 expires today.",
        ).forEach { content ->
            val result = SmsCodeUtils.parseSmsCodeResultIfExists(
                content = content,
                keywordsRegex = "验证码",
                rules = listOf(broadRule),
            )

            assertEquals("", result.code, "content=$content")
        }
    }
}
