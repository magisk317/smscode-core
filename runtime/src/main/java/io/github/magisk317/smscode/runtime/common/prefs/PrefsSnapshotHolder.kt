package io.github.magisk317.smscode.runtime.common.prefs

import android.content.Context
import java.util.Collections

/**
 * Shared snapshot plumbing for hook-side preference readers.
 *
 * Holds the last values a real source served, persisted in the hosted process so a frozen
 * module app cannot make a switch the user turned off read back as on. Installed from the
 * hosted process context because the module package's own storage is not writable there.
 */
class PrefsSnapshotHolder(
    private val prefsName: String = DEFAULT_SNAPSHOT_PREFS_NAME,
) {
    @Volatile
    private var snapshot: PrefsSnapshot? = null

    @Volatile
    private var installed = false

    private val fallbackTraceLoggedKeys: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    fun install(context: Context?, warn: (String, Throwable) -> Unit = { _, _ -> }) {
        if (context == null || installed) return
        runCatching {
            val target = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            snapshot = sharedPrefsSnapshot(prefs = { target })
            installed = true
        }.onFailure { warn("prefs snapshot install failed", it) }
    }

    fun asSource(): SnapshotPrefsSource? = snapshot?.let(::SnapshotPrefsSource)

    fun rememberBoolean(key: String, value: Boolean) {
        snapshot?.recordBoolean(key, value)
    }

    fun rememberString(key: String, value: String) {
        snapshot?.recordString(key, value)
    }

    fun rememberInt(key: String, value: Int) {
        snapshot?.recordInt(key, value)
    }

    /** One-shot diagnostics for snapshot/default fallbacks, deduped per key@source. */
    fun logFallbackOnce(key: String, source: String, warn: (String) -> Unit) {
        if (fallbackTraceLoggedKeys.add("$key@$source")) {
            warn("Diag prefs fallback: key=$key source=$source (remote provider unreachable)")
        }
    }

    private companion object {
        const val DEFAULT_SNAPSHOT_PREFS_NAME = "hook_prefs_snapshot"
    }
}
