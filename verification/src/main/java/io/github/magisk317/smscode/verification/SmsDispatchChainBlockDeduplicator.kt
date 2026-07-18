package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.rule.utils.BoundedTimestampMap

class SmsDispatchChainBlockDeduplicator(
    windowMs: Long = DEFAULT_WINDOW_MS,
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    data class Result(
        val shouldSkip: Boolean,
        val ageMs: Long? = null,
    )

    private val lock = Any()
    private val history = BoundedTimestampMap<String>(windowMs, maxEntries)

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
            val claim = history.claim(key, now)
            Result(shouldSkip = !claim.claimed, ageMs = claim.ageMs)
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
