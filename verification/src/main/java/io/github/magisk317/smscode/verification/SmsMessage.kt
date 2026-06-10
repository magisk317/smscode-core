package io.github.magisk317.smscode.verification

interface SmsMessage {
    val sender: String?
    val body: String?
    val date: Long
    val smsCode: String?
    val packageName: String?
    val company: String?
    val simSlot: Int
        get() = -1
    val subId: Int
        get() = 0
}
