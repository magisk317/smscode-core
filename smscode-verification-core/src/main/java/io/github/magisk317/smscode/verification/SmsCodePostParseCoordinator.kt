package io.github.magisk317.smscode.verification

import android.content.Context
import android.os.Handler
import java.util.concurrent.ScheduledExecutorService

object SmsCodePostParseCoordinator {
    data class Settings(
        val showNotification: Boolean,
        val autoCancelNotification: Boolean,
        val notificationRetentionMs: Long,
        val autoInputEnabled: Boolean,
        val autoInputDelayMs: Long,
        val copyToClipboardEnabled: Boolean,
        val showToast: Boolean,
        val recordSmsEnabled: Boolean,
        val blockSmsEnabled: Boolean,
        val markAsReadEnabled: Boolean,
        val deleteSmsEnabled: Boolean,
        val deduplicateSmsEnabled: Boolean,
    )

    data class UiPlan(
        val copyToClipboardEnabled: Boolean,
        val showToast: Boolean,
    )

    data class NotificationPlan(
        val autoCancelDelayMs: Long?,
    )

    data class ParsedSmsPlan(
        val blockSms: Boolean,
        val deduplicateSmsEnabled: Boolean,
        val uiPlan: UiPlan,
        val autoInputDelayMs: Long?,
        val notificationPlan: NotificationPlan?,
        val shouldRecord: Boolean,
        val operateSmsDelays: List<Long>,
    )

    data class ObservedSmsPlan(
        val deduplicateSmsEnabled: Boolean,
        val autoInputEnabled: Boolean,
        val autoInputDelayMs: Long?,
        val shouldRecord: Boolean,
    )

    fun loadSettings(prefs: VerificationPrefs): Settings {
        return SmsCodePlanFactory.loadSettings(prefs)
    }

    fun resolveOperateSmsDelays(settings: Settings): List<Long> {
        return SmsCodePlanFactory.resolveOperateSmsDelays(settings)
    }

    fun createParsedSmsPlan(
        settings: Settings,
    ): ParsedSmsPlan {
        return SmsCodePlanFactory.createParsedSmsPlan(settings)
    }

    fun createObservedSmsPlan(settings: Settings): ObservedSmsPlan {
        return SmsCodePlanFactory.createObservedSmsPlan(settings)
    }

    fun <M : SmsMessage> dispatchParsedSmsActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        eventId: String,
        plan: ParsedSmsPlan,
        uiDispatcher: (Handler, Context, Context, M, UiPlan) -> Unit,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, M, Long, Boolean) -> Unit,
        notificationScheduler: (ScheduledExecutorService, Context, Context, M, NotificationPlan) -> Unit,
        recordScheduler: (ScheduledExecutorService, Context, Context, M, String, Boolean) -> Unit,
        operateSmsScheduler: (ScheduledExecutorService, Context, Context, M, List<Long>) -> Unit,
    ) {
        SmsCodeActionDispatcher.dispatchParsedSmsActions(
            uiHandler = uiHandler,
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
            uiDispatcher = uiDispatcher,
            autoInputScheduler = autoInputScheduler,
            notificationScheduler = notificationScheduler,
            recordScheduler = recordScheduler,
            operateSmsScheduler = operateSmsScheduler,
        )
    }

    fun <M : SmsMessage> dispatchObservedSmsActions(
        executor: ScheduledExecutorService?,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: M,
        eventId: String,
        plan: ObservedSmsPlan,
        autoInputRunner: (Context, Context, M, Boolean) -> Unit,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, M, Long, Boolean) -> Unit,
        recordRunner: (Context, Context, M, String, Boolean) -> Unit,
    ) {
        SmsCodeActionDispatcher.dispatchObservedSmsActions(
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
            autoInputRunner = autoInputRunner,
            autoInputScheduler = autoInputScheduler,
            recordRunner = recordRunner,
        )
    }
}
