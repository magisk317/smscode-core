package io.github.magisk317.smscode.domain.utils

import java.util.LinkedHashMap

data class SmsForwardDedupSpec(
    val eventId: String = "",
    val sender: String? = null,
    val body: String? = null,
    val timestamp: Long = 0L,
    val msgType: String = "sms",
    val source: String = "sms_hook",
    val simSlot: Int? = null,
    val subId: Int? = null,
)

object SmsForwardDedupKeyFactory {
    fun build(spec: SmsForwardDedupSpec): String {
        val normalizedEventId = spec.eventId.trim()
        if (normalizedEventId.isNotEmpty()) {
            return "event:$normalizedEventId"
        }

        return buildString {
            append("sms:")
            append(spec.msgType)
            append('|')
            append(spec.source)
            append('|')
            append(spec.sender.orEmpty().trim())
            append('|')
            append(spec.body.orEmpty().trim())
            append('|')
            append(spec.timestamp)
            append('|')
            append(spec.simSlot ?: -1)
            append('|')
            append(spec.subId ?: -1)
        }
    }
}

class RecentEventDeduplicator(
    private val windowMs: Long,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val recent = LinkedHashMap<String, Long>(maxEntries, 0.75f, true)

    @Synchronized
    fun shouldDrop(key: String, now: Long = System.currentTimeMillis()): Boolean {
        pruneExpired(now)

        val lastSeenAt = recent[key]
        val duplicated = lastSeenAt != null && now - lastSeenAt <= windowMs
        recent[key] = now
        trimOverflow()
        return duplicated
    }

    @Synchronized
    fun clear() {
        recent.clear()
    }

    private fun pruneExpired(now: Long) {
        val iterator = recent.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > windowMs) {
                iterator.remove()
            }
        }
    }

    private fun trimOverflow() {
        while (recent.size > maxEntries) {
            val eldestKey = recent.entries.firstOrNull()?.key ?: return
            recent.remove(eldestKey)
        }
    }

    private companion object {
        private const val DEFAULT_MAX_ENTRIES = 256
    }
}
