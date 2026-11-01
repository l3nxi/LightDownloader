package com.lightdownloader.app.domain

import java.util.Locale
import kotlin.math.roundToInt

object FormatCatalog {
    val VIDEO_HEIGHTS = listOf(360, 480, 720, 1080)
    val AUDIO_FORMATS = AudioFormat.entries

    fun videoOptions(metadata: VideoMetadata): List<FormatOption> {
        val audio = bestAudio(metadata.formats)
        return VIDEO_HEIGHTS.map { height ->
            val video = bestVideoAt(metadata.formats, height)
            if (video == null) {
                FormatOption("${height}p", 0, available = false, detail = "Not available")
            } else {
                val videoBytes = video.estimatedBytes(metadata.durationSeconds)
                val audioBytes = if (video.isVideoOnly) {
                    audio?.estimatedBytes(metadata.durationSeconds) ?: 0
                } else {
                    0
                }
                val bytes = videoBytes + audioBytes
                FormatOption(
                    label = "${height}p",
                    estimatedBytes = bytes,
                    available = bytes > 0,
                    detail = if (bytes > 0) null else "Size unknown"
                )
            }
        }
    }

    fun audioOptions(metadata: VideoMetadata): List<FormatOption> {
        val audio = bestAudio(metadata.formats) ?: bestCombined(metadata.formats)
        val bytes = audio?.estimatedBytes(metadata.durationSeconds) ?: 0
        return AUDIO_FORMATS.map { format ->
            FormatOption(
                label = format.label,
                estimatedBytes = bytes,
                available = audio != null && bytes > 0,
                detail = if (bytes > 0) null else "Size unknown"
            )
        }
    }

    fun bestVideoAt(formats: List<SourceFormat>, height: Int): SourceFormat? =
        formats.asSequence()
            .filter { it.hasVideo && it.qualityHeight == height }
            .sortedWith(
                compareByDescending<SourceFormat> { it.isCombined }
                    .thenByDescending { it.extension == "mp4" }
                    .thenByDescending { it.vcodec?.startsWith("avc1") == true }
                    .thenByDescending { it.tbrKbps }
                    .thenByDescending { it.filesize + it.filesizeApprox }
            )
            .firstOrNull()

    fun bestAudio(formats: List<SourceFormat>): SourceFormat? =
        formats.asSequence()
            .filter { it.isAudioOnly }
            .sortedWith(
                compareByDescending<SourceFormat> { it.extension == "m4a" }
                    .thenByDescending { it.abrKbps }
                    .thenByDescending { it.tbrKbps }
                    .thenByDescending { it.filesize + it.filesizeApprox }
            )
            .firstOrNull()

    private fun bestCombined(formats: List<SourceFormat>): SourceFormat? =
        formats.asSequence()
            .filter { it.isCombined }
            .maxByOrNull { it.filesize + it.filesizeApprox }

    fun selectedEstimate(
        metadata: VideoMetadata,
        category: MediaCategory,
        videoHeight: Int,
        audioFormat: AudioFormat
    ): Long {
        val options = if (category == MediaCategory.VIDEO) {
            videoOptions(metadata)
        } else {
            audioOptions(metadata)
        }
        val label = if (category == MediaCategory.VIDEO) "${videoHeight}p" else audioFormat.label
        return options.firstOrNull { it.label == label }?.estimatedBytes ?: 0
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "size unknown"
        val mb = bytes / 1_000_000.0
        return when {
            mb >= 100 -> "~${mb.roundToInt()} MB"
            mb >= 10 -> "~${String.format(Locale.US, "%.0f", mb)} MB"
            mb >= 1 -> "~${String.format(Locale.US, "%.1f", mb)} MB"
            else -> "~${(bytes / 1000L).coerceAtLeast(1)} KB"
        }
    }
}
