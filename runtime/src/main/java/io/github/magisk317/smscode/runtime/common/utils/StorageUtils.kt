package io.github.magisk317.smscode.runtime.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.system.Os
import java.io.File
import java.nio.file.Files

/**
 * Utils for storage.
 */
object StorageUtils {

    private const val LOG_DIR_NAME = "log"
    private const val CRASH_DIR_NAME = "crash"
    private const val PRIVATE_LOG_EXPORT_DIR_NAME = "xsms_logs"

    @JvmStatic
    fun isSDCardMounted(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    /**
     * 获取日志路径
     */
    @JvmStatic
    fun getLogDir(context: Context): File? = ensurePrivateSubDir(context, LOG_DIR_NAME)

    /**
     * 获取Crash日志路径
     */
    @JvmStatic
    fun getCrashLogDir(context: Context): File? = ensurePrivateSubDir(context, CRASH_DIR_NAME)

    /**
     * 获取日志导出目录（应用私有）
     */
    @JvmStatic
    fun getPrivateLogExportDir(context: Context): File = ensurePrivateSubDir(
        context,
        PRIVATE_LOG_EXPORT_DIR_NAME,
    ) ?: File(context.filesDir, PRIVATE_LOG_EXPORT_DIR_NAME)

    @JvmStatic
    fun getPublicDocumentsDir(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir

    @JvmStatic
    fun getInternalDataDir(context: Context): File = context.dataDir

    @JvmStatic
    fun getInternalFilesDir(context: Context): File = File(getInternalDataDir(context), "files")

    @JvmStatic
    fun getExternalFilesDir(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir

    @JvmStatic
    fun getFilesDir(context: Context): File {
        val externalFilesDir = getExternalFilesDir(context)
        if (!externalFilesDir.exists()) {
            externalFilesDir.mkdirs()
        }
        return externalFilesDir
    }

    /**
     * Ensure external app-specific data directory has correct permissions.
     * Checks current permission mode first to avoid unnecessary disk I/O on cold start.
     */
    @JvmStatic
    fun ensureExternalAppDataPermissions(context: Context): Boolean {
        val externalFilesDir = runCatching { context.getExternalFilesDir(null) }.getOrNull()
            ?: run {
                android.util.Log.e("XSmsCode", "ensureExternalAppDataPermissions: getExternalFilesDir returned null")
                return false
            }
        val packageDir = externalFilesDir.parentFile ?: run {
            android.util.Log.e("XSmsCode", "ensureExternalAppDataPermissions: parentFile is null")
            return false
        }
        if (!isAndroidAppDataPath(packageDir) || externalFilesDir.name != "files") {
            android.util.Log.w("XSmsCode", "ensureExternalAppDataPermissions: not AndroidAppDataPath or not files: ${packageDir.absolutePath}")
            return false
        }

        val pkgExpected = isExpectedMode(packageDir, DIRECTORY_MODE)
        val filesExpected = isExpectedMode(externalFilesDir, DIRECTORY_MODE)
        if (pkgExpected && filesExpected) {
            android.util.Log.i("XSmsCode", "ensureExternalAppDataPermissions: already 02770, skipping repair")
            return true
        }

        android.util.Log.w("XSmsCode", "ensureExternalAppDataPermissions: unexpected modes (pkg=$pkgExpected, files=$filesExpected), triggering repair")
        return repairExternalAppDataPermissions(context)
    }

    /**
     * Restore the mode Android assigns to an app-specific external-storage tree.
     *
     * Older releases called [setFileWorldWritable] on `files/` and its parent,
     * which changed both directories to `0777` and also changed every snapshot
     * written through the same helper to `0777`.  The app UID owns this tree, so
     * the migration can be performed without changing ownership.  Do not use
     * `File.set*` here: those setters cannot restore the setgid bit that the
     * media provider uses (`02770`).
     */
    @JvmStatic
    fun repairExternalAppDataPermissions(context: Context): Boolean {
        val externalFilesDir = runCatching { context.getExternalFilesDir(null) }.getOrNull()
            ?: return false
        val packageDir = externalFilesDir.parentFile ?: return false
        if (!isAndroidAppDataPath(packageDir) || externalFilesDir.name != "files") {
            return false
        }

        val treeRepaired = normalizeExternalTree(externalFilesDir)
        val pkgRepaired = applyMode(packageDir, DIRECTORY_MODE)

        // If in-process chmod cannot restore the setgid bit due to app sandbox constraints,
        // attempt root repair as a fallback if root access is available.
        if (!isExpectedMode(packageDir, DIRECTORY_MODE) || !isExpectedMode(externalFilesDir, DIRECTORY_MODE)) {
            val rootOk = tryRootRepair(packageDir, externalFilesDir)
            android.util.Log.i(
                "XSmsCode",
                "repairExternalAppDataPermissions: in-process missing setgid, tried root repair result=$rootOk",
            )
            return rootOk
        }

        android.util.Log.i("XSmsCode", "repairExternalAppDataPermissions: done. treeRepaired=$treeRepaired, pkgRepaired=$pkgRepaired")
        return pkgRepaired && treeRepaired
    }

    private fun tryRootRepair(packageDir: File, externalFilesDir: File): Boolean = runCatching {
        val canonicalPkg = packageDir.canonicalPath
        val canonicalFiles = externalFilesDir.canonicalPath
        val mediaPkg = canonicalPkg.replace("/storage/emulated/", "/data/media/")
        val mediaFiles = canonicalFiles.replace("/storage/emulated/", "/data/media/")

        val script = buildString {
            append("chmod 2770 \"$canonicalPkg\" \"$canonicalFiles\" 2>/dev/null ; ")
            append("chmod 2770 \"$mediaPkg\" \"$mediaFiles\" 2>/dev/null ; ")
            append("find \"$canonicalFiles\" -type d -exec chmod 2770 {} + 2>/dev/null ; ")
            append("find \"$mediaFiles\" -type d -exec chmod 2770 {} + 2>/dev/null ; ")
            append("find \"$canonicalFiles\" -type f -exec chmod 660 {} + 2>/dev/null ; ")
            append("find \"$mediaFiles\" -type f -exec chmod 660 {} + 2>/dev/null ; ")
            append("exit\n")
        }

        val process = Runtime.getRuntime().exec("su")
        val os = java.io.DataOutputStream(process.outputStream)
        os.writeBytes(script)
        os.flush()
        val exitCode = process.waitFor()
        val pkgOk = isExpectedMode(packageDir, DIRECTORY_MODE)
        val filesOk = isExpectedMode(externalFilesDir, DIRECTORY_MODE)
        android.util.Log.i(
            "XSmsCode",
            "tryRootRepair executed: exitCode=$exitCode pkgOk=$pkgOk filesOk=$filesOk",
        )
        exitCode == 0 && pkgOk && filesOk
    }.getOrElse { e ->
        android.util.Log.w("XSmsCode", "tryRootRepair failed with exception: ${e.message}")
        false
    }

    /**
     * Set file world writable
     */
    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    @JvmStatic
    fun setFileWorldWritable(file: File, parentDepth: Int) {
        var currentFile: File? = file
        if (currentFile == null || !currentFile.exists()) {
            return
        }
        if (isAndroidAppDataPath(currentFile)) {
            // Keep the old call sites source-compatible while preventing a
            // world-writable Android/data tree.  A directory invocation is the
            // historical `files/` migration trigger; normalize the complete
            // subtree so already-written 0777 snapshots are repaired too.
            if (currentFile.isDirectory) {
                normalizeExternalTree(currentFile)
                repeat(parentDepth.coerceAtLeast(0)) {
                    currentFile = currentFile?.parentFile
                    val parent = currentFile ?: return@repeat
                    applyMode(parent, DIRECTORY_MODE)
                }
            } else {
                applyMode(currentFile, FILE_MODE)
                repeat(parentDepth.coerceAtLeast(0)) {
                    currentFile = currentFile?.parentFile
                    val parent = currentFile ?: return@repeat
                    applyMode(parent, DIRECTORY_MODE)
                }
            }
            return
        }
        if (currentFile.isDirectory) {
            // The only directory-level world-writable call in the consumers is
            // the legacy settings migration.  Never widen an app-private
            // directory when external storage is unavailable.
            repeat((parentDepth + 1).coerceAtLeast(0)) {
                currentFile?.setExecutable(true, true)
                currentFile?.setWritable(true, true)
                currentFile?.setReadable(true, true)
                currentFile = currentFile?.parentFile
            }
            return
        }
        val actualDepth = parentDepth + 1
        for (i in 0 until actualDepth) {
            currentFile?.setExecutable(true, false)
            currentFile?.setWritable(true, false)
            currentFile?.setReadable(true, false)
            currentFile = currentFile?.parentFile
            if (currentFile == null) {
                break
            }
        }
    }

    /**
     * Set file world readable
     */
    @SuppressLint("SetWorldReadable")
    @JvmStatic
    fun setFileWorldReadable(file: File, parentDepth: Int) {
        var currentFile: File? = file
        if (currentFile == null || !currentFile.exists()) {
            return
        }
        if (isAndroidAppDataPath(currentFile)) {
            applyMode(currentFile, if (currentFile.isDirectory) DIRECTORY_MODE else FILE_MODE)
            repeat(parentDepth.coerceAtLeast(0)) {
                currentFile = currentFile?.parentFile
                val parent = currentFile ?: return@repeat
                applyMode(parent, DIRECTORY_MODE)
            }
            return
        }
        for (i in 0 until parentDepth) {
            currentFile?.setReadable(true, false)
            currentFile?.setExecutable(true, false)
            currentFile = currentFile?.parentFile
            if (currentFile == null) {
                break
            }
        }
    }

    private fun ensurePrivateSubDir(context: Context, name: String): File? {
        val baseDir = runCatching { context.filesDir }.getOrNull() ?: return null
        val dir = File(baseDir, name)
        if (!dir.exists() && !dir.mkdirs()) {
            return null
        }
        return dir
    }

    private fun normalizeExternalTree(root: File): Boolean {
        if (runCatching { Files.isSymbolicLink(root.toPath()) }.getOrDefault(true)) return true
        var ok = true
        root.listFiles()?.forEach { child ->
            if (runCatching { Files.isSymbolicLink(child.toPath()) }.getOrDefault(true)) return@forEach
            if (child.isDirectory) {
                ok = normalizeExternalTree(child) && ok
            } else {
                ok = applyMode(child, FILE_MODE) && ok
            }
        }
        return applyMode(root, DIRECTORY_MODE) && ok
    }

    private fun applyMode(file: File, mode: Int): Boolean = runCatching {
        Os.chmod(file.absolutePath, mode)
        true
    }.getOrElse { false }

    internal fun isExpectedMode(file: File, expectedMode: Int): Boolean = runCatching {
        val stat = Os.stat(file.absolutePath)
        (stat.st_mode and PERMISSION_MASK) == expectedMode
    }.getOrDefault(false)

    internal fun isAndroidAppDataPath(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        val segments = path.split(File.separatorChar).filter(String::isNotEmpty)
        val androidIndex = segments.indexOfLast { it == "Android" }
        return androidIndex >= 0 && androidIndex + 2 < segments.size &&
            segments[androidIndex + 1] == "data"
    }

    // Android MediaProvider / FUSE external app-specific storage standards:
    // Directory: 02770 (S_ISGID | S_IRWXU | S_IRWXG) -> 1528
    internal const val DIRECTORY_MODE = 0b010_111_111_000
    // File: 0660 (S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP) -> 432
    internal const val FILE_MODE = 0b000_110_110_000
    // Standard Unix file permission bits mask (special bits + rwxrwxrwx) -> 07777 -> 4095
    private const val PERMISSION_MASK = 0b111_111_111_111
}

