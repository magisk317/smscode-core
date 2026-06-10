package io.github.magisk317.smscode.verification

import java.util.LinkedHashSet

class RecentEventIdTracker(
    private val maxSize: Int,
) {
    private val eventIds = LinkedHashSet<String>()

    fun mark(eventId: String): Boolean = synchronized(eventIds) {
        if (!eventIds.add(eventId)) {
            return false
        }
        while (eventIds.size > maxSize) {
            val first = eventIds.firstOrNull() ?: break
            eventIds.remove(first)
        }
        true
    }
}
