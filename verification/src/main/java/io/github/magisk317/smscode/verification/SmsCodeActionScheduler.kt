package io.github.magisk317.smscode.verification

import android.content.Context
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object SmsCodeActionScheduler {
    const val DEFAULT_TOAST_AFTER_AUTO_INPUT_BUFFER_MS = 250L

    fun resolveToastDelayAfterAutoInput(
        autoInputDelayMs: Long?,
        bufferMs: Long = DEFAULT_TOAST_AFTER_AUTO_INPUT_BUFFER_MS,
    ): Long {
        return autoInputDelayMs?.let { it + bufferMs } ?: 0L
    }

    fun <M, R> runAutoInputNowIfClaimed(
        pluginContext: Context,
        smsMsg: M,
        claimDelayMs: Long,
        claimAutoInputDispatch: (Context, M, Long) -> Boolean,
        actionFactory: () -> Callable<R>,
    ) {
        if (!claimAutoInputDispatch(pluginContext, smsMsg, claimDelayMs)) {
            return
        }
        actionFactory().call()
    }

    fun <M, R> scheduleAutoInputIfClaimed(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        smsMsg: M,
        delayMs: Long,
        claimAutoInputDispatch: (Context, M, Long) -> Boolean,
        actionFactory: () -> Callable<R>,
    ) {
        if (!claimAutoInputDispatch(pluginContext, smsMsg, delayMs)) {
            return
        }
        executor.schedule(actionFactory(), delayMs, TimeUnit.MILLISECONDS)
    }

    fun <R> scheduleNow(
        executor: ScheduledExecutorService,
        actionFactory: () -> Callable<R>,
    ) {
        executor.schedule(actionFactory(), 0L, TimeUnit.MILLISECONDS)
    }

    fun <R> scheduleEachDelay(
        executor: ScheduledExecutorService,
        delays: List<Long>,
        actionFactory: (Long) -> Callable<R>,
    ) {
        delays.forEach { delayMs ->
            executor.schedule(actionFactory(delayMs), delayMs, TimeUnit.MILLISECONDS)
        }
    }
}
