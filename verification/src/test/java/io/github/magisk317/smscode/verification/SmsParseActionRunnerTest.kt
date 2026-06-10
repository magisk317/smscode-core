package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsParseActionRunnerTest {
    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun runWithTimeout_returnsMessageWhenActionCompletes() {
        val smsMsg = TestSmsMessage(body = "code 123456", smsCode = "123456")
        val outcome = SmsParseActionRunner.runWithTimeout(
            executor = InlineExecutor(),
            runAction = { ParseBundle(duplicated = false, smsMsg = smsMsg) },
            duplicatedReader = ParseBundle::duplicated,
            messageReader = ParseBundle::smsMsg,
        )

        assertEquals(smsMsg, outcome?.smsMsg)
        assertEquals(false, outcome?.duplicated)
    }

    @Test
    fun runWithTimeout_returnsDuplicatedOutcomeWithoutReadingMessage() {
        val outcome = SmsParseActionRunner.runWithTimeout(
            executor = InlineExecutor(),
            runAction = { ParseBundle(duplicated = true, smsMsg = null) },
            duplicatedReader = ParseBundle::duplicated,
            messageReader = ParseBundle::smsMsg,
        )

        assertEquals(true, outcome?.duplicated)
        assertNull(outcome?.smsMsg)
    }

    @Test
    fun runWithTimeout_cancelsFutureWhenTimedOut() {
        XLog.setTestSink { _, _ -> }
        val future = TimeoutFuture<ParseBundle>()
        val outcome = SmsParseActionRunner.runWithTimeout(
            executor = FutureExecutor(future),
            timeoutMs = 10L,
            runAction = { ParseBundle(duplicated = false, smsMsg = TestSmsMessage()) },
            duplicatedReader = ParseBundle::duplicated,
            messageReader = ParseBundle::smsMsg,
        )

        assertNull(outcome)
        assertTrue(future.cancelled)
    }

    private data class ParseBundle(
        val duplicated: Boolean,
        val smsMsg: TestSmsMessage?,
    )

    private class InlineExecutor : NoopScheduledExecutorService() {
        override fun <T : Any?> submit(task: Callable<T>): Future<T> {
            return CompletedFuture(task.call())
        }
    }

    private class FutureExecutor<T>(
        private val future: Future<T>,
    ) : NoopScheduledExecutorService() {
        override fun <V : Any?> submit(task: Callable<V>): Future<V> {
            @Suppress("UNCHECKED_CAST")
            return future as Future<V>
        }
    }

    private class CompletedFuture<T>(
        private val value: T,
    ) : Future<T> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): T = value
        override fun get(timeout: Long, unit: TimeUnit): T = value
    }

    private class TimeoutFuture<T> : Future<T> {
        var cancelled = false

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelled = true
            return true
        }

        override fun isCancelled(): Boolean = cancelled
        override fun isDone(): Boolean = false
        override fun get(): T = throw ExecutionException(TimeoutException())
        override fun get(timeout: Long, unit: TimeUnit): T = throw TimeoutException()
    }

    private abstract class NoopScheduledExecutorService : ScheduledExecutorService {
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
        override fun execute(command: Runnable) = command.run()
        override fun <T : Any?> submit(task: Callable<T>): Future<T> = throw UnsupportedOperationException()
        override fun <T : Any?> submit(task: Runnable, result: T): Future<T> = throw UnsupportedOperationException()
        override fun submit(task: Runnable): Future<*> = throw UnsupportedOperationException()
        override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>): MutableList<Future<T>> =
            throw UnsupportedOperationException()
        override fun <T : Any?> invokeAll(
            tasks: MutableCollection<out Callable<T>>,
            timeout: Long,
            unit: TimeUnit,
        ): MutableList<Future<T>> = throw UnsupportedOperationException()
        override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>): T =
            throw UnsupportedOperationException()
        override fun <T : Any?> invokeAny(
            tasks: MutableCollection<out Callable<T>>,
            timeout: Long,
            unit: TimeUnit,
        ): T = throw UnsupportedOperationException()
        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
            throw UnsupportedOperationException()
        override fun <V : Any?> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> =
            throw UnsupportedOperationException()
        override fun scheduleAtFixedRate(
            command: Runnable,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit,
        ): ScheduledFuture<*> = throw UnsupportedOperationException()
        override fun scheduleWithFixedDelay(
            command: Runnable,
            initialDelay: Long,
            delay: Long,
            unit: TimeUnit,
        ): ScheduledFuture<*> = throw UnsupportedOperationException()
    }
}
