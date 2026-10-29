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
interface HookHostCapabilities : io.github.magisk317.xposed.runtime.HookHostCapabilities {
    /** Verbose-mode lookup for the module process. */
    override fun isVerboseLogMode(context: Context): Boolean

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
    override fun onProcessInit(context: Context) {}

    /**
     * Records a hook heartbeat for diagnostics. [packageName] is the hooked
     * package (e.g. com.android.mms, com.android.providers.telephony).
     */
    override fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
    }

    /**
     * Called once per hooked process after the module runtime is installed. Hosts use it to
     * report which processes the framework actually injected, so the settings UI can explain
     * a missing system-server hook (module installed or updated after boot).
     */
    override fun recordHookProcessOnline(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        moduleVersion: Int,
        verboseLogging: Boolean,
    ) {
    }

    /**
     * Records the publisher of a notification that looks like a call event, even when the
     * package is not in the call-source list. Diagnostics only: never gates forwarding.
     */
    override fun recordCallNotificationSource(
        context: Context,
        packageName: String,
        channelId: String,
        category: String,
        verboseLogging: Boolean,
    ) {
    }

    /** Records the install result of one hook so the settings page can show it. */
    override fun recordHookInstall(
        context: Context?,
        hook: String,
        status: String,
        packageName: String,
        processName: String,
        detail: String,
        verboseLogging: Boolean,
    ) {
    }

    /** Records the last notification seen by a relay path, including skip reasons. */
    override fun recordNotifyEvent(
        context: Context,
        source: String,
        packageName: String,
        channelId: String,
        msgType: String,
        skipReason: String,
        verboseLogging: Boolean,
    ) {
    }
}
