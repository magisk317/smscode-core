package io.github.magisk317.forward.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
