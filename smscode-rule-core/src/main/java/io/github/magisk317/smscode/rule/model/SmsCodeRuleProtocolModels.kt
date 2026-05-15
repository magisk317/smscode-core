package io.github.magisk317.smscode.rule.model

data class SmsCodeRuleProtocol(
    val company: String? = null,
    val codeKeyword: String,
    val codeRegex: String,
    val senderRegex: String? = null,
    val packageNameHint: String? = null,
    val priority: Int = 0,
)

object SmsCodeRuleProtocolMapper {
    fun fromProtocol(protocol: SmsCodeRuleProtocol): SmsCodeRuleSpec = SmsCodeRuleSpec(
        company = protocol.company,
        codeKeyword = protocol.codeKeyword,
        codeRegex = protocol.codeRegex,
        senderRegex = protocol.senderRegex,
        packageNameHint = protocol.packageNameHint,
        priority = protocol.priority,
    )

    fun toProtocol(spec: SmsCodeRuleSpec): SmsCodeRuleProtocol = SmsCodeRuleProtocol(
        company = spec.company,
        codeKeyword = spec.codeKeyword,
        codeRegex = spec.codeRegex,
        senderRegex = spec.senderRegex,
        packageNameHint = spec.packageNameHint,
        priority = spec.priority,
    )
}
