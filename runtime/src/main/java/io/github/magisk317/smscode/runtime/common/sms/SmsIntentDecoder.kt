package io.github.magisk317.smscode.runtime.common.sms

import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import io.github.magisk317.smscode.domain.utils.SmsMessageAssembler
import io.github.magisk317.smscode.domain.utils.SmsMessagePart

data class DecodedSmsMessage(
    val sender: String?,
    val body: String,
    val timestampMillis: Long,
)

object SmsIntentDecoder {
    @JvmStatic
    fun decode(intent: Intent): DecodedSmsMessage? {
        val messageParts = messagesFromIntent(intent)
        val firstMessage = messageParts.firstOrNull() ?: return null
        return DecodedSmsMessage(
            sender = firstMessage.displayOriginatingAddress,
            body = assembleBody(messageParts),
            timestampMillis = firstMessage.timestampMillis,
        )
    }

    @JvmStatic
    fun decodeBody(intent: Intent): String = assembleBody(messagesFromIntent(intent))

    internal fun messagesFromIntent(intent: Intent): Array<SmsMessage> =
        Telephony.Sms.Intents.getMessagesFromIntent(intent)

    internal fun assembleBody(messageParts: Array<SmsMessage>): String =
        SmsMessageAssembler.getMessageBody(
            messageParts.map { SmsMessagePart(it.displayMessageBody.orEmpty()) },
        )
}
