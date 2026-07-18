package io.github.magisk317.smscode.verification

object AutoInputFallbackPolicy {
    fun shouldRetryAccessibilityResult(
        success: Boolean,
        reason: String,
        windowPackage: String,
        modulePackage: String,
        homePackages: Set<String> = emptySet(),
    ): Boolean =
        io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputFallbackPolicy
            .shouldRetryAccessibilityResult(
                success = success,
                reason = reason,
                windowPackage = windowPackage,
                modulePackage = modulePackage,
                homePackages = homePackages,
            )

    fun shouldSkipFallbackAfterAccessibility(
        success: Boolean,
        reason: String,
        windowPackage: String,
        modulePackage: String,
        homePackages: Set<String> = emptySet(),
    ): Boolean =
        io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputFallbackPolicy
            .shouldSkipFallbackAfterAccessibility(
                success = success,
                reason = reason,
                windowPackage = windowPackage,
                modulePackage = modulePackage,
                homePackages = homePackages,
            )

    fun <T> runWithRetries(
        maxAttempts: Int,
        delayMs: Long,
        perform: (attemptIndex: Int) -> T,
        shouldRetry: (T) -> Boolean,
        sleeper: (Long) -> Unit,
    ): T =
        io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputFallbackPolicy
            .runWithRetries(
                maxAttempts = maxAttempts,
                delayMs = delayMs,
                perform = perform,
                shouldRetry = shouldRetry,
                sleeper = sleeper,
            )
}
