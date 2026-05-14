package io.github.magisk317.smscode.verification

import android.content.Context
import android.os.Handler
import java.util.concurrent.ScheduledExecutorService

object SmsCodeActionDispatcher {
    fun <M : SmsMessage> dispatchParsedSmsActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ParsedSmsPlan,
        uiDispatcher: (Handler, Context, Context, M, SmsCodePostParseCoordinator.UiPlan) -> Unit,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, M, Long, Boolean, Long?) -> Unit,
        notificationScheduler: (ScheduledExecutorService, Context, Context, M, SmsCodePostParseCoordinator.NotificationPlan) -> Unit,
        recordScheduler: (ScheduledExecutorService, Context, Context, M, String, Boolean) -> Unit,
        operateSmsScheduler: (ScheduledExecutorService, Context, Context, M, List<Long>) -> Unit,
    ) {
        uiDispatcher(
            uiHandler,
            pluginContext,
            phoneContext,
            smsMsg,
            plan.uiPlan,
        )

        plan.autoInputDelayMs?.let { delayMs ->
            val attemptId = System.currentTimeMillis()
            autoInputScheduler(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                delayMs,
                plan.deduplicateSmsEnabled,
                attemptId,
            )
        }

        plan.notificationPlan?.let { notificationPlan ->
            notificationScheduler(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                notificationPlan,
            )
        }

        if (plan.shouldRecord) {
            recordScheduler(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                eventId,
                plan.deduplicateSmsEnabled,
            )
        }

        operateSmsScheduler(
            executor,
            pluginContext,
            phoneContext,
            smsMsg,
            plan.operateSmsDelays,
        )
    }

    fun <M : SmsMessage> dispatchObservedSmsActions(
        executor: ScheduledExecutorService?,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
        autoInputRunner: (Context, Context, M, Boolean, Long?) -> Unit,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, M, Long, Boolean, Long?) -> Unit,
        recordRunner: (Context, Context, M, String, Boolean) -> Unit,
    ) {
        if (plan.autoInputEnabled) {
            val delayMs = plan.autoInputDelayMs ?: 0L
            if (delayMs > 0L && executor != null) {
                val attemptId = System.currentTimeMillis()
                autoInputScheduler(
                    executor,
                    pluginContext,
                    phoneContext,
                    smsMsg,
                    delayMs,
                    plan.deduplicateSmsEnabled,
                    attemptId,
                )
            } else {
                val attemptId = System.currentTimeMillis()
                autoInputRunner(
                    pluginContext,
                    phoneContext,
                    smsMsg,
                    plan.deduplicateSmsEnabled,
                    attemptId,
                )
            }
        }
        if (plan.shouldRecord) {
            recordRunner(
                pluginContext,
                phoneContext,
                smsMsg,
                eventId,
                plan.deduplicateSmsEnabled,
            )
        }
    }
}
