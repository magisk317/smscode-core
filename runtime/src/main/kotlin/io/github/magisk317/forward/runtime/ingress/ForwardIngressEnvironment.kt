package io.github.magisk317.forward.runtime.ingress

import io.github.magisk317.forward.engine.event.RelayEvent

/** Neutral outcome of handing one event to the product pipeline. */
data class ForwardPipelineOutcome(
    val dispatched: Boolean,
    val blockedReason: String? = null,
    val dispatchError: String? = null,
)

/** Neutral blacklist hit record persisted by the product. */
data class BlacklistHitRecord(
    val eventId: String,
    val source: String,
    val sender: String? = null,
    val body: String? = null,
    val smsDate: Long = 0L,
    val matchType: String? = null,
    val pattern: String? = null,
    val actionDelete: Boolean = false,
    val actionBlock: Boolean = false,
    val blockReason: String? = null,
    val createdAt: Long = 0L,
)

/** Neutral app-forwarding gate state for one package. */
data class AppForwardingState(
    val forwarding: Boolean,
    val forwardingConfigured: Boolean,
)

/** A telephony notification body reclassified into code + company by the product rule catalog. */
data class TelephonyCodeMatch(
    val code: String,
    val company: String,
)

/** Trace sink for the ingest flows; the product decides where the log lands. */
interface ForwardIngressTrace {
    fun d(traceId: String?, message: String)

    fun i(traceId: String?, message: String)

    fun w(traceId: String?, message: String)

    fun e(traceId: String?, message: String, throwable: Throwable? = null)
}

/**
 * The product seam behind the shared ingest receivers.
 *
 * The receivers own ordering, token validation, payload limits, dedup and
 * call-session policy; everything that touches product storage, settings,
 * contacts or the dispatch pipeline goes through this interface. Registered
 * once from the application composition root.
 */
interface ForwardIngressEnvironment {
    val trace: ForwardIngressTrace

    /** The namespaced forward broadcast action this receiver accepts. */
    val forwardAction: String

    /** The namespaced custom-message broadcast action this receiver accepts. */
    val customMessageAction: String

    /** Expected IPC token; blank means the receiver-side legacy compat bypass applies. */
    suspend fun expectedIpcToken(): String

    /** Configurable dedup window (seconds) for identical verification codes, already clamped. */
    fun smsCodeDedupWindowSeconds(): Int

    fun isAppNotifyMessageTypeEnabled(): Boolean

    fun isForwardAppNotifyEnabled(): Boolean

    /** Per-package forwarding gate; null when the package is unknown to the product. */
    suspend fun appForwardingState(packageName: String): AppForwardingState?

    suspend fun recordBlacklistHit(record: BlacklistHitRecord): Long?

    fun resolveContactName(sender: String): String

    fun resolvePhoneArea(sender: String): String

    /** Product rule-catalog pass over notification content; null when no code matches. */
    suspend fun parseTelephonyCode(content: String): TelephonyCodeMatch?

    suspend fun processEvent(event: RelayEvent, traceId: String): ForwardPipelineOutcome

    companion object {
        @Volatile
        private var instance: ForwardIngressEnvironment? = null

        /** Register the application-wide implementation, once from the composition root. */
        fun register(environment: ForwardIngressEnvironment) {
            instance = environment
        }

        fun get(): ForwardIngressEnvironment =
            instance ?: error(
                "ForwardIngressEnvironment not registered; call register() from the application composition root",
            )
    }
}
