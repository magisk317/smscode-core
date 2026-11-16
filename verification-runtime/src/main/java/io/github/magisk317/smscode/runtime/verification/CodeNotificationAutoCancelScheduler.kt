package io.github.magisk317.smscode.runtime.verification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

object CodeNotificationAutoCancelScheduler {
    enum class Outcome {
        DISABLED,
        INVALID_RETENTION,
        UID_MISMATCH,
        ALARM_UNAVAILABLE,
        SCHEDULED,
    }

    enum class AlarmMethod {
        SET,
        SET_AND_ALLOW_WHILE_IDLE,
        SET_EXACT,
        SET_EXACT_AND_ALLOW_WHILE_IDLE,
    }

    data class Request(
        val context: Context,
        val notificationId: Int,
        val autoCancelEnabled: Boolean,
        val retentionTimeMs: Long,
        val intentFactory: (Context, Int) -> Intent,
    )

    @SuppressLint("NewApi")
    data class Environment(
        val nowProvider: () -> Long = System::currentTimeMillis,
        val sdkInt: Int = Build.VERSION.SDK_INT,
        val processUidProvider: () -> Int = Process::myUid,
        val appUidProvider: (Context) -> Int = { context -> context.applicationInfo?.uid ?: -1 },
        val alarmManagerProvider: (Context) -> AlarmManager? = { context ->
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        },
        val canScheduleExactAlarmsProvider: (AlarmManager, Int) -> Boolean = { alarmManager, sdkInt ->
            sdkInt < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        },
        val pendingIntentFactory: (Context, Int, Intent, Int) -> PendingIntent = { context, requestCode, intent, flags ->
            PendingIntent.getBroadcast(context, requestCode, intent, flags)
        },
    )

    data class Plan(
        val outcome: Outcome,
        val notificationId: Int,
        val retentionTimeMs: Long,
        val processUid: Int,
        val appUid: Int,
        val triggerAtMs: Long? = null,
        val alarmMethod: AlarmMethod? = null,
    )

    fun createPlan(
        notificationId: Int,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
        nowMs: Long,
        processUid: Int,
        appUid: Int,
        sdkInt: Int,
        canScheduleExactAlarms: Boolean = true,
    ): Plan {
        if (!autoCancelEnabled) {
            return plan(Outcome.DISABLED, notificationId, retentionTimeMs, processUid, appUid)
        }
        if (retentionTimeMs <= 0L) {
            return plan(Outcome.INVALID_RETENTION, notificationId, retentionTimeMs, processUid, appUid)
        }
        if (processUid != appUid) {
            return plan(Outcome.UID_MISMATCH, notificationId, retentionTimeMs, processUid, appUid)
        }
        return plan(
            outcome = Outcome.SCHEDULED,
            notificationId = notificationId,
            retentionTimeMs = retentionTimeMs,
            processUid = processUid,
            appUid = appUid,
            triggerAtMs = nowMs + retentionTimeMs,
            alarmMethod = alarmMethodFor(sdkInt, canScheduleExactAlarms),
        )
    }

    fun schedule(
        request: Request,
        environment: Environment = Environment(),
    ): Plan {
        val nowMs = environment.nowProvider()
        val processUid = environment.processUidProvider()
        val appUid = environment.appUidProvider(request.context)
        val basePlan = createPlan(
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            nowMs = nowMs,
            processUid = processUid,
            appUid = appUid,
            sdkInt = environment.sdkInt,
        )
        if (basePlan.outcome != Outcome.SCHEDULED) {
            MagiskOtel.event(
                name = "notify.cancel",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "schedule",
                    "reason" to basePlan.outcome.name.lowercase(),
                ),
                statusOk = true,
            )
            return basePlan
        }

        val alarmManager = environment.alarmManagerProvider(request.context) ?: run {
            MagiskOtel.event(
                name = "notify.cancel",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "schedule",
                    "reason" to "no_alarm_manager",
                ),
                statusOk = true,
            )
            return basePlan.copy(
                outcome = Outcome.ALARM_UNAVAILABLE,
                triggerAtMs = null,
                alarmMethod = null,
            )
        }
        val plan = createPlan(
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            nowMs = nowMs,
            processUid = processUid,
            appUid = appUid,
            sdkInt = environment.sdkInt,
            canScheduleExactAlarms = environment.canScheduleExactAlarmsProvider(alarmManager, environment.sdkInt),
        )
        val intent = request.intentFactory(request.context, request.notificationId)
        val pendingIntent = environment.pendingIntentFactory(
            request.context,
            request.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )
        scheduleAlarm(alarmManager, plan.alarmMethod, plan.triggerAtMs, pendingIntent)
        MagiskOtel.event(
            name = "notify.cancel",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "schedule",
                "reason" to "scheduled_or_planned",
            ),
            statusOk = true,
        )
        return plan
    }

    fun schedule(
        context: Context,
        notificationId: Int,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
        intentFactory: (Context, Int) -> Intent,
        environment: Environment = Environment(),
    ): Plan {
        return schedule(
            Request(
                context = context,
                notificationId = notificationId,
                autoCancelEnabled = autoCancelEnabled,
                retentionTimeMs = retentionTimeMs,
                intentFactory = intentFactory,
            ),
            environment = environment,
        )
    }

    fun scheduleSafely(
        request: Request,
        source: String,
        environment: Environment = Environment(),
    ): Plan? {
        return runCatching {
            schedule(request, environment)
        }.onFailure { throwable ->
            XLog.w(
                message = "%s auto-cancel scheduling failed: id=%d retentionMs=%d err=%s",
                source,
                request.notificationId,
                request.retentionTimeMs,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }.getOrNull()
    }

    fun logSchedule(plan: Plan) {
        when (plan.outcome) {
            Outcome.DISABLED -> XLog.i("Auto cancel disabled")
            Outcome.INVALID_RETENTION ->
                XLog.i(message = "Auto cancel skipped: retentionTimeMs=%d", plan.retentionTimeMs)
            Outcome.UID_MISMATCH ->
                XLog.i(message = "Skip alarm auto cancel: uid=%d, appUid=%d", plan.processUid, plan.appUid)
            Outcome.ALARM_UNAVAILABLE -> XLog.d("Auto cancel alarm unavailable")
            Outcome.SCHEDULED ->
                XLog.i(message = "Schedule auto cancel alarm, id=%d, delayMs=%d", plan.notificationId, plan.retentionTimeMs)
        }
    }

    private fun alarmMethodFor(sdkInt: Int, canScheduleExactAlarms: Boolean): AlarmMethod {
        return when {
            sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms -> AlarmMethod.SET_AND_ALLOW_WHILE_IDLE
            sdkInt >= Build.VERSION_CODES.M -> AlarmMethod.SET_EXACT_AND_ALLOW_WHILE_IDLE
            sdkInt >= Build.VERSION_CODES.KITKAT -> AlarmMethod.SET_EXACT
            else -> AlarmMethod.SET
        }
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        alarmMethod: AlarmMethod?,
        triggerAtMs: Long?,
        pendingIntent: PendingIntent,
    ) {
        requireNotNull(triggerAtMs) { "triggerAtMs is required for scheduled auto cancel" }
        when (alarmMethod) {
            AlarmMethod.SET_EXACT_AND_ALLOW_WHILE_IDLE ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            AlarmMethod.SET_AND_ALLOW_WHILE_IDLE ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            AlarmMethod.SET_EXACT -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            AlarmMethod.SET -> alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            null -> error("alarmMethod is required for scheduled auto cancel")
        }
    }

    private fun plan(
        outcome: Outcome,
        notificationId: Int,
        retentionTimeMs: Long,
        processUid: Int,
        appUid: Int,
        triggerAtMs: Long? = null,
        alarmMethod: AlarmMethod? = null,
    ): Plan {
        return Plan(
            outcome = outcome,
            notificationId = notificationId,
            retentionTimeMs = retentionTimeMs,
            processUid = processUid,
            appUid = appUid,
            triggerAtMs = triggerAtMs,
            alarmMethod = alarmMethod,
        )
    }
}
