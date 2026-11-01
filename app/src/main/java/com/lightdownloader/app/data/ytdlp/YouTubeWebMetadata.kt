package com.lightdownloader.app.data.ytdlp

import com.lightdownloader.app.domain.SourceFormat
import com.lightdownloader.app.domain.UrlTools
import com.lightdownloader.app.domain.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fast metadata path for YouTube.
 *
 * yt-dlp has to download and execute YouTube's player JavaScript to sign
 * stream URLs, which is the slow part on a phone. For the option card we only
 * need titles, thumbnails, durations and format sizes, so we read YouTube's
 * own player bootstrap JSON (`pbj=1`) instead. It is one small request and
 * avoids the JS challenge entirely. Downloading still goes through yt-dlp.
 */
object YouTubeWebMetadata {
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val CONSENT_COOKIE =
        "CONSENT=YES+cb.20210328-17-p0.en+FX+123; SOCS=CAI"

    suspend fun fetch(url: String): VideoMetadata? = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(url) }.getOrNull()
    }

    private fun fetchBlocking(url: String): VideoMetadata? {
        val videoId = UrlTools.videoId(url) ?: return null
        val endpoint = "https://www.youtube.com/watch?v=$videoId" +
            "&pbj=1&hl=en&gl=US&has_verified=1&bpctr=9999999999"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", DESKTOP_UA)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("X-YouTube-Client-Name", "1")
            setRequestProperty("X-YouTube-Client-Version", "2.20241220.01.00")
            setRequestProperty("Cookie", CONSENT_COOKIE)
        }

        val body = try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val player = root.optJSONObject("playerResponse") ?: return null
        val status = player.optJSONObject("playabilityStatus")?.optString("status")
        if (status != "OK" && status != "PLAYABLE") return null

        val details = player.optJSONObject("videoDetails") ?: return null
        val streaming = player.optJSONObject("streamingData") ?: return null

        val formats = buildList {
            streaming.optJSONArray("formats")?.objects()?.forEach { item ->
                mapFormat(item)?.let(::add)
            }
            streaming.optJSONArray("adaptiveFormats")?.objects()?.forEach { item ->
                mapFormat(item)?.let(::add)
            }
        }.distinctBy { it.formatId }
        if (formats.isEmpty()) return null

        val thumbnails = details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnailUrl = thumbnails?.objects()
            ?.maxByOrNull { it.optInt("width") }
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }

        return VideoMetadata(
            id = details.optString("videoId").ifBlank { videoId },
            title = details.optString("title").ifBlank { "Untitled media" },
            uploader = details.optString("author").takeIf { it.isNotBlank() },
            durationSeconds = details.optString("lengthSeconds").toIntOrNull() ?: 0,
            thumbnailUrl = thumbnailUrl,
            webpageUrl = "https://www.youtube.com/watch?v=$videoId",
            formats = formats
        )
    }

    private fun mapFormat(item: JSONObject): SourceFormat? {
        val mimeType = item.optString("mimeType")
        if (mimeType.isBlank()) return null
        val type = mimeType.substringBefore(';').trim().lowercase()
        val hasVideo = type.startsWith("video/")
        val hasAudio = type.startsWith("audio/")
        if (!hasVideo && !hasAudio) return null

        val codecs = Regex("codecs=\"([^\"]*)\"")
            .find(mimeType)
            ?.groupValues
            ?.getOrNull(1)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val vcodec = if (hasVideo) codecs.firstOrNull() else null
        val acodec = when {
            hasAudio -> codecs.firstOrNull()
            hasVideo && codecs.size > 1 -> codecs[1]
            else -> null
        }

        val extension = when {
            type.endsWith("mp4") && hasAudio -> "m4a"
            type.endsWith("mp4") -> "mp4"
            type.endsWith("webm") -> "webm"
            type.endsWith("mpeg") -> "mp3"
            else -> type.substringAfter('/', "bin")
        }

        val bitrate = item.optString("bitrate").toIntOrNull() ?: 0
        val contentLength = item.optString("contentLength").toLongOrNull() ?: 0L

        return SourceFormat(
            formatId = item.optString("itag"),
            extension = extension,
            width = item.optString("width").toIntOrNull() ?: 0,
            height = item.optString("height").toIntOrNull() ?: 0,
            fps = item.optString("fps").toIntOrNull() ?: 0,
            vcodec = vcodec,
            acodec = acodec,
            filesize = contentLength,
            filesizeApprox = 0L,
            tbrKbps = if (bitrate > 0) bitrate / 1000 else 0,
            abrKbps = if (!hasVideo && bitrate > 0) bitrate / 1000 else 0,
            asrHz = item.optString("audioSampleRate").toIntOrNull() ?: 0
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}
