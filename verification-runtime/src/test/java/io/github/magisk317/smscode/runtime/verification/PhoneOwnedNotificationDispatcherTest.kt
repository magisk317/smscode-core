package io.github.magisk317.smscode.runtime.verification

import android.app.NotificationManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneOwnedNotificationDispatcherTest {

    @Test
    fun decideChannelAction_createsTheChannelWhenItIsMissing() {
        val action = PhoneOwnedNotificationDispatcher.decideChannelAction(
            exists = false,
            importance = null,
        )

        assertEquals(PhoneOwnedNotificationDispatcher.ChannelAction.CREATE_PRIMARY, action)
    }

    @Test
    fun decideChannelAction_reusesAUsableHighImportanceChannel() {
        val action = PhoneOwnedNotificationDispatcher.decideChannelAction(
            exists = true,
            importance = NotificationManager.IMPORTANCE_HIGH,
        )

        assertEquals(PhoneOwnedNotificationDispatcher.ChannelAction.USE_PRIMARY, action)
    }

    /**
     * Verification codes require heads-up / popup notification visibility.
     * When a channel is lowered below IMPORTANCE_HIGH (e.g. DEFAULT, LOW, MIN, NONE),
     * it must rotate to fallback so that high priority presentation is preserved.
     */
    @Test
    fun decideChannelAction_rotatesWhenTheChannelIsBelowHighImportance() {
        val actionNone = PhoneOwnedNotificationDispatcher.decideChannelAction(
            exists = true,
            importance = NotificationManager.IMPORTANCE_NONE,
        )
        assertEquals(PhoneOwnedNotificationDispatcher.ChannelAction.ROTATE_TO_FALLBACK, actionNone)

        val actionDefault = PhoneOwnedNotificationDispatcher.decideChannelAction(
            exists = true,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )
        assertEquals(PhoneOwnedNotificationDispatcher.ChannelAction.ROTATE_TO_FALLBACK, actionDefault)

        val actionLow = PhoneOwnedNotificationDispatcher.decideChannelAction(
            exists = true,
            importance = NotificationManager.IMPORTANCE_LOW,
        )
        assertEquals(PhoneOwnedNotificationDispatcher.ChannelAction.ROTATE_TO_FALLBACK, actionLow)
    }

    @Test
    fun decideChannelAction_treatsUnknownImportanceOnAnExistingChannelAsUsable() {
        val action = PhoneOwnedNotificationDispatcher.decideChannelAction(
            exists = true,
            importance = null,
        )

        assertEquals(PhoneOwnedNotificationDispatcher.ChannelAction.USE_PRIMARY, action)
    }

    @Test
    fun outcome_countsAcceptedAndUnverifiedAsDelivered() {
        assertTrue(PhoneOwnedNotificationDispatcher.Outcome.ACCEPTED.delivered)
        assertTrue(PhoneOwnedNotificationDispatcher.Outcome.UNVERIFIED.delivered)
    }

    @Test
    fun outcome_doesNotCountDroppedOrFailedAsDelivered() {
        assertFalse(PhoneOwnedNotificationDispatcher.Outcome.DROPPED.delivered)
        assertFalse(PhoneOwnedNotificationDispatcher.Outcome.NOTIFY_FAILED.delivered)
        assertFalse(PhoneOwnedNotificationDispatcher.Outcome.NOT_DELIVERABLE.delivered)
        assertFalse(PhoneOwnedNotificationDispatcher.Outcome.NO_MANAGER.delivered)
    }
}
