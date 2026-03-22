package io.github.magisk317.smscode.domain.model

data class SmsBlacklistConfig(
    val enabled: Boolean,
    val actionDelete: Boolean = false,
    val actionBlock: Boolean = false,
    val numbers: String = "",
    val prefixes: String = "",
    val content: String = "",
    val regex: String = "",
)

data class SmsBlacklistMatchResult(
    val matched: Boolean,
    val matchType: String? = null,
    val pattern: String? = null,
    val actionDelete: Boolean = false,
    val actionBlock: Boolean = false,
)
