package io.github.magisk317.smscode.domain.utils

import io.github.magisk317.smscode.domain.model.SmsBlacklistConfig
import io.github.magisk317.smscode.domain.model.SmsBlacklistMatchResult

object SmsBlacklistUtils {

    @JvmStatic
    fun match(config: SmsBlacklistConfig, sender: String?, body: String?): SmsBlacklistMatchResult {
        if (!config.enabled) {
            return SmsBlacklistMatchResult(matched = false)
        }
        val senderValue = sender.orEmpty()
        val bodyValue = body.orEmpty()
        if (senderValue.isBlank() && bodyValue.isBlank()) {
            return SmsBlacklistMatchResult(matched = false)
        }

        fun matched(type: String, pattern: String): SmsBlacklistMatchResult =
            SmsBlacklistMatchResult(
                matched = true,
                matchType = type,
                pattern = pattern,
                actionDelete = config.actionDelete,
                actionBlock = config.actionBlock,
            )

        val senderDigits = normalizeDigits(senderValue)

        val numberRules = splitRules(config.numbers)
        numberRules.firstOrNull { rule ->
            val ruleDigits = normalizeDigits(rule)
            ruleDigits.isNotBlank() && senderDigits == ruleDigits
        }?.let { return matched("number", it) }

        val prefixRules = splitRules(config.prefixes)
        prefixRules.firstOrNull { rule ->
            val ruleDigits = normalizeDigits(rule)
            ruleDigits.isNotBlank() && senderDigits.startsWith(ruleDigits)
        }?.let { return matched("prefix", it) }

        val contentRules = splitRules(config.content)
        contentRules.firstOrNull { rule ->
            bodyValue.contains(rule, ignoreCase = true)
        }?.let { return matched("content", it) }

        val regexRules = splitRules(config.regex)
        val target = buildString {
            append(senderValue)
            append('\n')
            append(bodyValue)
        }
        regexRules.firstOrNull { rule ->
            runCatching { Regex(rule, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(target) }.getOrDefault(false)
        }?.let { return matched("regex", it) }

        return SmsBlacklistMatchResult(matched = false)
    }

    private fun splitRules(raw: String): List<String> =
        raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun normalizeDigits(input: String): String = input.filter { it.isDigit() }
}
