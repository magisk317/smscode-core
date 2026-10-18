package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.withTimeout

class SmsParseAction<M>(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsIntent: Intent?,
    private val deduplicateEnabled: Boolean,
    private val incomingSmsParser: (Intent) -> M,
    private val sensitiveDebugLogReader: (Context) -> Boolean,
    private val summarizeSender: (Boolean, String?) -> String,
    private val summarizeBody: (Boolean, String?) -> String,
    private val summarizeCode: (Boolean, String?) -> String,
    private val duplicateChecker: suspend (String?, String, Long) -> Boolean,
    private val preparedSmsResolver: suspend (Context, Context, M, Intent, Long) -> M?,
    // Per-step soft timeout for the suspending duplicate check / prepared-SMS resolution. Kept well
    // below the runner's wall-clock timeout so a well-behaved (cancellation-cooperative) suspend
    // point aborts on its own. See the NOTE on [runCancellable] for what this can and can not do.
    private val stepTimeoutMs: Long = DEFAULT_STEP_TIMEOUT_MS,
) where M : SmsMessage, M : Parcelable {
    data class Outcome<M>(
        val smsMsg: M? = null,
        val duplicated: Boolean = false,
    ) {
        fun toBundle(): Bundle? {
            if (smsMsg == null && !duplicated) return null
            return Bundle().apply {
                putBoolean(KEY_SMS_DUPLICATED, duplicated)
                smsMsg?.let { putParcelable(KEY_SMS_MSG, it as Parcelable) }
            }
        }
    }

    fun parse(): Outcome<M>? {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, extra: Map<String, String> = emptyMap()) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            MagiskOtel.event(
                name = "sms.parse",
                attributes = mapOf(
                    "result" to result,
                    "duration_ms" to durationMs.toString(),
                    "process" to "hook",
                ) + extra,
                statusOk = statusOk,
            )
        }

        val intent = smsIntent ?: run {
            emit(result = "skip", extra = mapOf("reason" to "missing_intent"))
            return null
        }
        val smsMsg = incomingSmsParser(intent)
        XLog.i(
            message = "Diag SMS parsed from intent: senderPresent=%s, bodyLength=%d, timestamp=%d simSlot=%d subId=%d",
            !smsMsg.sender.isNullOrBlank(),
            smsMsg.body?.length ?: 0,
            smsMsg.date,
            smsMsg.simSlot,
            smsMsg.subId,
        )

        val sender = smsMsg.sender
        val msgBody = smsMsg.body
        val sensitiveDebugLog = sensitiveDebugLogReader(pluginContext)

        XLog.d(message = "Sender: %s", summarizeSender(sensitiveDebugLog, sender))
        XLog.d(message = "Body: %s", summarizeBody(sensitiveDebugLog, msgBody))

        if (sender.isNullOrBlank() || msgBody.isNullOrBlank()) {
            XLog.w("Diag SMS parse aborted: sender/body is empty")
            emit(result = "skip", extra = mapOf("reason" to "empty_sender_or_body"))
            return null
        }

        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()
        XLog.d(message = "Diag SMS body: %s", summarizeBody(sensitiveDebugLog, msgBody))

        if (deduplicateEnabled) {
            val duplicated = runCancellable("duplicateChecker", default = false) {
                duplicateChecker(sender, msgBody, timestamp)
            }
            if (duplicated) {
                XLog.i("Duplicate SMS detected by fingerprint, skip parsing.")
                emit(result = "skip", extra = mapOf("reason" to "duplicate"))
                return Outcome(duplicated = true)
            }
        }

        val resolvedSmsMsg = runCancellable("preparedSmsResolver", default = null) {
            preparedSmsResolver(
                pluginContext,
                phoneContext,
                smsMsg,
                intent,
                timestamp,
            )
        } ?: run {
            XLog.d(
                message = "Diag SMS parsed but no code matched, body=%s",
                summarizeBody(sensitiveDebugLog, msgBody),
            )
            emit(result = "skip", extra = mapOf("reason" to "no_code_match"))
            return null
        }

        val smsCode = resolvedSmsMsg.smsCode.orEmpty()
        XLog.i(
            message = "Diag SMS code matched: companyPresent=%s, codeLength=%d, code=%s, body=%s simSlot=%d subId=%d",
            !resolvedSmsMsg.company.isNullOrBlank(),
            smsCode.length,
            summarizeCode(sensitiveDebugLog, smsCode),
            summarizeBody(sensitiveDebugLog, msgBody),
            resolvedSmsMsg.simSlot,
            resolvedSmsMsg.subId,
        )
        emit(
            result = "ok",
            extra = mapOf(
                "code_length" to smsCode.length.toString(),
                "company_present" to (!resolvedSmsMsg.company.isNullOrBlank()).toString(),
            ),
        )
        return Outcome(
            smsMsg = resolvedSmsMsg,
            duplicated = false,
        )
    }

    /**
     * Run a suspending step inside a [runBlocking] scope guarded by [withTimeout].
     *
     * NOTE (honest limitation): [withTimeout] only cancels at coroutine *suspension points*. The
     * real work inside [duplicateChecker] / [preparedSmsResolver] is a blocking Binder / cross-process
     * `ContentResolver.query` call, which does NOT suspend and does NOT observe cancellation while it
     * is in-flight. When the peer process is frozen, the blocking call itself can still sit on this
     * thread for the full Binder timeout regardless of [withTimeout].
     *
     * What this DOES buy us:
     *  - If the resolver is written to cooperate (uses suspending IO or checks `isActive` between
     *    hops), the timeout aborts it promptly with [TimeoutCancellationException].
     *  - On timeout / failure we return [default] instead of propagating, so [parse] finishes and the
     *    caller (runner) is never left waiting on this step's result.
     *
     * The hard guarantee against a hung thread starving the next SMS lives in [SmsParseActionRunner],
     * which runs the whole [parse] on a dedicated, disposable thread (never the shared executor).
     */
    private fun <T> runCancellable(step: String, default: T, block: suspend () -> T): T =
        runBlocking {
            try {
                withTimeout(stepTimeoutMs) { block() }
            } catch (error: TimeoutCancellationException) {
                XLog.w(
                    message = "Diag SMS parse step '%s' timed out after %dms: %s",
                    step,
                    stepTimeoutMs,
                    error.message ?: error.javaClass.simpleName,
                )
                default
            }
        }

    companion object {
        const val KEY_SMS_MSG = "sms_msg"
        const val KEY_SMS_DUPLICATED = "sms_duplicated"

        /**
         * Default soft timeout for each suspending step. Deliberately shorter than
         * [SmsParseActionRunner.DEFAULT_TIMEOUT_MS] so a cooperative step aborts before the runner's
         * wall-clock timeout fires.
         */
        const val DEFAULT_STEP_TIMEOUT_MS = 8_000L
    }
}
