package io.github.magisk317.smscode.runtime.verification

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps a bounded number of intercepted SMS operations until accepted or retries are exhausted.
 *
 * The caller supplies its existing hook executor and must [close] before hot reload. Submission
 * means this process has accepted responsibility for trying the operation, not durable storage or
 * confirmation that an Android receiver delivered it. A false submission must not suppress the
 * original SMS. The operation must itself have a bounded wait and return true only once accepted.
 */
@Suppress("TooGenericExceptionCaught")
class BoundedSmsRetryQueue(
    private val scheduler: ScheduledExecutorService,
    private val maxPending: Int = DEFAULT_MAX_PENDING,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val onFinished: (String, FinishReason, Int) -> Unit = { _, _, _ -> },
) {
    enum class FinishReason { ACCEPTED, EXHAUSTED, CLOSED, REJECTED }

    private class Entry(val key: String, val enqueuedAt: Long, val operation: () -> Boolean) {
        var attempts = 0
        var future: ScheduledFuture<*>? = null
    }

    private val lock = Any()
    private val pending = LinkedHashMap<String, Entry>()
    private var closed = false

    init {
        require(maxPending > 0)
        require(maxAttempts > 0)
        require(retryDelayMs >= 0)
        require(maxAgeMs > 0)
    }

    /** Duplicate submissions share the existing attempt; capacity or scheduler failure rejects. */
    fun submit(key: String, operation: () -> Boolean): Boolean = synchronized(lock) {
        if (closed) return false
        if (pending.containsKey(key)) return true
        if (pending.size >= maxPending) return false
        val entry = Entry(key, clock(), operation)
        pending[key] = entry
        schedule(entry, 0L)
    }

    /** Successful independent stages are never rerun when a later stage needs recovery. */
    fun submitStages(key: String, vararg operations: () -> Boolean): Boolean {
        require(operations.isNotEmpty())
        val accepted = BooleanArray(operations.size)
        return submit(key) {
            operations.forEachIndexed { index, operation ->
                if (!accepted[index]) accepted[index] = operation()
            }
            accepted.all { it }
        }
    }

    /** Cancels queued work and prevents in-flight failures from scheduling another attempt. */
    fun close() {
        val cancelled = synchronized(lock) {
            if (closed) return
            closed = true
            pending.values.toList().also { pending.clear() }
        }
        cancelled.forEach { entry ->
            entry.future?.cancel(false)
            report(entry, FinishReason.CLOSED)
        }
    }

    val size: Int
        get() = synchronized(lock) { pending.size }

    private fun schedule(entry: Entry, delayMs: Long): Boolean {
        return try {
            entry.future = scheduler.schedule({ run(entry) }, delayMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: RejectedExecutionException) {
            if (pending[entry.key] === entry) pending.remove(entry.key)
            report(entry, FinishReason.REJECTED)
            false
        }
    }

    private fun run(entry: Entry) {
        synchronized(lock) {
            if (closed || pending[entry.key] !== entry) return
            if (clock() - entry.enqueuedAt >= maxAgeMs) {
                pending.remove(entry.key)
                report(entry, FinishReason.EXHAUSTED)
                return
            }
            entry.attempts += 1
        }
        val accepted = try {
            entry.operation()
        } catch (_: Exception) {
            // Operation-specific diagnostics belong to the host; never log an SMS or IPC token.
            false
        }
        synchronized(lock) {
            if (closed || pending[entry.key] !== entry) return
            when {
                accepted -> {
                    pending.remove(entry.key)
                    report(entry, FinishReason.ACCEPTED)
                }
                entry.attempts >= maxAttempts || clock() - entry.enqueuedAt >= maxAgeMs -> {
                    pending.remove(entry.key)
                    report(entry, FinishReason.EXHAUSTED)
                }
                else -> schedule(entry, retryDelayMs)
            }
        }
    }

    private fun report(entry: Entry, reason: FinishReason) {
        try {
            onFinished(entry.key, reason, entry.attempts)
        } catch (_: Exception) {
            // Diagnostics must not affect telephony delivery or strand pending work.
        }
    }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        const val DEFAULT_MAX_PENDING = 20
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_RETRY_DELAY_MS = 1_500L
        const val DEFAULT_MAX_AGE_MS = 30_000L
    }
}
