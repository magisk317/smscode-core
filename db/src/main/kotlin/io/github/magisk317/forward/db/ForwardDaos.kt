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
        "SELECT * FROM forward_event WHERE sms_code = :smsCode AND package_name = :packageName " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
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
        "SELECT notify_channel_id FROM forward_event WHERE package_name = :packageName " +
            "AND msg_type = :msgType AND notify_channel_id != '' " +
            "GROUP BY notify_channel_id ORDER BY MAX(date) DESC LIMIT :limit",
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

    @Query("SELECT COUNT(*) FROM forward_sender_dispatch_log WHERE created_at >= :fromMs")
    suspend fun countTotalFrom(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM forward_sender_dispatch_log WHERE created_at >= :fromMs AND success = 1")
    suspend fun countSuccessFrom(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM forward_sender_dispatch_log WHERE created_at >= :fromMs AND success = 0")
    suspend fun countFailedFrom(fromMs: Long): Long

    @Query(
        "SELECT sender_type AS senderType, " +
            "COUNT(*) AS sent, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failed " +
            "FROM forward_sender_dispatch_log " +
            "WHERE created_at >= :fromMs " +
            "GROUP BY sender_type",
    )
    suspend fun aggregateBySenderType(fromMs: Long): List<ForwardSenderDispatchStatRow>
}

data class ForwardSenderDispatchStatRow(
    val senderType: Int,
    val sent: Long,
    val success: Long,
    val failed: Long,
)

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

    @Query("UPDATE forward_sender SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("UPDATE forward_sender SET priority = :priority WHERE id = :id")
    suspend fun updatePriorityById(id: Long, priority: Int)

    @Delete
    suspend fun delete(sender: ForwardSenderEntity)

    @Query("DELETE FROM forward_sender")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM forward_sender")
    suspend fun count(): Int
}

@Dao
interface ForwardRouteRuleDao {
    @Query("SELECT * FROM forward_route_rule ORDER BY update_time DESC")
    fun observeAll(): Flow<List<ForwardRouteRuleEntity>>

    @Query("SELECT * FROM forward_route_rule")
    suspend fun all(): List<ForwardRouteRuleEntity>

    @Query("SELECT sender_id FROM forward_route_rule WHERE scope = :scope AND package_name = :packageName")
    fun observeSenderIdsByScopeAndPackage(scope: Int, packageName: String): Flow<List<Long>>

    @Query("SELECT sender_id FROM forward_route_rule WHERE scope = :scope AND package_name = :packageName")
    suspend fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long>

    @Query("SELECT package_name FROM forward_route_rule WHERE scope = :scope AND sender_id = :senderId")
    fun observePackageNamesByScopeAndSender(scope: Int, senderId: Long): Flow<List<String>>

    @Query("SELECT sender_id FROM forward_route_rule WHERE scope = :scope AND sender_id IN (:senderIds)")
    suspend fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long>

    @Query("DELETE FROM forward_route_rule WHERE scope = :scope AND package_name = :packageName")
    suspend fun deleteByScopeAndPackage(scope: Int, packageName: String): Int

    @Query("DELETE FROM forward_route_rule WHERE scope IN (:scopes) AND sender_id = :senderId")
    suspend fun deleteByScopesAndSender(scopes: List<Int>, senderId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ForwardRouteRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<ForwardRouteRuleEntity>)

    @Delete
    suspend fun delete(rule: ForwardRouteRuleEntity)

    @Query("DELETE FROM forward_route_rule")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM forward_route_rule")
    suspend fun count(): Int
}

@Dao
interface ForwardFilterRuleDao {
    @Query("SELECT * FROM forward_filter_rule WHERE enabled = 1")
    suspend fun enabled(): List<ForwardFilterRuleEntity>

    @Query("SELECT * FROM forward_filter_rule")
    suspend fun all(): List<ForwardFilterRuleEntity>

    @Query("SELECT * FROM forward_filter_rule WHERE msg_type = :msgType AND enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledByMsgType(msgType: String): List<ForwardFilterRuleEntity>

    @Query("SELECT * FROM forward_filter_rule")
    fun observeAll(): Flow<List<ForwardFilterRuleEntity>>

    @Query(
        "SELECT * FROM forward_filter_rule WHERE msg_type = :msgType " +
            "AND scope_type = :scopeType " +
            "AND (:scopeKey IS NULL OR scope_key = :scopeKey) " +
            "AND (:senderId IS NULL OR sender_id = :senderId) " +
            "ORDER BY update_time DESC",
    )
    fun observeByScope(msgType: String, scopeType: String, scopeKey: String?, senderId: Long?): Flow<List<ForwardFilterRuleEntity>>

    @Query(
        "SELECT * FROM forward_filter_rule WHERE msg_type = :msgType " +
            "AND scope_type = :scopeType AND scope_key LIKE :scopeKeyPrefix || '%' " +
            "ORDER BY update_time DESC",
    )
    fun observeByScopePrefix(msgType: String, scopeType: String, scopeKeyPrefix: String): Flow<List<ForwardFilterRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ForwardFilterRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<ForwardFilterRuleEntity>)

    @Update
    suspend fun update(rule: ForwardFilterRuleEntity)

    @Query("DELETE FROM forward_filter_rule WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE forward_filter_rule SET enabled = :enabled, update_time = :updateTime WHERE id = :id")
    suspend fun updateEnabledById(id: Long, enabled: Int, updateTime: Long)

    @Query("DELETE FROM forward_filter_rule")
    suspend fun clearAll()

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

    @Query("SELECT * FROM forward_scheduled_task ORDER BY id DESC")
    fun getAllFlow(): Flow<List<ForwardScheduledTaskEntity>>

    @Query("SELECT * FROM forward_scheduled_task")
    suspend fun getAll(): List<ForwardScheduledTaskEntity>

    @Query("SELECT * FROM forward_scheduled_task WHERE status = 1")
    fun getActiveTasksFlow(): Flow<List<ForwardScheduledTaskEntity>>

    @Query("SELECT * FROM forward_scheduled_task WHERE status = 1")
    suspend fun getActiveTasks(): List<ForwardScheduledTaskEntity>

    @Query("SELECT * FROM forward_scheduled_task WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ForwardScheduledTaskEntity?

    @Query(
        "UPDATE forward_scheduled_task SET next_run_time = 0 " +
            "WHERE id = :id AND status = 1 " +
            "AND next_run_time > 0 AND next_run_time <= :dueBefore " +
            "AND last_run_time <= :dedupeBefore",
    )
    suspend fun claimRunIfDue(id: Long, dueBefore: Long, dedupeBefore: Long): Int

    @Query("UPDATE forward_scheduled_task SET last_run_time = :runTime WHERE id = :id")
    suspend fun markRunSucceeded(id: Long, runTime: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ForwardScheduledTaskEntity): Long

    @Update
    suspend fun update(task: ForwardScheduledTaskEntity)

    @Query("DELETE FROM forward_scheduled_task WHERE id = :id")
    suspend fun deleteById(id: Long)
}
