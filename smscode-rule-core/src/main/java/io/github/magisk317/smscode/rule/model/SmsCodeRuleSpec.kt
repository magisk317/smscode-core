package io.github.magisk317.smscode.rule.model

data class SmsCodeRuleSpec(
    val company: String?,
    val codeKeyword: String,
    val codeRegex: String,
    val senderRegex: String? = null,
    val packageNameHint: String? = null,
    val priority: Int = 0,
)
