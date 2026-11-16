package io.github.magisk317.smscode.runtime.verification

import android.app.Notification
import android.os.Build
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationReadActionFinderTest {

    @Test
    fun `semantic action matches on P and above`() {
        assertTrue(
            NotificationReadActionFinder.matches(
                Notification.Action.SEMANTIC_ACTION_MARK_AS_READ,
                null,
                sdkInt = Build.VERSION_CODES.P,
            ),
        )
    }

    @Test
    fun `unrelated semantic action does not match even on P`() {
        assertFalse(
            NotificationReadActionFinder.matches(
                Notification.Action.SEMANTIC_ACTION_REPLY,
                null,
                sdkInt = Build.VERSION_CODES.P,
            ),
        )
    }

    @Test
    fun `below P semantic action is ignored and title heuristic decides`() {
        assertFalse(
            NotificationReadActionFinder.matches(
                Notification.Action.SEMANTIC_ACTION_MARK_AS_READ,
                null,
                sdkInt = Build.VERSION_CODES.O_MR1,
            ),
        )
        assertTrue(
            NotificationReadActionFinder.matches(
                NotificationReadActionFinder.NO_SEMANTIC_ACTION,
                "Mark as read",
                sdkInt = Build.VERSION_CODES.O_MR1,
            ),
        )
    }

    @Test
    fun `title heuristic requires both mark and read words`() {
        assertTrue(NotificationReadActionFinder.matches(NotificationReadActionFinder.NO_SEMANTIC_ACTION, "Mark as read"))
        assertFalse(NotificationReadActionFinder.matches(NotificationReadActionFinder.NO_SEMANTIC_ACTION, "Mark as unread"))
        assertFalse(NotificationReadActionFinder.matches(NotificationReadActionFinder.NO_SEMANTIC_ACTION, "标记为已读"))
        assertFalse(NotificationReadActionFinder.matches(NotificationReadActionFinder.NO_SEMANTIC_ACTION, null))
    }
}
