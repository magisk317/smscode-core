package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.xposed.runtime.contract.autoinput.AutoInputFallbackPolicy

/**
 * Shared retry orchestration for accessibility auto-input execution.
 *
 * Both products' node-matching algorithm lives in [AutoInputAccessibilityNodeHelper] and
 * the retry policy in [AutoInputFallbackPolicy]; this orchestrator is the single place
 * that wires the policy's window-settle retries around a host-supplied [perform] lambda.
 */
class AutoInputExecutionOrchestrator(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun <T> execute(
        perform: (attemptIndex: Int) -> T,
        shouldRetry: (T) -> Boolean,
    ): T = AutoInputFallbackPolicy.runWithRetries(
        maxAttempts = maxAttempts,
        delayMs = retryDelayMs,
        perform = perform,
        shouldRetry = shouldRetry,
        sleeper = sleeper,
    )

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 6
        const val DEFAULT_RETRY_DELAY_MS = 300L
    }
}
