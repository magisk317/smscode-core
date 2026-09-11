package io.github.magisk317.smscode.runtime.common.diagnostics

/**
 * Tracks when verbose logging was last toggled on.
 * Used to enforce a minimum interval between enabling verbose logging
 * and exporting logs, ensuring users have time to reproduce the issue.
 *
 * Not persisted — resets on app restart. If verbose logging was already
 * enabled before restart (from SharedPreferences), the timestamp is null
 * and the time check is skipped, trusting long-running verbose mode.
 */
object VerboseLogEnableTracker {

    private const val MIN_INTERVAL_MS = 60_000L

    @Volatile
    private var enableTimestamp: Long? = null

    /**
     * Called when the verbose log toggle changes.
     * Records the timestamp when enabled; clears it when disabled.
     */
    fun onVerboseLogToggled(enabled: Boolean) {
        enableTimestamp = if (enabled) System.currentTimeMillis() else null
    }

    /**
     * Returns a string resource key if the export should be blocked, or null to allow.
     * Blocks if verbose logging was recently toggled on (within [MIN_INTERVAL_MS]).
     * Returns null if no timestamp exists (already enabled before restart → skip check).
     */
    fun shouldBlockExport(): String? {
        val ts = enableTimestamp ?: return null
        val elapsed = System.currentTimeMillis() - ts
        return if (elapsed < MIN_INTERVAL_MS) {
            "uikit_log_export_need_reproduce"
        } else {
            null
        }
    }
}
