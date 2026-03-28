package io.github.magisk317.smscode.verification

import android.content.Context
import io.github.magisk317.smscode.xposed.utils.XLog

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
        val runtime = runCatching { runtimeInitializer(phoneContext) }
            .onFailure { XLog.e("Create plugin context failed: %s", it) }
            .getOrNull()
        if (runtime == null) {
            XLog.e("Plugin context is null after creation attempt")
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
        return Outcome(suppressedByRelay = suppressByRelay)
    }

    private companion object {
        private const val CONSTRUCTOR_CONFLICT_SOURCE = "SmsHandlerHook#constructor"
        private const val CONSTRUCTOR_HEARTBEAT_SOURCE = "sms_handler_constructor"
        private const val CONSTRUCTOR_STAGE = "constructor"
    }
}
