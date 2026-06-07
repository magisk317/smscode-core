package io.github.magisk317.smscode.verification

data class BlacklistMatchResult(
    val matched: Boolean,
    val matchType: String? = null,
    val pattern: String? = null,
    val actionDelete: Boolean = false,
    val actionBlock: Boolean = false,
)
