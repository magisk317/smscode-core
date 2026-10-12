package io.github.magisk317.smscode.runtime.common.diagnostics
import io.github.magisk317.xposed.logging.VerboseLogEnableTracker

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
import android.content.Intent
import android.util.Log
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import io.github.magisk317.xposed.diagnostics.DiagnosticShell
import io.github.magisk317.smscode.runtime.common.packageenv.PackageEnvCore
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.xposed.diagnostics.DiagnosticArchive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogBundleExportResult(
    val file: File?,
    val details: String,
)

data class LogBundleClearResult(
    val success: Boolean,
    val details: String,
)

object LogBundleExporter {

    private val LSPOSED_LOG_DIRS = listOf(
        "/data/adb/lspd/log",
        "/data/adb/lspd/log.old",
    )
    private val redundantAppRouteRuntimeLogPattern = Regex("""^runtime\.app\.\d{4}-\d{2}-\d{2}\.jsonl$""")
    private val legacyTextLogPattern = Regex("""^(logs_\d{4}-\d{2}-\d{2}|runtime(?:\.[A-Za-z0-9_.-]+)?)\.(txt|log)$""")
    private val legacyModuleTextLogPattern = Regex("""^[A-Za-z0-9_.-]+_\d{4}-\d{2}-\d{2}\.txt$""")
    private val opLock = Any()

    fun checkPreExport(verboseLogEnabled: Boolean): String? {
        if (!PackageEnvCore.hasRootAccess()) {
            return "uikit_log_export_need_root"
        }
        if (!verboseLogEnabled) {
            return "uikit_log_export_need_verbose_log"
        }
        return VerboseLogEnableTracker.shouldBlockExport()
    }

    @Suppress("TooGenericExceptionCaught")
    fun buildLogBundle(
        context: Context,
        mode: DiagnosticExportMode,
    ): LogBundleExportResult {
        synchronized(opLock) {
            val config = RuntimeDiagnosticsEnvironment.current()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val exportDir = getPrivateExportDir(context)
            val result = DiagnosticArchive.buildBundle(
                context = context,
                timestamp = timestamp,
                exportDir = exportDir,
                exportFilePrefix = config.exportFilePrefix,
                stagingDirPrefix = config.stagingDirPrefix,
                deletePath = { path ->
                    DiagnosticShell.deleteRecursivelyWithSuFallback(
                        target = path,
                        onWarning = { message -> logWarn(config.logTag, message) },
                    )
                },
                collect = { stagingDir, details ->
                    details += "export mode=" + mode.name
                    val appLogSrc = StorageUtils.getLogDir(context)
                    if (appLogSrc != null && appLogSrc.exists()) {
                        val deletedLegacyLogs = deleteLegacyTextLogFiles(appLogSrc)
                        if (deletedLegacyLogs > 0) {
                            details += "legacy runtime text logs cleared: $deletedLegacyLogs"
                        }
                        val stagedAppLogDir = File(stagingDir, "app/log")
                        DiagnosticArchive.copyDirectory(
                            source = appLogSrc,
                            target = stagedAppLogDir,
                            includeFile = { file -> !isRedundantAppRouteRuntimeLog(file) },
                            onWarning = { message -> logWarn(config.logTag, message) },
                        )
                        writeRuntimeLogSummaryFile(stagedAppLogDir)
                        details += "app log: ${appLogSrc.absolutePath}"
                        details += summarizeRuntimeLogFiles(stagedAppLogDir)
                    } else {
                        details += "app log missing"
                        details += "runtime log files: 0"
                    }

                    val crashLogSrc = StorageUtils.getCrashLogDir(context)
                    if (crashLogSrc != null && crashLogSrc.exists()) {
                        DiagnosticArchive.copyDirectory(
                            source = crashLogSrc,
                            target = File(stagingDir, "app/crash"),
                            onWarning = { message -> logWarn(config.logTag, message) },
                        )
                        details += "crash log: ${crashLogSrc.absolutePath}"
                    } else {
                        details += "crash log missing"
                    }

                    DiagnosticShell.copyLsposedLogs(
                        dirs = LSPOSED_LOG_DIRS,
                        stagingDir = stagingDir,
                        details = details,
                        onWarning = { message -> logWarn(config.logTag, message) },
                    )
                    DiagnosticShell.captureLogcat(
                        stagingDir = stagingDir,
                        details = details,
                        mode = mode,
                    )
                },
                onInfo = { message -> logInfo(config.logTag, message) },
                onWarning = { message -> logWarn(config.logTag, message) },
                onError = { message, error ->
                    val detail = error?.message ?: error?.javaClass?.simpleName ?: message
                    logError(config.logTag, "$message: $detail")
                },
                makeWorldReadable = true,
                worldReadableParentDepth = 1,
            )
            MagiskOtel.event(
                name = "app.monitor",
                attributes = mapOf(
                    "result" to if (result.file != null) "ok" else "error",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "log_bundle_export",
                    "reason" to if (result.file != null) "exported" else "export_failed",
                    "found_count" to if (result.details.isBlank()) "0" else result.details.count { it == ';' }.plus(1).toString(),
                ),
                statusOk = result.file != null,
            )
            return LogBundleExportResult(result.file, result.details)
        }
    }

    fun shareLogBundle(context: Context, file: File) {
        val config = RuntimeDiagnosticsEnvironment.current()
        val intent = DiagnosticArchive.buildShareIntent(
            context = context,
            file = file,
            authority = context.packageName + ".files",
            onInfo = { message -> logInfo(config.logTag, message) },
            onWarning = { message -> logWarn(config.logTag, message) },
        )
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            clipData = intent.clipData
        }
        context.startActivity(chooser)
    }

    fun clearLogFolders(context: Context): LogBundleClearResult {
        synchronized(opLock) {
            val details = mutableListOf<String>()
            var success = true

            RuntimeLogStore.clear()

            val targets = listOf(
                "log" to StorageUtils.getLogDir(context),
                "crash" to StorageUtils.getCrashLogDir(context),
                "private_export" to getPrivateExportDir(context),
            )

            targets.forEach { (name, dir) ->
                if (dir == null) {
                    success = false
                    details += "$name unavailable"
                    return@forEach
                }
                val ok = clearDirectoryContents(RuntimeDiagnosticsEnvironment.current().logTag, dir)
                if (ok) {
                    details += "$name cleared"
                } else {
                    success = false
                    details += "$name clear failed"
                }
            }

            MagiskOtel.event(
                name = "app.monitor",
                attributes = mapOf(
                    "result" to if (success) "ok" else "error",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "log_bundle_clear",
                    "reason" to if (success) "cleared" else "clear_failed",
                    "found_count" to details.size.toString(),
                ),
                statusOk = success,
            )
            return LogBundleClearResult(success = success, details = details.joinToString("; "))
        }
    }

    private fun summarizeRuntimeLogFiles(stagedAppLogDir: File): String {
        val runtimeFiles = stagedAppLogDir.listFiles().orEmpty()
            .filter { file ->
                file.isFile && (file.name == "runtime.jsonl" || (file.name.startsWith("runtime.") && file.name.endsWith(".jsonl")))
            }
            .sortedBy { it.name }
        if (runtimeFiles.isEmpty()) return "runtime log files: 0"
        val names = runtimeFiles.joinToString(", ") { "${it.name}(${it.length()}B)" }
        return "runtime log files: ${runtimeFiles.size} [$names]"
    }

    private fun writeRuntimeLogSummaryFile(stagedAppLogDir: File) {
        runCatching {
            val summary = RuntimeLogStore.summarizeFiles()
            val content = buildJsonObject {
                put("fileCount", summary.fileCount)
                put("totalBytes", summary.totalBytes)
                put("entryCount", summary.entryCount)
                if (summary.firstTimestamp == null) {
                    put("firstTimestamp", JsonNull)
                } else {
                    put("firstTimestamp", summary.firstTimestamp)
                }
                if (summary.lastTimestamp == null) {
                    put("lastTimestamp", JsonNull)
                } else {
                    put("lastTimestamp", summary.lastTimestamp)
                }
                putJsonArray("files") {
                    summary.files.forEach { file ->
                        add(
                            buildJsonObject {
                                put("name", file.name)
                                put("sizeBytes", file.sizeBytes)
                                put("lineCount", file.lineCount)
                                if (file.firstTimestamp == null) {
                                    put("firstTimestamp", JsonNull)
                                } else {
                                    put("firstTimestamp", file.firstTimestamp)
                                }
                                if (file.lastTimestamp == null) {
                                    put("lastTimestamp", JsonNull)
                                } else {
                                    put("lastTimestamp", file.lastTimestamp)
                                }
                            },
                        )
                    }
                }
            }
            File(stagedAppLogDir, "runtime_summary.json").writeText(content.toString())
        }
    }

    internal fun isRedundantAppRouteRuntimeLog(file: File): Boolean {
        return file.isFile && redundantAppRouteRuntimeLogPattern.matches(file.name)
    }


    internal fun deleteLegacyTextLogFiles(logDir: File): Int {
        if (!logDir.exists() || !logDir.isDirectory) return 0
        var deleted = 0
        val targetDirs = listOf(logDir, File(logDir, "modules"))
        targetDirs.forEach { dir ->
            dir.listFiles().orEmpty().forEach { file ->
                if (file.isFile &&
                    (legacyTextLogPattern.matches(file.name) || legacyModuleTextLogPattern.matches(file.name)) &&
                    file.delete()
                ) {
                    deleted += 1
                }
            }
        }
        return deleted
    }


    private fun clearDirectoryContents(logTag: String, dir: File): Boolean {
        return runCatching {
            if (!ensureDirectory(dir, recreateWhenFile = true)) {
                return@runCatching false
            }
            var deletedAll = true
            dir.listFiles().orEmpty().forEach { child ->
                if (!DiagnosticShell.deleteRecursivelyWithSuFallback(
                        target = child,
                        onWarning = { message -> logWarn(logTag, message) },
                    )
                ) {
                    deletedAll = false
                    logWarn(logTag, "Failed to delete log child: ${child.absolutePath}")
                }
            }
            deletedAll && ensureDirectory(dir, recreateWhenFile = true)
        }.getOrDefault(false)
    }

    private fun ensureDirectory(dir: File, recreateWhenFile: Boolean): Boolean {
        return DiagnosticArchive.ensureDirectory(dir, recreateWhenFile)
    }

    private fun getPrivateExportDir(context: Context): File {
        return StorageUtils.getPrivateLogExportDir(context)
    }

    private fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    private fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    private fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }
}
