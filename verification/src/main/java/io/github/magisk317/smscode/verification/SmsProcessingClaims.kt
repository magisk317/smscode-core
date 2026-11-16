package io.github.magisk317.smscode.verification

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Process-local ownership of SMS action dispatch, shared by live and inbox-observer entry points.
 *
 * Completing a lease means the caller accepted the work it owns. Callers must release it when that
 * work could not be accepted; this class does not imply that asynchronous side effects succeeded.
 * Unexpired claims are never evicted to make room for another SMS.
 */
class SmsProcessingClaims(
    private val pendingTtlMs: Long = DEFAULT_PENDING_TTL_MS,
    private val completedTtlMs: Long = DEFAULT_COMPLETED_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) {
    class Lease internal constructor(
        internal val owner: Any,
        internal val fingerprint: String,
        val id: Long,
    ) {
        override fun toString(): String = "Lease(id=$id)"
    }

    sealed class ClaimResult {
        data class Acquired(val lease: Lease) : ClaimResult()
        object InFlight : ClaimResult()
        object Completed : ClaimResult()
        object CapacityExceeded : ClaimResult()
    }

    private sealed class Entry {
        data class Pending(val id: Long, val since: Long) : Entry()
        data class Completed(val since: Long) : Entry()
    }

    private val lock = Any()
    private val owner = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private var nextLeaseId = 0L

    init {
        require(pendingTtlMs > 0L) { "pendingTtlMs must be positive" }
        require(completedTtlMs > 0L) { "completedTtlMs must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun claim(sender: String, body: String, date: Long): ClaimResult {
        val key = fingerprint(sender, body, date)
        return synchronized(lock) {
            val now = clock()
            prune(now)
            when (entries[key]) {
                is Entry.Pending -> ClaimResult.InFlight
                is Entry.Completed -> ClaimResult.Completed
                null -> {
                    if (entries.size >= maxEntries) {
                        ClaimResult.CapacityExceeded
                    } else {
                        val lease = Lease(owner, key, ++nextLeaseId)
                        entries[key] = Entry.Pending(lease.id, now)
                        ClaimResult.Acquired(lease)
                    }
                }
            }
        }
    }

    fun hasClaim(sender: String, body: String, date: Long): Boolean {
        val key = fingerprint(sender, body, date)
        return synchronized(lock) {
            prune(clock())
            entries.containsKey(key)
        }
    }

    fun isCompleted(sender: String, body: String, date: Long): Boolean {
        val key = fingerprint(sender, body, date)
        return synchronized(lock) {
            prune(clock())
            entries[key] is Entry.Completed
        }
    }

    fun isActive(lease: Lease): Boolean = synchronized(lock) {
        prune(clock())
        owns(lease)
    }

    fun complete(lease: Lease): Boolean = synchronized(lock) {
        val now = clock()
        prune(now)
        if (!owns(lease)) return@synchronized false
        entries[lease.fingerprint] = Entry.Completed(now)
        true
    }

    fun release(lease: Lease): Boolean = synchronized(lock) {
        prune(clock())
        if (!owns(lease)) return@synchronized false
        entries.remove(lease.fingerprint)
        true
    }

    fun clear() = synchronized(lock) {
        entries.clear()
    }

    private fun owns(lease: Lease): Boolean {
        if (lease.owner !== owner) return false
        return (entries[lease.fingerprint] as? Entry.Pending)?.id == lease.id
    }

    private fun prune(now: Long) {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val (since, ttl) = when (entry) {
                is Entry.Pending -> entry.since to pendingTtlMs
                is Entry.Completed -> entry.since to completedTtlMs
            }
            if (now - since >= ttl) iterator.remove()
        }
    }

    private fun fingerprint(sender: String, body: String, date: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(sender, body).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(date).array())
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val DEFAULT_PENDING_TTL_MS = 30_000L
        const val DEFAULT_COMPLETED_TTL_MS = 10 * 60 * 1_000L
        const val DEFAULT_MAX_ENTRIES = 256
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
