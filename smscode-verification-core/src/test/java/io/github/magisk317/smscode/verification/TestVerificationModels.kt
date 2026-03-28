package io.github.magisk317.smscode.verification

internal data class TestSmsMessage(
    override val sender: String? = null,
    override val body: String? = null,
    override val date: Long = 0L,
    override val smsCode: String? = null,
    override val packageName: String? = null,
    override val company: String? = null,
) : SmsMessage

internal data class TestParseResult(
    override val isBlockSms: Boolean = false,
) : SmsParseResult
