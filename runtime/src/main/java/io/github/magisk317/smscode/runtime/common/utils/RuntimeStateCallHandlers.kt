package io.github.magisk317.smscode.runtime.common.utils

import android.content.Context
import android.os.Bundle
import io.github.magisk317.xposed.runtime.RuntimeStateProviderContract
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore

/**
 * Shared handlers for the RuntimeStateProviderContract call() methods both products
 * implement. The two hosts historically disagreed on gate-failure semantics
 * (XSC fail-open, xinyi fail-closed); [claimRuntimeGate] makes that an explicit
 * parameter instead of two drifting copies.
 */
object RuntimeStateCallHandlers {
    /** CR/LF-stripped, length-capped value for runtime-state writes (matches both hosts). */
    fun sanitized(value: String?): String =
        value.orEmpty().replace('\n', ' ').replace('\r', ' ').take(256)

    fun claimRuntimeGate(
        ctx: Context,
        fileName: String?,
        extras: Bundle?,
        failOpen: Boolean,
        maxEntriesCap: Int,
    ): Bundle? {
        val resolvedFileName = fileName?.takeIf(String::isNotBlank)
        val keys = extras?.getStringArrayList(RuntimeStateProviderContract.EXTRA_KEYS).orEmpty()
        val windowMs = extras?.getLong(RuntimeStateProviderContract.EXTRA_WINDOW_MS, 0L) ?: 0L
        val maxEntries = extras?.getInt(
            RuntimeStateProviderContract.EXTRA_MAX_ENTRIES,
            RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES,
        ) ?: RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES
        val result = runCatching {
            if (resolvedFileName.isNullOrBlank()) error("missing gate file name")
            if (windowMs <= 0L) error("invalid runtime gate window")
            require(maxEntries in 1..maxEntriesCap) { "invalid runtime gate max entries" }
            SharedRuntimeGate.claimAllWithinWindow(
                file = SharedRuntimeGate.internalGateFile(ctx, resolvedFileName),
                keys = keys,
                windowMs = windowMs,
                maxEntries = maxEntries,
            )
        }.getOrNull()
        if (result == null) {
            if (failOpen) {
                // Preserve XSC's historical fail-open behavior when app storage is unavailable.
                return Bundle().apply {
                    putBoolean(RuntimeStateProviderContract.RESULT_OK, true)
                    putBoolean(RuntimeStateProviderContract.RESULT_CLAIMED, true)
                    putLong(RuntimeStateProviderContract.RESULT_AGE_MS, RuntimeStateProviderContract.NO_AGE_MS)
                }
            }
            return null
        }
        return Bundle().apply {
            putBoolean(RuntimeStateProviderContract.RESULT_OK, true)
            putBoolean(RuntimeStateProviderContract.RESULT_CLAIMED, result.claimed)
            putLong(
                RuntimeStateProviderContract.RESULT_AGE_MS,
                result.ageMs ?: RuntimeStateProviderContract.NO_AGE_MS,
            )
            result.key?.let { putString(RuntimeStateProviderContract.RESULT_BLOCKED_KEY, it) }
        }
    }

    fun recordHookHeartbeat(
        ctx: Context,
        extras: Bundle?,
        defaultRoute: String,
    ): Bundle? {
        val packageName = sanitized(extras?.getString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME))
        val processName = sanitized(extras?.getString(RuntimeStateProviderContract.EXTRA_PROCESS_NAME))
        val source = sanitized(extras?.getString(RuntimeStateProviderContract.EXTRA_SOURCE))
        if (packageName.isBlank() || processName.isBlank() || source.isBlank()) return null
        val ok = runCatching {
            RuntimeStateStore.recordHookHeartbeat(
                context = ctx.applicationContext ?: ctx,
                packageName = packageName,
                processName = processName,
                source = source,
                verboseLogging = extras?.getBoolean(
                    RuntimeStateProviderContract.EXTRA_VERBOSE_LOGGING,
                    false,
                ) ?: false,
                route = sanitized(extras?.getString(RuntimeStateProviderContract.EXTRA_ROUTE))
                    .takeIf(String::isNotBlank)
                    ?: defaultRoute,
                moduleVersion = extras?.getInt(RuntimeStateProviderContract.EXTRA_MODULE_VERSION, 0) ?: 0,
            )
        }.isSuccess
        return Bundle().apply { putBoolean(RuntimeStateProviderContract.RESULT_OK, ok) }
    }
}
