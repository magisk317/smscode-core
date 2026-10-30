package io.github.magisk317.smscode.runtime.contract.record

import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecord

object SmsMsgCursorContract {
    val defaultColumns = arrayOf(
        "_id",
        "sender",
        "body",
        "date",
        "processed_time",
        "company",
        "sms_code",
        "package_name",
        "notify_channel_id",
        "sim_slot",
        "sub_id",
        "msg_type",
        "call_type",
    )

    fun valueFromRecord(record: SmsMsgRecord, column: String): Any? =
        when (column.lowercase()) {
            "_id", "id" -> record.id
            "sender" -> record.sender
            "body" -> record.body
            "date" -> record.date
            "processed_time" -> record.processedTime
            "company" -> record.company
            "sms_code" -> record.smsCode
            "package_name" -> record.packageName
            "notify_channel_id" -> record.notifyChannelId
            "sim_slot" -> record.simSlot
            "sub_id" -> record.subId
            "msg_type" -> record.msgType
            "call_type" -> record.callType
            else -> null
        }
}
