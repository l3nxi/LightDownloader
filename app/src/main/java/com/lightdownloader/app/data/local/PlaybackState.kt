package com.lightdownloader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_state")
data class PlaybackState(
    @PrimaryKey val mediaId: Long,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)
