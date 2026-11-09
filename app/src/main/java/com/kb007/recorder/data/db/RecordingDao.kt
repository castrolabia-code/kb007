package com.kb007.recorder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE status != 'RECORDING' AND status != 'FAILED' ORDER BY createdAt DESC")
    fun observeFinished(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observe(id: Long): Flow<Recording?>

    @Query("SELECT * FROM recordings WHERE status = 'RECORDING'")
    suspend fun inProgress(): List<Recording>

    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int

    @Insert
    suspend fun insert(r: Recording): Long

    @Update
    suspend fun update(r: Recording)

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE recordings SET cassetteStyle = :style WHERE id = :id")
    suspend fun setStyle(id: Long, style: Int)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}
