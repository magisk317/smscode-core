package io.github.magisk317.smscode.runtime.common.record

interface SmsMsgRecord {
    val id: Long?
    val sender: String?
    val body: String?
    val date: Long
    val company: String?
    val smsCode: String?
    val packageName: String?
    val notifyChannelId: String
    val forwardStatus: Int
    val forwardTarget: String?
    val forwardMessage: String?
    val forwardTime: Long
    val msgType: Int
    val callType: Int
}
