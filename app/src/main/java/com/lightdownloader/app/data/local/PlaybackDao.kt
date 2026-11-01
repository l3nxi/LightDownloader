package com.lightdownloader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_state")
    fun observeAll(): Flow<List<PlaybackState>>

    @Query("SELECT * FROM playback_state WHERE mediaId = :mediaId LIMIT 1")
    suspend fun find(mediaId: Long): PlaybackState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlaybackState)

    @Query("DELETE FROM playback_state WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)
}
