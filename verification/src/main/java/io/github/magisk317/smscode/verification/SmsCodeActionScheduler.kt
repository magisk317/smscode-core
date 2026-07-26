package io.github.magisk317.smscode.verification

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
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
            emitAutoInputSchedule(
                result = "skip",
                stage = "run_now",
                reason = "claim_rejected",
                delayMs = claimDelayMs,
            )
            return
        }
        emitAutoInputSchedule(
            result = "ok",
            stage = "run_now",
            reason = "claimed",
            delayMs = claimDelayMs,
        )
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
            emitAutoInputSchedule(
                result = "skip",
                stage = "schedule",
                reason = "claim_rejected",
                delayMs = delayMs,
            )
            return
        }
        emitAutoInputSchedule(
            result = "ok",
            stage = "schedule",
            reason = "claimed",
            delayMs = delayMs,
        )
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

    private fun emitAutoInputSchedule(
        result: String,
        stage: String,
        reason: String,
        delayMs: Long,
    ) {
        MagiskOtel.event(
            name = "sms.schedule",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to stage,
                "reason" to reason,
                "delay_ms" to delayMs.coerceAtLeast(0L).toString(),
                "source" to "auto_input",
            ),
            statusOk = true,
        )
    }
}
