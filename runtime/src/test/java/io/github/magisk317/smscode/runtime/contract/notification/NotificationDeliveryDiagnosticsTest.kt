package io.github.magisk317.smscode.runtime.contract.notification

import android.app.NotificationManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationDeliveryDiagnosticsTest {

    @Test
    fun canPost_isFalseWhenTheChannelIsMissing() {
        // Android 8+ drops notify() calls for unknown channels without throwing, so a missing
        // channel must never be reported as postable.
        assertFalse(diagnostics(channelImportance = null).canPost)
    }

    @Test
    fun canPost_isFalseWhenTheChannelIsSwitchedOff() {
        assertFalse(diagnostics(channelImportance = NotificationManager.IMPORTANCE_NONE).canPost)
    }

    @Test
    fun canPost_isFalseWhenNotificationsAreDisabled() {
        assertFalse(
            diagnostics(
                channelImportance = NotificationManager.IMPORTANCE_HIGH,
                notificationsEnabled = false,
            ).canPost,
        )
    }

    @Test
    fun canPost_isFalseWhenPostNotificationsIsNotGranted() {
        assertFalse(
            diagnostics(
                channelImportance = NotificationManager.IMPORTANCE_HIGH,
                postNotificationsGranted = false,
            ).canPost,
        )
    }

    @Test
    fun canPost_isTrueForAUsableChannel() {
        assertTrue(diagnostics(channelImportance = NotificationManager.IMPORTANCE_HIGH).canPost)
    }

    private fun diagnostics(
        channelImportance: Int?,
        notificationsEnabled: Boolean = true,
        postNotificationsGranted: Boolean = true,
    ) = NotificationDeliveryDiagnostics(
        notificationsEnabled = notificationsEnabled,
        postNotificationsGranted = postNotificationsGranted,
        channelImportance = channelImportance,
    )
}
