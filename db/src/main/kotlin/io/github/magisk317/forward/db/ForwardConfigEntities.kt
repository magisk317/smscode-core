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
    @ColumnInfo(name = "created_at", defaultValue = "0") var createdAt: Long = 0,
)

/** Per-package forwarding preference, imported 1:1 from the legacy `AppInfo` table. */
@Entity(tableName = "forward_app_info")
data class ForwardAppInfoEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "label") val label: String? = null,
    @ColumnInfo(name = "blocked") val blocked: Boolean = false,
    @ColumnInfo(name = "forwarding") val forwarding: Boolean = false,
    @ColumnInfo(name = "forwarding_configured") val forwardingConfigured: Boolean = false,
    @ColumnInfo(name = "notify_template") val notifyTemplate: String = "",
)

/** Legacy forwarding rule, imported 1:1 from the `Rule` table. */
@Entity(tableName = "forward_rule")
@TypeConverters(ForwardDateConverter::class)
data class ForwardRuleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "type", defaultValue = "sms") val type: String = "sms",
    @ColumnInfo(name = "filed", defaultValue = "transpond_all") val filed: String = "transpond_all",
    @ColumnInfo(name = "check", defaultValue = "is") val check: String = "is",
    @ColumnInfo(name = "value", defaultValue = "") val value: String = "",
    @ColumnInfo(name = "sender_id", defaultValue = "0") val senderId: Long = 0,
    @ColumnInfo(name = "sms_template", defaultValue = "") val smsTemplate: String = "",
    @ColumnInfo(name = "regex_replace", defaultValue = "") val regexReplace: String = "",
    @ColumnInfo(name = "sim_slot", defaultValue = "ALL") val simSlot: String = "ALL",
    @ColumnInfo(name = "status", defaultValue = "1") val status: Int = 1,
    @ColumnInfo(name = "time") val time: Date = Date(),
    @ColumnInfo(name = "sender_list_json", defaultValue = "[]") val senderListJson: String = "[]",
    @ColumnInfo(name = "sender_logic", defaultValue = "ALL") val senderLogic: String = "ALL",
    @ColumnInfo(name = "silent_period_start", defaultValue = "0") val silentPeriodStart: Int = 0,
    @ColumnInfo(name = "silent_period_end", defaultValue = "0") val silentPeriodEnd: Int = 0,
    @ColumnInfo(name = "silent_day_of_week", defaultValue = "") val silentDayOfWeek: String = "",
    @ColumnInfo(name = "title", defaultValue = "") val title: String = "",
)

/** Key/value bookkeeping for one-time forward database maintenance. */
@Entity(tableName = "forward_meta")
data class ForwardMetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)
