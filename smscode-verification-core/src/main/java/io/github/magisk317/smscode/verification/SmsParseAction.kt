package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import kotlinx.coroutines.runBlocking
import io.github.magisk317.smscode.xposed.utils.XLog

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
        val intent = smsIntent ?: return null
        val smsMsg = incomingSmsParser(intent)
        XLog.w(
            "Diag SMS parsed from intent: senderPresent=%s, bodyLength=%d, timestamp=%d",
            !smsMsg.sender.isNullOrBlank(),
            smsMsg.body?.length ?: 0,
            smsMsg.date,
        )

        val sender = smsMsg.sender
        val msgBody = smsMsg.body
        val sensitiveDebugLog = sensitiveDebugLogReader(pluginContext)

        XLog.d("Sender: %s", summarizeSender(sensitiveDebugLog, sender))
        XLog.d("Body: %s", summarizeBody(sensitiveDebugLog, msgBody))

        if (sender.isNullOrBlank() || msgBody.isNullOrBlank()) {
            XLog.w("Diag SMS parse aborted: sender/body is empty")
            return null
        }

        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()
        XLog.w("Diag SMS body: %s", summarizeBody(sensitiveDebugLog, msgBody))

        if (deduplicateEnabled) {
            val duplicated = runBlocking { duplicateChecker(sender, msgBody, timestamp) }
            if (duplicated) {
                XLog.i("Duplicate SMS detected by fingerprint, skip parsing.")
                return Outcome(duplicated = true)
            }
        }

        val resolvedSmsMsg = runBlocking {
            preparedSmsResolver(
                pluginContext,
                phoneContext,
                smsMsg,
                intent,
                timestamp,
            )
        } ?: run {
            XLog.w(
                "Diag SMS parsed but no code matched, body=%s",
                summarizeBody(sensitiveDebugLog, msgBody),
            )
            return null
        }

        val smsCode = resolvedSmsMsg.smsCode.orEmpty()
        XLog.w(
            "Diag SMS code matched: companyPresent=%s, codeLength=%d, code=%s, body=%s",
            !resolvedSmsMsg.company.isNullOrBlank(),
            smsCode.length,
            summarizeCode(sensitiveDebugLog, smsCode),
            summarizeBody(sensitiveDebugLog, msgBody),
        )
        return Outcome(
            smsMsg = resolvedSmsMsg,
            duplicated = false,
        )
    }

    companion object {
        const val KEY_SMS_MSG = "sms_msg"
        const val KEY_SMS_DUPLICATED = "sms_duplicated"
    }
}
