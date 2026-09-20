package io.github.magisk317.smscode.runtime.common.prefs

import android.content.SharedPreferences
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.smscode.runtime.contract.prefs.PrefsSource

private const val STAMP_SUFFIX = ".__stamp_ms"
private const val TYPE_SUFFIX = ".__type"

/**
 * Backs a [PrefsSnapshot] with a `SharedPreferences` supplied by the host process.
 *
 * The snapshot has to live in storage the hook process can write without the module app running.
 * A `SharedPreferences` obtained from the hosted process context qualifies; one derived from the
 * module package does not, because the hook process runs as a different uid.
 */
fun sharedPrefsSnapshot(
    prefs: () -> SharedPreferences?,
    clock: () -> Long = { System.currentTimeMillis() },
    maxAgeMs: Long = PrefsSnapshot.DEFAULT_MAX_AGE_MS,
): PrefsSnapshot {
    fun readEntry(key: String): PrefsSnapshot.Entry? {
        val target = prefs() ?: return null
        val raw = target.getString(key, null) ?: return null
        val type = target.getString(key + TYPE_SUFFIX, null)?.firstOrNull() ?: return null
        return PrefsSnapshot.Entry(
            type = type,
            raw = raw,
            stampMs = target.getLong(key + STAMP_SUFFIX, 0L),
        )
    }

    fun writeEntry(key: String, entry: PrefsSnapshot.Entry) {
        val target = prefs() ?: return
        target.edit()
            .putString(key, entry.raw)
            .putString(key + TYPE_SUFFIX, entry.type.toString())
            .putLong(key + STAMP_SUFFIX, entry.stampMs)
            .apply()
    }

    return PrefsSnapshot(
        read = ::readEntry,
        write = ::writeEntry,
        clock = clock,
        maxAgeMs = maxAgeMs,
    )
}

/** Adapts a [PrefsSnapshot] into the resolution chain as a read-only [PrefsSource]. */
class SnapshotPrefsSource(
    private val snapshot: PrefsSnapshot,
) : PrefsSource {
    override val sourceName: String = PrefsSnapshot.SOURCE_SNAPSHOT
    override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> = snapshot.readBoolean(key)
    override fun readString(key: String, defaultValue: String): PrefRead<String> = snapshot.readString(key)
    override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = snapshot.readInt(key)
}
