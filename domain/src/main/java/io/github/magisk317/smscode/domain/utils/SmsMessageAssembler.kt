package io.github.magisk317.smscode.domain.utils

data class SmsMessagePart(
    val body: String,
)

object SmsMessageAssembler {
    fun getMessageBody(messageParts: Iterable<SmsMessagePart>): String = buildString {
        messageParts.forEach { append(it.body) }
    }
}
