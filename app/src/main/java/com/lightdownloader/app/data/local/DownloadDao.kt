package com.lightdownloader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): DownloadRecord?

    @Insert
    suspend fun insert(record: DownloadRecord): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}
