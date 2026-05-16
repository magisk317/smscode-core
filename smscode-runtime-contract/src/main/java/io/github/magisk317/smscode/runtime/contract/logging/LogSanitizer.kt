package io.github.magisk317.smscode.runtime.contract.logging

import java.util.Locale

fun interface LogSanitizer {
    fun sanitize(message: String): String
}

object NoopLogSanitizer : LogSanitizer {
    override fun sanitize(message: String): String = message
}

object DefaultLogSanitizer : LogSanitizer {
    private const val SANITIZED_LOG_MAX_LENGTH = 800

    private val messageFieldNames = listOf(
        "content",
        "msg",
        "message",
        "text",
        "body",
        "title",
        "org_content",
    )
    private val senderFieldNames = listOf("from", "sender", "phone", "mobile", "number")
    private val codeFieldNames = listOf("code", "smsCode", "otp", "verifyCode")

    override fun sanitize(message: String): String {
        var sanitized = maskSecrets(message)
        sanitized = summarizeFields(sanitized, messageFieldNames, ::summarizePayload)
        sanitized = summarizeFields(sanitized, senderFieldNames, ::summarizeSender)
        sanitized = summarizeFields(sanitized, codeFieldNames, ::summarizeCode)
        sanitized = maskLikelyPhoneNumbers(sanitized)
        return truncate(sanitized, SANITIZED_LOG_MAX_LENGTH)
    }

    private fun summarizeFields(
        message: String,
        fieldNames: List<String>,
        summarizer: (String) -> String,
    ): String {
        var text = message
        fieldNames.forEach { field ->
            val queryRegex = Regex("(?i)(\\b$field=)([^&\\s]+)")
            text = queryRegex.replace(text) { match ->
                match.groupValues[1] + summarizer(match.groupValues[2])
            }
            val jsonRegex = Regex("(?i)(\"$field\"\\s*:\\s*\")([^\"]*)(\")")
            text = jsonRegex.replace(text) { match ->
                match.groupValues[1] + summarizer(match.groupValues[2]) + match.groupValues[3]
            }
        }
        return text
    }

    private fun maskSecrets(raw: String): String {
        var text = raw
        val urlUserInfo = Regex("(https?://)([^/@\\s:]+):([^@\\s]+)@")
        text = urlUserInfo.replace(text) { match ->
            "${match.groupValues[1]}***:***@"
        }
        val keyValuePattern = Regex(
            "(?i)(access[_-]?token|token|secret|sign|authorization|password|passwd|pwd|proxy-authorization)(\\s*[:=]\\s*)([^&\\s,\\\"]+)",
        )
        text = keyValuePattern.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}***"
        }
        val bearerPattern = Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-+/=]+")
        text = bearerPattern.replace(text) { match ->
            "${match.groupValues[1]}***"
        }
        val basicPattern = Regex("(?i)(basic\\s+)[A-Za-z0-9+/=]+")
        text = basicPattern.replace(text) { match ->
            "${match.groupValues[1]}***"
        }
        return text
    }

    private fun maskLikelyPhoneNumbers(message: String): String {
        return Regex("""(?<!\d)(1[3-9]\d{9})(?!\d)""").replace(message) { match ->
            maskKeepEdges(match.value, 3, 2)
        }
    }

    private fun summarizeSender(value: String): String {
        if (value.isBlank()) return "sender[len=0 hash=none]"
        return "sender[len=${value.length} tail=${value.takeLast(minOf(2, value.length))} hash=${shortHash(value)}]"
    }

    private fun summarizeCode(value: String): String {
        if (value.isBlank()) return "code[len=0 hash=none]"
        return "code[len=${value.length} masked=${maskKeepEdges(value, 2, 1)} hash=${shortHash(value)}]"
    }

    private fun summarizePayload(value: String): String {
        if (value.isBlank()) return "payload[len=0 hash=none]"
        return "payload[len=${value.length} hash=${shortHash(value)}]"
    }

    private fun maskKeepEdges(value: String, prefix: Int, suffix: Int): String {
        if (value.isBlank()) return "<empty>"
        val safePrefix = prefix.coerceAtLeast(0)
        val safeSuffix = suffix.coerceAtLeast(0)
        if (value.length <= safePrefix + safeSuffix) {
            return "*".repeat(value.length.coerceAtLeast(1))
        }
        return buildString(value.length) {
            append(value.take(safePrefix))
            append("*".repeat((value.length - safePrefix - safeSuffix).coerceAtLeast(1)))
            append(value.takeLast(safeSuffix))
        }
    }

    private fun shortHash(value: String): String {
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode()).lowercase(Locale.ROOT)
    }

    private fun truncate(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.take(max) + "...(len=${value.length})"
    }
}
