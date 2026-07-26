package io.github.magisk317.smscode.verification

import android.content.Context
import android.os.Bundle
import io.github.magisk317.xposed.logging.MagiskOtel

class RecordSmsActionHelper<M : SmsMessage>(
    private val pluginContext: Context,
    private val smsMsg: M,
    private val eventId: String = "",
    private val enabled: Boolean,
    private val deduplicateEnabled: Boolean,
    private val withFileLock: (Context, String, () -> Boolean) -> Boolean?,
    private val shouldSkipByDedup: (M, String) -> Boolean,
    private val primaryInserter: (M) -> InsertResult,
    private val fallbackExporter: (M) -> Boolean,
) {
    data class InsertResult(
        val success: Boolean,
        val detail: String? = null,
        val error: String? = null,
    )

    fun run(): Bundle? {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
            )
            if (reason != null) {
                attrs["reason"] = reason
            }
            MagiskOtel.event(
                name = "sms.record",
                attributes = attrs,
                statusOk = statusOk,
            )
        }

        if (!enabled) {
            emit(result = "skip", reason = "disabled")
            return null
        }
        recordSmsMsg(smsMsg, emit = { result, statusOk, reason -> emit(result, statusOk, reason) })
        return null
    }

    private fun recordSmsMsg(
        smsMsg: M,
        emit: (result: String, statusOk: Boolean, reason: String?) -> Unit,
    ) {
        val eventLabel = eventId.ifBlank { "<none>" }
        XLog.i(
            "Diag record start: event_id=%s sender_hash=%s body_len=%d code_present=%s simSlot=%d subId=%d",
            eventLabel,
            senderHash(smsMsg.sender),
            smsMsg.body?.length ?: 0,
            !smsMsg.smsCode.isNullOrBlank(),
            smsMsg.simSlot,
            smsMsg.subId,
        )
        if (deduplicateEnabled) {
            val locked = withFileLock(pluginContext, SHARED_RECORD_DEDUP_FILE_NAME) {
                if (shouldSkipByDedup(smsMsg, eventLabel)) {
                    return@withFileLock false
                }
                persistSmsMsg(smsMsg, eventLabel, emit)
                true
            }
            if (locked != null) {
                if (!locked) {
                    emit("skip", true, "duplicate")
                }
                return
            }
            if (shouldSkipByDedup(smsMsg, eventLabel)) {
                emit("skip", true, "duplicate")
                return
            }
        }
        persistSmsMsg(smsMsg, eventLabel, emit)
    }

    private fun persistSmsMsg(
        smsMsg: M,
        eventLabel: String,
        emit: (result: String, statusOk: Boolean, reason: String?) -> Unit,
    ) {
        val insertResult = primaryInserter(smsMsg)
        if (insertResult.success) {
            XLog.i(
                "Diag record provider insert success: event_id=%s %s",
                eventLabel,
                insertResult.detail ?: "",
            )
            emit("ok", true, "provider")
            return
        }
        XLog.w(
            "Diag record provider insert failed: event_id=%s err=%s",
            eventLabel,
            insertResult.error ?: insertResult.detail ?: "unknown",
        )
        if (fallbackExporter(smsMsg)) {
            XLog.i("Diag record file fallback success: event_id=%s", eventLabel)
            emit("ok", true, "fallback")
        } else {
            XLog.w("Diag record file fallback failed: event_id=%s", eventLabel)
            emit("error", false, "insert_failed")
        }
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    private companion object {
        private const val SHARED_RECORD_DEDUP_FILE_NAME = "record_insert_gate"
    }
}
