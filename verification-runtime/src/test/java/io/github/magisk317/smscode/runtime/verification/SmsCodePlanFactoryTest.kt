package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.VerificationPrefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsCodePlanFactoryTest {
    @Test
    fun loadSettings_preservesAutoInputTimingInMilliseconds() {
        val settings = SmsCodePlanFactory.loadSettings(FakeVerificationPrefs())

        assertEquals(125L, settings.autoInputDelayMs)
        assertEquals(80L, settings.inputIntervalMs)
    }

    private class FakeVerificationPrefs : VerificationPrefs {
        override fun showNotification() = true
        override fun autoCancelNotification() = false
        override fun notificationRetentionMs() = 0L
        override fun autoInputEnabled() = true
        override fun autoInputDelayMs() = 125L
        override fun inputIntervalMs() = 80L
        override fun copyToClipboardEnabled() = false
        override fun showToast() = false
        override fun recordSmsEnabled() = false
        override fun blockSmsEnabled() = false
        override fun markAsReadEnabled() = false
        override fun deleteSmsEnabled() = false
        override fun deduplicateSmsEnabled() = false
    }
}
