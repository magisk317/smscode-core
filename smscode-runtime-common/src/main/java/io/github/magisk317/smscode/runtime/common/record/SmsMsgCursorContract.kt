package io.github.magisk317.smscode.runtime.common.record

object SmsMsgCursorContract {
    val defaultColumns = arrayOf(
        "_id",
        "sender",
        "body",
        "date",
        "company",
        "sms_code",
        "package_name",
        "notify_channel_id",
        "msg_type",
        "call_type",
        "forward_status",
        "forward_target",
        "forward_message",
        "forward_time",
    )

    fun valueFromRecord(record: SmsMsgRecord, column: String): Any? =
        when (column.lowercase()) {
            "_id", "id" -> record.id
            "sender" -> record.sender
            "body" -> record.body
            "date" -> record.date
            "company" -> record.company
            "sms_code" -> record.smsCode
            "package_name" -> record.packageName
            "notify_channel_id" -> record.notifyChannelId
            "msg_type" -> record.msgType
            "call_type" -> record.callType
            "forward_status" -> record.forwardStatus
            "forward_target" -> record.forwardTarget
            "forward_message" -> record.forwardMessage
            "forward_time" -> record.forwardTime
            else -> null
        }
}
