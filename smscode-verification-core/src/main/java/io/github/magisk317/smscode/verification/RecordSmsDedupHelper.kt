package io.github.magisk317.smscode.verification

import io.github.magisk317.smscode.xposed.utils.XLog

object RecordSmsDedupHelper {
    fun <M : SmsMessage> shouldSkipByWindow(
        smsMsg: M,
        eventLabel: String,
        windowMs: Long = DEFAULT_DEDUP_WINDOW_MS,
        hasFingerprintDuplicate: (sender: String, body: String, from: Long, to: Long) -> Boolean,
        hasCodeDuplicateInWindow: (code: String, from: Long, to: Long) -> Boolean = { _, _, _ -> false },
        hasCodeDuplicateByPackage: (code: String, packageName: String, from: Long, to: Long) -> Boolean,
        hasCodeDuplicateByCompany: (code: String, company: String, from: Long, to: Long) -> Boolean,
    ): Boolean {
        val sender = smsMsg.sender
        val body = smsMsg.body
        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()
        val from = (timestamp - windowMs).coerceAtLeast(0L)
        val to = timestamp + windowMs

        val code = smsMsg.smsCode
        if (!code.isNullOrBlank()) {
            if (hasCodeDuplicateInWindow(code, from, to)) {
                XLog.w("Diag record dedup skip: reason=code_window event_id=%s", eventLabel)
                return true
            }

            val pkg = smsMsg.packageName
            if (!pkg.isNullOrBlank() && hasCodeDuplicateByPackage(code, pkg, from, to)) {
                XLog.w("Diag record dedup skip: reason=code_package event_id=%s", eventLabel)
                return true
            }

            val company = smsMsg.company
            if (!company.isNullOrBlank() && hasCodeDuplicateByCompany(code, company, from, to)) {
                XLog.w("Diag record dedup skip: reason=code_company event_id=%s", eventLabel)
                return true
            }
        }

        if (!sender.isNullOrBlank() && !body.isNullOrBlank() && hasFingerprintDuplicate(sender, body, from, to)) {
            XLog.w("Diag record dedup skip: reason=fingerprint_window event_id=%s", eventLabel)
            return true
        }

        return false
    }

    private const val DEFAULT_DEDUP_WINDOW_MS = 20_000L
}
