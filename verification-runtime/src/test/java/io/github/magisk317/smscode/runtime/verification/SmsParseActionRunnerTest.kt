package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.xposed.logging.XLog
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsParseActionRunnerTest {
    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
        SmsParseActionRunner.DaemonThreadTimeoutDispatcher.resetInFlightForTest()
    }

    @Test
    fun runWithTimeout_returnsMessageWhenActionCompletes() {
        val smsMsg = TestSmsMessage(body = "code 123456", smsCode = "123456")
        val outcome = SmsParseActionRunner.runWithTimeout(
            executor = NoopExecutor(),
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
            executor = NoopExecutor(),
            runAction = { ParseBundle(duplicated = true, smsMsg = null) },
            duplicatedReader = ParseBundle::duplicated,
            messageReader = ParseBundle::smsMsg,
        )

        assertEquals(true, outcome?.duplicated)
        assertNull(outcome?.smsMsg)
    }

    @Test
    fun runWithTimeout_returnsNullWhenActionExceedsTimeout() {
        XLog.setTestSink { _, _ -> }
        val started = CountDownLatch(1)
        val outcome = SmsParseActionRunner.runWithTimeout(
            executor = NoopExecutor(),
            timeoutMs = 50L,
            runAction = {
                started.countDown()
                // Simulate a hung uninterruptible cross-process call that far outlives the timeout.
                try {
                    Thread.sleep(5_000L)
                } catch (_: InterruptedException) {
                    // best-effort interrupt is fine; still return late so the runner must abandon us
                }
                ParseBundle(duplicated = false, smsMsg = TestSmsMessage())
            },
            duplicatedReader = ParseBundle::duplicated,
            messageReader = ParseBundle::smsMsg,
        )

        assertTrue(started.await(2, TimeUnit.SECONDS), "action should have started")
        assertNull(outcome)
    }

    @Test
    fun runWithTimeout_doesNotOccupySharedExecutorWhenActionHangs() {
        XLog.setTestSink { _, _ -> }
        // The shared executor must be usable for the *next* SMS even while a previous parse hangs.
        // The runner must never submit the timed action onto this executor.
        val sharedExecutor = Executors.newSingleThreadScheduledExecutor()
        val hangReleased = AtomicBoolean(false)
        try {
            val timedOut = SmsParseActionRunner.runWithTimeout(
                executor = sharedExecutor,
                timeoutMs = 50L,
                runAction = {
                    while (!hangReleased.get()) {
                        Thread.sleep(10L)
                    }
                    ParseBundle(duplicated = false, smsMsg = TestSmsMessage())
                },
                duplicatedReader = ParseBundle::duplicated,
                messageReader = ParseBundle::smsMsg,
            )
            assertNull(timedOut)

            // The shared executor's single thread must still be free to run the next task promptly.
            val ranNext = sharedExecutor.submit(Callable { true })
            assertTrue(ranNext.get(1, TimeUnit.SECONDS), "shared executor should run the next task")
        } finally {
            hangReleased.set(true)
            sharedExecutor.shutdownNow()
        }
    }

    @Test
    fun runWithTimeout_returnsNullWhenActionThrows() {
        XLog.setTestSink { _, _ -> }
        val outcome = SmsParseActionRunner.runWithTimeout(
            executor = NoopExecutor(),
            runAction = { throw IllegalStateException("boom") },
            duplicatedReader = ParseBundle::duplicated,
            messageReader = ParseBundle::smsMsg,
        )

        assertNull(outcome)
    }

    @Test
    fun daemonDispatcher_usesDaemonThreadThatCannotBlockJvmShutdown() {
        XLog.setTestSink { _, _ -> }
        val wasDaemon = AtomicBoolean(false)
        SmsParseActionRunner.DaemonThreadTimeoutDispatcher.run("probe", 1_000L) {
            wasDaemon.set(Thread.currentThread().isDaemon)
            Any()
        }
        assertTrue(wasDaemon.get(), "timed action must run on a daemon thread")
    }

    @Test
    fun daemonDispatcher_rejectsWhenInFlightWorkersAtCap() {
        XLog.setTestSink { _, _ -> }
        val hang = CountDownLatch(1)
        val started = CountDownLatch(SmsParseActionRunner.DaemonThreadTimeoutDispatcher.MAX_IN_FLIGHT_WORKERS)
        val executor = Executors.newCachedThreadPool()
        try {
            // Saturate the in-flight cap with workers that hang until released.
            val fills = (1..SmsParseActionRunner.DaemonThreadTimeoutDispatcher.MAX_IN_FLIGHT_WORKERS).map {
                executor.submit {
                    SmsParseActionRunner.DaemonThreadTimeoutDispatcher.run("fill", 60_000L) {
                        started.countDown()
                        hang.await()
                        Any()
                    }
                }
            }
            assertTrue(started.await(3, TimeUnit.SECONDS), "workers should start")
            assertEquals(
                SmsParseActionRunner.DaemonThreadTimeoutDispatcher.MAX_IN_FLIGHT_WORKERS,
                SmsParseActionRunner.DaemonThreadTimeoutDispatcher.inFlightForTest(),
            )

            val rejected = SmsParseActionRunner.DaemonThreadTimeoutDispatcher.run("overflow", 1_000L) {
                error("should not run")
            }
            assertNull(rejected)

            hang.countDown()
            fills.forEach { it.get(3, TimeUnit.SECONDS) }
        } finally {
            hang.countDown()
            executor.shutdownNow()
            SmsParseActionRunner.DaemonThreadTimeoutDispatcher.resetInFlightForTest()
        }
    }

    @Test
    fun customDispatcher_isUsedWhenProvided() {
        val invoked = AtomicBoolean(false)
        val smsMsg = TestSmsMessage(smsCode = "999")
        val dispatcher = object : SmsParseActionRunner.TimeoutDispatcher {
            override fun <R : Any> run(actionName: String, timeoutMs: Long, action: () -> R?): R? {
                invoked.set(true)
                return action()
            }
        }
        val outcome = SmsParseActionRunner.runWithTimeout(
            executor = NoopExecutor(),
            runAction = { ParseBundle(duplicated = false, smsMsg = smsMsg) },
            duplicatedReader = ParseBundle::duplicated,
            messageReader = ParseBundle::smsMsg,
            dispatcher = dispatcher,
        )

        assertTrue(invoked.get())
        assertEquals(smsMsg, outcome?.smsMsg)
        assertFalse(outcome?.duplicated ?: true)
    }

    private data class ParseBundle(
        val duplicated: Boolean,
        val smsMsg: TestSmsMessage?,
    )

    /** The runner no longer submits work to the executor, so a no-op stub is sufficient. */
    private class NoopExecutor : NoopScheduledExecutorService()

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
