package com.lightdownloader.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.lightdownloader.app.domain.DownloadTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class PublishedFile(
    val fileName: String,
    val displayPath: String,
    val contentUri: Uri,
    val sizeBytes: Long
)

/**
 * yt-dlp needs a real POSIX path, so every download lands in app-private staging
 * first. This class then publishes it either to public Downloads/LightDownloader
 * through MediaStore, or to a user-selected SAF tree.
 */
class StoragePublisher(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun publish(stagedFile: File, target: DownloadTarget): PublishedFile =
        withContext(Dispatchers.IO) {
            when (target) {
                DownloadTarget.PublicDownloads -> publishToPublicDownloads(stagedFile)
                is DownloadTarget.Tree -> publishToTree(stagedFile, target)
            }
        }

    private fun publishToPublicDownloads(staged: File): PublishedFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, staged.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType(staged.extension))
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/LightDownloader"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IOException("Could not create the Downloads entry")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    staged.inputStream().buffered().use { input ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                } ?: throw IOException("Could not open the Downloads destination")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                val actualName = queryDisplayName(uri) ?: staged.name
                return PublishedFile(
                    fileName = actualName,
                    displayPath = "Download/LightDownloader/$actualName",
                    contentUri = uri,
                    sizeBytes = staged.length()
                )
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        }
        return publishLegacy(staged)
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(staged: File): PublishedFile {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloads, "LightDownloader")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IOException("Could not create Downloads/LightDownloader")
        }
        val destination = uniqueFile(folder, staged.name)
        staged.copyTo(destination, overwrite = false)
        return PublishedFile(
            fileName = destination.name,
            displayPath = "Download/LightDownloader/${destination.name}",
            contentUri = Uri.fromFile(destination),
            sizeBytes = destination.length()
        )
    }

    private fun publishToTree(staged: File, target: DownloadTarget.Tree): PublishedFile {
        val tree = DocumentFile.fromTreeUri(context, target.uri)
            ?: throw IOException("The selected folder is no longer available")

        val mime = mimeType(staged.extension)
        val base = staged.name.substringBeforeLast('.', staged.name)
        val extension = staged.extension
        var candidate = staged.name
        var existing = tree.findFile(candidate)
        var counter = 1
        while (existing != null && counter < 100) {
            candidate = if (extension.isBlank()) {
                "$base ($counter)"
            } else {
                "$base ($counter).$extension"
            }
            existing = tree.findFile(candidate)
            counter++
        }

        val document = existing
            ?: tree.createFile(mime, candidate)
            ?: throw IOException("Could not create a file in the selected folder")

        resolver.openOutputStream(document.uri, "w")?.use { output ->
            staged.inputStream().buffered().use { input ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        } ?: throw IOException("Could not write to the selected folder")

        val actualName = document.name ?: candidate
        val folderName = tree.name ?: target.displayName
        return PublishedFile(
            fileName = actualName,
            displayPath = "$folderName/$actualName",
            contentUri = document.uri,
            sizeBytes = staged.length()
        )
    }

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun uniqueFile(folder: File, name: String): File {
        var candidate = File(folder, name)
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var counter = 1
        while (candidate.exists() && counter < 100) {
            candidate = File(
                folder,
                if (extension.isBlank()) "$base ($counter)" else "$base ($counter).$extension"
            )
            counter++
        }
        return candidate
    }

    companion object {
        fun mimeType(extension: String): String = when (extension.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }
}
