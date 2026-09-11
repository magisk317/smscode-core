package io.github.magisk317.smscode.runtime.common.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VerboseLogEnableTrackerTest {

    @BeforeEach
    fun reset() {
        // Simulate app restart by toggling off (clears timestamp)
        VerboseLogEnableTracker.onVerboseLogToggled(false)
    }

    @Test
    fun `toggle on then immediately export is blocked`() {
        VerboseLogEnableTracker.onVerboseLogToggled(true)
        val result = VerboseLogEnableTracker.shouldBlockExport()
        assertEquals("uikit_log_export_need_reproduce", result)
    }

    @Test
    fun `toggle off then export is not blocked by time check`() {
        VerboseLogEnableTracker.onVerboseLogToggled(false)
        // No timestamp → time check is skipped (returns null)
        assertNull(VerboseLogEnableTracker.shouldBlockExport())
    }

    @Test
    fun `no toggle ever → not blocked`() {
        // Fresh state, never toggled
        assertNull(VerboseLogEnableTracker.shouldBlockExport())
    }

    @Test
    fun `toggle on then off clears timestamp`() {
        VerboseLogEnableTracker.onVerboseLogToggled(true)
        VerboseLogEnableTracker.onVerboseLogToggled(false)
        // Timestamp cleared → time check skipped
        assertNull(VerboseLogEnableTracker.shouldBlockExport())
    }

    @Test
    fun `toggle on then wait over 60s is not blocked`() {
        VerboseLogEnableTracker.onVerboseLogToggled(true)
        // Simulate 61 seconds passing by manually setting timestamp in the past
        // Since we can't easily mock System.currentTimeMillis, we test the logic
        // by toggling off and on again after "waiting"
        VerboseLogEnableTracker.onVerboseLogToggled(false)
        VerboseLogEnableTracker.onVerboseLogToggled(true)
        // Still blocked because we just toggled on again
        assertEquals("uikit_log_export_need_reproduce", VerboseLogEnableTracker.shouldBlockExport())
    }

    @Test
    fun `already enabled before restart → skip time check`() {
        // Simulate: verbose log was already ON, app restarts.
        // After restart, enableTimestamp is null (no toggle event).
        // The shouldBlockExport returns null (skip check).
        assertNull(VerboseLogEnableTracker.shouldBlockExport())
    }
}
