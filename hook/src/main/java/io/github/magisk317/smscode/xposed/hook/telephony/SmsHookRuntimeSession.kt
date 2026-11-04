package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import io.github.magisk317.smscode.runtime.verification.VerificationRuntimeContext
import io.github.magisk317.xposed.capability.helper.SmsHookContextRecovery

/** Runtime contexts resolved inside a telephony hook process. */
data class SmsHookRuntimeContext(
    override val phoneContext: Context,
    override val pluginContext: Context,
) : VerificationRuntimeContext

/**
 * Shared telephony-hook context lifecycle.
 *
 * Product-specific snapshot, diagnostics, logging and heartbeat behavior are injected as callbacks.
 */
class SmsHookRuntimeSession(
    private val applicationId: String,
    private val fallbackPackageName: String? = null,
    private val pluginContextResolver: (Context, String) -> Context? = { phoneContext, packageName ->
        runCatching {
            phoneContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()
    },
    private val fallbackPhoneContextResolver: (Any?) -> Context? = { owner ->
        SmsHookContextRecovery.resolve(owner, "handler")?.context
    },
    private val onPhoneContextInitialized: (Context) -> Unit = {},
    private val onPluginContextResolved: (Context, Context) -> Unit = { _, _ -> },
    private val onPhoneContextRecovered: (Context) -> Unit = {},
    private val heartbeatRecorder: (Context, Context, String, String) -> Unit = { _, _, _, _ -> },
) {
    @Volatile
    private var phoneContext: Context? = null

    @Volatile
    private var pluginContext: Context? = null

    fun initialize(context: Context): SmsHookRuntimeContext? = synchronized(this) {
        initializePhoneContextLocked(context)
        currentOrResolveLocked()
    }

    fun currentOrResolve(): SmsHookRuntimeContext? = synchronized(this) {
        currentOrResolveLocked()
    }

    fun currentOrResolveWithFallback(owner: Any?): SmsHookRuntimeContext? = synchronized(this) {
        currentOrResolveLocked()?.let { return it }
        if (phoneContext == null) {
            fallbackPhoneContextResolver(owner)?.let { recovered ->
                initializePhoneContextLocked(recovered)
                onPhoneContextRecovered(recovered)
            }
        }
        currentOrResolveLocked()
    }

    fun recordHeartbeat(source: String): SmsHookRuntimeContext? {
        val runtime = currentOrResolve() ?: return null
        val packageName = runtime.phoneContext.packageName.ifBlank { fallbackPackageName.orEmpty() }
        heartbeatRecorder(runtime.pluginContext, runtime.phoneContext, packageName, source)
        return runtime
    }

    fun reset() = synchronized(this) {
        phoneContext = null
        pluginContext = null
    }

    private fun initializePhoneContextLocked(context: Context) {
        if (phoneContext != null) return
        phoneContext = context
        onPhoneContextInitialized(context)
    }

    private fun currentOrResolveLocked(): SmsHookRuntimeContext? {
        val resolvedPhoneContext = phoneContext ?: return null
        val resolvedPluginContext = pluginContext ?: pluginContextResolver(
            resolvedPhoneContext,
            applicationId,
        )?.also { resolved ->
            pluginContext = resolved
            onPluginContextResolved(resolved, resolvedPhoneContext)
        } ?: return null
        return SmsHookRuntimeContext(
            phoneContext = resolvedPhoneContext,
            pluginContext = resolvedPluginContext,
        )
    }
}
