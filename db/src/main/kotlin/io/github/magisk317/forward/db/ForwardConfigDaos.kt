package io.github.magisk317.forward.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Per-package forwarding preferences, formerly the legacy `AppInfo` table. */
@Dao
interface ForwardAppInfoDao {
    @Query("SELECT * FROM forward_app_info")
    fun observeAll(): Flow<List<ForwardAppInfoEntity>>

    @Query("SELECT * FROM forward_app_info")
    suspend fun all(): List<ForwardAppInfoEntity>

    @Query("SELECT * FROM forward_app_info WHERE package_name = :packageName")
    suspend fun byPackage(packageName: String): ForwardAppInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(appInfo: ForwardAppInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(appInfos: List<ForwardAppInfoEntity>)

    @Query("DELETE FROM forward_app_info WHERE package_name IN (:packageNames)")
    suspend fun deleteByPackageNames(packageNames: List<String>): Int

    @Delete
    suspend fun delete(appInfo: ForwardAppInfoEntity)

    @Query("DELETE FROM forward_app_info")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM forward_app_info")
    suspend fun count(): Int
}

/** Legacy forwarding rules, formerly the `Rule` table. */
@Dao
interface ForwardRuleDao {
    @Query("SELECT * FROM forward_rule ORDER BY id DESC")
    fun observeAll(): Flow<List<ForwardRuleEntity>>

    @Query("SELECT * FROM forward_rule ORDER BY id DESC")
    suspend fun all(): List<ForwardRuleEntity>

    @Query("SELECT * FROM forward_rule WHERE id = :id")
    suspend fun byId(id: Long): ForwardRuleEntity

    @Query("SELECT * FROM forward_rule WHERE sender_id = :senderId ORDER BY id DESC")
    fun observeBySender(senderId: Long): Flow<List<ForwardRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ForwardRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<ForwardRuleEntity>)

    @Update
    suspend fun update(rule: ForwardRuleEntity)

    @Delete
    suspend fun delete(rule: ForwardRuleEntity)

    @Query("DELETE FROM forward_rule")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM forward_rule")
    suspend fun count(): Int
}

@Dao
interface ForwardMetaDao {
    @Query("SELECT value FROM forward_meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: ForwardMetaEntity)

    @Query("DELETE FROM forward_meta WHERE key = :key")
    suspend fun remove(key: String)
}
