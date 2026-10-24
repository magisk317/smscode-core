package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.xposed.logging.CrashLogRecorder
import io.github.magisk317.xposed.logging.MagiskLogPolicy

/**
 * Gives the exported `crash/` folder something to write into it.
 *
 * Both host apps shipped that folder in their bundle and no code ever filled it, because each
 * assumed the other layer owned an uncaught-exception handler. One call from an application
 * entry point is all a host needs.
 */
object RuntimeCrashLogs {
    fun install(context: Context) {
        val dir = StorageUtils.getCrashLogDir(context) ?: return
        CrashLogRecorder.install(
            dirProvider = { dir },
            prune = { crashDir -> CrashLogRecorder.pruneExpired(crashDir, retentionDays(context)) },
        )
    }

    private fun retentionDays(context: Context): Int {
        val provider = RuntimeDiagnosticsEnvironment.current().logRetentionDaysProvider
        val configured = provider?.invoke(context) ?: MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS
        return MagiskLogPolicy.retentionDays(configured)
    }
}
