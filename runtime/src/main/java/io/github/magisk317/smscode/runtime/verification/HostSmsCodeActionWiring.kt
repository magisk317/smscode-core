package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import android.os.Handler
import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.smscode.runtime.verification.XLog
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService

/**
 * Host-parameterized scheduling skeletons behind [SmsCodeActionDispatcher].
 *
 * Both hosts previously duplicated this whole orchestration (entitlement gates,
 * dispatch claims, executor scheduling, action construction order) and drifted in
 * small ways (toast skip style, notification dedup source, inline-vs-scheduled
 * notification). The hosts now only supply their action factories, gate claimers,
 * and the entitlement reader; the shared semantics live here once.
 */
class HostSmsCodeActionWiring<M : SmsMessage>(
    val mobileAutomationAllowed: (Context) -> Boolean,
    val claimAutoInputDispatch: (Context, M, Long) -> Boolean,
    val claimNotificationDispatch: (Context, M) -> Boolean,
    val newCopyAction: (Context, Context, M, Boolean) -> Runnable,
    val newToastAction: (Context, Context, M, Boolean) -> Runnable?,
    val newAutoInputAction: (Context, Context, M, Boolean, Long, Long?) -> Callable<*>,
    val newRecordAction: (Context, Context, M, String, Boolean) -> () -> Unit,
    val newNotifyAction: (Context, Context, M, Boolean, Long) -> () -> Unit,
    val newOperateSmsAction: (Context, Context, M) -> () -> Unit,
) {
    fun dispatchUiActions(
        uiHandler: Handler,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        uiPlan: SmsCodePostParseCoordinator.UiPlan,
    ) {
        if (!mobileAutomationAllowed(pluginContext)) {
            XLog.i("Mobile entitlement gate skipped UI actions")
            return
        }
        uiHandler.post(newCopyAction(pluginContext, phoneContext, smsMsg, uiPlan.copyToClipboardEnabled))
        newToastAction(pluginContext, phoneContext, smsMsg, uiPlan.showToast)?.let { uiHandler.post(it) }
    }

    fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        SmsCodeActionScheduler.runAutoInputNowIfClaimed(
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            claimDelayMs = 0L,
            claimAutoInputDispatch = { context, msg, delayMs ->
                claimAutoInputDispatch(context, msg, delayMs)
            },
        ) {
            Callable { newAutoInputAction(pluginContext, phoneContext, smsMsg, deduplicateEnabled, 0L, attemptId).call() }
        }
    }

    fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        delayMs: Long,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        SmsCodeActionScheduler.scheduleAutoInputIfClaimed(
            executor = executor,
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            delayMs = delayMs,
            claimAutoInputDispatch = { context, msg, claimDelayMs ->
                claimAutoInputDispatch(context, msg, claimDelayMs)
            },
        ) {
            Callable { newAutoInputAction(pluginContext, phoneContext, smsMsg, deduplicateEnabled, delayMs, attemptId).call() }
        }
    }

    fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        newRecordAction(pluginContext, phoneContext, smsMsg, eventId, deduplicateEnabled)()
    }

    fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        SmsCodeActionScheduler.scheduleNow(executor) {
            Callable { newRecordAction(pluginContext, phoneContext, smsMsg, eventId, deduplicateEnabled)() }
        }
    }

    fun scheduleNotification(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        plan: SmsCodePostParseCoordinator.NotificationPlan,
        deduplicateEnabled: Boolean,
    ) {
        if (!mobileAutomationAllowed(pluginContext)) {
            XLog.i("Mobile entitlement gate skipped code notification")
            return
        }
        if (deduplicateEnabled && !claimNotificationDispatch(pluginContext, smsMsg)) return
        XLog.i("scheduleNotification() running inline: smsCode=%s", smsMsg.smsCode)
        runCatching {
            newNotifyAction(
                pluginContext,
                phoneContext,
                smsMsg,
                plan.autoCancelDelayMs != null,
                plan.autoCancelDelayMs ?: 0L,
            )()
        }.onFailure { error ->
            XLog.e("scheduleNotification() failed: %s", error)
        }
    }

    fun scheduleOperateSmsActions(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        delays: List<Long>,
    ) {
        SmsCodeActionScheduler.scheduleEachDelay(
            executor = executor,
            delays = delays,
        ) {
            Callable {
                if (mobileAutomationAllowed(pluginContext)) {
                    newOperateSmsAction(pluginContext, phoneContext, smsMsg)()
                } else {
                    XLog.i("Mobile entitlement gate skipped SMS operations")
                }
            }
        }
    }
}
