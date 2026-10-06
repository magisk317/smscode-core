package io.github.magisk317.smscode.db.dao

import androidx.room.*
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsCodeRuleDao {
    @Query("SELECT * FROM sms_code_rule")
    suspend fun getAll(): List<SmsCodeRule>

    @Query("SELECT * FROM sms_code_rule WHERE id = :id")
    suspend fun getById(id: Long): SmsCodeRule?

    @Query("SELECT * FROM sms_code_rule")
    fun getAllFlow(): Flow<List<SmsCodeRule>>

    @Query(
        "SELECT * FROM sms_code_rule WHERE company = :company AND code_keyword = :codeKeyword AND code_regex = :codeRegex",
    )
    suspend fun queryRules(company: String?, codeKeyword: String, codeRegex: String): List<SmsCodeRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SmsCodeRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<SmsCodeRule>)

    @Update
    suspend fun update(rule: SmsCodeRule)

    @Delete
    suspend fun delete(rule: SmsCodeRule)

    @Delete
    suspend fun deleteAll(rules: List<SmsCodeRule>)

    @Query("DELETE FROM sms_code_rule")
    suspend fun clearAll()

    @Query("SELECT count(*) FROM sms_code_rule")
    suspend fun count(): Long
}
