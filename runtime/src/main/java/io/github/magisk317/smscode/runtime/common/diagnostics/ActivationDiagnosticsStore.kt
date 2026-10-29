package io.github.magisk317.smscode.runtime.common.diagnostics

import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.CallNotificationSourceSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.HookInstallStatus
import io.github.magisk317.smscode.runtime.contract.diagnostics.HookProcessStatus
import io.github.magisk317.smscode.runtime.contract.diagnostics.NotifyEventSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationStatusInputs
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationStatusState

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
import android.os.SystemClock
import android.util.Log
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import java.io.File
import java.io.IOException
import java.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActivationDiagnosticsStore {
    private const val FILE_NAME = "activation_diagnostics"
    private const val INTERNAL_DIR_NAME = "runtime_diagnostics"
    private const val KEY_LAST_SERVICE_BIND_AT_MS = "last_service_bind_at_ms"
    private const val KEY_LAST_SERVICE_FRAMEWORK_NAME = "last_service_framework_name"
    private const val KEY_LAST_SERVICE_FRAMEWORK_VERSION = "last_service_framework_version"
    private const val KEY_LAST_HOOK_AT_MS = "last_hook_at_ms"
    private const val KEY_LAST_HOOK_PACKAGE = "last_hook_package"
    private const val KEY_LAST_HOOK_PROCESS = "last_hook_process"
    private const val KEY_LAST_HOOK_SOURCE = "last_hook_source"
    private const val KEY_LAST_CALL_NOTIFY_SOURCE = "call_notify_source"
    private const val KEY_NOTIFY_EVENT = "notify_event"

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
        MagiskOtel.event(
            name = "hook.service",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "service_bind",
                "reason" to "bound",
                "source" to frameworkName.ifBlank { "unknown" },
            ),
            statusOk = true,
        )
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
        MagiskOtel.event(
            name = "hook.service",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "service_died",
                "reason" to "died",
            ),
            statusOk = false,
        )
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
        moduleVersion: Int = 0,
    ) {
        val snapshot = synchronized(lock) {
            val current = readSnapshotLocked(context)
            val now = System.currentTimeMillis()
            val nextProcesses = ActivationDiagnosticsCodec.trimProcesses(
                current.hookProcesses
                    .filterNot { it.packageName == packageName && it.processName == processName } +
                    HookProcessStatus(
                        packageName = packageName,
                        processName = processName,
                        lastSeenAtMs = now,
                        source = source,
                        moduleVersion = moduleVersion,
                    ),
            )
            current.copy(
                lastHookAtMs = now,
                lastHookPackage = packageName,
                lastHookProcess = processName,
                lastHookSource = source,
                hookProcesses = nextProcesses,
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

    fun recordHookInstall(
        context: Context,
        hook: String,
        status: String,
        packageName: String,
        processName: String,
        detail: String,
    ) {
        if (hook.isBlank() || status.isBlank()) return
        synchronized(lock) {
            val current = readSnapshotLocked(context)
            val nextInstalls = current.hookInstalls
                .filterNot { it.hook == hook }
                .plus(
                    HookInstallStatus(
                        hook = hook,
                        status = status,
                        packageName = packageName,
                        processName = processName,
                        detail = detail,
                        lastSeenAtMs = System.currentTimeMillis(),
                    ),
                )
                .sortedByDescending { it.lastSeenAtMs }
                .take(ActivationDiagnosticsCodec.MAX_HOOK_INSTALLS)
            current.copy(hookInstalls = nextInstalls).also {
                writeSnapshotLocked(context, it)
                publishStatusLocked(context, it)
            }
        }
    }

    fun recordNotifyEvent(
        context: Context,
        source: String,
        packageName: String,
        channelId: String,
        msgType: String,
        skipReason: String,
    ) {
        if (source.isBlank() || packageName.isBlank()) return
        synchronized(lock) {
            val current = readSnapshotLocked(context)
            current.copy(
                lastNotifyEvent = NotifyEventSnapshot(
                    source = source,
                    packageName = packageName,
                    channelId = channelId,
                    msgType = msgType,
                    skipReason = skipReason,
                    lastSeenAtMs = System.currentTimeMillis(),
                ),
            ).also {
                writeSnapshotLocked(context, it)
                publishStatusLocked(context, it)
            }
        }
    }

    fun recordCallNotificationSource(
        context: Context,
        packageName: String,
        channelId: String,
        category: String,
    ) {
        synchronized(lock) {
            val current = readSnapshotLocked(context)
            current.copy(
                lastCallNotifySource = CallNotificationSourceSnapshot(
                    packageName = packageName,
                    channelId = channelId,
                    category = category,
                    lastSeenAtMs = System.currentTimeMillis(),
                ),
            ).also {
                writeSnapshotLocked(context, it)
                publishStatusLocked(context, it)
            }
        }
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
        )
        val resolver = RuntimeDiagnosticsEnvironment.current().activationStatusResolver
        val isEnabled = resolver?.invoke(context, snapshot, inputs)
            ?: (inputs.runtimeConnected || inputs.hasHookHeartbeat)
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
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }
        val hookProcesses = values.entries
            .filter { it.key.startsWith(ActivationDiagnosticsCodec.HOOK_PROCESS_KEY_PREFIX) }
            .mapNotNull { (key, raw) -> ActivationDiagnosticsCodec.decodeHookProcess(key, raw) }
            .sortedByDescending { it.lastSeenAtMs }
        val callSource = values[KEY_LAST_CALL_NOTIFY_SOURCE]
            ?.let(ActivationDiagnosticsCodec::decodeCallSource)
            ?: CallNotificationSourceSnapshot()
        val hookInstalls = values.entries
            .filter { it.key.startsWith(ActivationDiagnosticsCodec.HOOK_INSTALL_KEY_PREFIX) }
            .mapNotNull { (key, raw) -> ActivationDiagnosticsCodec.decodeHookInstall(key, raw) }
            .sortedByDescending { it.lastSeenAtMs }
        val notifyEvent = values[KEY_NOTIFY_EVENT]
            ?.let(ActivationDiagnosticsCodec::decodeNotifyEvent)
            ?: NotifyEventSnapshot()
        return ActivationDiagnosticsSnapshot(
            lastServiceBindAtMs = values[KEY_LAST_SERVICE_BIND_AT_MS]?.toLongOrNull() ?: 0L,
            lastServiceFrameworkName = values[KEY_LAST_SERVICE_FRAMEWORK_NAME].orEmpty(),
            lastServiceFrameworkVersion = values[KEY_LAST_SERVICE_FRAMEWORK_VERSION].orEmpty(),
            lastHookAtMs = values[KEY_LAST_HOOK_AT_MS]?.toLongOrNull() ?: 0L,
            lastHookPackage = values[KEY_LAST_HOOK_PACKAGE].orEmpty(),
            lastHookProcess = values[KEY_LAST_HOOK_PROCESS].orEmpty(),
            lastHookSource = values[KEY_LAST_HOOK_SOURCE].orEmpty(),
            hookProcesses = hookProcesses,
            lastCallNotifySource = callSource,
            hookInstalls = hookInstalls,
            lastNotifyEvent = notifyEvent,
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
                    appendLine(
                        "$KEY_LAST_CALL_NOTIFY_SOURCE=" +
                            ActivationDiagnosticsCodec.encodeCallSource(snapshot.lastCallNotifySource),
                    )
                    snapshot.hookProcesses.forEach { status ->
                        appendLine(
                            ActivationDiagnosticsCodec.hookProcessKey(
                                status.packageName,
                                status.processName,
                            ) + "=" + ActivationDiagnosticsCodec.encodeHookProcess(status),
                        )
                    }
                    appendLine(
                        "$KEY_NOTIFY_EVENT=" + ActivationDiagnosticsCodec.encodeNotifyEvent(
                            snapshot.lastNotifyEvent,
                        ),
                    )
                    snapshot.hookInstalls.forEach { status ->
                        appendLine(
                            ActivationDiagnosticsCodec.hookInstallKey(status.hook) +
                                "=" + ActivationDiagnosticsCodec.encodeHookInstall(status),
                        )
                    }
                },
            )
        } catch (_: IOException) {
            // ignore
        }
    }

    private fun getStoreFile(context: Context): File {
        val target = File(File(context.noBackupFilesDir, INTERNAL_DIR_NAME), FILE_NAME)
        if (!target.exists()) {
            migrateLegacyStore(context, target)
        }
        return target
    }

    private fun migrateLegacyStore(context: Context, target: File) {
        val legacy = runCatching {
            File(StorageUtils.getExternalFilesDir(context), FILE_NAME)
        }.getOrNull() ?: return
        if (!legacy.isFile) return
        runCatching {
            target.parentFile?.mkdirs()
            legacy.copyTo(target, overwrite = false)
        }.onFailure {
            // ignored here: no logger is reachable from this file and the failure has no remedy at this point
        }
    }

    private fun formatTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "never"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
    }
}

/**
 * Line codec for the diagnostics file. Kept separate from file IO so the encoding can be
 * tested without an Android context.
 */
internal object ActivationDiagnosticsCodec {
    const val HOOK_PROCESS_KEY_PREFIX = "hook_process."
    const val HOOK_INSTALL_KEY_PREFIX = "hook_install."
    const val MAX_HOOK_PROCESSES = 16
    const val MAX_HOOK_INSTALLS = 16
    private const val SEPARATOR = "|"
    private const val MAX_SOURCE_LENGTH = 120

    fun hookProcessKey(packageName: String, processName: String): String =
        HOOK_PROCESS_KEY_PREFIX + encode(packageName) + "." + encode(processName)

    fun decodeHookProcessKey(key: String): Pair<String, String>? {
        if (!key.startsWith(HOOK_PROCESS_KEY_PREFIX)) return null
        val body = key.removePrefix(HOOK_PROCESS_KEY_PREFIX)
        val separator = body.indexOf('.')
        if (separator <= 0 || separator == body.lastIndex) return null
        val packageName = decode(body.substring(0, separator)) ?: return null
        val processName = decode(body.substring(separator + 1)) ?: return null
        return packageName to processName
    }

    fun encodeHookProcess(status: HookProcessStatus): String = listOf(
        status.lastSeenAtMs.toString(),
        status.moduleVersion.toString(),
        sanitize(status.source),
    ).joinToString(SEPARATOR)

    fun decodeHookProcess(key: String, raw: String): HookProcessStatus? {
        val (packageName, processName) = decodeHookProcessKey(key) ?: return null
        val parts = raw.split(SEPARATOR)
        if (parts.size < 2) return null
        return HookProcessStatus(
            packageName = packageName,
            processName = processName,
            lastSeenAtMs = parts[0].toLongOrNull() ?: 0L,
            moduleVersion = parts[1].toIntOrNull() ?: 0,
            source = parts.getOrNull(2).orEmpty(),
        )
    }

    fun encodeCallSource(snapshot: CallNotificationSourceSnapshot): String = listOf(
        snapshot.lastSeenAtMs.toString(),
        encode(snapshot.packageName),
        encode(snapshot.channelId),
        encode(snapshot.category),
    ).joinToString(SEPARATOR)

    fun decodeCallSource(raw: String): CallNotificationSourceSnapshot {
        val parts = raw.split(SEPARATOR)
        if (parts.size < 4) return CallNotificationSourceSnapshot()
        return CallNotificationSourceSnapshot(
            lastSeenAtMs = parts[0].toLongOrNull() ?: 0L,
            packageName = decode(parts[1]).orEmpty(),
            channelId = decode(parts[2]).orEmpty(),
            category = decode(parts[3]).orEmpty(),
        )
    }

    fun trimProcesses(processes: List<HookProcessStatus>): List<HookProcessStatus> =
        processes.sortedByDescending { it.lastSeenAtMs }.take(MAX_HOOK_PROCESSES)

    fun hookInstallKey(hook: String): String = HOOK_INSTALL_KEY_PREFIX + encode(hook)

    fun decodeHookInstallKey(key: String): String? {
        if (!key.startsWith(HOOK_INSTALL_KEY_PREFIX)) return null
        return decode(key.removePrefix(HOOK_INSTALL_KEY_PREFIX))
    }

    fun encodeHookInstall(status: HookInstallStatus): String = listOf(
        status.lastSeenAtMs.toString(),
        encode(status.status),
        encode(status.packageName),
        encode(status.processName),
        encode(status.detail),
    ).joinToString(SEPARATOR)

    fun decodeHookInstall(key: String, raw: String): HookInstallStatus? {
        val hook = decodeHookInstallKey(key) ?: return null
        val parts = raw.split(SEPARATOR)
        if (parts.size < 5) return null
        return HookInstallStatus(
            hook = hook,
            status = decode(parts[1]).orEmpty(),
            packageName = decode(parts[2]).orEmpty(),
            processName = decode(parts[3]).orEmpty(),
            detail = decode(parts[4]).orEmpty(),
            lastSeenAtMs = parts[0].toLongOrNull() ?: 0L,
        )
    }

    fun encodeNotifyEvent(event: NotifyEventSnapshot): String = listOf(
        event.lastSeenAtMs.toString(),
        encode(event.source),
        encode(event.packageName),
        encode(event.channelId),
        encode(event.msgType),
        encode(event.skipReason),
    ).joinToString(SEPARATOR)

    fun decodeNotifyEvent(raw: String): NotifyEventSnapshot {
        val parts = raw.split(SEPARATOR)
        if (parts.size < 6) return NotifyEventSnapshot()
        return NotifyEventSnapshot(
            source = decode(parts[1]).orEmpty(),
            packageName = decode(parts[2]).orEmpty(),
            channelId = decode(parts[3]).orEmpty(),
            msgType = decode(parts[4]).orEmpty(),
            skipReason = decode(parts[5]).orEmpty(),
            lastSeenAtMs = parts[0].toLongOrNull() ?: 0L,
        )
    }

    private fun sanitize(value: String): String = value.replace(SEPARATOR, "/").take(MAX_SOURCE_LENGTH)

    private fun encode(raw: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))

    private fun decode(token: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
    }.getOrNull()
}
