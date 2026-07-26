package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.rule.utils.BoundedTimestampMap
import java.io.File
import java.io.RandomAccessFile
import io.github.magisk317.xposed.logging.MagiskOtel

class SmsDispatchIntentDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    data class Result(
        val shouldSkip: Boolean,
        val ageMs: Long? = null,
        val key: String? = null,
        val timestampMs: Long? = null,
    )

    private val memoryLock = Any()
    private val memoryHistory = BoundedTimestampMap<String>(windowMs, maxEntries)

    fun shouldSkipInMemory(eventId: String, action: String?): Result {
        val key = buildKeyOrNull(eventId, action) ?: return Result(shouldSkip = false)
        val now = timeProvider()
        val result = synchronized(memoryLock) {
            val claim = memoryHistory.claim(key, now)
            if (!claim.claimed) {
                Result(
                    shouldSkip = true,
                    ageMs = claim.ageMs,
                    key = key,
                    timestampMs = now,
                )
            } else {
                Result(
                    shouldSkip = false,
                    key = key,
                    timestampMs = now,
                )
            }
        }
        emitDedupe(result, stage = "memory", eventId = eventId)
        return result
    }

    fun shouldSkipByFile(file: File, eventId: String, action: String?): Result {
        val key = buildKeyOrNull(eventId, action) ?: return Result(shouldSkip = false)
        val now = timeProvider()
        file.parentFile?.mkdirs()
        val result = RandomAccessFile(file, "rw").use { raf ->
            raf.channel.use { channel ->
                channel.lock().use {
                    claimFileEntry(raf, key, now)
                }
            }
        }
        emitDedupe(result, stage = "file", eventId = eventId)
        return result
    }

    fun syncFileIfLockAvailable(file: File, key: String, timestampMs: Long) {
        if (key.isBlank()) return
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.channel.use { channel ->
                val lock = channel.tryLock() ?: return
                try {
                    syncFileEntry(raf, key, timestampMs)
                } finally {
                    lock.release()
                }
            }
        }
    }

    private fun claimFileEntry(
        raf: RandomAccessFile,
        key: String,
        now: Long,
    ): Result {
        val history = BoundedTimestampMap(windowMs, maxEntries, readEntries(raf))
        val claim = history.claim(key, now)
        if (!claim.claimed) {
            writeEntries(raf, history.snapshot())
            return Result(
                shouldSkip = true,
                ageMs = claim.ageMs,
                key = key,
                timestampMs = now,
            )
        }
        writeEntries(raf, history.snapshot())
        return Result(
            shouldSkip = false,
            key = key,
            timestampMs = now,
        )
    }

    private fun syncFileEntry(
        raf: RandomAccessFile,
        key: String,
        now: Long,
    ) {
        val history = BoundedTimestampMap(windowMs, maxEntries, readEntries(raf))
        history.ensure(key, now)
        if (history.isDirty) {
            writeEntries(raf, history.snapshot())
        }
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

    private fun emitDedupe(result: Result, stage: String, eventId: String) {
        MagiskOtel.event(
            name = "sms.dispatch",
            attributes = mapOf(
                "result" to if (result.shouldSkip) "skip" else "ok",
                "duration_ms" to (result.ageMs ?: 0L).coerceAtLeast(0L).toString(),
                "process" to "hook",
                "stage" to "dedupe_$stage",
                "reason" to if (result.shouldSkip) "duplicate_window" else "unique",
                "event_id_present" to eventId.isNotBlank().toString(),
            ),
            statusOk = true,
        )
    }

    private fun buildKeyOrNull(eventId: String, action: String?): String? {
        if (eventId.isBlank() || action.isNullOrBlank()) return null
        return "$eventId|$action"
    }

    companion object {
        const val DEFAULT_FILE_NAME = "dispatch_dedup"
        const val DEFAULT_WINDOW_MS = 8_000L
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
