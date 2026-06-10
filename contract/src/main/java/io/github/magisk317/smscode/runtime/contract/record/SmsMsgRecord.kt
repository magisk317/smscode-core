package io.github.magisk317.smscode.runtime.contract.record

interface SmsMsgRecord {
    val id: Long?
    val sender: String?
    val body: String?
    val date: Long
    val processedTime: Long
    val company: String?
    val smsCode: String?
    val packageName: String?
    val notifyChannelId: String
    val simSlot: Int
        get() = -1
    val subId: Int
        get() = 0
    val forwardStatus: Int
    val forwardTarget: String?
    val forwardMessage: String?
    val forwardTime: Long
    val msgType: Int
    val callType: Int
}
