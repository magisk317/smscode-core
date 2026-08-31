package io.github.magisk317.smscode.runtime.common.utils

import android.content.Context
import android.util.Log
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.smscode.rule.utils.BoundedTimestampMap
import java.io.File
import java.io.RandomAccessFile
import java.util.LinkedHashMap

object SharedRuntimeGate {
    private const val LOG_TAG = "runtime-common"

    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
        val key: String? = null,
    )

    fun claimWithinWindow(
        context: Context,
        fileName: String,
        key: String,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult {
        return claimAllWithinWindow(
            context = context,
            fileName = fileName,
            keys = listOf(key),
            windowMs = windowMs,
            maxEntries = maxEntries,
        )
    }

    fun claimWithinWindow(
        file: File,
        key: String,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult = claimAllWithinWindow(
        file = file,
        keys = listOf(key),
        windowMs = windowMs,
        maxEntries = maxEntries,
    )

    fun claimAllWithinWindow(
        context: Context,
        fileName: String,
        keys: Collection<String>,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult {
        return claimAllWithinWindow(
            file = File(StorageUtils.getExternalFilesDir(context), fileName),
            keys = keys,
            windowMs = windowMs,
            maxEntries = maxEntries,
        )
    }

    fun claimAllWithinWindow(
        file: File,
        keys: Collection<String>,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult {
        val normalizedKeys = keys
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (normalizedKeys.isEmpty()) {
            return ClaimResult(claimed = true)
        }
        val now = System.currentTimeMillis()
        val result = withFileLock(file) { raf ->
            val history = BoundedTimestampMap(windowMs, maxEntries, readEntries(raf))
            val claim = history.claimAll(normalizedKeys, now)
            if (!claim.claimed) {
                writeEntries(raf, history.snapshot())
                return@withFileLock ClaimResult(
                    claimed = false,
                    ageMs = claim.ageMs,
                    key = claim.blockedKey,
                )
            }
            writeEntries(raf, history.snapshot())
            ClaimResult(claimed = true)
        }
        val resolved = result ?: ClaimResult(claimed = true)
        if (!resolved.claimed || result == null) {
            MagiskOtel.event(
                name = "sms.dispatch",
                attributes = mapOf(
                    "result" to if (resolved.claimed) "ok" else "skip",
                    "duration_ms" to (resolved.ageMs ?: 0L).coerceAtLeast(0L).toString(),
                    "process" to "hook",
                    "stage" to "runtime_gate",
                    "reason" to if (result == null) "lock_unavailable_allow" else "duplicate_window",
                    "found_count" to normalizedKeys.size.toString(),
                ),
                statusOk = true,
            )
        }
        return resolved
    }

    fun <T> withFileLock(
        context: Context,
        fileName: String,
        block: (RandomAccessFile) -> T,
    ): T? = withFileLock(File(StorageUtils.getExternalFilesDir(context), fileName), block)

    fun <T> withFileLock(
        file: File,
        block: (RandomAccessFile) -> T,
    ): T? {
        return synchronized(processLock) {
            runCatching {
                file.parentFile?.mkdirs()
                RandomAccessFile(file, "rw").use { raf ->
                    raf.channel.use { channel ->
                        channel.lock().use {
                            block(raf)
                        }
                    }
                }
            }.onFailure {
                Log.w(
                    LOG_TAG,
                    "SharedRuntimeGate failed: file=${file.name} err=${it.message ?: it.javaClass.simpleName}",
                )
            }.getOrNull()
        }
    }

    @JvmStatic
    fun internalGateFile(context: Context, fileName: String): File {
        require(fileName.isNotBlank() && SAFE_FILE_NAME.matches(fileName)) {
            "Invalid runtime gate file name"
        }
        return File(File(context.noBackupFilesDir, INTERNAL_GATE_DIR_NAME), fileName)
    }

    private fun readEntries(raf: RandomAccessFile): LinkedHashMap<String, Long> {
        val entries = LinkedHashMap<String, Long>()
        raf.seek(0L)
        while (true) {
            val rawLine = raf.readLine() ?: break
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val split = line.indexOf('=')
            if (split <= 0) continue
            val key = line.substring(0, split)
            val value = line.substring(split + 1).toLongOrNull() ?: continue
            entries[key] = value
        }
        return entries
    }

    private fun writeEntries(
        raf: RandomAccessFile,
        entries: LinkedHashMap<String, Long>,
    ) {
        raf.setLength(0L)
        raf.seek(0L)
        val content = buildString {
            entries.forEach { (key, value) ->
                append(key)
                append('=')
                append(value)
                append('\n')
            }
        }
        raf.write(content.toByteArray())
    }

    private const val DEFAULT_MAX_ENTRIES = 256
    private const val INTERNAL_GATE_DIR_NAME = "runtime_gates"
    private val SAFE_FILE_NAME = Regex("[A-Za-z0-9_.-]{1,96}")
    private val processLock = Any()
}
