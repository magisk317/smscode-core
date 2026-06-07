package io.github.magisk317.smscode.rule.testing

import io.github.magisk317.smscode.rule.model.SmsBlacklistConfig
import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRule
import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseResult
import io.github.magisk317.smscode.rule.model.SmsCodeRuleProtocol
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSetIndex
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSetIndexEntry
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSetSpec
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec

object RuleTestFixtures {

    fun ruleSpec(
        company: String? = "ACME",
        codeKeyword: String = "验证码",
        codeRegex: String = "\\d{4,6}",
        senderRegex: String? = null,
        packageNameHint: String? = null,
        priority: Int = 0,
    ) = SmsCodeRuleSpec(
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        senderRegex = senderRegex,
        packageNameHint = packageNameHint,
        priority = priority,
    )

    fun ruleProtocol(
        company: String? = "ACME",
        codeKeyword: String = "验证码",
        codeRegex: String = "\\d{4,6}",
        senderRegex: String? = null,
        packageNameHint: String? = null,
        priority: Int = 0,
    ) = SmsCodeRuleProtocol(
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        senderRegex = senderRegex,
        packageNameHint = packageNameHint,
        priority = priority,
    )

    fun parseResult(
        code: String = "123456",
        matchedRule: SmsCodeMatchedRule? = matchedRule(),
    ) = SmsCodeParseResult(
        code = code,
        matchedRule = matchedRule,
    )

    fun matchedRule(
        source: SmsCodeMatchedRuleSource = SmsCodeMatchedRuleSource.BUILTIN,
        ordinal: Int = 0,
    ) = SmsCodeMatchedRule(
        source = source,
        ordinal = ordinal,
    )

    fun blacklistConfig(
        enabled: Boolean = true,
        numbers: String = "",
        prefixes: String = "",
        content: String = "",
        regex: String = "",
    ) = SmsBlacklistConfig(
        enabled = enabled,
        numbers = numbers,
        prefixes = prefixes,
        content = content,
        regex = regex,
    )

    fun ruleSetSpec(
        id: String = "test-rules",
        version: String = "1",
        rules: List<SmsCodeRuleSpec> = listOf(ruleSpec()),
        source: String? = null,
    ) = SmsCodeRuleSetSpec(
        id = id,
        version = version,
        rules = rules,
        source = source,
    )

    fun ruleSetIndexEntry(
        id: String = "test-rules",
        version: String = "1",
        title: String = "Test Rules",
        source: String = "https://example.com/rules.json",
    ) = SmsCodeRuleSetIndexEntry(
        id = id,
        version = version,
        title = title,
        source = source,
    )

    fun ruleSetIndex(
        entries: List<SmsCodeRuleSetIndexEntry> = listOf(ruleSetIndexEntry()),
    ) = SmsCodeRuleSetIndex(entries = entries)
}
