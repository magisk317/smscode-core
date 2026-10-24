package io.github.magisk317.smscode.verification

enum class SmsDuplicateCheckResult {
    DUPLICATE,
    NOT_DUPLICATE,
    UNAVAILABLE,
}

fun resolveSmsDuplicateCheck(token: String?, query: () -> Boolean?): SmsDuplicateCheckResult {
    if (token.isNullOrBlank()) return SmsDuplicateCheckResult.UNAVAILABLE
    return try {
        when (query()) {
            true -> SmsDuplicateCheckResult.DUPLICATE
            false -> SmsDuplicateCheckResult.NOT_DUPLICATE
            null -> SmsDuplicateCheckResult.UNAVAILABLE
        }
    } catch (_: Exception) {
        SmsDuplicateCheckResult.UNAVAILABLE
    }
}
