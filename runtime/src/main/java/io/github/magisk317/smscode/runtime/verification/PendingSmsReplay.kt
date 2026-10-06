package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import android.content.Intent
import java.util.ArrayDeque

/**
 * Holds SMS dispatches that arrived before the hook runtime was ready.
 *
 * The telephony process can deliver an SMS before the plugin context exists (the
 * constructor hook may not have fired yet, or a hot module update left the runtime
 * uninitialised). Dropping those dispatches silently loses the message; replaying
 * them immediately would run side effects against a half-initialised runtime. So we
 * park them briefly and replay once the host reports the runtime is ready.
 *
 * Hosts stay in control: they decide what "ready" means and what to do with a replay.
 */
class PendingSmsReplayQueue(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Entry(
        val intent: Intent,
        val eventId: String,
        val source: String,
        val hookArgs: Array<Any?>?,
        val receiver: Any?,
        val inboundSmsHandler: Any?,
        val enqueuedAt: Long,
    )

    data class ReplayOutcome(
        val replayed: Int,
        val expired: Int,
        val droppedByLimit: Int,
    )

    private val entries = ArrayDeque<Entry>()

    /**
     * Parks [intent] until the runtime becomes ready.
     *
     * Returns false when the queue is full, so the caller can log the drop instead
     * of silently losing the SMS.
     */
    fun enqueue(
        intent: Intent,
        eventId: String,
        source: String,
        hookArgs: Array<Any?>? = null,
        receiver: Any? = null,
        inboundSmsHandler: Any? = null,
    ): Boolean {
        synchronized(entries) {
            pruneLocked()
            if (entries.size >= maxEntries) return false
            entries.addLast(
                Entry(
                    intent = intent,
                    eventId = eventId,
                    source = source,
                    hookArgs = hookArgs,
                    receiver = receiver,
                    inboundSmsHandler = inboundSmsHandler,
                    enqueuedAt = clock(),
                ),
            )
            return true
        }
    }

    /**
     * Removes and returns every entry that is still within [ttlMs], oldest first.
     *
     * Call this when the runtime becomes ready. Expired entries are counted and
     * dropped so the caller can report them.
     */
    fun drainReady(): Pair<List<Entry>, Int> {
        synchronized(entries) {
            val now = clock()
            val ready = ArrayList<Entry>(entries.size)
            var expired = 0
            while (entries.isNotEmpty()) {
                val head = entries.peekFirst() ?: break
                if (now - head.enqueuedAt > ttlMs) {
                    entries.removeFirst()
                    expired += 1
                } else {
                    break
                }
            }
            while (entries.isNotEmpty()) {
                ready.add(entries.removeFirst())
            }
            return ready to expired
        }
    }

    /** Drops everything older than [ttlMs]; returns how many were dropped. */
    fun prune(): Int {
        synchronized(entries) { return pruneLocked() }
    }

    val size: Int
        get() = synchronized(entries) { entries.size }

    private fun pruneLocked(): Int {
        val now = clock()
        var dropped = 0
        while (entries.isNotEmpty() && now - (entries.peekFirst()?.enqueuedAt ?: 0L) > ttlMs) {
            entries.removeFirst()
            dropped += 1
        }
        return dropped
    }

    companion object {
        const val DEFAULT_TTL_MS = 5_000L
        const val DEFAULT_MAX_ENTRIES = 20
    }
}

/**
 * Remembers the plugin context of a parked dispatch so a replay does not have to
 * re-derive it.
 */
class PendingSmsRuntimeGate(
    private val retryTimeoutMs: Long = DEFAULT_RETRY_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var pendingRuntime: Pair<Context, Context>? = null

    @Volatile
    private var retryDeadline: Long = 0L

    /** Remembers the contexts a failed dispatch was waiting for. */
    fun expectRuntime(pluginContext: Context, phoneContext: Context) {
        pendingRuntime = pluginContext to phoneContext
        retryDeadline = clock() + retryTimeoutMs
    }

    /** Returns the remembered contexts once the retry window opens, then clears them. */
    fun takeRuntimeIfReady(): Pair<Context, Context>? {
        val runtime = pendingRuntime ?: return null
        if (clock() < retryDeadline) return null
        pendingRuntime = null
        retryDeadline = 0L
        return runtime
    }

    fun hasPendingRuntime(): Boolean = pendingRuntime != null

    fun clear() {
        pendingRuntime = null
        retryDeadline = 0L
    }

    companion object {
        const val DEFAULT_RETRY_TIMEOUT_MS = 1_500L
    }
}
