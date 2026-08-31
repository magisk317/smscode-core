package io.github.magisk317.smscode.runtime.contract.ipc

/** Wire contract for hook-to-app runtime state calls made through a ContentProvider. */
object RuntimeStateProviderContract {
    const val METHOD_CLAIM_RUNTIME_GATE = "smscode.runtime.claim_gate"
    const val METHOD_RECORD_HOOK_HEARTBEAT = "smscode.runtime.record_hook_heartbeat"
    const val METHOD_GET_RETENTION_DAYS = "smscode.runtime.get_retention_days"

    const val EXTRA_KEYS = "keys"
    const val EXTRA_WINDOW_MS = "window_ms"
    const val EXTRA_MAX_ENTRIES = "max_entries"
    const val EXTRA_PACKAGE_NAME = "package_name"
    const val EXTRA_PROCESS_NAME = "process_name"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_VERBOSE_LOGGING = "verbose_logging"
    const val EXTRA_ROUTE = "route"

    const val RESULT_OK = "ok"
    const val RESULT_CLAIMED = "claimed"
    const val RESULT_AGE_MS = "age_ms"
    const val RESULT_BLOCKED_KEY = "blocked_key"
    const val RESULT_RETENTION_DAYS = "retention_days"

    const val NO_AGE_MS = -1L
    const val DEFAULT_MAX_ENTRIES = 256
}
