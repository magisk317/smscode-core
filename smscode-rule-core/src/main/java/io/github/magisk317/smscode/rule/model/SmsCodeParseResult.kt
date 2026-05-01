package io.github.magisk317.smscode.rule.model

enum class SmsCodeMatchedRuleSource {
    BUILTIN,
    CUSTOM,
}

data class SmsCodeMatchedRule(
    val source: SmsCodeMatchedRuleSource,
    val ordinal: Int,
)

data class SmsCodeParseResult(
    val code: String,
    val matchedRule: SmsCodeMatchedRule? = null,
)
