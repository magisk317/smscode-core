package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LogBundleExportResult(
    val file: File?,
    val details: String,
)

data class LogBundleClearResult(
    val success: Boolean,
    val details: String,
)

object LogBundleExporter {
    private const val ZIP_MIME_TYPE = "application/zip"
    private val LSPOSED_LOG_DIRS = listOf(
        "/data/adb/lspd/log",
        "/data/adb/lspd/log.old",
    )
    private val opLock = Any()

    @Suppress("TooGenericExceptionCaught")
    fun buildLogBundle(context: Context): LogBundleExportResult {
        synchronized(opLock) {
            val config = RuntimeDiagnosticsEnvironment.current()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val exportDir = getPrivateExportDir(context)
            if (!ensureDirectory(exportDir, recreateWhenFile = true)) {
                val details = "export root unavailable: ${exportDir.absolutePath}"
                logError(config.logTag, "buildLogBundle failed: $details")
                return LogBundleExportResult(null, details)
            }
            val stagingDir = File(exportDir, "${config.stagingDirPrefix}$timestamp").apply {
                if (exists()) deleteRecursively()
            }
            if (!ensureDirectory(stagingDir, recreateWhenFile = true)) {
                val details = "staging dir unavailable: ${stagingDir.absolutePath}"
                logError(config.logTag, "buildLogBundle failed: $details")
                return LogBundleExportResult(null, details)
            }
            val details = mutableListOf<String>()

            try {
                val appLogSrc = StorageUtils.getLogDir(context)
                if (appLogSrc != null && appLogSrc.exists()) {
                    val stagedAppLogDir = File(stagingDir, "app/log")
                    copyDirectory(config.logTag, appLogSrc, stagedAppLogDir)
                    details += "app log: ${appLogSrc.absolutePath}"
                    details += summarizeRuntimeLogFiles(stagedAppLogDir)
                } else {
                    details += "app log missing"
                    details += "runtime log files: 0"
                }

                val crashLogSrc = StorageUtils.getCrashLogDir(context)
                if (crashLogSrc != null && crashLogSrc.exists()) {
                    copyDirectory(config.logTag, crashLogSrc, File(stagingDir, "app/crash"))
                    details += "crash log: ${crashLogSrc.absolutePath}"
                } else {
                    details += "crash log missing"
                }

                val lsposedCopied = copyLsposedLogs(config.logTag, stagingDir, details)
                if (!lsposedCopied) {
                    details += "lsposed log missing or unreadable"
                }

                captureLogcat(stagingDir, details)

                File(stagingDir, "summary.txt").writeText(
                    buildString {
                        appendLine("Export Time: $timestamp")
                        appendLine("Package: ${context.packageName}")
                        details.forEach { appendLine("- $it") }
                    },
                )

                val zipFile = File(exportDir, "${config.exportFilePrefix}$timestamp.zip")
                zipDirectory(config.logTag, stagingDir, zipFile)
                StorageUtils.setFileWorldReadable(zipFile, 1)
                logInfo(config.logTag, "buildLogBundle success: file=${zipFile.absolutePath} size=${zipFile.length()}")
                return LogBundleExportResult(zipFile, details.joinToString("; "))
            } catch (t: Throwable) {
                logError(config.logTag, "buildLogBundle failed: ${t.message ?: t.javaClass.simpleName}")
                return LogBundleExportResult(null, t.message ?: t.javaClass.simpleName)
            } finally {
                runCatching {
                    if (!deleteRecursivelyWithSuFallback(config.logTag, stagingDir)) {
                        logWarn(config.logTag, "Failed to cleanup staging dir: ${stagingDir.absolutePath}")
                    }
                }
            }
        }
    }

    fun shareLogBundle(context: Context, file: File) {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            throw IllegalStateException("share file unavailable: ${file.absolutePath}")
        }
        val authority = context.packageName + ".files"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        val resolvedTargets = context.packageManager.queryIntentActivities(intent, 0)
        resolvedTargets.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            runCatching {
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
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

            return LogBundleClearResult(success = success, details = details.joinToString("; "))
        }
    }

    private fun copyLsposedLogs(logTag: String, stagingDir: File, details: MutableList<String>): Boolean {
        val lsposedTargetRoot = File(stagingDir, "lsposed")
        var copied = false

        LSPOSED_LOG_DIRS.forEach { path ->
            val src = File(path)
            if (src.exists() && src.canRead()) {
                val target = File(lsposedTargetRoot, src.name)
                copyDirectory(logTag, src, target)
                details += "lsposed direct: $path"
                copied = true
            }
        }
        if (copied) return true

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
                details += "lsposed copied via su"
                return true
            }
        }
        details += "lsposed su failed: ${suResult.stderr.ifBlank { suResult.stdout }.ifBlank { "unknown" }}"
        return false
    }

    private fun captureLogcat(stagingDir: File, details: MutableList<String>) {
        val logcatDir = File(stagingDir, "logcat")
        if (!ensureDirectory(logcatDir, recreateWhenFile = true)) return
        val output = File(logcatDir, "logcat_all.txt")
        val command = listOf("logcat", "-d", "-v", "threadtime", "-b", "all")
        val ok = dumpCommandOutput(command, output)
        if (ok) {
            details += "logcat: direct"
            return
        }
        val okSu = dumpCommandOutput(listOf("su", "-c", "logcat -d -v threadtime -b all"), output)
        if (okSu) {
            details += "logcat: su"
        }
    }

    private fun dumpCommandOutput(command: List<String>, output: File): Boolean {
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
            output.exists() && output.length() > 0
        }.getOrDefault(false)
    }

    private fun summarizeRuntimeLogFiles(stagedAppLogDir: File): String {
        val runtimeFiles = stagedAppLogDir.listFiles().orEmpty()
            .filter { file ->
                file.isFile && (file.name == "runtime.log" || file.name.startsWith("runtime."))
            }
            .sortedBy { it.name }
        if (runtimeFiles.isEmpty()) return "runtime log files: 0"
        val names = runtimeFiles.joinToString(", ") { "${it.name}(${it.length()}B)" }
        return "runtime log files: ${runtimeFiles.size} [$names]"
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

    private fun copyDirectory(logTag: String, source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source).path
            val dest = if (relative.isEmpty()) target else File(target, relative)
            if (file.isDirectory) {
                ensureDirectory(dest, recreateWhenFile = true)
            } else {
                if (!file.exists()) return@forEach
                val parent = dest.parentFile
                if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
                    logWarn(logTag, "Skip copy due to invalid parent dir: ${dest.absolutePath}")
                    return@forEach
                }
                runCatching {
                    file.copyTo(dest, overwrite = true)
                }.onFailure {
                    logWarn(logTag, "Skip copy file failed: src=${file.absolutePath} dst=${dest.absolutePath}")
                }
            }
        }
    }

    private fun zipDirectory(logTag: String, sourceDir: File, outputZip: File) {
        FileOutputStream(outputZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                sourceDir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        runCatching {
                            val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                            zos.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }.onFailure {
                            logWarn(logTag, "Skip zipping unreadable file: ${file.absolutePath}")
                        }
                    }
            }
        }
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
        if (dir.exists()) {
            if (dir.isDirectory) return true
            if (!recreateWhenFile) return false
            if (!dir.delete()) {
                return false
            }
        }
        if (!dir.mkdirs() && !dir.exists()) {
            return false
        }
        return dir.isDirectory
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
