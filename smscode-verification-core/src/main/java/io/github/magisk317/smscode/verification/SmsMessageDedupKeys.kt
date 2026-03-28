package io.github.magisk317.smscode.verification

object SmsMessageDedupKeys {
    fun buildMessageKey(message: SmsMessage): String {
        return buildMessageKey(
            sender = message.sender,
            body = message.body,
            code = message.smsCode,
            packageName = message.packageName,
            company = message.company,
        )
    }

    fun buildMessageKey(
        sender: String?,
        body: String?,
        code: String?,
        packageName: String?,
        company: String?,
    ): String {
        val normalizedSender = sender.orEmpty()
        val normalizedBody = body.orEmpty()
        val normalizedCode = code.orEmpty()
        if (normalizedSender.isBlank() && normalizedBody.isBlank() && normalizedCode.isBlank()) {
            return ""
        }

        val parts = ArrayList<String>(4)
        if (normalizedSender.isNotBlank() && normalizedBody.isNotBlank()) {
            parts += "fp:${hashValue(normalizedSender)}:${hashValue(normalizedBody)}"
        }
        if (normalizedCode.isNotBlank()) {
            val channel = when {
                !packageName.isNullOrBlank() -> "pkg:$packageName"
                !company.isNullOrBlank() -> "co:$company"
                else -> "co:unknown"
            }
            parts += "code:$normalizedCode|$channel"
        }
        return parts.joinToString("|")
    }

    fun buildObservedKey(
        smsId: Long,
        date: Long,
        sender: String?,
        body: String?,
        code: String?,
    ): String {
        val normalizedCode = code.orEmpty()
        if (smsId > 0) {
            return "id:$smsId|date:$date|code:$normalizedCode"
        }
        return "fp:${hashValue(sender.orEmpty())}:${hashValue(body.orEmpty())}|date:$date|code:$normalizedCode"
    }

    private fun hashValue(value: String): String = Integer.toHexString(value.hashCode())
}
