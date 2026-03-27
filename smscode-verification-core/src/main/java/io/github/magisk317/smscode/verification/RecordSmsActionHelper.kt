package io.github.magisk317.smscode.verification

import android.content.Context
import android.os.Bundle
import io.github.magisk317.smscode.xposed.utils.XLog

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
        if (enabled) {
            recordSmsMsg(smsMsg)
        }
        return null
    }

    private fun recordSmsMsg(smsMsg: M) {
        val eventLabel = eventId.ifBlank { "<none>" }
        XLog.w(
            "Diag record start: event_id=%s sender_hash=%s body_len=%d code_present=%s",
            eventLabel,
            senderHash(smsMsg.sender),
            smsMsg.body?.length ?: 0,
            !smsMsg.smsCode.isNullOrBlank(),
        )
        if (deduplicateEnabled) {
            val locked = withFileLock(pluginContext, SHARED_RECORD_DEDUP_FILE_NAME) {
                if (shouldSkipByDedup(smsMsg, eventLabel)) {
                    return@withFileLock false
                }
                persistSmsMsg(smsMsg, eventLabel)
                true
            }
            if (locked != null) {
                return
            }
            if (shouldSkipByDedup(smsMsg, eventLabel)) {
                return
            }
        }
        persistSmsMsg(smsMsg, eventLabel)
    }

    private fun persistSmsMsg(smsMsg: M, eventLabel: String) {
        val insertResult = primaryInserter(smsMsg)
        if (insertResult.success) {
            XLog.w(
                "Diag record provider insert success: event_id=%s %s",
                eventLabel,
                insertResult.detail ?: "",
            )
            return
        }
        XLog.w(
            "Diag record provider insert failed: event_id=%s err=%s",
            eventLabel,
            insertResult.error ?: insertResult.detail ?: "unknown",
        )
        if (fallbackExporter(smsMsg)) {
            XLog.w("Diag record file fallback success: event_id=%s", eventLabel)
        } else {
            XLog.w("Diag record file fallback failed: event_id=%s", eventLabel)
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
