package io.github.magisk317.forward.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    @Query("SELECT COUNT(*) FROM forward_event")
    suspend fun count(): Int

    @Query("DELETE FROM forward_event WHERE id = :id")
    suspend fun delete(id: Long)
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
