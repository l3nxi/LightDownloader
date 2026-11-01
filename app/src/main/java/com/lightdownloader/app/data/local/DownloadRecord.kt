package com.lightdownloader.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [Index("timestamp"), Index("sourceUrl")]
)
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceTitle: String,
    val filePath: String,
    val contentUri: String,
    val thumbnailUrl: String?,
    val timestamp: Long,
    val mediaType: String,
    val fileName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sourceUrl: String,
    val videoId: String
)
