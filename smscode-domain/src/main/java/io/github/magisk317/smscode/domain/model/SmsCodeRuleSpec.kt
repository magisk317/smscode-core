package io.github.magisk317.smscode.domain.model

data class SmsCodeRuleSpec(
    val company: String?,
    val codeKeyword: String,
    val codeRegex: String,
)
