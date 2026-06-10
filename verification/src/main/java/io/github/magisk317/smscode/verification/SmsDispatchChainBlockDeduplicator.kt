package io.github.magisk317.smscode.verification

class SmsDispatchChainBlockDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    data class Result(
        val shouldSkip: Boolean,
        val ageMs: Long? = null,
    )

    private val lock = Any()
    private val history = LinkedHashMap<String, Long>()

    fun shouldSkip(
        smsMsg: SmsMessage?,
        action: String?,
        reason: String,
    ): Result {
        if (smsMsg == null || action.isNullOrBlank()) {
            return Result(shouldSkip = false)
        }
        val now = timeProvider()
        val key = buildKey(smsMsg, action, reason)
        return synchronized(lock) {
            pruneExpired(now)
            val last = history[key]
            if (last != null && now - last <= windowMs) {
                Result(shouldSkip = true, ageMs = now - last)
            } else {
                history[key] = now
                trimToCapacity()
                Result(shouldSkip = false)
            }
        }
    }

    private fun pruneExpired(now: Long) {
        val iterator = history.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > windowMs) {
                iterator.remove()
            }
        }
    }

    private fun trimToCapacity() {
        while (history.size > maxEntries) {
            val firstKey = history.entries.firstOrNull()?.key ?: break
            history.remove(firstKey)
        }
    }

    private fun buildKey(
        smsMsg: SmsMessage,
        action: String,
        reason: String,
    ): String {
        return buildString {
            append(senderHash(smsMsg.sender))
            append('|')
            append(smsMsg.body.orEmpty().hashCode())
            append('|')
            append(smsMsg.date)
            append('|')
            append(action)
            append('|')
            append(reason)
        }
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 8_000L
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
