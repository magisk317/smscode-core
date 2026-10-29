package io.github.magisk317.forward.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

class ForwardDateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}

/** Sender configuration, imported 1:1 from the legacy `Sender` table. */
@Entity(tableName = "forward_sender")
@TypeConverters(ForwardDateConverter::class)
data class ForwardSenderEntity(
    @PrimaryKey @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "type", defaultValue = "1") var type: Int = 1,
    @ColumnInfo(name = "name", defaultValue = "") var name: String,
    @ColumnInfo(name = "json_setting", defaultValue = "") var jsonSetting: String,
    @ColumnInfo(name = "status", defaultValue = "1") var status: Int = 1,
    @ColumnInfo(name = "time") var time: Date = Date(),
    @ColumnInfo(name = "receive_code", defaultValue = "1") var receiveCode: Int = 1,
    @ColumnInfo(name = "receive_non_code", defaultValue = "0") var receiveNonCode: Int = 0,
    @ColumnInfo(name = "receive_app_notify", defaultValue = "1") var receiveAppNotify: Int = 1,
    @ColumnInfo(name = "receive_call_notify", defaultValue = "0") var receiveCallNotify: Int = 0,
    @ColumnInfo(name = "active_schedule_json", defaultValue = "") var activeScheduleJson: String = "",
    @ColumnInfo(name = "priority", defaultValue = "0") var priority: Int = 0,
    @ColumnInfo(name = "custom_template", defaultValue = "") var customTemplate: String = "",
)

/** Per-package/sender routing rule for notifications. */
@Entity(tableName = "forward_route_rule")
data class ForwardRouteRuleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "scope") val scope: Int,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "sender_id") val senderId: Long,
    @ColumnInfo(name = "update_time", defaultValue = "0") val updateTime: Long = 0L,
)

/** Forward filter rule keyed by message type and scope. */
@Entity(tableName = "forward_filter_rule")
data class ForwardFilterRuleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "msg_type") val msgType: String,
    @ColumnInfo(name = "scope_type") val scopeType: String,
    @ColumnInfo(name = "scope_key", defaultValue = "") val scopeKey: String = "",
    @ColumnInfo(name = "sender_id", defaultValue = "0") val senderId: Long = 0L,
    @ColumnInfo(name = "policy") val policy: String,
    @ColumnInfo(name = "match_mode") val matchMode: String,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "enabled", defaultValue = "1") val enabled: Int = 1,
    @ColumnInfo(name = "update_time", defaultValue = "0") val updateTime: Long = 0L,
)

/** Scheduled forward task. */
@Entity(tableName = "forward_scheduled_task")
data class ForwardScheduledTaskEntity(
    @PrimaryKey @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "name", defaultValue = "") var name: String = "",
    @ColumnInfo(name = "task_type", defaultValue = "sms") var taskType: String = "sms",
    @ColumnInfo(name = "cron_expression", defaultValue = "") var cronExpression: String = "",
    @ColumnInfo(name = "sim_slot", defaultValue = "0") var simSlot: Int = 0,
    @ColumnInfo(name = "mobiles", defaultValue = "") var mobiles: String = "",
    @ColumnInfo(name = "content", defaultValue = "") var content: String = "",
    @ColumnInfo(name = "status", defaultValue = "1") var status: Int = 1,
    @ColumnInfo(name = "last_run_time", defaultValue = "0") var lastRunTime: Long = 0,
    @ColumnInfo(name = "next_run_time", defaultValue = "0") var nextRunTime: Long = 0,
)
