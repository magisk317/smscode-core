package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.hook.HookHostCapabilities
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

/**
 * Host-specific wiring for [BaseSmsProviderHook].
 *
 * Hosts implement the domain pieces and supply one [HookHostCapabilities] adapter;
 * the capability members below have default implementations delegating to it, so
 * XSC (`HookRuntimeBridge`) and xinyi-relay (`xpbridge` facades) converge on the
 * same contract instead of drifting per file.
 */
interface SmsProviderHookHost {
    /** Application id of the host module, used to build the plugin `Context`. */
    val applicationId: String

    /** Unified host capabilities (prefs, escaping, parsing, heartbeat, process init). */
    val capabilities: HookHostCapabilities

    /**
     * Called once per hooked process after the plugin context becomes available,
     * before any SMS write is observed. Hosts use it to install per-process
     * runtime state (e.g. binding the log context).
     */
    fun onPluginContextReady(pluginContext: Context, phoneContext: Context) {
        capabilities.onProcessInit(pluginContext)
    }

    /** Records the per-write hook heartbeat. */
    fun recordHeartbeat(
        pluginContext: Context,
        phoneContext: Context,
        processName: String,
        source: String,
    ) {
        capabilities.recordHookHeartbeat(
            context = pluginContext,
            packageName = SmsProviderHookInstaller.TARGET_PACKAGE,
            processName = processName,
            source = source,
            verboseLogging = capabilities.isVerboseLogMode(pluginContext),
        )
    }

    /** Returns true when verbose logging is enabled for this process. */
    fun isVerboseLogMode(pluginContext: Context): Boolean =
        capabilities.isVerboseLogMode(pluginContext)

    /** Optional verbose diagnostics emitted right after the target process is matched. */
    fun onTargetProcessHit(param: LoadParam) {}

    /** Optional verbose diagnostics emitted when the target class could not be found. */
    fun onTargetClassMissing(param: LoadParam) {}
}

/**
 * Shared `com.android.providers.telephony` hook skeleton.
 *
 * Installation, package filtering, class-miss handling and write diagnostics all
 * live here so both hosts only provide [SmsProviderHookHost].
 */
abstract class BaseSmsProviderHook(
    private val host: SmsProviderHookHost,
) : BaseHook() {

    override fun hookOnLoadPackage(): Boolean = true

    final override fun onLoadPackage(param: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK.id) {
            onLoadPackageRouted(param)
        }
    }

    private fun onLoadPackageRouted(param: LoadParam) {
        if (!isTargetPackage(param.packageName)) return
        host.onTargetProcessHit(param)
        val installation = SmsProviderHookInstaller.install(param.classLoader) { call ->
            XLog.withRoute(LogRoute.SMS_HOOK.id) { handleWrite(call) }
        }
        if (!installation.classFound) {
            XLog.w(message = "SmsProviderHook: class not found: %s", SmsProviderHookInstaller.TARGET_CLASS)
            host.onTargetClassMissing(param)
            SmsProviderHookInstaller.recordLoad(result = "skip", reason = "class_missing")
            return
        }
        SmsProviderHookInstaller.recordLoad(result = "ok", reason = "installed")
    }

    private fun handleWrite(call: SmsProviderHookInstaller.WriteCall) {
        val context = call.context
        val pluginContext = runCatching {
            context?.createPackageContext(host.applicationId, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()
        if (pluginContext != null && context != null) {
            host.onPluginContextReady(pluginContext, context)
            host.recordHeartbeat(
                pluginContext = pluginContext,
                phoneContext = context,
                processName = context.applicationInfo?.processName
                    ?: SmsProviderHookInstaller.TARGET_PACKAGE,
                source = "sms_provider_${call.methodName}",
            )
        }
        val verboseDiag = pluginContext != null && host.isVerboseLogMode(pluginContext)
        SmsProviderHookInstaller.logWriteDiag(call, verbose = verboseDiag)
    }

    companion object {
        /**
         * Only the telephony provider process carries SMS writes. Extracted so both hosts
         * (and unit tests) share one definition of "am I the target process".
         */
        @JvmStatic
        fun isTargetPackage(packageName: String?): Boolean =
            packageName == SmsProviderHookInstaller.TARGET_PACKAGE
    }
}
