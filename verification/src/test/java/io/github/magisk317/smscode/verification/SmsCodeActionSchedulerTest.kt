package io.github.magisk317.smscode.verification

import android.content.Context
import io.mockk.mockk
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsCodeActionSchedulerTest {
    @Test
    fun resolveToastDelayAfterAutoInput_addsBufferOnlyWhenAutoInputIsDelayed() {
        assertEquals(1_750L, SmsCodeActionScheduler.resolveToastDelayAfterAutoInput(1_500L))
        assertEquals(0L, SmsCodeActionScheduler.resolveToastDelayAfterAutoInput(null))
    }

    @Test
    fun runAutoInputNowIfClaimed_runsOnlyWhenClaimSucceeds() {
        val pluginContext = mockk<Context>(relaxed = true)
        var ran = false

        SmsCodeActionScheduler.runAutoInputNowIfClaimed(
            pluginContext = pluginContext,
            smsMsg = "message",
            claimDelayMs = 0L,
            claimAutoInputDispatch = { _, _, _ -> true },
            actionFactory = {
                Callable {
                    ran = true
                    null
                }
            },
        )

        assertTrue(ran)

        ran = false
        SmsCodeActionScheduler.runAutoInputNowIfClaimed(
            pluginContext = pluginContext,
            smsMsg = "message",
            claimDelayMs = 0L,
            claimAutoInputDispatch = { _, _, _ -> false },
            actionFactory = {
                Callable {
                    ran = true
                    null
                }
            },
        )

        assertFalse(ran)
    }

    @Test
    fun scheduleAutoInputIfClaimed_schedulesOnlyWhenClaimSucceeds() {
        val pluginContext = mockk<Context>(relaxed = true)
        val executor = RecordingExecutor()

        SmsCodeActionScheduler.scheduleAutoInputIfClaimed(
            executor = executor,
            pluginContext = pluginContext,
            smsMsg = "message",
            delayMs = 300L,
            claimAutoInputDispatch = { _, _, _ -> true },
            actionFactory = { Callable { null } },
        )
        SmsCodeActionScheduler.scheduleAutoInputIfClaimed(
            executor = executor,
            pluginContext = pluginContext,
            smsMsg = "message",
            delayMs = 500L,
            claimAutoInputDispatch = { _, _, _ -> false },
            actionFactory = { Callable { null } },
        )

        assertEquals(listOf(300L), executor.scheduledDelaysMs)
    }

    @Test
    fun scheduleEachDelay_schedulesEveryDelay() {
        val executor = RecordingExecutor()

        SmsCodeActionScheduler.scheduleEachDelay(
            executor = executor,
            delays = listOf(300L, 1_000L),
            actionFactory = { Callable { null } },
        )

        assertEquals(listOf(300L, 1_000L), executor.scheduledDelaysMs)
    }

    private class RecordingExecutor : ScheduledThreadPoolExecutor(1) {
        val scheduledDelaysMs = mutableListOf<Long>()

        override fun <V : Any?> schedule(
            callable: Callable<V>,
            delay: Long,
            unit: TimeUnit,
        ): ScheduledFuture<V> {
            scheduledDelaysMs += unit.toMillis(delay)
            return mockk(relaxed = true)
        }
    }
}
