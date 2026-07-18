package io.github.magisk317.smscode.verification

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs an SMS parse action under a hard wall-clock timeout.
 *
 * Historically the action was submitted to the shared [ScheduledExecutorService] and awaited with
 * `Future.get(timeout)`. That executor is single-threaded and is also used to schedule the follow-up
 * actions (kill-me, notifications, next-SMS handling). When the parse action blocks on an
 * uninterruptible cross-process Binder call (e.g. reading module prefs / rules while the module app
 * process is frozen), `Future.cancel(true)` can NOT unblock it, so the executor thread stays occupied
 * for the full (~25s) Binder timeout and every task queued behind it is starved.
 *
 * To keep a hung parse from ever occupying the shared executor, the timed action now runs on a
 * dedicated, disposable daemon thread. On timeout we interrupt that thread (best-effort) and simply
 * abandon it: the caller returns `null` immediately and the shared executor is never touched, so the
 * next SMS is unaffected. Because the worker is a daemon thread it can not block JVM shutdown and it
 * dies on its own once the underlying Binder call finally returns.
 *
 * NOTE (honest limitation): interrupting the abandoned worker does NOT cancel an in-flight blocking
 * Binder / ContentResolver call — those calls are not responsive to `Thread.interrupt()`. The
 * guarantee here is only that the shared executor is never blocked and the main flow returns
 * promptly, NOT that the worker thread stops the instant the timeout fires.
 */
object SmsParseActionRunner {
    const val DEFAULT_TIMEOUT_MS = 10_000L

    fun <R : Any, M : SmsMessage> runWithTimeout(
        // Retained for source/binary compatibility with existing callers (hook CodeWorker passes the
        // shared scheduled executor). The blocking action is intentionally NOT run on this executor
        // anymore; see the class doc for why. Kept as a parameter so callers do not need changing.
        @Suppress("UNUSED_PARAMETER") executor: ScheduledExecutorService,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        actionName: String = "SmsParseAction",
        runAction: () -> R?,
        duplicatedReader: (R) -> Boolean,
        messageReader: (R) -> M?,
        dispatcher: TimeoutDispatcher = DaemonThreadTimeoutDispatcher,
    ): CodeWorker.ParseOutcome<M>? {
        val parseResult = dispatcher.run(actionName, timeoutMs, runAction) ?: return null

        if (duplicatedReader(parseResult)) {
            return CodeWorker.ParseOutcome(duplicated = true)
        }
        return CodeWorker.ParseOutcome(
            smsMsg = messageReader(parseResult) ?: return null,
            duplicated = false,
        )
    }

    /**
     * Executes [action] and returns its result, or `null` if it does not finish within [timeoutMs]
     * (or throws). Implementations MUST never let a hung [action] keep occupying a thread that the
     * caller shares for other work.
     */
    interface TimeoutDispatcher {
        fun <R : Any> run(actionName: String, timeoutMs: Long, action: () -> R?): R?
    }

    /**
     * Default dispatcher: runs [action] on a fresh daemon thread and joins for at most [timeoutMs].
     * On timeout the worker is interrupted (best-effort) and abandoned so it can never block the
     * caller's shared executor.
     */
    object DaemonThreadTimeoutDispatcher : TimeoutDispatcher {
        /**
         * Hard cap on concurrent abandoned/in-flight parse workers. Each timed-out Binder
         * call can leave a daemon thread stuck for ~tens of seconds; without a bound, a
         * burst of SMS while the module process is frozen would pile up workers in
         * com.android.phone. Past this limit we fail closed (return null) instead of
         * starting another worker.
         */
        const val MAX_IN_FLIGHT_WORKERS = 8

        private val inFlightWorkers = AtomicInteger(0)

        /** Test hook: resets the in-flight counter between cases. */
        internal fun resetInFlightForTest() {
            inFlightWorkers.set(0)
        }

        /** Test hook: current in-flight count. */
        internal fun inFlightForTest(): Int = inFlightWorkers.get()

        override fun <R : Any> run(actionName: String, timeoutMs: Long, action: () -> R?): R? {
            // Fail closed when too many workers are already outstanding (including ones
            // abandoned after a previous timeout that have not yet returned from Binder).
            while (true) {
                val current = inFlightWorkers.get()
                if (current >= MAX_IN_FLIGHT_WORKERS) {
                    XLog.w(
                        "%s rejected: %d in-flight parse workers already at cap %d",
                        actionName,
                        current,
                        MAX_IN_FLIGHT_WORKERS,
                    )
                    return null
                }
                if (inFlightWorkers.compareAndSet(current, current + 1)) break
            }

            val resultRef = AtomicReference<R?>(null)
            val errorRef = AtomicReference<Throwable?>(null)
            val worker = Thread({
                try {
                    resultRef.set(action())
                } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                    // The worker is disposable; capture whatever it threw (including coroutine
                    // cancellation from an inner withTimeout) and report it as no-result below.
                    errorRef.set(error)
                } finally {
                    inFlightWorkers.decrementAndGet()
                }
            }, "smscode-parse-timed")
            worker.isDaemon = true
            worker.start()

            try {
                worker.join(timeoutMs)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                worker.interrupt()
                XLog.w("%s interrupted while awaiting result", actionName)
                return null
            }

            if (worker.isAlive) {
                // Timed out. Abandon the (daemon) worker: best-effort interrupt, then return. The
                // shared executor is never touched, so the next SMS is not queued behind this hang.
                // inFlightWorkers stays elevated until the worker's finally block runs.
                worker.interrupt()
                XLog.w("%s timed out after %d ms; abandoning worker thread", actionName, timeoutMs)
                return null
            }

            val error = errorRef.get()
            if (error != null) {
                // Mirror the previous Future.get() behaviour: any failure inside the action is
                // swallowed and reported as no-result rather than propagated into the phone process.
                XLog.w("%s failed: %s", actionName, error.message ?: error.javaClass.simpleName)
                return null
            }
            return resultRef.get()
        }
    }
}
