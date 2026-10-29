package io.github.magisk317.forward.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The forward-domain record of one ingested message event (sms / app notify /
 * call notify) with its delivery outcome. This is the new home for the
 * forwarding projection that used to ride on the SMS-domain `sms_msg` table.
 */
@Entity(
    tableName = "forward_event",
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["date"]),
        Index(value = ["msg_type", "forward_status"]),
    ],
)
data class ForwardEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    /** Dedup/trace identity from the broadcast; empty for legacy imports. */
    @ColumnInfo(name = "event_id")
    val eventId: String = "",
    @ColumnInfo(name = "msg_type")
    val msgType: Int,
    @ColumnInfo(name = "sender")
    val sender: String? = null,
    @ColumnInfo(name = "body")
    val body: String? = null,
    @ColumnInfo(name = "date")
    val date: Long = 0,
    @ColumnInfo(name = "processed_time", defaultValue = "0")
    val processedTime: Long = 0,
    @ColumnInfo(name = "company")
    val company: String? = null,
    @ColumnInfo(name = "sms_code")
    val smsCode: String? = null,
    @ColumnInfo(name = "package_name")
    val packageName: String? = null,
    @ColumnInfo(name = "notify_channel_id", defaultValue = "''")
    val notifyChannelId: String = "",
    @ColumnInfo(name = "call_type", defaultValue = "0")
    val callType: Int = 0,
    @ColumnInfo(name = "sim_slot", defaultValue = "-1")
    val simSlot: Int = -1,
    @ColumnInfo(name = "sub_id", defaultValue = "0")
    val subId: Int = 0,
    @ColumnInfo(name = "contact_name", defaultValue = "''")
    val contactName: String = "",
    @ColumnInfo(name = "phone_area", defaultValue = "''")
    val phoneArea: String = "",
    @ColumnInfo(name = "session_key", defaultValue = "''")
    val sessionKey: String = "",
    @ColumnInfo(name = "forward_status")
    val forwardStatus: Int = FORWARD_STATUS_NONE,
    @ColumnInfo(name = "forward_target")
    val forwardTarget: String? = null,
    @ColumnInfo(name = "forward_message")
    val forwardMessage: String? = null,
    @ColumnInfo(name = "forward_time")
    val forwardTime: Long? = null,
) {
    companion object {
        const val FORWARD_STATUS_NONE = 0
        const val FORWARD_STATUS_SUCCESS = 1
        const val FORWARD_STATUS_FAILED = 2
        const val FORWARD_STATUS_PARTIAL = 3
        const val FORWARD_STATUS_BLOCKED = 4

        const val MSG_TYPE_SMS = 0
        const val MSG_TYPE_APP_NOTIFY = 1
        const val MSG_TYPE_CALL_NOTIFY = 2
    }
}
