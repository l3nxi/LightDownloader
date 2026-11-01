package com.lightdownloader.app.ui

import com.lightdownloader.app.domain.FormatCatalog
import com.lightdownloader.app.domain.MediaCategory
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestamp))

fun formatSizeCompact(bytes: Long): String =
    if (bytes > 0) FormatCatalog.formatSize(bytes).removePrefix("~") else "—"

fun formatEtaLabel(seconds: Long): String {
    if (seconds <= 0L) return ""
    val minutes = seconds / 60L
    val remainder = seconds % 60L
    return if (minutes > 0L) {
        String.format(Locale.US, "%dm %02ds left", minutes, remainder)
    } else {
        String.format(Locale.US, "%ds left", remainder)
    }
}

fun mediaTypeLabel(mediaType: String): String =
    if (mediaType == MediaCategory.MUSIC.name) "MUSIC" else "VIDEO"

fun targetLabel(target: com.lightdownloader.app.domain.DownloadTarget): String =
    when (target) {
        com.lightdownloader.app.domain.DownloadTarget.PublicDownloads ->
            "Downloads/LightDownloader"
        is com.lightdownloader.app.domain.DownloadTarget.Tree -> target.displayName
    }
