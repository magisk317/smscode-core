package io.github.magisk317.smscode.runtime.common.utils

import android.content.Context
import android.util.Log
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.xposed.contract.dedup.BoundedTimestampMap
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

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
        val storageMode = currentStorageMode()
        val normalizedKeys = keys
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { storageKey(it, storageMode) }
            .distinct()
            .toList()
        if (normalizedKeys.isEmpty()) {
            return ClaimResult(claimed = true)
        }
        val now = System.currentTimeMillis()
        val result = withFileLock(file) { raf ->
            val history = BoundedTimestampMap(windowMs, maxEntries, readEntries(raf, storageMode))
            val claim = history.claimAll(normalizedKeys, now)
            if (!claim.claimed) {
                writeEntries(raf, history.snapshot(), storageMode)
                return@withFileLock ClaimResult(
                    claimed = false,
                    ageMs = claim.ageMs,
                    key = claim.blockedKey,
                )
            }
            writeEntries(raf, history.snapshot(), storageMode)
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

    private fun readEntries(
        raf: RandomAccessFile,
        storageMode: String,
    ): LinkedHashMap<String, Long> {
        val entries = LinkedHashMap<String, Long>()
        val length = raf.length()
        if (length <= 0L) return entries
        if (length > MAX_STORED_BYTES) {
            try {
                Log.w(LOG_TAG, "Runtime gate snapshot too large, resetting: bytes=$length")
            } catch (_: Throwable) {
                // android.jar stubs throw in local JVM tests; logging must not change fail-open.
            }
            return entries
        }
        val bytes = ByteArray(length.toInt())
        raf.seek(0L)
        if (bytes.isNotEmpty()) raf.readFully(bytes)
        val content = bytes.toString(Charsets.UTF_8).trim()
        if (content.isBlank()) return entries

        val snapshot = decodeSnapshot(content)
        if (snapshot != null) {
            // Build modes intentionally do not share a short-lived gate history. Moving from a
            // debug/CI build to release must rewrite the file without carrying raw keys forward;
            // moving the other way cannot recover redacted keys, so both transitions start fresh.
            if (snapshot.mode != storageMode) return entries
            entries.putAll(snapshot.entries)
            return entries
        }

        // v1 was `key=timestamp` with platform-default line decoding. Only the plaintext capture
        // channel migrates the readable subset; release drops it so legacy SMS bodies cannot be
        // copied into the new file before redaction takes effect.
        if (storageMode != MODE_PLAINTEXT) return entries
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val split = line.lastIndexOf('=')
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
        storageMode: String,
    ) {
        raf.setLength(0L)
        raf.seek(0L)
        val content = buildJsonObject {
            put("version", FORMAT_VERSION)
            put("mode", storageMode)
            put(
                "entries",
                buildJsonArray {
                    entries.forEach { (key, value) ->
                        add(
                            buildJsonObject {
                                put("key", key)
                                put("timestampMs", value)
                            },
                        )
                    }
                },
            )
        }.toString()
        raf.write(content.toByteArray(Charsets.UTF_8))
    }

    private data class StoredSnapshot(
        val mode: String,
        val entries: LinkedHashMap<String, Long>,
    )

    private fun decodeSnapshot(content: String): StoredSnapshot? = runCatching {
        val root = JSON.parseToJsonElement(content).jsonObject
        if (root["version"]?.jsonPrimitive?.intOrNull != FORMAT_VERSION) return null
        val mode = root["mode"]?.jsonPrimitive?.content
            ?.takeIf { it == MODE_PLAINTEXT || it == MODE_REDACTED }
            ?: return null
        val entries = LinkedHashMap<String, Long>()
        root["entries"]?.jsonArray.orEmpty().forEach { item ->
            val row = item.jsonObject
            val key = row["key"]?.jsonPrimitive?.content.orEmpty()
            val timestamp = row["timestampMs"]?.jsonPrimitive?.longOrNull
            if (key.isNotBlank() && timestamp != null) entries[key] = timestamp
        }
        StoredSnapshot(mode, entries)
    }.getOrNull()

    private fun currentStorageMode(): String =
        if (MagiskLogPolicy.current().sanitizesText) MODE_REDACTED else MODE_PLAINTEXT

    private fun storageKey(key: String, storageMode: String): String {
        if (storageMode == MODE_PLAINTEXT) return key
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return buildString(REDACTED_PREFIX.length + digest.size * 2) {
            append(REDACTED_PREFIX)
            digest.forEach { byte -> append("%02x".format(byte.toInt() and BYTE_MASK)) }
        }
    }

    private const val FORMAT_VERSION = 2
    private const val MODE_PLAINTEXT = "plaintext"
    private const val MODE_REDACTED = "redacted"
    private const val REDACTED_PREFIX = "sha256:"
    private const val BYTE_MASK = 0xff
    private const val MAX_STORED_BYTES = 1024L * 1024L
    private val JSON = Json { ignoreUnknownKeys = true }

    private const val DEFAULT_MAX_ENTRIES = 256
    private const val INTERNAL_GATE_DIR_NAME = "runtime_gates"
    private val SAFE_FILE_NAME = Regex("[A-Za-z0-9_.-]{1,96}")
    private val processLock = Any()
}
