package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import io.github.magisk317.xposed.logging.MagiskOtel
import android.util.Log
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleIdentity
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleNames
import io.github.magisk317.xposed.diagnostics.DiagnosticBuildStamps
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import io.github.magisk317.xposed.diagnostics.DiagnosticShell
import io.github.magisk317.smscode.runtime.common.packageenv.PackageEnvCore
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.xposed.diagnostics.DiagnosticArchive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class LogBundleExportResult(
    val file: File?,
    val details: String,
)

data class LogBundleClearResult(
    val success: Boolean,
    val details: String,
)

internal data class RuntimePackageIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val distributionChannel: String,
    val commitSha: String,
    val apkPath: String,
    val apkSha256: String,
) {
    fun toSummaryLine(): String {
        return "app package=$packageName versionName=$versionName versionCode=$versionCode " +
            "distributionChannel=$distributionChannel commitSha=$commitSha " +
            "apkPath=${apkPath.ifBlank { "unavailable" }} apkSha256=$apkSha256"
    }
}

object LogBundleExporter {

    private val legacyTextLogPattern = Regex("""^(logs_\d{4}-\d{2}-\d{2}|runtime(?:\.[A-Za-z0-9_.-]+)?)\.(txt|log)$""")
    private val legacyModuleTextLogPattern = Regex("""^[A-Za-z0-9_.-]+_\d{4}-\d{2}-\d{2}\.txt$""")
    private val opLock = Any()

    const val BLOCK_REASON_NEED_ROOT = "uikit_log_export_need_root"
    private const val HASH_BUFFER_SIZE = 64 * 1024
    private const val HEX_BYTE_MASK = 0xff
    private const val CORE_LIBRARY_NAME = "smscode-core"
    private const val CORE_BUILD_CONFIG = "io.github.magisk317.smscode.runtime.common.BuildConfig"


    /**
     * Root gate for log export: the bundle pulls LSPosed/module log dirs under
     * /data, so a non-root device can never produce a usable archive.
     *
     * @return a block reason key, or null when export may proceed.
     */
    fun checkPreExport(): String? =
        if (!PackageEnvCore.hasRootAccess()) BLOCK_REASON_NEED_ROOT else null

    @Suppress("TooGenericExceptionCaught")
    fun buildLogBundle(
        context: Context,
        mode: DiagnosticExportMode = DiagnosticExportMode.current(),
    ): LogBundleExportResult {
        checkPreExport()?.let { reason ->
            return LogBundleExportResult(file = null, details = reason)
        }
        synchronized(opLock) {
            val config = RuntimeDiagnosticsEnvironment.current()
            val packageIdentity = readPackageIdentity(context, config)
            // A host cannot forward this module's own stamp: core depends on the kit with
            // `implementation`, so its BuildConfig is invisible to the app that embeds core.
            val libraryCommitShas = config.libraryCommitShas +
                (CORE_LIBRARY_NAME to DiagnosticBuildStamps.moduleCommitHash(CORE_BUILD_CONFIG))
            val identity = DiagnosticBundleIdentity.of(
                product = config.product,
                packageName = packageIdentity.packageName,
                versionName = packageIdentity.versionName,
                versionCode = packageIdentity.versionCode,
                distributionChannel = packageIdentity.distributionChannel,
                commitSha = packageIdentity.commitSha,
                libraryCommitShas = libraryCommitShas,
            )
            val timestamp = DiagnosticBundleNames.exportTimestamp()
            val exportDir = getPrivateExportDir(context, config)
            val result = DiagnosticArchive.buildBundle(
                identity = identity,
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
                    details += packageIdentity.toSummaryLine()
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
                            includeFile = { file -> !RuntimeLogStore.isRedundantRoutedRuntimeLog(file) },
                            onWarning = { message -> logWarn(config.logTag, message) },
                        )
                        writeRuntimeLogSummaryFile(stagedAppLogDir, packageIdentity, identity)
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
                        stagingDir = stagingDir,
                        details = details,
                        mode = mode,
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

    fun clearLogFolders(context: Context): LogBundleClearResult {
        synchronized(opLock) {
            val details = mutableListOf<String>()
            var success = true
            val config = RuntimeDiagnosticsEnvironment.current()

            RuntimeLogStore.clear()

            val targets = listOf(
                "log" to StorageUtils.getLogDir(context),
                "crash" to StorageUtils.getCrashLogDir(context),
                "private_export" to getPrivateExportDir(context, config),
            ) + StorageUtils.getLegacyPrivateLogExportDirs(context, config.privateExportDirName)
                .map { "legacy_export:${it.name}" to it }

            targets.forEach { (name, dir) ->
                if (dir == null) {
                    success = false
                    details += "$name unavailable"
                    return@forEach
                }
                val ok = clearDirectoryContents(config.logTag, dir)
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

    private fun writeRuntimeLogSummaryFile(
        stagedAppLogDir: File,
        packageIdentity: RuntimePackageIdentity,
        identity: DiagnosticBundleIdentity,
    ) {
        runCatching {
            val summary = RuntimeLogStore.summarizeFiles()
            val content = buildRuntimeSummaryJson(summary, packageIdentity, identity)
            File(stagedAppLogDir, "runtime_summary.json").writeText(content.toString())
        }
    }

    internal fun buildRuntimeSummaryJson(
        summary: RuntimeLogFileSummary,
        packageIdentity: RuntimePackageIdentity,
        identity: DiagnosticBundleIdentity,
    ): JsonObject {
        return buildJsonObject {
            identity.jsonFields().forEach { (key, value) -> put(key, value) }
            put("apkPath", packageIdentity.apkPath)
            put("apkSha256", packageIdentity.apkSha256)
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
    }

    internal fun readPackageIdentity(
        context: Context,
        config: RuntimeDiagnosticsConfig,
    ): RuntimePackageIdentity {
        val packageName = context.packageName
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.getOrElse { error ->
            return RuntimePackageIdentity(
                packageName = packageName,
                versionName = "unavailable",
                versionCode = "unavailable",
                distributionChannel = config.distributionChannel,
                commitSha = config.commitHash,
                apkPath = "",
                apkSha256 = "unavailable:${error.javaClass.simpleName}",
            )
        }
        val apkPath = packageInfo.applicationInfo?.sourceDir.orEmpty()
        return RuntimePackageIdentity(
            packageName = packageName,
            versionName = packageInfo.versionName.orEmpty().ifBlank { "unavailable" },
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
            distributionChannel = config.distributionChannel,
            commitSha = config.commitHash,
            apkPath = apkPath,
            apkSha256 = sha256FileOrUnavailable(apkPath),
        )
    }

    internal fun sha256FileOrUnavailable(path: String): String {
        if (path.isBlank()) return "unavailable:path_missing"
        val file = File(path)
        if (!file.isFile) return "unavailable:file_missing"
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(HASH_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString(separator = "") { byte ->
                (byte.toInt() and HEX_BYTE_MASK).toString(16).padStart(2, '0')
            }
        }.getOrElse { error ->
            "unavailable:${error.javaClass.simpleName}"
        }
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

    private fun getPrivateExportDir(context: Context, config: RuntimeDiagnosticsConfig): File {
        return StorageUtils.getPrivateLogExportDir(context, config.privateExportDirName)
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
