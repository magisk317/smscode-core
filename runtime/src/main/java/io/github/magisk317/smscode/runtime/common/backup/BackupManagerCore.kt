package io.github.magisk317.smscode.runtime.common.backup

import io.github.magisk317.smscode.runtime.contract.backup.BackupConst

import io.github.magisk317.smscode.runtime.contract.backup.BackupImportResult
import io.github.magisk317.smscode.runtime.contract.backup.BackupPayload
import io.github.magisk317.smscode.runtime.contract.backup.BackupRule
import io.github.magisk317.smscode.runtime.contract.backup.BackupSmsRecord
import io.github.magisk317.smscode.runtime.contract.backup.ExportResult
import io.github.magisk317.smscode.runtime.contract.backup.ImportResult
import io.github.magisk317.smscode.runtime.contract.backup.ImportWarning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.PushbackInputStream
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import io.github.magisk317.xposed.logging.MagiskOtel

data class BackupManagerConfig(
    val backupDirectoryName: String,
    val backupFileNamePrefix: String,
    val databaseFileName: String,
    val fileProviderAuthority: String,
)

data class BackupDatabaseHooks(
    val beforeDatabaseSnapshot: (Context) -> Unit = {},
    val beforeDatabaseRestore: (Context) -> Unit = {},
    val afterDatabaseRestore: (Context) -> Unit = {},
)

object BackupManagerCore {
    private const val BACKUP_FILE_EXTENSION = ".scebak"
    private const val BACKUP_ZIP_EXTENSION = ".zip"
    private const val BACKUP_MIME_TYPE = "application/json"
    private const val BACKUP_ZIP_MIME_TYPE = "application/zip"
    private const val BACKUP_PAYLOAD_ENTRY = "backup.scebak"
    private const val LOG_TAG = "BackupManagerCore"

    @JvmStatic
    fun getBackupDir(context: Context, config: BackupManagerConfig): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(baseDir, config.backupDirectoryName)
    }

    @JvmStatic
    fun getBackupFileExtension(): String = BACKUP_FILE_EXTENSION

    @JvmStatic
    fun getDefaultBackupFilename(
        context: Context,
        config: BackupManagerConfig,
        includeDatabase: Boolean = false,
    ): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val backupDir = getBackupDir(context, config)
        val existingNames = backupDir.list()?.toSet().orEmpty()
        return buildBackupFilename(config, dateStr, includeDatabase, existingNames)
    }

    internal fun buildBackupFilename(
        config: BackupManagerConfig,
        dateStr: String,
        includeDatabase: Boolean,
        existingNames: Set<String>,
    ): String {
        val suffix = if (includeDatabase) "-db" else ""
        val basename = "${config.backupFileNamePrefix}${dateStr}-schema${BackupConst.BACKUP_VERSION}$suffix"
        val extension = if (includeDatabase) BACKUP_ZIP_EXTENSION else BACKUP_FILE_EXTENSION
        var filename = basename + extension
        var index = 2
        while (filename in existingNames) {
            filename = "$basename-$index$extension"
            index++
        }
        return filename
    }

    @JvmStatic
    fun getBackupFiles(context: Context, config: BackupManagerConfig): Array<File>? {
        val backupDir = getBackupDir(context, config)
        if (!backupDir.exists()) return null
        val files = backupDir.listFiles { _, name -> name.endsWith(BACKUP_FILE_EXTENSION) }

        if (files != null) {
            Arrays.sort(files) { f1, f2 ->
                val s1 = f1.name
                val s2 = f2.name
                val extLength = BACKUP_FILE_EXTENSION.length
                val n1 = s1.substring(0, s1.length - extLength)
                val n2 = s2.substring(0, s2.length - extLength)
                n1.compareTo(n2)
            }
        }
        return files
    }

    @JvmStatic
    fun exportRuleList(file: File, ruleList: List<BackupRule>, appVersion: String): ExportResult {
        val parentFile = file.parentFile
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs()
        }

        return try {
            RuleExporter(file).use { exporter ->
                exporter.doExport(ruleList, appVersion)
                ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Export backup rules failed", e)
            ExportResult.FAILED
        }
    }

    @JvmStatic
    fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String,
        config: BackupManagerConfig,
        hooks: BackupDatabaseHooks = BackupDatabaseHooks(),
        includeDatabase: Boolean = false,
    ): ExportResult {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "export_backup",
                "include_database" to includeDatabase.toString(),
                "rule_count" to ruleList.size.toString(),
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "prefs.backup", attributes = attrs, statusOk = statusOk)
        }

        Log.i(
            LOG_TAG,
            "exportBackup start: " +
                "uri=$uri " +
                "rules=${ruleList.size} " +
                "prefs=${preferences?.size ?: 0} " +
                "records=${records?.size ?: 0} " +
                "appVersion=$appVersion " +
                "includeDatabase=$includeDatabase",
        )
        if (includeDatabase) {
            return try {
                exportBackupZipWithDatabase(context, uri, ruleList, preferences, records, appVersion, config, hooks)
                Log.i(LOG_TAG, "exportBackup success (zip with database)")
                emit(result = "ok", reason = "zip_with_database")
                ExportResult.SUCCESS
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Export backup(zip) failed", e)
                emit(result = "error", statusOk = false, reason = "IOException")
                ExportResult.FAILED
            } catch (e: IllegalStateException) {
                Log.e(LOG_TAG, "Export backup(zip) failed", e)
                emit(result = "error", statusOk = false, reason = "IllegalStateException")
                ExportResult.FAILED
            }
        }
        return try {
            RuleExporter(context.contentResolver.openOutputStream(uri)).use { exporter ->
                exporter.doExport(ruleList, appVersion, preferences, records)
                Log.i(LOG_TAG, "exportBackup success")
                emit(result = "ok", reason = "rules_only")
                ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Export backup failed", e)
            emit(result = "error", statusOk = false, reason = "IOException")
            ExportResult.FAILED
        }
    }

    @JvmStatic
    fun exportRuleList(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        appVersion: String,
        config: BackupManagerConfig,
    ): ExportResult = exportBackup(context, uri, ruleList, null, null, appVersion, config)

    @JvmStatic
    fun getExportRuleListSAFIntent(
        context: Context,
        config: BackupManagerConfig,
        includeDatabase: Boolean = false,
    ): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (includeDatabase) BACKUP_ZIP_MIME_TYPE else BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename(context, config, includeDatabase))
        }
    }

    @JvmStatic
    fun importRuleList(context: Context, uri: Uri, currentAppVersion: String): BackupImportResult {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "import_rules",
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "prefs.restore", attributes = attrs, statusOk = statusOk)
        }

        var ruleImporter: RuleImporter? = null
        try {
            Log.i(LOG_TAG, "importRuleList start: uri=$uri")
            val payloadBytes = readPayloadBytes(context, uri)
            if (payloadBytes == null) {
                emit(result = "error", statusOk = false, reason = "read_failed")
                return BackupImportResult(ImportResult.READ_FAILED)
            }
            ruleImporter = RuleImporter(ByteArrayInputStream(payloadBytes))
            val payload = ruleImporter.parsePayload()
            val schemaVersion = payload.schemaVersion
            Log.i(
                LOG_TAG,
                "importRuleList parsed: " +
                    "schema=$schemaVersion " +
                    "appVersion=${payload.appVersion} " +
                    "rules=${payload.rules.size} " +
                    "prefs=${payload.preferences?.size ?: 0} " +
                    "records=${payload.records?.size ?: 0}",
            )
            if (schemaVersion > BackupConst.BACKUP_VERSION) {
                emit(result = "skip", reason = "version_too_new")
                return BackupImportResult(ImportResult.VERSION_TOO_NEW)
            }
            if (schemaVersion < BackupConst.BACKUP_VERSION) {
                emit(result = "skip", reason = "version_too_old")
                return BackupImportResult(ImportResult.VERSION_TOO_OLD)
            }
            val warning = resolveWarning(payload.appVersion, currentAppVersion)
            emit(
                result = "ok",
                reason = if (warning != null) "success_with_warning" else "success",
            )
            return BackupImportResult(
                ImportResult.SUCCESS,
                payload.rules,
                payload.preferences,
                payload.records,
                warning,
            )
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error occurs in importRuleList", e)
            emit(result = "error", statusOk = false, reason = "IOException")
            return BackupImportResult(ImportResult.READ_FAILED)
        } catch (e: VersionMissedException) {
            Log.e(LOG_TAG, "Error occurs in importRuleList", e)
            emit(result = "error", statusOk = false, reason = "version_missed")
            return BackupImportResult(ImportResult.VERSION_MISSED)
        } catch (e: VersionInvalidException) {
            Log.e(LOG_TAG, "Error occurs in importRuleList", e)
            emit(result = "error", statusOk = false, reason = "version_unknown")
            return BackupImportResult(ImportResult.VERSION_UNKNOWN)
        } catch (e: BackupInvalidException) {
            Log.e(LOG_TAG, "Error occurs in importRuleList", e)
            emit(result = "error", statusOk = false, reason = "backup_invalid")
            return BackupImportResult(ImportResult.BACKUP_INVALID)
        } finally {
            ruleImporter?.close()
        }
    }

    @JvmStatic
    fun getImportRuleListSAFIntent(
        context: Context,
        config: BackupManagerConfig,
    ): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(BACKUP_MIME_TYPE, BACKUP_ZIP_MIME_TYPE))
            putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename(context, config))
        }
    }

    @JvmStatic
    fun restoreDatabaseFromBackup(
        context: Context,
        uri: Uri,
        config: BackupManagerConfig,
        hooks: BackupDatabaseHooks = BackupDatabaseHooks(),
    ): Boolean {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "restore_database",
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "prefs.restore", attributes = attrs, statusOk = statusOk)
        }

        context.contentResolver.openInputStream(uri)?.use { raw ->
            val pb = PushbackInputStream(BufferedInputStream(raw), 4)
            val header = ByteArray(4)
            val readCount = pb.read(header)
            if (readCount > 0) {
                pb.unread(header, 0, readCount)
            }
            val isZip = readCount == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte()
            if (!isZip) {
                Log.w(LOG_TAG, "restoreDatabaseFromBackup skipped: not zip uri=$uri")
                emit(result = "skip", reason = "not_zip")
                return false
            }

            val dbDir = context.getDatabasePath(config.databaseFileName).parentFile
                ?: throw IllegalStateException("database dir unavailable")
            if (!dbDir.exists() && !dbDir.mkdirs()) {
                throw IllegalStateException("create database dir failed: ${dbDir.absolutePath}")
            }

            val tmpDir = File(context.cacheDir, "db_restore_tmp").apply {
                if (exists()) {
                    deleteRecursively()
                }
                mkdirs()
            }

            try {
                var foundMainDb = false
                ZipInputStream(pb).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val baseName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                            val normalizedName = normalizeBackupDbName(config, baseName)
                            if (normalizedName != null) {
                                val outFile = File(tmpDir, normalizedName)
                                outFile.outputStream().use { output -> zis.copyTo(output) }
                                if (normalizedName == config.databaseFileName) {
                                    foundMainDb = true
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                if (!foundMainDb) {
                    Log.w(LOG_TAG, "restoreDatabaseFromBackup failed: main db not found in zip")
                    emit(result = "error", statusOk = false, reason = "main_db_missing")
                    return false
                }

                runCatching { hooks.beforeDatabaseRestore(context) }
                    .onFailure { Log.w(LOG_TAG, "beforeDatabaseRestore failed", it) }

                allDatabaseFileNames(config).forEach { name ->
                    runCatching { File(dbDir, name).delete() }
                }

                var copiedMainDb = false
                tmpDir.listFiles().orEmpty().forEach { src ->
                    val dst = File(dbDir, src.name)
                    src.copyTo(dst, overwrite = true)
                    if (src.name == config.databaseFileName) copiedMainDb = true
                }

                runCatching { hooks.afterDatabaseRestore(context) }
                    .onFailure { Log.w(LOG_TAG, "afterDatabaseRestore failed", it) }

                Log.i(
                    LOG_TAG,
                    "restoreDatabaseFromBackup copied: mainDb=$copiedMainDb path=${dbDir.absolutePath}",
                )
                emit(
                    result = if (copiedMainDb) "ok" else "error",
                    statusOk = copiedMainDb,
                    reason = if (copiedMainDb) "copied" else "copy_failed",
                )
                return copiedMainDb
            } finally {
                runCatching { tmpDir.deleteRecursively() }
            }
        }
        Log.w(LOG_TAG, "restoreDatabaseFromBackup failed: openInputStream null uri=$uri")
        emit(result = "error", statusOk = false, reason = "open_stream_null")
        return false
    }

    @JvmStatic
    fun shareBackupFile(context: Context, file: File, config: BackupManagerConfig) {
        val intent = Intent(Intent.ACTION_SEND)
        val uri = FileProvider.getUriForFile(context, config.fileProviderAuthority, file)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = BACKUP_MIME_TYPE
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, null))
    }

    internal fun normalizeBackupDbName(config: BackupManagerConfig, baseName: String): String? = when {
        baseName == config.databaseFileName -> config.databaseFileName
        baseName == "${config.databaseFileName}-wal" -> "${config.databaseFileName}-wal"
        baseName == "${config.databaseFileName}-shm" -> "${config.databaseFileName}-shm"
        else -> null
    }

    private fun resolveWarning(backupAppVersion: String, currentAppVersion: String): ImportWarning? {
        val backupMajor = backupAppVersion.split(".").firstOrNull()?.toIntOrNull()
        val currentMajor = currentAppVersion.split(".").firstOrNull()?.toIntOrNull()
        if (backupMajor == null || currentMajor == null) return null
        return if (backupMajor != currentMajor) ImportWarning.APP_VERSION_MISMATCH else null
    }

    @Throws(IOException::class)
    private fun exportBackupZipWithDatabase(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String,
        config: BackupManagerConfig,
        hooks: BackupDatabaseHooks,
    ) {
        runCatching { hooks.beforeDatabaseSnapshot(context) }
            .onFailure { Log.w(LOG_TAG, "beforeDatabaseSnapshot failed", it) }

        val payloadBytes = JsonUtils.json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                version = BackupConst.BACKUP_VERSION,
                schemaVersion = BackupConst.BACKUP_VERSION,
                appVersion = appVersion,
                rules = ruleList,
                preferences = preferences,
                records = records,
            ),
        ).toByteArray(Charsets.UTF_8)

        val dbFiles = collectDatabaseFiles(context, config)
        if (dbFiles.none { it.first == "database/${config.databaseFileName}" }) {
            throw IllegalStateException("database file missing: ${config.databaseFileName}")
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_PAYLOAD_ENTRY))
                zip.write(payloadBytes)
                zip.closeEntry()

                dbFiles.forEach { (entryName, file) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IOException("openOutputStream returned null: $uri")
    }

    private fun collectDatabaseFiles(context: Context, config: BackupManagerConfig): List<Pair<String, File>> {
        val normalizedNames = listOf(
            config.databaseFileName,
            "${config.databaseFileName}-wal",
            "${config.databaseFileName}-shm",
        )
        return normalizedNames.mapNotNull { normalized ->
            val primary = context.getDatabasePath(normalized)
            if (primary.exists() && primary.isFile && primary.canRead()) {
                "database/$normalized" to primary
            } else {
                null
            }
        }
    }

    private fun readPayloadBytes(context: Context, uri: Uri): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            val pb = PushbackInputStream(BufferedInputStream(raw), 4)
            val header = ByteArray(4)
            val readCount = pb.read(header)
            if (readCount > 0) {
                pb.unread(header, 0, readCount)
            }
            val isZip = readCount == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte()
            if (!isZip) {
                return pb.readBytes()
            }
            ZipInputStream(pb).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase(Locale.ROOT)
                    if (!entry.isDirectory && (name.endsWith(".scebak") || name.endsWith(".json"))) {
                        return zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
            Log.e(LOG_TAG, "importRuleList failed: zip payload entry not found")
            return null
        }
        Log.e(LOG_TAG, "importRuleList failed: openInputStream null, uri=$uri")
        return null
    }

    private fun allDatabaseFileNames(config: BackupManagerConfig): List<String> =
        listOf(
            config.databaseFileName,
            "${config.databaseFileName}-wal",
            "${config.databaseFileName}-shm",
        )
}
