package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object SmsParseActionRunner {
    const val DEFAULT_TIMEOUT_MS = 2000L

    fun <R : Any, M : SmsMessage> runWithTimeout(
        executor: ScheduledExecutorService,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        actionName: String = "SmsParseAction",
        runAction: () -> R?,
        duplicatedReader: (R) -> Boolean,
        messageReader: (R) -> M?,
    ): CodeWorker.ParseOutcome<M>? {
        val future = executor.submit(Callable { runAction() })
        val parseResult = runCatching {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        }.recoverCatching { error ->
            when (error) {
                is TimeoutException,
                is InterruptedException,
                is ExecutionException,
                is CancellationException,
                is RejectedExecutionException,
                is IllegalArgumentException,
                is IllegalStateException,
                is SecurityException,
                is UnsupportedOperationException -> {
                    if (error is InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    XLog.w("%s timed out or failed: %s", actionName, error.message ?: error.javaClass.simpleName)
                    future.cancel(true)
                    null
                }
                else -> throw error
            }
        }.getOrThrow() ?: return null

        if (duplicatedReader(parseResult)) {
            return CodeWorker.ParseOutcome(duplicated = true)
        }
        return CodeWorker.ParseOutcome(
            smsMsg = messageReader(parseResult) ?: return null,
            duplicated = false,
        )
    }
}
