package io.github.magisk317.smscode.db.dao

import androidx.room.*
import io.github.magisk317.smscode.db.entity.AutoInputEvent

@Dao
interface AutoInputEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AutoInputEvent): Long

    @Query("UPDATE auto_input_event SET success = :success, fail_reason = :reason WHERE id = :id")
    suspend fun updateResult(id: Long, success: Boolean, reason: String?): Int

    @Query(
        """
        INSERT OR REPLACE INTO auto_input_event (id, code_length, success, fail_reason, attempt_at)
        VALUES (:id, :codeLength, :success, :reason, :attemptAt)
        """,
    )
    suspend fun upsertResult(
        id: Long,
        codeLength: Int,
        success: Boolean,
        reason: String?,
        attemptAt: Long = System.currentTimeMillis(),
    ): Long

    @Query("SELECT COUNT(*) FROM auto_input_event WHERE attempt_at >= :fromMs")
    suspend fun countAttempts(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM auto_input_event WHERE attempt_at >= :fromMs AND success = 1")
    suspend fun countSuccess(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM auto_input_event WHERE attempt_at >= :fromMs AND success = 0")
    suspend fun countFailed(fromMs: Long): Long
}
