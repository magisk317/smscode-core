package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
import android.content.Intent
import android.util.Log
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.xposed.diagnostics.DiagnosticArchive
import io.github.magisk317.xposed.diagnostics.DiagnosticTextSanitizer
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LogBundleExportResult(
    val file: File?,
    val details: String,
)

data class LogBundleClearResult(
    val success: Boolean,
    val details: String,
)

object LogBundleExporter {
    internal fun logcatCommand(mode: DiagnosticExportMode): List<String> =
        if (mode.usesBoundedLogcat) {
            listOf("logcat", "-d", "-v", "threadtime", "-t", "24h", "-b", "main,system,crash")
        } else {
            listOf("logcat", "-d", "-v", "threadtime", "-b", "all")
        }

    private const val LSPOSED_ROTATING_LOGS_PER_KIND = 2
    private val LSPOSED_LOG_DIRS = listOf(
        "/data/adb/lspd/log",
        "/data/adb/lspd/log.old",
    )
    private val redundantAppRouteRuntimeLogPattern = Regex("""^runtime\.app\.\d{4}-\d{2}-\d{2}\.jsonl$""")
    private val lsposedRotatingLogPattern = Regex("""^(verbose|modules)_.+\.log$""")
    private val legacyTextLogPattern = Regex("""^(logs_\d{4}-\d{2}-\d{2}|runtime(?:\.[A-Za-z0-9_.-]+)?)\.(txt|log)$""")
    private val legacyModuleTextLogPattern = Regex("""^[A-Za-z0-9_.-]+_\d{4}-\d{2}-\d{2}\.txt$""")
    private val opLock = Any()

    @Suppress("TooGenericExceptionCaught")
    fun buildLogBundle(
        context: Context,
        mode: DiagnosticExportMode,
        sanitizeLogs: Boolean = true,
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
                deletePath = { path -> deleteRecursivelyWithSuFallback(config.logTag, path) },
                collect = { stagingDir, details ->
                    details += "export mode=" + mode.name
                    val appLogSrc = StorageUtils.getLogDir(context)
                    if (appLogSrc != null && appLogSrc.exists()) {
                        val deletedLegacyLogs = deleteLegacyTextLogFiles(appLogSrc)
                        if (deletedLegacyLogs > 0) {
                            details += "legacy runtime text logs cleared: $deletedLegacyLogs"
                        }
                        val stagedAppLogDir = File(stagingDir, "app/log")
                        copyDirectory(
                            logTag = config.logTag,
                            source = appLogSrc,
                            target = stagedAppLogDir,
                            sanitizeFiles = sanitizeLogs,
                        ) { file -> !isRedundantAppRouteRuntimeLog(file) }
                        writeRuntimeLogSummaryFile(stagedAppLogDir)
                        details += "app log: ${appLogSrc.absolutePath}"
                        details += summarizeRuntimeLogFiles(stagedAppLogDir)
                        if (!sanitizeLogs) {
                            details += "log sanitization disabled by sensitive debug mode"
                        }
                    } else {
                        details += "app log missing"
                        details += "runtime log files: 0"
                    }

                    val crashLogSrc = StorageUtils.getCrashLogDir(context)
                    if (crashLogSrc != null && crashLogSrc.exists()) {
                        copyDirectory(
                            logTag = config.logTag,
                            source = crashLogSrc,
                            target = File(stagingDir, "app/crash"),
                            sanitizeFiles = sanitizeLogs,
                        )
                        details += "crash log: ${crashLogSrc.absolutePath}"
                    } else {
                        details += "crash log missing"
                    }

                    val lsposedCopied = copyLsposedLogs(
                        logTag = config.logTag,
                        stagingDir = stagingDir,
                        details = details,
                        allowSuFallback = mode.capturesRootHeavyDiagnostics,
                    )
                    if (!lsposedCopied) {
                        details += if (mode.capturesRootHeavyDiagnostics) {
                            "lsposed log missing or unreadable"
                        } else {
                            "lsposed log unavailable without direct access in standard mode"
                        }
                    }

                    captureLogcat(config.logTag, stagingDir, details, sanitizeLogs, mode)
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

    private fun copyLsposedLogs(
        logTag: String,
        stagingDir: File,
        details: MutableList<String>,
        allowSuFallback: Boolean,
    ): Boolean {
        val lsposedTargetRoot = File(stagingDir, "lsposed")
        var copied = false

        LSPOSED_LOG_DIRS.forEach { path ->
            val src = File(path)
            if (src.exists() && src.canRead()) {
                val target = File(lsposedTargetRoot, src.name)
                copyDirectory(logTag, src, target)
                val trimmed = trimLsposedRotatingLogs(logTag, target)
                if (trimmed > 0) {
                    details += "lsposed rotating logs trimmed: $trimmed"
                }
                details += "lsposed direct: $path"
                copied = true
            }
        }
        if (copied) return true

        if (!allowSuFallback) {
            details += "lsposed su fallback skipped in standard mode"
            return false
        }

        val targetPath = lsposedTargetRoot.absolutePath
        val targetUid = runCatching { android.os.Process.myUid() }.getOrDefault(-1)
        val shellCmd = buildString {
            append("mkdir -p ${shQuote(targetPath)}; ")
            LSPOSED_LOG_DIRS.forEach { path ->
                val name = File(path).name
                append("if [ -d ${shQuote(path)} ]; then ")
                append("cp -R ${shQuote(path)} ${shQuote("$targetPath/")} && ")
                append("chmod -R a+rX ${shQuote("$targetPath/$name")} ; ")
                if (targetUid > 0) {
                    append("chown -R $targetUid:$targetUid ${shQuote("$targetPath/$name")} ; ")
                }
                append("fi; ")
            }
        }
        val suResult = runSuCommand(shellCmd)
        if (suResult.exitCode == 0) {
            val hasAny = lsposedTargetRoot.exists() &&
                (lsposedTargetRoot.listFiles()?.isNotEmpty() == true)
            if (hasAny) {
                sanitizeDirectory(logTag, lsposedTargetRoot)
                val trimmed = trimLsposedRotatingLogs(logTag, lsposedTargetRoot)
                if (trimmed > 0) {
                    details += "lsposed rotating logs trimmed: $trimmed"
                }
                details += "lsposed copied via su"
                return true
            }
        }
        details += "lsposed su failed: ${suResult.stderr.ifBlank { suResult.stdout }.ifBlank { "unknown" }}"
        return false
    }

    private fun captureLogcat(
        logTag: String,
        stagingDir: File,
        details: MutableList<String>,
        sanitizeLogs: Boolean,
        mode: DiagnosticExportMode,
    ) {
        val logcatDir = File(stagingDir, "logcat")
        if (!ensureDirectory(logcatDir, recreateWhenFile = true)) return
        val output = File(logcatDir, "logcat_all.txt")
        val command = logcatCommand(mode)
        val ok = dumpCommandOutput(command, output, logTag, sanitizeLogs)
        if (ok) {
            details += if (mode.usesBoundedLogcat) {
                "logcat: direct bounded main,system,crash 24h"
            } else {
                "logcat: direct"
            }
            return
        }
        if (mode.capturesRootHeavyDiagnostics) {
            val okSu = dumpCommandOutput(listOf("su", "-c", "logcat -d -v threadtime -b all"), output, logTag, sanitizeLogs)
            if (okSu) {
                details += "logcat: su"
            }
        }
    }

    private fun dumpCommandOutput(
        command: List<String>,
        output: File,
        logTag: String,
        sanitizeLogs: Boolean,
    ): Boolean {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            output.outputStream().use { out ->
                process.inputStream.copyTo(out)
            }
            process.waitFor(6, TimeUnit.SECONDS)
            if (process.isAlive) {
                process.destroy()
            }
            if (sanitizeLogs) {
                sanitizeTextLogFile(output, logTag)
            }
            output.exists() && output.length() > 0
        }.getOrDefault(false)
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

    private data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runSuCommand(command: String): ShellResult = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        ShellResult(exitCode, stdout, stderr)
    } catch (e: java.io.IOException) {
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    } catch (e: SecurityException) {
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    }

    private fun copyDirectory(
        logTag: String,
        source: File,
        target: File,
        sanitizeFiles: Boolean = true,
        includeFile: (File) -> Boolean = { true },
    ) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source).path
            val dest = if (relative.isEmpty()) target else File(target, relative)
            if (file.isDirectory) {
                ensureDirectory(dest, recreateWhenFile = true)
            } else {
                if (!file.exists()) return@forEach
                if (!includeFile(file)) return@forEach
                val parent = dest.parentFile
                if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
                    logWarn(logTag, "Skip copy due to invalid parent dir: ${dest.absolutePath}")
                    return@forEach
                }
                runCatching {
                    file.copyTo(dest, overwrite = true)
                    if (sanitizeFiles) {
                        sanitizeTextLogFile(dest, logTag)
                    }
                }.onFailure {
                    logWarn(logTag, "Skip copy file failed: src=${file.absolutePath} dst=${dest.absolutePath}")
                }
            }
        }
    }

    private fun sanitizeDirectory(logTag: String, dir: File) {
        DiagnosticTextSanitizer.sanitizeDirectory(
            root = dir,
            sanitizeText = DefaultLogSanitizer::sanitizeUnbounded,
            onWarning = { warning -> logWarn(logTag, warning) },
        )
    }

    private fun sanitizeTextLogFile(file: File, logTag: String) {
        DiagnosticTextSanitizer.sanitizeTextLogFile(
            file = file,
            sanitizeText = DefaultLogSanitizer::sanitizeUnbounded,
            onWarning = { warning -> logWarn(logTag, warning) },
        )
    }

    internal fun isRedundantAppRouteRuntimeLog(file: File): Boolean {
        return file.isFile && redundantAppRouteRuntimeLogPattern.matches(file.name)
    }

    internal fun trimLsposedRotatingLogs(logTag: String, root: File): Int {
        if (!root.exists() || !root.isDirectory) return 0
        var deleted = 0
        root.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val kind = lsposedRotatingLogPattern.matchEntire(file.name)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: return@mapNotNull null
                "${file.parentFile?.absolutePath.orEmpty()}:$kind" to file
            }
            .groupBy({ it.first }, { it.second })
            .values
            .forEach { files ->
                files.sortedWith(compareByDescending<File> { it.name }.thenByDescending { it.lastModified() })
                    .drop(LSPOSED_ROTATING_LOGS_PER_KIND)
                    .forEach { file ->
                        if (deleteRecursivelyWithSuFallback(logTag, file)) {
                            deleted += 1
                        }
                    }
            }
        return deleted
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
                if (!deleteRecursivelyWithSuFallback(logTag, child)) {
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

    private fun deleteRecursivelyWithSuFallback(logTag: String, target: File): Boolean {
        if (!target.exists()) return true
        if (target.deleteRecursively()) return true
        val suResult = runSuCommand("rm -rf ${shQuote(target.absolutePath)}")
        val deleted = !target.exists()
        if (!deleted) {
            logWarn(logTag, "su rm fallback failed: path=${target.absolutePath} exit=${suResult.exitCode}")
        }
        return deleted
    }

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

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
