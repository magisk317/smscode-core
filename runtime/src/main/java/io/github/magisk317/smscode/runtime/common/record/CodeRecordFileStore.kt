package io.github.magisk317.smscode.runtime.common.record

import android.content.Context
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * File-backed transfer store for single code records (`CodeRecord_<date>.json`).
 *
 * The hook process exports a record to a file when the module database is not
 * reachable over Binder; the app process later imports the files back into its
 * database (files are deleted only after a successful insert). Export and import
 * emit the same `sms.record` telemetry for every host; hosts inject the concrete
 * record type, its filename accessor, and their insert/trim closures.
 */
class CodeRecordFileStore<T : Any>(
    private val recordSerializer: KSerializer<T>,
    private val fileNameFor: (T) -> String,
    private val filePrefix: String = DEFAULT_RECORD_FILE_PREFIX,
) {
    fun exportToFile(context: Context, record: T): Boolean {
        val startedAt = System.nanoTime()
        var caught: Throwable? = null
        val ok = runCatching {
            val recordFile = File(StorageUtils.getFilesDir(context), fileNameFor(record))
            OutputStreamWriter(FileOutputStream(recordFile), StandardCharsets.UTF_8).use { writer ->
                JsonUtils.toJson(record, recordSerializer, writer)
            }
            true
        }.onFailure { e ->
            caught = e
            XLog.e("Export code record to file failed", e)
        }.getOrDefault(false)
        emitRecord(
            result = if (ok) "ok" else "error",
            reason = if (ok) "export_file" else "export_file_failed",
            statusOk = ok,
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            extra = errorExtra(ok, caught),
        )
        return ok
    }

    fun getRecordFiles(context: Context): Array<File>? {
        val filesDir = StorageUtils.getFilesDir(context)
        return filesDir.listFiles { _, name -> name.startsWith(filePrefix) }
    }

    /**
     * Imports every parsable record file, inserts them via [insertRecords], and
     * deletes only the files whose records were handed to the inserter (so a
     * crash before insert never loses the file). Returns false only when the
     * inserter itself threw.
     */
    suspend fun importRecordFiles(
        recordFiles: Array<File>?,
        insertRecords: suspend (List<T>) -> Unit,
        logSuccess: (String) -> Unit = XLog::d,
        logError: (String, Throwable) -> Unit = XLog::e,
    ): Boolean {
        val startedAt = System.nanoTime()
        var importedCount = 0
        var caught: Throwable? = null
        val ok = runCatching {
            val records = mutableListOf<T>()
            val importedFiles = mutableListOf<File>()
            recordFiles.orEmpty().forEach { recordFile ->
                val record = loadFromFile(recordFile)
                if (record != null) {
                    records.add(record)
                    importedFiles.add(recordFile)
                }
            }
            if (records.isNotEmpty()) {
                insertRecords(records)
                importedFiles.forEach { it.delete() }
                importedCount = records.size
                logSuccess("Import code records to database succeed")
            }
            true
        }.onFailure { e ->
            caught = e
            logError("Import code records to database failed.", e)
        }.getOrDefault(false)
        emitRecord(
            result = if (ok) "ok" else "error",
            reason = if (ok) "import_db" else "import_db_failed",
            statusOk = ok,
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            extra = mapOf("imported_count" to importedCount.toString()).let { base ->
                base + errorExtra(ok, caught)
            },
        )
        return ok
    }

    /**
     * Discovers the pending record files under the app files dir and imports them.
     * Hosts put their storage insert (and any post-import trim) into
     * [insertRecords]; the trim runs only when at least one record was imported.
     */
    suspend fun importToDatabase(
        context: Context,
        insertRecords: suspend (List<T>) -> Unit,
        logSuccess: (String) -> Unit = XLog::d,
        logError: (String, Throwable) -> Unit = XLog::e,
    ): Boolean = importRecordFiles(getRecordFiles(context), insertRecords, logSuccess, logError)

    private fun loadFromFile(recordFile: File): T? {
        var reader: InputStreamReader? = null
        return try {
            reader = InputStreamReader(FileInputStream(recordFile), StandardCharsets.UTF_8)
            JsonUtils.fromJson(reader, recordSerializer)
        } catch (e: FileNotFoundException) {
            XLog.e("Code record file missing: ${recordFile.name}", e)
            null
        } catch (e: IOException) {
            XLog.e("Load code record file failed: ${recordFile.name}", e)
            null
        } catch (e: IllegalArgumentException) {
            XLog.e("Load code record file failed: ${recordFile.name}", e)
            null
        } catch (e: SerializationException) {
            XLog.e("Load code record file failed: ${recordFile.name}", e)
            null
        } finally {
            try {
                reader?.close()
            } catch (ignored: IOException) {
                // Safe to ignore
            }
        }
    }

    private fun errorExtra(ok: Boolean, caught: Throwable?): Map<String, String> {
        if (ok || caught == null) return emptyMap()
        val c = caught
        return mapOf(
            "error_class" to safeErrorClass(c),
            "error_msg" to safeErrorMessage(c),
        )
    }

    private fun emitRecord(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        durationMs: Long = 0L,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "app",
            "stage" to "code_record_restore",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "sms.record", attributes = attrs, statusOk = statusOk)
    }

    /**
     * Throwable class name for telemetry. Framework exceptions keep their real
     * name at runtime; the kit's consumer-rules keep Throwable names from R8 in
     * release builds, so this stays meaningful after obfuscation. Not the
     * `reason` attribute, so it passes the hygiene gate untouched.
     */
    private fun safeErrorClass(t: Throwable): String {
        val name = t.javaClass.simpleName
        return name.ifBlank { t.javaClass.name }
    }

    /**
     * Best-effort error message for telemetry. Collapses newlines and caps length
     * so a verbose exception does not bloat the span; the kit truncates again at
     * the attribute byte limit as a safety net.
     */
    private fun safeErrorMessage(t: Throwable): String {
        val raw = (t.message ?: t.javaClass.name).replace('\n', ' ').replace('\r', ' ')
        return if (raw.length > 200) raw.substring(0, 200) else raw
    }

    companion object {
        const val DEFAULT_RECORD_FILE_PREFIX = "CodeRecord_"

        /** Post-import record cap shared by both hosts (XSC PrefConst value). */
        const val DEFAULT_MAX_RECORDS = 20
    }
}
