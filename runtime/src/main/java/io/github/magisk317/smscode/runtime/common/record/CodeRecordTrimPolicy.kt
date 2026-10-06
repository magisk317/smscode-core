package io.github.magisk317.smscode.runtime.common.record

/**
 * History-limit policy for code records, shared by the hosts' DBProvider
 * content providers. Storage stays host-side: the host maps its msgType to a
 * [CodeRecordChannel], resolves the effective limit via [resolveLimit], and
 * deletes the records returned by [selectOutdated] through its own DAO.
 */
/** Record channel; SMS is split by whether a code was extracted. */
enum class CodeRecordChannel { APP_NOTIFY, CALL_NOTIFY, SMS_CODE, SMS_PLAIN }

object CodeRecordTrimPolicy {

    /** Per-channel preference keys supplied by each host. */
    class LimitKeys(
        val appNotify: String,
        val callNotify: String,
        val codeSms: String,
        val plainSms: String,
    )

    fun limitKeyFor(channel: CodeRecordChannel, keys: LimitKeys): String = when (channel) {
        CodeRecordChannel.APP_NOTIFY -> keys.appNotify
        CodeRecordChannel.CALL_NOTIFY -> keys.callNotify
        CodeRecordChannel.SMS_CODE -> keys.codeSms
        CodeRecordChannel.SMS_PLAIN -> keys.plainSms
    }

    /**
     * Resolves the effective per-channel limit. [defaultLimitKey] supplies the
     * legacy total-key fallback for hosts that have one (read with default "0");
     * hosts without a legacy key pass null so an unset channel key disables trimming.
     */
    suspend fun resolveLimit(
        readString: suspend (String, String) -> String,
        limitKey: String,
        defaultLimitKey: String? = null,
    ): Int {
        val fallback = if (defaultLimitKey != null) readString(defaultLimitKey, "0") else "0"
        return readString(limitKey, fallback).toIntOrNull() ?: 0
    }

    /**
     * Selects the outdated records to delete: same channel as the incoming
     * record, oldest first, keeping the newest [limit] - 1 so the incoming
     * record still fits. Returns an empty list when [limit] is unset (<= 0)
     * or the channel is under the limit.
     */
    fun <T> selectOutdated(
        records: List<T>,
        incomingChannel: CodeRecordChannel,
        limit: Int,
        channelOf: (T) -> CodeRecordChannel,
        dateOf: (T) -> Long?,
    ): List<T> {
        if (limit <= 0) return emptyList()
        val matching = records.asSequence()
            .filter { channelOf(it) == incomingChannel }
            .sortedBy(dateOf)
            .toList()
        if (matching.size < limit) return emptyList()
        val deleteCount = matching.size - limit + 1
        return matching.take(deleteCount)
    }
}
