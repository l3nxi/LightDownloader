package com.lightdownloader.app.data.ytdlp

import android.content.Context
import com.lightdownloader.app.domain.SourceFormat
import com.lightdownloader.app.domain.UrlTools
import com.lightdownloader.app.domain.VideoMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Small on-disk cache for extracted metadata. Re-pasting a link should feel
 * instant, and yt-dlp extraction is the slowest part of the search flow.
 */
object MetadataCache {
    private const val TTL_MS = 24L * 60L * 60L * 1000L
    private const val VERSION = 1

    fun read(context: Context, url: String): VideoMetadata? {
        val file = fileFor(context, url)
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > TTL_MS) {
            runCatching { file.delete() }
            return null
        }
        return runCatching {
            val root = JSONObject(file.readText())
            if (root.optInt("version") != VERSION) return@runCatching null
            fromJson(root)
        }.getOrNull()?.takeIf { it.formats.isNotEmpty() }
    }

    fun write(context: Context, url: String, metadata: VideoMetadata) {
        if (metadata.formats.isEmpty()) return
        runCatching {
            val file = fileFor(context, url)
            file.parentFile?.mkdirs()
            file.writeText(toJson(metadata).toString())
        }
    }

    fun clear(context: Context) {
        runCatching { File(context.cacheDir, "metadata").deleteRecursively() }
    }

    private fun fileFor(context: Context, url: String): File {
        val key = UrlTools.videoId(url)?.let { "yt_$it" }
            ?: "u_" + Integer.toHexString(url.hashCode())
        return File(File(context.cacheDir, "metadata"), "$key.json")
    }

    private fun toJson(metadata: VideoMetadata): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("id", metadata.id)
        put("title", metadata.title)
        put("uploader", metadata.uploader)
        put("duration", metadata.durationSeconds)
        put("thumbnail", metadata.thumbnailUrl)
        put("webpage", metadata.webpageUrl)
        put("formats", JSONArray().apply {
            metadata.formats.forEach { format ->
                put(JSONObject().apply {
                    put("formatId", format.formatId)
                    put("ext", format.extension)
                    put("width", format.width)
                    put("height", format.height)
                    put("fps", format.fps)
                    put("vcodec", format.vcodec)
                    put("acodec", format.acodec)
                    put("filesize", format.filesize)
                    put("filesizeApprox", format.filesizeApprox)
                    put("tbr", format.tbrKbps)
                    put("abr", format.abrKbps)
                    put("asr", format.asrHz)
                })
            }
        })
    }

    private fun fromJson(root: JSONObject): VideoMetadata {
        val formats = root.optJSONArray("formats")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        SourceFormat(
                            formatId = item.optString("formatId"),
                            extension = item.optString("ext").takeIf { it.isNotBlank() },
                            width = item.optInt("width"),
                            height = item.optInt("height"),
                            fps = item.optInt("fps"),
                            vcodec = item.optString("vcodec").takeIf { it.isNotBlank() },
                            acodec = item.optString("acodec").takeIf { it.isNotBlank() },
                            filesize = item.optLong("filesize"),
                            filesizeApprox = item.optLong("filesizeApprox"),
                            tbrKbps = item.optInt("tbr"),
                            abrKbps = item.optInt("abr"),
                            asrHz = item.optInt("asr")
                        )
                    )
                }
            }
        }.orEmpty()

        return VideoMetadata(
            id = root.optString("id"),
            title = root.optString("title"),
            uploader = root.optString("uploader").takeIf { it.isNotBlank() },
            durationSeconds = root.optInt("duration"),
            thumbnailUrl = root.optString("thumbnail").takeIf { it.isNotBlank() },
            webpageUrl = root.optString("webpage"),
            formats = formats
        )
    }
}
