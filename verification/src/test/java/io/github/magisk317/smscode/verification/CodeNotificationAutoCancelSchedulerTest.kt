package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeNotificationAutoCancelSchedulerTest {
    @Test
    fun createPlan_returnsDisabledWhenAutoCancelOff() {
        val plan = createPlan(autoCancelEnabled = false)

        assertEquals(CodeNotificationAutoCancelScheduler.Outcome.DISABLED, plan.outcome)
        assertNull(plan.triggerAtMs)
        assertNull(plan.alarmMethod)
    }

    @Test
    fun createPlan_rejectsNonPositiveRetention() {
        val plan = createPlan(retentionTimeMs = 0L)

        assertEquals(CodeNotificationAutoCancelScheduler.Outcome.INVALID_RETENTION, plan.outcome)
        assertNull(plan.triggerAtMs)
        assertNull(plan.alarmMethod)
    }

    @Test
    fun createPlan_rejectsCrossProcessSchedule() {
        val plan = createPlan(processUid = 2000, appUid = 1000)

        assertEquals(CodeNotificationAutoCancelScheduler.Outcome.UID_MISMATCH, plan.outcome)
        assertEquals(2000, plan.processUid)
        assertEquals(1000, plan.appUid)
    }

    @Test
    fun createPlan_usesSetExactAndAllowWhileIdleOnMarshmallowAndAbove() {
        val plan = createPlan(sdkInt = 23)

        assertEquals(CodeNotificationAutoCancelScheduler.Outcome.SCHEDULED, plan.outcome)
        assertEquals(12_345L, plan.triggerAtMs)
        assertEquals(
            CodeNotificationAutoCancelScheduler.AlarmMethod.SET_EXACT_AND_ALLOW_WHILE_IDLE,
            plan.alarmMethod,
        )
    }

    @Test
    fun createPlan_usesSetAndAllowWhileIdleWhenExactAlarmUnavailableOnAndroidSAndAbove() {
        val plan = createPlan(sdkInt = 31, canScheduleExactAlarms = false)

        assertEquals(
            CodeNotificationAutoCancelScheduler.AlarmMethod.SET_AND_ALLOW_WHILE_IDLE,
            plan.alarmMethod,
        )
    }

    @Test
    fun createPlan_usesSetExactOnKitkatToLollipop() {
        val plan = createPlan(sdkInt = 19)

        assertEquals(CodeNotificationAutoCancelScheduler.AlarmMethod.SET_EXACT, plan.alarmMethod)
    }

    @Test
    fun createPlan_usesSetBeforeKitkat() {
        val plan = createPlan(sdkInt = 18)

        assertEquals(CodeNotificationAutoCancelScheduler.AlarmMethod.SET, plan.alarmMethod)
    }

    private fun createPlan(
        autoCancelEnabled: Boolean = true,
        retentionTimeMs: Long = 345L,
        processUid: Int = 1000,
        appUid: Int = 1000,
        sdkInt: Int = 35,
        canScheduleExactAlarms: Boolean = true,
    ): CodeNotificationAutoCancelScheduler.Plan {
        return CodeNotificationAutoCancelScheduler.createPlan(
            notificationId = 7,
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
            nowMs = 12_000L,
            processUid = processUid,
            appUid = appUid,
            sdkInt = sdkInt,
            canScheduleExactAlarms = canScheduleExactAlarms,
        )
    }
}
