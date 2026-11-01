package com.lightdownloader.app.domain

import android.net.Uri

enum class MediaCategory { VIDEO, MUSIC }

enum class AudioFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    MP3("MP3", "mp3", "audio/mpeg"),
    M4A("M4A", "m4a", "audio/mp4")
}

data class SourceFormat(
    val formatId: String,
    val extension: String?,
    val width: Int,
    val height: Int,
    val fps: Int,
    val vcodec: String?,
    val acodec: String?,
    val filesize: Long,
    val filesizeApprox: Long,
    val tbrKbps: Int,
    val abrKbps: Int,
    val asrHz: Int
) {
    val hasVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != "none"
    val hasAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != "none"
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
    val isVideoOnly: Boolean get() = hasVideo && !hasAudio
    val isCombined: Boolean get() = hasVideo && hasAudio

    /** Uses the short edge so vertical shorts still map to 360p/720p/1080p. */
    val qualityHeight: Int get() = when {
        width > 0 && height > 0 -> minOf(width, height)
        height > 0 -> height
        else -> 0
    }

    fun estimatedBytes(durationSeconds: Int): Long {
        if (filesize > 0) return filesize
        if (filesizeApprox > 0) return filesizeApprox
        val bitrate = if (tbrKbps > 0) tbrKbps else abrKbps
        if (bitrate <= 0 || durationSeconds <= 0) return 0
        return bitrate.toLong() * 1000L / 8L * durationSeconds
    }
}

data class VideoMetadata(
    val id: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val webpageUrl: String,
    val formats: List<SourceFormat>
)

data class FormatOption(
    val label: String,
    val estimatedBytes: Long,
    val available: Boolean,
    val detail: String? = null
)

data class DownloadRequest(
    val sourceUrl: String,
    val videoId: String,
    val title: String,
    val customFileName: String,
    val category: MediaCategory,
    val videoHeight: Int,
    val audioFormat: AudioFormat,
    val target: DownloadTarget,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val estimatedBytes: Long
)

sealed interface DownloadTarget {
    data object PublicDownloads : DownloadTarget
    data class Tree(val uri: Uri, val displayName: String) : DownloadTarget
}

sealed interface EngineState {
    data object Idle : EngineState
    data object Preparing : EngineState
    data class Ready(val ytDlpVersion: String?) : EngineState
    data class Failed(val message: String) : EngineState
}

/** Result of comparing the installed yt-dlp with the latest stable release. */
sealed interface EngineUpdateState {
    data object Unknown : EngineUpdateState
    data object Checking : EngineUpdateState
    data object Updating : EngineUpdateState
    data class Available(val installed: String?, val latest: String) : EngineUpdateState
    data class UpToDate(val installed: String?, val latest: String?) : EngineUpdateState
    data class Failed(val message: String) : EngineUpdateState
}

sealed interface DownloadState {
    data class Running(
        val percent: Float,
        val etaSeconds: Long,
        val phase: String,
        val statusLine: String?
    ) : DownloadState

    data class Publishing(val fileName: String) : DownloadState

    data class Success(
        val recordId: Long,
        val fileName: String,
        val displayPath: String
    ) : DownloadState

    data class Failed(val message: String) : DownloadState
    data object Cancelled : DownloadState
}
