package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.verification.SmsIntentHookSupport
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.logging.MagiskOtel
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * Outcome of a host block evaluation, flattened to the fields core needs.
 *
 * Hosts already own an evaluator shell with its own result type; this holder lets
 * them report it without adopting core's generic result shape.
 */
data class BlockEvaluation(
    val smsMsg: Any?,
    val blockReason: String?,
    val blacklistDeleteOnly: Boolean,
    /**
     * Blacklist match detail. Only xinyi persists it (its blacklist-hit recorder
     * writes a row per hit); XSC has no forward pipeline and leaves it null.
     */
    val blacklistResult: Any? = null,
    /**
     * Full dispatch decision, same story as [blacklistResult].
     */
    val decision: Any? = null,
)

/**
 * Host-specific wiring for [MmsEntryPointHook].
 *
 * Core owns the MMS entry-point interception flow (install, duplicate/conflict
 * filtering, block evaluation, telemetry). Hosts supply the pieces that differ:
 * their own conflict arbiter, blacklist recorder, block-evaluator/parse-worker
 * factories, verbose-mode lookup and heartbeat routing.
 */
interface MmsEntryPointHookHost {
    /** Application id of the host module, used to build the plugin `Context`. */
    val applicationId: String

    /** Returns true when another module already owns this SMS and we must stay silent. */
    fun shouldSuppressByRelay(context: Context, tag: String): Boolean

    /** Verbose-mode lookup for the module process. */
    fun isVerboseLogMode(pluginContext: Context): Boolean

    /** Called once per intercepted SMS before evaluation (bind log context, heartbeat). */
    fun onPluginContextReady(
        pluginContext: Context,
        phoneContext: Context,
        source: String,
    ) {
    }

    /**
     * Evaluates whether this SMS must be blocked. Hosts inject their own SMS
     * parser, blacklist matcher and code parser; core only drives the flow and
     * records telemetry.
     */
    fun evaluateBlock(
        pluginContext: Context,
        intent: Intent,
        eventId: String,
        source: String,
    ): BlockEvaluation?

    /**
     * Runs the post-parse pipeline (code extraction, auto-input, notification).
     * Invoked only when the SMS is actually blocked, after the block decision.
     */
    fun runParseWorker(
        pluginContext: Context,
        phoneContext: Context,
        intent: Intent,
        eventId: String,
    )

    /**
     * Records a blacklist hit. xinyi persists it for the outbound forward
     * pipeline; XSC has no such pipeline and leaves this empty.
     */
    fun onBlacklistEvaluated(
        pluginContext: Context,
        evaluation: BlockEvaluation,
        eventId: String,
        source: String,
    ) {
    }

    /**
     * Deletes a blacklisted SMS from the inbox. Core runs this off the intercept
     * thread. XSC has no inbox deletion and leaves it empty; xinyi forces delete.
     */
    fun deleteSmsFromInbox(
        pluginContext: Context,
        hostContext: Context,
        smsMsg: Any,
    ) {
    }

    /** Optional notice shown when a conflict was detected (XSC only). */
    fun onConflictNotice(pluginContext: Context, hostContext: Context, eventId: String) {}
}

/**
 * Shared `com.android.mms` entry-point hook.
 *
 * Flow (matches the xinyi semantics the hosts agreed on): evaluate first, and
 * only run the parse worker when the SMS is actually blocked.
 */
class MmsEntryPointHook(
    private val host: MmsEntryPointHookHost,
) : BaseHook() {

    private val smsOperationExecutor = Executors.newSingleThreadExecutor()

    override fun onLoadPackage(param: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK) {
            onLoadPackageRouted(param)
        }
    }

    override fun onHotReloading() {
        smsOperationExecutor.shutdownNow()
    }

    private fun onLoadPackageRouted(lpparam: LoadParam) {
        if (!isTargetPackage(lpparam.packageName)) return
        val classLoader = lpparam.classLoader
        XLog.i("MmsMessagesHook initializing")
        val installation = MmsEntryPointHookInstaller.install(classLoader) { context, intent, source, hookParam ->
            XLog.withRoute(LogRoute.SMS_HOOK) {
                maybeBlock(context, intent, source, hookParam)
            }
        }
        installation.missingClassNames.forEach { className ->
            XLog.w("MmsMessagesHook entrypoint class missing: %s", className)
        }
        val totalHooks = installation.hookedMethodCount
        if (totalHooks == 0) {
            XLog.w("MmsMessagesHook found no usable receiver/service entrypoints in %s", TARGET_PACKAGE)
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms_hook",
                    "reason" to "no_entrypoints",
                    "target_package" to TARGET_PACKAGE,
                ),
                statusOk = true,
            )
        } else {
            XLog.i("MmsMessagesHook installed methods=%d", totalHooks)
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms_hook",
                    "reason" to "installed",
                    "target_package" to TARGET_PACKAGE,
                    "found_count" to totalHooks.toString(),
                ),
                statusOk = true,
            )
        }
    }

    private fun maybeBlock(
        context: Context,
        intent: Intent,
        source: String,
        param: MethodHookParam,
    ) {
        val action = intent.action
        if (!SmsIntentHookSupport.isSmsAction(action)) return
        val eventId = SmsIntentHookSupport.ensureEventId(intent)
        val pluginContext = runCatching {
            context.createPackageContext(host.applicationId, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()
        val verboseDiag = pluginContext != null && host.isVerboseLogMode(pluginContext)
        if (verboseDiag) {
            XLog.w(
                "Diag MMS SMS entry: source=%s action=%s event_id=%s owner=%s",
                source,
                action,
                eventId,
                param.thisObject?.javaClass?.name ?: "<none>",
            )
        }
        if (SmsIntentHookSupport.markDispatchHandled(intent, action)) {
            XLog.w("MmsMessagesHook duplicate skip: source=%s event_id=%s action=%s", source, eventId, action)
            emitDispatch(
                result = "skip",
                reason = "duplicate",
                eventId = eventId,
                source = source,
            )
            return
        }
        if (host.shouldSuppressByRelay(context, "MmsMessagesHook#$source")) {
            XLog.w(
                "MmsMessagesHook suppressed: source=%s reason=%s event_id=%s",
                source,
                SUPPRESSION_REASON,
                eventId,
            )
            MagiskOtel.event(
                name = "hook.conflict",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms",
                    "reason" to "suppressed_by_relay",
                    "event_id_present" to eventId.isNotBlank().toString(),
                ),
                statusOk = true,
            )
            return
        }
        val resolvedPluginContext = pluginContext ?: return
        host.onPluginContextReady(resolvedPluginContext, context, source)
        val evaluation = host.evaluateBlock(resolvedPluginContext, intent, eventId, "mms") ?: return
        host.onBlacklistEvaluated(resolvedPluginContext, evaluation, eventId, "mms")
        val blacklistedSms = evaluation.smsMsg
        if (evaluation.blacklistDeleteOnly && blacklistedSms != null) {
            smsOperationExecutor.execute {
                XLog.withRoute(LogRoute.SMS_HOOK) {
                    runCatching {
                        host.deleteSmsFromInbox(resolvedPluginContext, context, blacklistedSms)
                    }
                        .onFailure {
                            XLog.w(
                                "MmsMessagesHook blacklist delete failed: %s",
                                it.message ?: "unknown",
                            )
                        }
                }
            }
        }
        val reason = evaluation.blockReason ?: run {
            emitDispatch(
                result = "ok",
                reason = "allow_after_eval",
                eventId = eventId,
                source = source,
            )
            return
        }
        XLog.w("MmsMessagesHook block start: source=%s reason=%s event_id=%s", source, reason, eventId)
        MagiskOtel.event(
            name = "sms.block",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "mms",
                "reason" to reason.take(MAX_OTEL_REASON_LENGTH),
                "event_id_present" to eventId.isNotBlank().toString(),
                "source" to source.substringAfterLast('.').take(MAX_OTEL_SOURCE_LENGTH),
            ),
            statusOk = true,
        )
        host.runParseWorker(resolvedPluginContext, context, intent, eventId)
        param.result = SmsIntentHookSupport.defaultResultForType((param.method as? Method)?.returnType)
    }

    private fun emitDispatch(result: String, reason: String, eventId: String, source: String) {
        MagiskOtel.event(
            name = "sms.dispatch",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "mms",
                "reason" to reason,
                "event_id_present" to eventId.isNotBlank().toString(),
                "source" to source.substringAfterLast('.').take(MAX_OTEL_SOURCE_LENGTH),
            ),
            statusOk = true,
        )
    }

    companion object {
        const val TARGET_PACKAGE = "com.android.mms"
        const val SUPPRESSION_REASON = "relay_module_active"
        private const val MAX_OTEL_SOURCE_LENGTH = 48
        private const val MAX_OTEL_REASON_LENGTH = 64

        /** Extracted so both hosts (and unit tests) share one MMS target definition. */
        @JvmStatic
        fun isTargetPackage(packageName: String?): Boolean = packageName == TARGET_PACKAGE
    }
}
