package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage

import android.content.Context
import android.os.Bundle
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

class RecordSmsActionHelper<M : SmsMessage>(
    private val pluginContext: Context,
    private val smsMsg: M,
    private val eventId: String = "",
    private val enabled: Boolean,
    private val deduplicateEnabled: Boolean,
    private val withFileLock: (Context, String, () -> Boolean) -> Boolean? = { _, _, block -> block() },
    private val shouldSkipByDedup: (M, String) -> Boolean,
    private val primaryInserter: (M) -> InsertResult,
    private val fallbackExporter: ((M) -> Boolean)? = null,
) {
    data class InsertResult(
        val success: Boolean,
        val duplicate: Boolean = false,
        val detail: String? = null,
        val error: String? = null,
        val errorClass: String? = null,
    )

    fun run(): Bundle? {
        val startedAt = System.nanoTime()
        fun emit(
            result: String,
            statusOk: Boolean = true,
            reason: String? = null,
            extra: Map<String, String> = emptyMap(),
        ) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
            )
            if (eventId.isNotBlank()) {
                attrs["event_id"] = eventId.take(MAX_ATTRIBUTE_VALUE_LENGTH)
            }
            if (reason != null) {
                attrs["reason"] = reason
            }
            extra.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    attrs[key] = value.take(MAX_ATTRIBUTE_VALUE_LENGTH)
                }
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
        recordSmsMsg(
            smsMsg,
            emit = { result, statusOk, reason, extra -> emit(result, statusOk, reason, extra) },
        )
        return null
    }

    private fun recordSmsMsg(
        smsMsg: M,
        emit: (result: String, statusOk: Boolean, reason: String?, extra: Map<String, String>) -> Unit,
    ) {
        val eventLabel = eventId.ifBlank { "<none>" }
        XLog.i(
            message = "Diag record start: event_id=%s sender_hash=%s body_len=%d code_present=%s simSlot=%d subId=%d",
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
                    emit("skip", true, "duplicate", emptyMap())
                }
                return
            }
            if (shouldSkipByDedup(smsMsg, eventLabel)) {
                emit("skip", true, "duplicate", emptyMap())
                return
            }
        }
        persistSmsMsg(smsMsg, eventLabel, emit)
    }

    private fun persistSmsMsg(
        smsMsg: M,
        eventLabel: String,
        emit: (result: String, statusOk: Boolean, reason: String?, extra: Map<String, String>) -> Unit,
    ) {
        val insertResult = primaryInserter(smsMsg)
        if (insertResult.success) {
            if (insertResult.duplicate) {
                XLog.i(
                    message = "Diag record provider duplicate: event_id=%s %s",
                    eventLabel,
                    insertResult.detail ?: "",
                )
                emit("skip", true, "duplicate", emptyMap())
                return
            }
            XLog.i(
                message = "Diag record provider insert success: event_id=%s %s",
                eventLabel,
                insertResult.detail ?: "",
            )
            emit("ok", true, "provider", emptyMap())
            return
        }
        XLog.w(
            message = "Diag record provider insert failed: event_id=%s err=%s",
            eventLabel,
            insertResult.error ?: insertResult.detail ?: "unknown",
        )
        if (fallbackExporter?.invoke(smsMsg) == true) {
            XLog.i(message = "Diag record file fallback success: event_id=%s", eventLabel)
            emit("ok", true, "fallback", emptyMap())
        } else {
            XLog.w(message = "Diag record file fallback failed: event_id=%s", eventLabel)
            emit(
                "error",
                false,
                "insert_failed",
                recordSmsInsertFailureAttributes(insertResult),
            )
        }
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    private companion object {
        private const val SHARED_RECORD_DEDUP_FILE_NAME = "record_insert_gate"
        private const val MAX_ATTRIBUTE_VALUE_LENGTH = 160
    }
}

internal fun recordSmsInsertFailureAttributes(
    insertResult: RecordSmsActionHelper.InsertResult,
): Map<String, String> {
    val error = insertResult.error ?: insertResult.detail ?: "unknown"
    val errorMessage = error
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(160)
    return mapOf(
        "error" to error,
        "error_msg" to errorMessage,
        "error_class" to (insertResult.errorClass ?: "unknown"),
    )
}
