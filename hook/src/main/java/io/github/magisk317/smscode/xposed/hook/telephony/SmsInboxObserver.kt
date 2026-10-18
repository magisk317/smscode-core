package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.verification.ObservedInboxScanner
import io.github.magisk317.smscode.runtime.verification.SmsRoleStateResolver
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.SmsInboxSeenTracker
import io.github.magisk317.smscode.xposed.hook.HookHostCapabilities
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Watches the SMS inbox and re-runs verification for messages the provider may have
 * written outside the hooked dispatch path.
 *
 * The scanning, routing repair and telemetry live here. Host capabilities (prefs,
 * escaping, parsing) arrive through the unified [HookHostCapabilities] contract so
 * both hosts consume core identically.
 */
class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val recordHandler: (ObservedInboxScanRecord) -> Unit,
    private val capabilities: HookHostCapabilities,
) {
    private val smsRoleStateResolver = SmsRoleStateResolver()
    private val queryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val smsInboxScanner = createInboxScanner()
    private var routingRepair: ((ObservedInboxScanRecord) -> Boolean)? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            queryExecutor.execute { scanRecentInbox(uri?.toString().orEmpty()) }
        }
    }

    /** Registers the observer and kicks off a routing repair pass. */
    fun register() {
        runCatching {
            phoneContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            XLog.i("SmsInboxObserver registered")
            emitObserver(result = "ok", reason = "registered")
            queryExecutor.execute { repairRecentRouting() }
        }.onFailure {
            XLog.w(message = "SmsInboxObserver register failed: %s", it.message ?: it.javaClass.simpleName)
            emitObserver(result = "error", reason = "register_failed", statusOk = false)
        }
    }

    fun unregister() {
        runCatching {
            phoneContext.contentResolver.unregisterContentObserver(observer)
            XLog.i("SmsInboxObserver unregistered")
            emitObserver(result = "ok", reason = "unregistered")
        }.onFailure {
            XLog.w(message = "SmsInboxObserver unregister failed: %s", it.message ?: it.javaClass.simpleName)
            emitObserver(result = "error", reason = "unregister_failed", statusOk = false)
        }
        queryExecutor.shutdownNow()
    }

    /**
     * Supplies the host's routing repair, which needs the host's own handler instance.
     * Without it the repair pass still scans and reports, it just cannot fix anything.
     */
    fun setRoutingRepair(repair: (ObservedInboxScanRecord) -> Boolean) {
        routingRepair = repair
    }

    private fun repairRecentRouting() {
        var scanned = 0
        var updated = 0
        createInboxScanner().scanRouting(
            triggerUri = ROUTING_REPAIR_TRIGGER_URI,
            recentSmsWindowMs = ROUTING_REPAIR_WINDOW_MS,
        ).forEach { record ->
            scanned += 1
            if (routingRepair?.invoke(record) == true) {
                updated += 1
            }
        }
        XLog.log(
            Log.INFO,
            LogRoute.SMS_HOOK.id,
            false,
            false,
            "Diag SMS routing repair finished: scanned=%d updated=%d",
            scanned,
            updated,
        )
        emitObserver(
            result = "ok",
            reason = "routing_repair",
            extra = mapOf(
                "scanned_count" to scanned.toString(),
                "updated_count" to updated.toString(),
            ),
        )
    }

    private fun scanRecentInbox(triggerUri: String) {
        val startedAt = System.nanoTime()
        var handled = 0
        smsInboxScanner.scan(
            triggerUri = triggerUri,
            recentSmsWindowMs = RECENT_SMS_WINDOW_MS,
        ).forEach { record ->
            handled += 1
            val sensitive = capabilities.isSensitiveDebugLogMode(pluginContext)
            XLog.w(
                message = "Diag SMS provider observed: sms_id=%d trigger_uri=%s sender_hash=%s date=%d read=%s simSlot=%d subId=%d code=%s body=%s",
                record.smsId,
                record.triggerUri,
                senderHash(record.sender),
                record.date,
                record.read,
                record.simSlot,
                record.subId,
                if (sensitive) capabilities.escapeCode(record.code) else capabilities.summarizeCode(record.code),
                if (sensitive) capabilities.escapeBody(record.body) else capabilities.summarizeBody(record.body),
            )
            logSmsRoleStateForSms(record.smsId, record.triggerUri)
            recordHandler(record)
        }
        emitObserver(
            result = "ok",
            reason = if (handled == 0) "scan_empty" else "scan_handled",
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            extra = mapOf("handled_count" to handled.toString()),
        )
    }

    private fun emitObserver(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        durationMs: Long = 0L,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "hook",
            "stage" to "sms_inbox_observer",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "sms.observe", attributes = attrs, statusOk = statusOk)
    }

    private fun logSmsRoleStateForSms(smsId: Long, triggerUri: String) {
        val roleState = smsRoleStateResolver.resolve(phoneContext)
        XLog.w(
            "Diag observer sms role: sms_id=%d trigger_uri=%s defaultSms=%s roleHolders=%s",
            smsId,
            triggerUri.ifBlank { Telephony.Sms.CONTENT_URI.toString() },
            roleState.defaultSms ?: "<none>",
            if (roleState.roleHolders.isEmpty()) "<none>" else roleState.roleHolders.joinToString(","),
        )
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    private fun createInboxScanner(): ObservedInboxScanner = ObservedInboxScanner(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        smsIdTracker = SmsInboxSeenTracker(MAX_TRACKED_SMS_IDS),
        smsCodeParser = capabilities::parseSmsCode,
    )

    private companion object {
        const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
        const val ROUTING_REPAIR_WINDOW_MS = 24 * 60 * 60 * 1000L
        const val ROUTING_REPAIR_TRIGGER_URI = "content://sms"
        const val MAX_TRACKED_SMS_IDS = 128
    }
}
