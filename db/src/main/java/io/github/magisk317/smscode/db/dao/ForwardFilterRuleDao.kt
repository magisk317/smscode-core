package io.github.magisk317.smscode.db.dao

import androidx.room.*
import io.github.magisk317.smscode.db.entity.ForwardFilterRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardFilterRuleDao {
    @Query("SELECT * FROM forward_filter_rule ORDER BY id DESC")
    suspend fun getAll(): List<ForwardFilterRule>

    @Query("SELECT * FROM forward_filter_rule ORDER BY id DESC")
    fun getAllFlow(): Flow<List<ForwardFilterRule>>

    @Query("SELECT * FROM forward_filter_rule WHERE msg_type = :msgType AND enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledByMsgType(msgType: String): List<ForwardFilterRule>

    @Query(
        "SELECT * FROM forward_filter_rule " +
            "WHERE msg_type = :msgType " +
            "AND scope_type = :scopeType " +
            "AND (:scopeKey IS NULL OR scope_key = :scopeKey) " +
            "AND (:senderId IS NULL OR sender_id = :senderId) " +
            "ORDER BY id DESC",
    )
    fun observeByScope(
        msgType: String,
        scopeType: String,
        scopeKey: String? = null,
        senderId: Long? = null,
    ): Flow<List<ForwardFilterRule>>

    @Query(
        "SELECT * FROM forward_filter_rule " +
            "WHERE msg_type = :msgType " +
            "AND scope_type = :scopeType " +
            "AND scope_key LIKE :scopeKeyPrefix " +
            "ORDER BY id DESC",
    )
    fun observeByScopePrefix(
        msgType: String,
        scopeType: String,
        scopeKeyPrefix: String,
    ): Flow<List<ForwardFilterRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ForwardFilterRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ForwardFilterRule>)

    @Update
    suspend fun update(rule: ForwardFilterRule)

    @Delete
    suspend fun delete(rule: ForwardFilterRule)

    @Query("DELETE FROM forward_filter_rule WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE forward_filter_rule SET enabled = :enabled, update_time = :updateTime WHERE id = :id")
    suspend fun updateEnabledById(id: Long, enabled: Int, updateTime: Long): Int

    @Query("DELETE FROM forward_filter_rule")
    suspend fun clearAll()
}
