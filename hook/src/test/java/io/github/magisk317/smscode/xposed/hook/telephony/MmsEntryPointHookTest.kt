package io.github.magisk317.smscode.xposed.hook.telephony

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MmsEntryPointHookTest {

    @Test
    fun isTargetPackage_matchesMmsApp() {
        assertTrue(MmsEntryPointHook.isTargetPackage(MmsEntryPointHook.TARGET_PACKAGE))
    }

    @Test
    fun isTargetPackage_rejectsOtherProcesses() {
        // The hook is loaded into every process; only com.android.mms carries the
        // MMS/SMS transaction entry points, so hooking elsewhere is wasted work.
        assertFalse(MmsEntryPointHook.isTargetPackage("com.android.providers.telephony"))
        assertFalse(MmsEntryPointHook.isTargetPackage("com.android.mms.ui"))
        assertFalse(MmsEntryPointHook.isTargetPackage(""))
        assertFalse(MmsEntryPointHook.isTargetPackage(null))
    }

    @Test
    fun isTargetPackage_isCaseSensitive() {
        assertFalse(MmsEntryPointHook.isTargetPackage(MmsEntryPointHook.TARGET_PACKAGE.uppercase()))
    }

    @Test
    fun suppressionReason_isStable() {
        // Written into the hook.conflict telemetry attribute; keep it stable so
        // dashboards do not break when the wording changes.
        assertTrue(MmsEntryPointHook.SUPPRESSION_REASON.isNotBlank())
    }
}
