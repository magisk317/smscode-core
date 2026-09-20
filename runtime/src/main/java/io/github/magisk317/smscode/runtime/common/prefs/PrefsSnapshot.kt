package io.github.magisk317.smscode.runtime.common.prefs

import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead

/**
 * A persisted snapshot of preference values that the hook process read successfully.
 *
 * The hook process has no local preference source of its own: every value comes from the
 * cross-process remote-preferences Provider. When the module app is frozen that Provider is
 * unreachable, and a read falls back to the method default. For a side-effecting switch such as
 * "show a code notification" the default is `true`, so a switch the user explicitly turned off
 * silently reads back as on.
 *
 * The snapshot closes that hole. It records values that the Provider actually served (so it never
 * invents a setting) and is consulted only when the Provider cannot be reached. It is persisted
 * rather than kept in memory because the whole point is to survive the module process being frozen
 * for a long time, and to survive the hook process restarting.
 *
 * Snapshots age out. An entry older than [maxAgeMs] reports [PrefRead.Unavailable] instead of a
 * hit, so a snapshot that never got a chance to refresh cannot stay authoritative forever.
 *
 * The core type is deliberately free of Android types: [read] and [write] are supplied by the
 * caller, which keeps the logic unit testable without a `SharedPreferences`.
 */
class PrefsSnapshot(
    private val read: (String) -> Entry?,
    private val write: (String, Entry) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) {

    /** One stored value: the encoded payload, the type it was stored as, and when it was written. */
    data class Entry(val type: Char, val raw: String, val stampMs: Long)

    companion object {
        /** Seven days: long enough to outlast a freeze, short enough to stop being trusted. */
        const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000L

        /** Source label written into [PrefRead.Hit], so callers can tell a snapshot from the real thing. */
        const val SOURCE_SNAPSHOT = "snapshot"

        private const val TYPE_BOOL = 'b'
        private const val TYPE_STRING = 's'
        private const val TYPE_INT = 'i'
    }

    fun recordBoolean(key: String, value: Boolean) {
        record(key, TYPE_BOOL, value.toString())
    }

    fun recordString(key: String, value: String) {
        record(key, TYPE_STRING, value)
    }

    fun recordInt(key: String, value: Int) {
        record(key, TYPE_INT, value.toString())
    }

    fun readBoolean(key: String): PrefRead<Boolean> = readAs(key, TYPE_BOOL) { it.toBoolean() }

    fun readString(key: String): PrefRead<String> = readAs(key, TYPE_STRING) { it }

    fun readInt(key: String): PrefRead<Int> = readAs(key, TYPE_INT) { it.toIntOrNull() }

    private fun record(key: String, type: Char, raw: String) {
        val next = Entry(type = type, raw = raw, stampMs = clock())
        val current = runCatching { read(key) }.getOrNull()
        if (current != null && current.type == type && current.raw == raw) return
        runCatching { write(key, next) }
    }

    private fun <T> readAs(key: String, type: Char, decode: (String) -> T?): PrefRead<T> {
        val entry = runCatching { read(key) }.getOrNull() ?: return PrefRead.Unavailable
        if (entry.type != type) return PrefRead.Unavailable
        if (maxAgeMs > 0 && clock() - entry.stampMs > maxAgeMs) return PrefRead.Unavailable
        val value = decode(entry.raw) ?: return PrefRead.Unavailable
        return PrefRead.Hit(value, SOURCE_SNAPSHOT)
    }
}
