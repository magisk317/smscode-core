package io.github.magisk317.smscode.verification

import java.util.Collections
import java.util.LinkedHashSet

class SmsInboxSeenTracker(
    private val maxTrackedSmsIds: Int,
    private val recentSmsIds: MutableSet<Long> = Collections.synchronizedSet(LinkedHashSet<Long>()),
) {
    fun markSeen(smsId: Long): Boolean = synchronized(recentSmsIds) {
        if (!recentSmsIds.add(smsId)) {
            return false
        }
        while (recentSmsIds.size > maxTrackedSmsIds) {
            val first = recentSmsIds.firstOrNull() ?: break
            recentSmsIds.remove(first)
        }
        true
    }
}
