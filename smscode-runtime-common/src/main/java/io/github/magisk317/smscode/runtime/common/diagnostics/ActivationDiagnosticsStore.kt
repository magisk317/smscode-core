package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActivationDiagnosticsStore {
    private const val FILE_NAME = "activation_diagnostics"
    private const val KEY_LAST_SERVICE_BIND_AT_MS = "last_service_bind_at_ms"
    private const val KEY_LAST_SERVICE_FRAMEWORK_NAME = "last_service_framework_name"
    private const val KEY_LAST_SERVICE_FRAMEWORK_VERSION = "last_service_framework_version"
    private const val KEY_LAST_HOOK_AT_MS = "last_hook_at_ms"
    private const val KEY_LAST_HOOK_PACKAGE = "last_hook_package"
    private const val KEY_LAST_HOOK_PROCESS = "last_hook_process"
    private const val KEY_LAST_HOOK_SOURCE = "last_hook_source"

    private val lock = Any()
    private val statusState = MutableStateFlow(ActivationStatusState())

    fun snapshot(context: Context): ActivationDiagnosticsSnapshot = synchronized(lock) {
        val snapshot = readSnapshotLocked(context)
        publishStatusLocked(context, snapshot)
        snapshot
    }

    fun hasHookHeartbeatThisBoot(context: Context): Boolean {
        val snapshot = snapshot(context)
        return snapshot.lastHookAtMs >= currentBootStartAtMs()
    }

    fun isRuntimeConnected(): Boolean = RuntimeDiagnosticsEnvironment.current().runtimeConnectedProvider.invoke()

    fun isModuleActivated(context: Context): Boolean {
        return synchronized(lock) {
            publishStatusLocked(context)
            statusState.value.isEnabled
        }
    }

    fun observeStatus(context: Context): StateFlow<ActivationStatusState> {
        synchronized(lock) {
            publishStatusLocked(context)
        }
        return statusState.asStateFlow()
    }

    fun recordServiceBind(
        context: Context,
        frameworkName: String,
        frameworkVersion: String,
        verboseLogging: Boolean,
    ) {
        val snapshot = synchronized(lock) {
            val current = readSnapshotLocked(context)
            current.copy(
                lastServiceBindAtMs = System.currentTimeMillis(),
                lastServiceFrameworkName = frameworkName,
                lastServiceFrameworkVersion = frameworkVersion,
            ).also {
                writeSnapshotLocked(context, it)
                publishStatusLocked(context, it)
            }
        }
        logSnapshotIfVerbose(
            context = context,
            verboseLogging = verboseLogging,
            source = "service_bind",
            route = RuntimeLogStore.ROUTE_APP,
            snapshot = snapshot,
            runtimeConnected = true,
        )
    }

    fun recordServiceDied(
        context: Context,
        verboseLogging: Boolean,
    ) {
        synchronized(lock) {
            publishStatusLocked(context)
        }
        logSnapshotIfVerbose(
            context = context,
            verboseLogging = verboseLogging,
            source = "service_died",
            route = RuntimeLogStore.ROUTE_APP,
            snapshot = snapshot(context),
            runtimeConnected = false,
        )
    }

    fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
        route: String = RuntimeLogStore.ROUTE_SMS_HOOK,
    ) {
        val snapshot = synchronized(lock) {
            val current = readSnapshotLocked(context)
            current.copy(
                lastHookAtMs = System.currentTimeMillis(),
                lastHookPackage = packageName,
                lastHookProcess = processName,
                lastHookSource = source,
            ).also {
                writeSnapshotLocked(context, it)
                publishStatusLocked(context, it)
            }
        }
        logSnapshotIfVerbose(
            context = context,
            verboseLogging = verboseLogging,
            source = source,
            route = route,
            snapshot = snapshot,
            runtimeConnected = null,
        )
    }

    private fun logSnapshotIfVerbose(
        context: Context,
        verboseLogging: Boolean,
        source: String,
        route: String,
        snapshot: ActivationDiagnosticsSnapshot,
        runtimeConnected: Boolean?,
    ) {
        if (!verboseLogging) return
        val logTag = RuntimeDiagnosticsEnvironment.current().logTag
        val message = buildString {
            append("Diag activation snapshot: source=")
            append(source)
            append(" service=")
            append(
                when (runtimeConnected) {
                    true -> "connected"
                    false -> "disconnected"
                    null -> "unknown"
                },
            )
            append(" lastService=")
            append(formatTime(snapshot.lastServiceBindAtMs))
            append(" framework=")
            append(
                listOf(
                    snapshot.lastServiceFrameworkName.ifBlank { "unknown" },
                    snapshot.lastServiceFrameworkVersion.ifBlank { "unknown" },
                ).joinToString(" "),
            )
            append(" lastHook=")
            append(snapshot.lastHookPackage.ifBlank { "<none>" })
            append("/")
            append(snapshot.lastHookProcess.ifBlank { "<none>" })
            append(" at=")
            append(formatTime(snapshot.lastHookAtMs))
            append(" hookSource=")
            append(snapshot.lastHookSource.ifBlank { "<none>" })
        }
        RuntimeLogStore.initialize(context, enableDetailedLogs = true)
        RuntimeLogStore.append(
            priority = Log.INFO,
            tag = logTag,
            message = message,
            force = true,
            route = route,
        )
        Log.i(logTag, message)
        Log.i("LSPosed-Bridge", "$logTag: $message")
    }

    private fun currentBootStartAtMs(): Long {
        return (System.currentTimeMillis() - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun computeStatusLocked(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot,
    ): ActivationStatusState {
        val runtimeConnected = isRuntimeConnected()
        val inputs = ActivationStatusInputs(
            runtimeConnected = runtimeConnected,
            hasHookHeartbeat = snapshot.lastHookAtMs >= currentBootStartAtMs(),
            hasLegacyActivationMarker = hasLegacyActivationMarker(context),
        )
        val resolver = RuntimeDiagnosticsEnvironment.current().activationStatusResolver
        val isEnabled = resolver?.invoke(context, snapshot, inputs)
            ?: (inputs.runtimeConnected || inputs.hasHookHeartbeat || inputs.hasLegacyActivationMarker)
        return ActivationStatusState(
            isEnabled = isEnabled,
            runtimeConnected = runtimeConnected,
            diagnostics = snapshot,
        )
    }

    private fun publishStatusLocked(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot = readSnapshotLocked(context),
    ) {
        val next = computeStatusLocked(context, snapshot)
        if (statusState.value != next) {
            statusState.value = next
        }
    }

    private fun hasLegacyActivationMarker(context: Context): Boolean {
        val config = RuntimeDiagnosticsEnvironment.current()
        val lastActivatedAt = getLegacyActivationTimestamp(context, config.legacyActivationFileName)
        if (lastActivatedAt <= 0L) return false
        return System.currentTimeMillis() - lastActivatedAt <= config.legacyActivationMaxActiveAgeMs
    }

    private fun getLegacyActivationTimestamp(context: Context, fileName: String): Long {
        val file = File(context.getExternalFilesDir(null) ?: context.filesDir, fileName)
        return try {
            val text = file.readText().trim()
            text.toLongOrNull() ?: file.lastModified()
        } catch (_: IOException) {
            0L
        }
    }

    private fun readSnapshotLocked(context: Context): ActivationDiagnosticsSnapshot {
        val file = getStoreFile(context)
        if (!file.exists()) return ActivationDiagnosticsSnapshot()
        val values = mutableMapOf<String, String>()
        runCatching {
            file.forEachLine { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@forEachLine
                val key = line.substring(0, index)
                val value = line.substring(index + 1)
                values[key] = value
            }
        }
        return ActivationDiagnosticsSnapshot(
            lastServiceBindAtMs = values[KEY_LAST_SERVICE_BIND_AT_MS]?.toLongOrNull() ?: 0L,
            lastServiceFrameworkName = values[KEY_LAST_SERVICE_FRAMEWORK_NAME].orEmpty(),
            lastServiceFrameworkVersion = values[KEY_LAST_SERVICE_FRAMEWORK_VERSION].orEmpty(),
            lastHookAtMs = values[KEY_LAST_HOOK_AT_MS]?.toLongOrNull() ?: 0L,
            lastHookPackage = values[KEY_LAST_HOOK_PACKAGE].orEmpty(),
            lastHookProcess = values[KEY_LAST_HOOK_PROCESS].orEmpty(),
            lastHookSource = values[KEY_LAST_HOOK_SOURCE].orEmpty(),
        )
    }

    private fun writeSnapshotLocked(context: Context, snapshot: ActivationDiagnosticsSnapshot) {
        val file = getStoreFile(context)
        try {
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine("$KEY_LAST_SERVICE_BIND_AT_MS=${snapshot.lastServiceBindAtMs}")
                    appendLine("$KEY_LAST_SERVICE_FRAMEWORK_NAME=${snapshot.lastServiceFrameworkName}")
                    appendLine("$KEY_LAST_SERVICE_FRAMEWORK_VERSION=${snapshot.lastServiceFrameworkVersion}")
                    appendLine("$KEY_LAST_HOOK_AT_MS=${snapshot.lastHookAtMs}")
                    appendLine("$KEY_LAST_HOOK_PACKAGE=${snapshot.lastHookPackage}")
                    appendLine("$KEY_LAST_HOOK_PROCESS=${snapshot.lastHookProcess}")
                    appendLine("$KEY_LAST_HOOK_SOURCE=${snapshot.lastHookSource}")
                },
            )
        } catch (_: IOException) {
            // ignore
        }
    }

    private fun getStoreFile(context: Context): File {
        return File(StorageUtils.getExternalFilesDir(context), FILE_NAME)
    }

    private fun formatTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "never"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
    }
}
