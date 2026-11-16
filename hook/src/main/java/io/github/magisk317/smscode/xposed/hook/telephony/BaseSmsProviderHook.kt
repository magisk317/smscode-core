package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import io.github.magisk317.xposed.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

/**
 * Host-specific wiring for [BaseSmsProviderHook].
 *
 * Hosts implement the domain pieces and supply one [SmsHookCapabilities] adapter;
 * the capability members below have default implementations delegating to it, so
 * XSC (`HookRuntimeBridge`) and xinyi-relay (`xpbridge` facades) converge on the
 * same contract instead of drifting per file.
 */
interface SmsProviderHookHost {
    /** Application id of the host module, used to build the plugin `Context`. */
    val applicationId: String

    /** Unified host capabilities (prefs, escaping, parsing, heartbeat, process init). */
    val capabilities: SmsHookCapabilities

    /**
     * Optional process-scoped context lifecycle shared with the other SMS hooks.
     *
     * When present, the hook resolves the plugin context once and reuses it for every
     * write instead of rebuilding it through PackageManager on each callback.
     */
    val runtimeSession: SmsHookRuntimeSession?
        get() = null

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

    /**
     * Evaluates inbound rows about to be written through the sms provider
     * (network SMS that never passes the telephony dispatch chain). Return
     * [SmsProviderWriteDecision.Block] to cancel the write; the default keeps
     * observe-only hosts (XSC) fully inert. Only fired for insert/bulkInsert
     * calls carrying at least one inbound row (type absent or 1).
     */
    fun onInboundSmsProviderWrite(
        pluginContext: Context,
        phoneContext: Context,
        drafts: List<SmsProviderDraft>,
        call: SmsProviderHookInstaller.WriteCall,
    ): SmsProviderWriteDecision = SmsProviderWriteDecision.Allow

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
            host.capabilities.recordHookInstall(
                context = null,
                hook = "sms_provider",
                status = "class_missing",
                packageName = param.packageName,
                processName = param.processName.ifBlank { param.packageName },
                detail = SmsProviderHookInstaller.TARGET_CLASS,
                verboseLogging = false,
            )
            return
        }
        if (!installation.smsProviderClassFound) {
            XLog.w(
                message = "SmsProviderHook: sms provider class not found: %s",
                SmsProviderHookInstaller.TARGET_SMS_PROVIDER_CLASS,
            )
        }
        SmsProviderHookInstaller.recordLoad(result = "ok", reason = "installed")
        host.capabilities.recordHookInstall(
            context = null,
            hook = "sms_provider",
            status = "installed",
            packageName = param.packageName,
            processName = param.processName.ifBlank { param.packageName },
            detail = "installed sms_provider=${installation.smsProviderHookedMethodCount}",
            verboseLogging = false,
        )
    }

    private fun handleWrite(call: SmsProviderHookInstaller.WriteCall) {
        val context = call.context
        val source = "sms_provider_${call.methodName}"
        val session = host.runtimeSession
        val runtime = if (session != null && context != null) {
            runCatching { session.initialize(context) }
                .onFailure { XLog.w("SmsProviderHook: session initialization failed: %s", it) }
                .getOrNull()
        } else {
            null
        }

        val pluginContext: Context?
        val phoneContext: Context?
        if (runtime != null && session != null) {
            // The session owns process init and heartbeat through its recorder, so the
            // per-write callback only refreshes the heartbeat.
            pluginContext = runtime.pluginContext
            phoneContext = runtime.phoneContext
            runCatching { session.recordHeartbeat(source) }
                .onFailure { XLog.w("SmsProviderHook: session heartbeat failed: %s", it) }
        } else {
            // Hosts without a session (and transient session failures) keep the legacy
            // path so blacklist enforcement never loses its plugin context.
            phoneContext = context
            pluginContext = runCatching {
                context?.createPackageContext(host.applicationId, Context.CONTEXT_IGNORE_SECURITY)
            }.getOrNull()
            if (pluginContext != null && phoneContext != null) {
                runCatching { host.onPluginContextReady(pluginContext, phoneContext) }
                    .onFailure { XLog.w("SmsProviderHook: legacy initialization failed: %s", it) }
                runCatching {
                    host.recordHeartbeat(
                        pluginContext = pluginContext,
                        phoneContext = phoneContext,
                        processName = phoneContext.applicationInfo?.processName
                            ?: SmsProviderHookInstaller.TARGET_PACKAGE,
                        source = source,
                    )
                }.onFailure { XLog.w("SmsProviderHook: legacy heartbeat failed: %s", it) }
            }
        }
        // Diagnostics must not prevent an otherwise available blacklist decision.
        runCatching {
            val verboseDiag = pluginContext != null && host.isVerboseLogMode(pluginContext)
            SmsProviderHookInstaller.logWriteDiag(call, verbose = verboseDiag)
        }.onFailure { XLog.w("SmsProviderHook: write diagnostics failed: %s", it) }
        maybeBlockInboundWrite(call, pluginContext, phoneContext)
    }

    /**
     * Blacklist enforcement for network SMS that never passes the telephony
     * dispatch chain. The host decides; the shared layer only substitutes the
     * blocking result so write-cancellation semantics stay in one place.
     */
    private fun maybeBlockInboundWrite(
        call: SmsProviderHookInstaller.WriteCall,
        pluginContext: Context?,
        phoneContext: Context?,
    ) {
        if (pluginContext == null || phoneContext == null) return
        if (call.methodName !in SmsProviderHookInstaller.BLOCKABLE_METHOD_NAMES) return
        val drafts = runCatching {
            SmsProviderHookInstaller.extractInboundDrafts(
                call.contentValues.map(SmsProviderHookInstaller::contentValuesToMap),
            )
        }.getOrDefault(emptyList())
        if (drafts.isEmpty()) return
        val decision = host.onInboundSmsProviderWrite(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            drafts = drafts,
            call = call,
        )
        if (decision is SmsProviderWriteDecision.Block) {
            call.hookParam.result = SmsProviderHookInstaller.blockingResultFor(call.methodName)
            XLog.w(
                message = "Diag sms provider block: method=%s reason=%s drafts=%d class=%s",
                call.methodName,
                decision.reason,
                drafts.size,
                call.targetClassName,
            )
        }
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
