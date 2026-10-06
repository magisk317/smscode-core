package io.github.magisk317.smscode.db.dao

import androidx.room.*
import io.github.magisk317.smscode.db.entity.NotifyRouteRule
import kotlinx.coroutines.flow.Flow

@Dao
interface NotifyRouteRuleDao {
    @Query("SELECT * FROM notify_route_rule")
    suspend fun getAll(): List<NotifyRouteRule>

    @Query("SELECT * FROM notify_route_rule")
    fun getAllFlow(): Flow<List<NotifyRouteRule>>

    @Query("SELECT sender_id FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    suspend fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long>

    @Query("SELECT sender_id FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    fun observeSenderIdsByScopeAndPackage(scope: Int, packageName: String): Flow<List<Long>>

    @Query("SELECT package_name FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    suspend fun getPackageNamesByScopeAndSender(scope: Int, senderId: Long): List<String>

    @Query("SELECT package_name FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    fun observePackageNamesByScopeAndSender(scope: Int, senderId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT sender_id FROM notify_route_rule WHERE scope = :scope AND sender_id IN (:senderIds)")
    suspend fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long>

    @Query("DELETE FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    suspend fun deleteByScopeAndPackage(scope: Int, packageName: String): Int

    @Query("DELETE FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    suspend fun deleteByScopeAndSender(scope: Int, senderId: Long): Int

    @Query("DELETE FROM notify_route_rule WHERE scope IN (:scopes) AND sender_id = :senderId")
    suspend fun deleteByScopesAndSender(scopes: List<Int>, senderId: Long): Int

    @Query("DELETE FROM notify_route_rule")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: NotifyRouteRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<NotifyRouteRule>)
}
