package io.github.magisk317.smscode.rule.model

import io.github.magisk317.smscode.verification.BlacklistMatchResult

data class SmsBlacklistConfig(
    val enabled: Boolean,
    val actionDelete: Boolean = false,
    val actionBlock: Boolean = false,
    val numbers: String = "",
    val prefixes: String = "",
    val content: String = "",
    val regex: String = "",
)

/**
 * Blacklist match outcome.
 *
 * Owned by the verification contract; the alias keeps the rule-side name usable so
 * there is a single shape rather than two structurally identical ones.
 */
typealias SmsBlacklistMatchResult = BlacklistMatchResult
