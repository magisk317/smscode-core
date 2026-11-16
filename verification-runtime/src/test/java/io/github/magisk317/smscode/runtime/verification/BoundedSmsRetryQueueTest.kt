package io.github.magisk317.smscode.runtime.verification

import io.mockk.every
import io.mockk.mockk
import java.util.ArrayDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundedSmsRetryQueueTest {
    private class Scheduler {
        val executor = mockk<ScheduledExecutorService>()
        val tasks = ArrayDeque<Runnable>()
        val delays = mutableListOf<Long>()
        var reject = false

        init {
            every { executor.schedule(any<Runnable>(), any<Long>(), TimeUnit.MILLISECONDS) } answers {
                if (reject) throw RejectedExecutionException()
                tasks.addLast(firstArg())
                delays += secondArg<Long>()
                mockk<ScheduledFuture<*>>(relaxed = true)
            }
        }

        fun next() = tasks.removeFirst().run()
    }

    @Test
    fun transientFailureRetriesSameEventThenReleasesItsSlot() {
        val scheduler = Scheduler()
        val finished = mutableListOf<BoundedSmsRetryQueue.FinishReason>()
        val queue = BoundedSmsRetryQueue(scheduler.executor, onFinished = { _, reason, _ -> finished += reason })
        var attempts = 0
        assertTrue(queue.submit("event") { ++attempts == 2 })
        scheduler.next()
        assertEquals(1, queue.size)
        scheduler.next()
        assertEquals(2, attempts)
        assertEquals(listOf(0L, 1_500L), scheduler.delays)
        assertEquals(listOf(BoundedSmsRetryQueue.FinishReason.ACCEPTED), finished)
        assertEquals(0, queue.size)
    }

    @Test
    fun repeatedSubmissionAndCapacityCannotCreateUnboundedWork() {
        val scheduler = Scheduler()
        val queue = BoundedSmsRetryQueue(scheduler.executor, maxPending = 1)
        var duplicateOperationRan = false
        assertTrue(queue.submit("event") { true })
        assertTrue(queue.submit("event") { duplicateOperationRan = true; true })
        assertFalse(queue.submit("other") { true })
        assertEquals(1, scheduler.tasks.size)
        scheduler.next()
        assertFalse(duplicateOperationRan)
        assertTrue(queue.submit("other") { true })
    }

    @Test
    fun exhaustedFailureHasFiniteAttemptsAndDoesNotRetainPayload() {
        val scheduler = Scheduler()
        var outcome: BoundedSmsRetryQueue.FinishReason? = null
        var attemptCount = 0
        val queue = BoundedSmsRetryQueue(scheduler.executor, onFinished = { _, reason, attempts ->
            outcome = reason
            attemptCount = attempts
        })
        queue.submit("event") { false }
        repeat(3) { scheduler.next() }
        assertEquals(BoundedSmsRetryQueue.FinishReason.EXHAUSTED, outcome)
        assertEquals(3, attemptCount)
        assertEquals(0, queue.size)
        assertTrue(scheduler.tasks.isEmpty())
    }

    @Test
    fun exceptionCanRecoverWithoutReplayingCompletedStage() {
        val scheduler = Scheduler()
        val queue = BoundedSmsRetryQueue(scheduler.executor)
        var parseCalls = 0
        var forwardCalls = 0
        queue.submitStages("event",
            { parseCalls += 1; true },
            { forwardCalls += 1; if (forwardCalls == 1) error("temporary"); true },
        )
        scheduler.next()
        scheduler.next()
        assertEquals(1, parseCalls)
        assertEquals(2, forwardCalls)
        assertEquals(0, queue.size)
    }

    @Test
    fun retryingParsingDoesNotResendAForwardThatAlreadySucceeded() {
        val scheduler = Scheduler()
        val queue = BoundedSmsRetryQueue(scheduler.executor)
        var parseCalls = 0
        var forwardCalls = 0
        queue.submitStages("event",
            { ++parseCalls == 2 },
            { forwardCalls += 1; true },
        )
        scheduler.next()
        scheduler.next()
        assertEquals(2, parseCalls)
        assertEquals(1, forwardCalls)
        assertEquals(0, queue.size)
    }

    @Test
    fun initialSchedulerRejectionDoesNotAcceptResponsibility() {
        val scheduler = Scheduler().apply { reject = true }
        val queue = BoundedSmsRetryQueue(scheduler.executor)
        assertFalse(queue.submit("event") { true })
        assertEquals(0, queue.size)
    }

    @Test
    fun laterSchedulerRejectionReleasesCapacityAndReportsFailure() {
        val scheduler = Scheduler()
        val finished = mutableListOf<BoundedSmsRetryQueue.FinishReason>()
        val queue = BoundedSmsRetryQueue(scheduler.executor, onFinished = { _, reason, _ -> finished += reason })
        queue.submit("event") { false }
        scheduler.reject = true
        scheduler.next()
        assertEquals(listOf(BoundedSmsRetryQueue.FinishReason.REJECTED), finished)
        assertEquals(0, queue.size)
    }

    @Test
    fun closeCancelsPendingAndPreventsAnInflightFailureFromRetrying() {
        val scheduler = Scheduler()
        val queue = BoundedSmsRetryQueue(scheduler.executor)
        queue.submit("event") { queue.close(); false }
        scheduler.next()
        assertEquals(0, queue.size)
        assertTrue(scheduler.tasks.isEmpty())
        assertFalse(queue.submit("next") { true })
    }

    @Test
    fun expiredQueuedWorkNeverRunsEvenIfTheExecutorWasBusy() {
        val scheduler = Scheduler()
        var now = 0L
        var called = false
        var finished: BoundedSmsRetryQueue.FinishReason? = null
        val queue = BoundedSmsRetryQueue(
            scheduler.executor,
            clock = { now },
            onFinished = { _, reason, _ -> finished = reason },
        )
        queue.submit("event") { called = true; true }
        now = BoundedSmsRetryQueue.DEFAULT_MAX_AGE_MS
        scheduler.next()
        assertFalse(called)
        assertEquals(BoundedSmsRetryQueue.FinishReason.EXHAUSTED, finished)
        assertEquals(0, queue.size)
    }

    @Test
    fun aQueuedRunnableCannotExecuteAfterClose() {
        val scheduler = Scheduler()
        val queue = BoundedSmsRetryQueue(scheduler.executor)
        var called = false
        queue.submit("event") { called = true; true }
        queue.close()
        scheduler.next()
        assertFalse(called)
    }
}
