package io.github.magisk317.smscode.verification

import java.io.File
import java.io.RandomAccessFile

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
    private val memoryHistory = LinkedHashMap<String, Long>()

    fun shouldSkipInMemory(eventId: String, action: String?): Result {
        val key = buildKeyOrNull(eventId, action) ?: return Result(shouldSkip = false)
        val now = timeProvider()
        return synchronized(memoryLock) {
            pruneExpired(memoryHistory, now)
            val last = memoryHistory[key]
            if (last != null && now - last <= windowMs) {
                Result(
                    shouldSkip = true,
                    ageMs = now - last,
                    key = key,
                    timestampMs = now,
                )
            } else {
                memoryHistory[key] = now
                trimToCapacity(memoryHistory)
                Result(
                    shouldSkip = false,
                    key = key,
                    timestampMs = now,
                )
            }
        }
    }

    fun shouldSkipByFile(file: File, eventId: String, action: String?): Result {
        val key = buildKeyOrNull(eventId, action) ?: return Result(shouldSkip = false)
        val now = timeProvider()
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.channel.use { channel ->
                channel.lock().use {
                    return claimFileEntry(raf, key, now)
                }
            }
        }
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
        val entries = readEntries(raf)
        pruneExpired(entries, now)
        val last = entries[key]
        if (last != null && now - last <= windowMs) {
            writeEntries(raf, entries)
            return Result(
                shouldSkip = true,
                ageMs = now - last,
                key = key,
                timestampMs = now,
            )
        }
        entries[key] = now
        trimToCapacity(entries)
        writeEntries(raf, entries)
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
        val entries = readEntries(raf)
        val beforeSize = entries.size
        pruneExpired(entries, now)
        var changed = entries.size != beforeSize
        if (!entries.containsKey(key)) {
            entries[key] = now
            changed = true
        }
        val beforeTrimSize = entries.size
        trimToCapacity(entries)
        changed = changed || entries.size != beforeTrimSize
        if (changed) {
            writeEntries(raf, entries)
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

    private fun pruneExpired(entries: LinkedHashMap<String, Long>, now: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > windowMs) {
                iterator.remove()
            }
        }
    }

    private fun trimToCapacity(entries: LinkedHashMap<String, Long>) {
        while (entries.size > maxEntries) {
            val firstKey = entries.entries.firstOrNull()?.key ?: break
            entries.remove(firstKey)
        }
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
