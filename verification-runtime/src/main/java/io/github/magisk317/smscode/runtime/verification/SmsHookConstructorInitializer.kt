package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

class SmsHookConstructorInitializer<R : VerificationRuntimeContext>(
    private val runtimeInitializer: (Context) -> R?,
    private val conflictNoticeChannelInitializer: (Context, Context) -> Unit,
    private val conflictSuppressor: (Context, String) -> Boolean,
    private val showNotificationReader: (Context) -> Boolean,
    private val notificationChannelInitializer: (R) -> Unit = {},
    private val copyCodeRegistrar: (R) -> Unit = {},
    private val activationMarker: (Context) -> Unit,
    private val heartbeatRecorder: (String) -> Unit = {},
    private val suppressionLogger: (String) -> Unit = {},
    private val inboxObserverRegistrar: (R) -> Unit = {},
) {
    enum class StopReason {
        RUNTIME_UNAVAILABLE,
    }

    data class Outcome(
        val stopReason: StopReason? = null,
        val suppressedByRelay: Boolean = false,
    ) {
        val initialized: Boolean = stopReason == null
    }

    fun handle(phoneContext: Context): Outcome {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "hook.init", attributes = attrs, statusOk = statusOk)
        }

        val runtime = runCatching { runtimeInitializer(phoneContext) }
            .onFailure { XLog.e(message = "Create plugin context failed: %s", it) }
            .getOrNull()
        if (runtime == null) {
            XLog.e("Plugin context is null after creation attempt")
            emit(result = "error", statusOk = false, reason = "runtime_unavailable")
            return Outcome(stopReason = StopReason.RUNTIME_UNAVAILABLE)
        }

        conflictNoticeChannelInitializer(runtime.pluginContext, runtime.phoneContext)
        val suppressByRelay = conflictSuppressor(runtime.phoneContext, CONSTRUCTOR_CONFLICT_SOURCE)
        if (showNotificationReader(runtime.pluginContext)) {
            notificationChannelInitializer(runtime)
            if (!suppressByRelay) {
                copyCodeRegistrar(runtime)
            }
        }
        activationMarker(runtime.pluginContext)
        heartbeatRecorder(CONSTRUCTOR_HEARTBEAT_SOURCE)
        if (suppressByRelay) {
            suppressionLogger(CONSTRUCTOR_STAGE)
        } else {
            inboxObserverRegistrar(runtime)
        }
        emit(
            result = "ok",
            reason = if (suppressByRelay) "suppressed_by_relay" else "initialized",
        )
        return Outcome(suppressedByRelay = suppressByRelay)
    }

    private companion object {
        private const val CONSTRUCTOR_CONFLICT_SOURCE = "SmsHandlerHook#constructor"
        private const val CONSTRUCTOR_HEARTBEAT_SOURCE = "sms_handler_constructor"
        private const val CONSTRUCTOR_STAGE = "constructor"
    }
}
