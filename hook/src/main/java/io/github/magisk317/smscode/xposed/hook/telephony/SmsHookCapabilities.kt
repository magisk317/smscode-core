package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import io.github.magisk317.xposed.runtime.HookHostCapabilities

/** SMS-specific host adapter layered on top of the product-neutral kit contract. */
interface SmsHookCapabilities : io.github.magisk317.xposed.runtime.HookHostCapabilities {
    /** Sensitive-debug lookup: when true, codes/bodies are escaped instead of summarized. */
    fun isSensitiveDebugLogMode(context: Context): Boolean

    /** Escapes a code for safe logging. */
    fun escapeCode(value: String): String

    /** Summarizes a code for diagnostics. */
    fun summarizeCode(value: String): String

    /** Escapes an SMS body for safe logging. */
    fun escapeBody(value: String): String

    /** Summarizes an SMS body for diagnostics. */
    fun summarizeBody(value: String): String

    /** Parses a verification code out of SMS or notification content. */
    suspend fun parseSmsCode(context: Context, content: String): String

    /** Legacy SMS-hook heartbeat overload; the kit API carries moduleVersion separately. */
    fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {}
}
