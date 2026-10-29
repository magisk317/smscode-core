package io.github.magisk317.forward.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One sender dispatch attempt against a forward event. */
@Entity(
    tableName = "forward_sender_dispatch_log",
    indices = [
        Index(value = ["record_id"]),
        Index(value = ["sender_id"]),
        Index(value = ["created_at"]),
    ],
)
data class SenderDispatchLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    /** Forward event this dispatch belongs to (legacy: the sms_msg row id). */
    @ColumnInfo(name = "record_id")
    val recordId: Long? = null,
    @ColumnInfo(name = "sender_id")
    val senderId: Long,
    @ColumnInfo(name = "sender_type")
    val senderType: Int,
    @ColumnInfo(name = "msg_type")
    val msgType: Int,
    @ColumnInfo(name = "success")
    val success: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
