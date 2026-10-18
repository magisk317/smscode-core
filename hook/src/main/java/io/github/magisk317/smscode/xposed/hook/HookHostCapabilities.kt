package io.github.magisk317.smscode.xposed.hook

import android.content.Context

/**
 * Unified host capability contract for the shared SMS hook layer.
 *
 * Before this contract each host wired core's base hooks with its own lambda set:
 * XSC routed prefs through `HookRuntimeBridge` while xinyi-relay routed the same
 * lookups through its `xpbridge` facades (`XpPrefs`, `XpStringEscaper`,
 * `XpSmsCodeParser`, `XpHookDiagnostics`). Core consumed those lambdas directly,
 * so the two per-app wiring layers drifted file by file (27 cloned files, two
 * bridge styles, two lifecycle patterns).
 *
 * Hosts now implement this interface exactly once - a thin adapter over whatever
 * runtime bridge they own - and core's hooks consume the contract. Domain hooks
 * (forward pipeline, keep-alive, verification prefs) stay outside it.
 */
interface HookHostCapabilities {
    /** Verbose-mode lookup for the module process. */
    fun isVerboseLogMode(context: Context): Boolean

    /** Sensitive-debug lookup: when true, codes/bodies are escaped instead of summarized. */
    fun isSensitiveDebugLogMode(context: Context): Boolean

    /** Escapes a code for safe logging. */
    fun escapeCode(value: String): String

    /** Summarizes a code for telemetry. */
    fun summarizeCode(value: String): String

    /** Escapes an SMS body for safe logging. */
    fun escapeBody(value: String): String

    /** Summarizes an SMS body for telemetry. */
    fun summarizeBody(value: String): String

    /** Parses a verification code out of SMS content; empty when none. */
    suspend fun parseSmsCode(context: Context, content: String): String

    /**
     * Called once per hooked process after the plugin context becomes available.
     * Hosts use it to install per-process runtime state (log context binding etc.).
     */
    fun onProcessInit(context: Context) {}

    /**
     * Records a hook heartbeat for diagnostics. [packageName] is the hooked
     * package (e.g. com.android.mms, com.android.providers.telephony).
     */
    fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
    }
}
