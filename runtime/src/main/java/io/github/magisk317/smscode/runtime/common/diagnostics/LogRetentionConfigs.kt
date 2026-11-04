package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object LogRetentionConfigs {
    /**
     * Reads the configured runtime-log retention days from the module app's DataStore.
     *
     * RuntimeLogStore may invoke this callback with a target-process context when the
     * provider route is unavailable; a context from another package must never be
     * interpreted as module configuration, so those calls get the policy default.
     */
    fun readConfiguredLogRetentionDays(
        context: Context,
        applicationId: String,
        key: String,
        dataStoreDefaultDays: Int,
    ): Int {
        if (context.packageName != applicationId) {
            return MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS
        }
        return runBlocking(Dispatchers.IO) {
            AppPreferencesDataStore.getInt(context, key, dataStoreDefaultDays)
        }.let(MagiskLogPolicy::retentionDays)
    }
}
