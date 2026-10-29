package io.github.magisk317.forward.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardEventDao {

    @Insert
    suspend fun insert(event: ForwardEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<ForwardEventEntity>): List<Long>

    @Query("SELECT * FROM forward_event WHERE id = :id")
    suspend fun byId(id: Long): ForwardEventEntity?

    @Query("SELECT * FROM forward_event ORDER BY date DESC")
    fun observeAll(): Flow<List<ForwardEventEntity>>

    @Query("SELECT * FROM forward_event ORDER BY date DESC")
    suspend fun getAll(): List<ForwardEventEntity>

    @Query("SELECT * FROM forward_event ORDER BY date DESC")
    fun getAllFlow(): Flow<List<ForwardEventEntity>>

    @Query("SELECT COUNT(*) FROM forward_event")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM forward_event")
    fun countFlow(): Flow<Long>

    @Query("DELETE FROM forward_event WHERE id = :id")
    suspend fun delete(id: Long)

    @Delete
    suspend fun delete(event: ForwardEventEntity)

    @Query("DELETE FROM forward_event")
    suspend fun clearAll()

    @Update
    suspend fun update(event: ForwardEventEntity)

    @Delete
    suspend fun deleteInTx(events: List<ForwardEventEntity>)

    @Query(
        "SELECT * FROM forward_event WHERE sender IS :sender AND body IS :body AND date = :date AND msg_type = :msgType LIMIT 1",
    )
    suspend fun getByFingerprint(sender: String?, body: String?, date: Long, msgType: Int): ForwardEventEntity?

    @Query("SELECT * FROM forward_event WHERE msg_type = :msgType AND session_key = :sessionKey LIMIT 1")
    suspend fun getBySessionKey(msgType: Int, sessionKey: String): ForwardEventEntity?

    @Query(
        "SELECT * FROM forward_event WHERE sender = :sender AND body = :body AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    suspend fun getByFingerprintInRange(sender: String, body: String, msgType: Int, dateFrom: Long, dateTo: Long): ForwardEventEntity?

    @Query(
        "SELECT * FROM forward_event WHERE sim_slot = :simSlot AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    suspend fun getBySimSlotInRange(simSlot: Int, msgType: Int, dateFrom: Long, dateTo: Long): ForwardEventEntity?

    @Query(
        "SELECT * FROM forward_event WHERE sms_code = :smsCode AND package_name = :packageName AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    suspend fun getByCodeAndPackageInRange(smsCode: String, packageName: String, msgType: Int, dateFrom: Long, dateTo: Long): ForwardEventEntity?

    @Query(
        "SELECT * FROM forward_event WHERE sms_code = :smsCode AND company = :company AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    suspend fun getByCodeAndCompanyInRange(smsCode: String, company: String, msgType: Int, dateFrom: Long, dateTo: Long): ForwardEventEntity?

    @Query(
        "SELECT * FROM forward_event WHERE sms_code = :smsCode AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo",
    )
    suspend fun getByCodeInRange(smsCode: String, msgType: Int, dateFrom: Long, dateTo: Long): List<ForwardEventEntity>

    @Query(
        "SELECT notify_channel_id FROM forward_event WHERE package_name = :packageName AND msg_type = :msgType AND notify_channel_id != '' GROUP BY notify_channel_id ORDER BY MAX(date) DESC LIMIT :limit",
    )
    fun observeRecentNotifyChannelIds(packageName: String, msgType: Int, limit: Int): Flow<List<String>>
}

@Dao
interface SenderDispatchLogDao {

    @Insert
    suspend fun insert(entry: SenderDispatchLogEntity): Long

    @Insert
    suspend fun insertAll(entries: List<SenderDispatchLogEntity>): List<Long>

    @Query("SELECT * FROM forward_sender_dispatch_log WHERE record_id = :recordId ORDER BY created_at")
    suspend fun byRecordId(recordId: Long): List<SenderDispatchLogEntity>

    @Query("SELECT COUNT(*) FROM forward_sender_dispatch_log")
    suspend fun count(): Int
}

@Dao
interface ForwardSenderDao {
    @Query("SELECT * FROM forward_sender ORDER BY priority DESC, id")
    fun observeAll(): Flow<List<ForwardSenderEntity>>

    @Query("SELECT * FROM forward_sender ORDER BY priority DESC, id")
    suspend fun all(): List<ForwardSenderEntity>

    @Query("SELECT * FROM forward_sender WHERE id = :id")
    suspend fun byId(id: Long): ForwardSenderEntity?

    @Query("SELECT * FROM forward_sender WHERE status = 1 ORDER BY priority DESC, id")
    suspend fun enabled(): List<ForwardSenderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sender: ForwardSenderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(senders: List<ForwardSenderEntity>)

    @Update
    suspend fun update(sender: ForwardSenderEntity)

    @Delete
    suspend fun delete(sender: ForwardSenderEntity)

    @Query("SELECT COUNT(*) FROM forward_sender")
    suspend fun count(): Int
}

@Dao
interface ForwardRouteRuleDao {
    @Query("SELECT * FROM forward_route_rule ORDER BY update_time DESC")
    fun observeAll(): Flow<List<ForwardRouteRuleEntity>>

    @Query("SELECT * FROM forward_route_rule")
    suspend fun all(): List<ForwardRouteRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ForwardRouteRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<ForwardRouteRuleEntity>)

    @Delete
    suspend fun delete(rule: ForwardRouteRuleEntity)

    @Query("SELECT COUNT(*) FROM forward_route_rule")
    suspend fun count(): Int
}

@Dao
interface ForwardFilterRuleDao {
    @Query("SELECT * FROM forward_filter_rule WHERE enabled = 1")
    suspend fun enabled(): List<ForwardFilterRuleEntity>

    @Query("SELECT * FROM forward_filter_rule")
    suspend fun all(): List<ForwardFilterRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ForwardFilterRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<ForwardFilterRuleEntity>)

    @Delete
    suspend fun delete(rule: ForwardFilterRuleEntity)

    @Query("SELECT COUNT(*) FROM forward_filter_rule")
    suspend fun count(): Int
}

@Dao
interface ForwardScheduledTaskDao {
    @Query("SELECT * FROM forward_scheduled_task ORDER BY id")
    fun observeAll(): Flow<List<ForwardScheduledTaskEntity>>

    @Query("SELECT * FROM forward_scheduled_task")
    suspend fun all(): List<ForwardScheduledTaskEntity>

    @Query("SELECT * FROM forward_scheduled_task WHERE status = 1")
    suspend fun active(): List<ForwardScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ForwardScheduledTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<ForwardScheduledTaskEntity>)

    @Delete
    suspend fun delete(task: ForwardScheduledTaskEntity)

    @Query("SELECT COUNT(*) FROM forward_scheduled_task")
    suspend fun count(): Int
}
