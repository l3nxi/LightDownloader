package com.lightdownloader.app.data.ytdlp

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.lightdownloader.app.domain.AudioFormat
import com.lightdownloader.app.domain.DownloadRequest
import com.lightdownloader.app.domain.FileNames
import com.lightdownloader.app.domain.MediaCategory
import com.lightdownloader.app.domain.SourceFormat
import com.lightdownloader.app.domain.VideoMetadata
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin, coroutine-safe facade over youtubedl-android. Every blocking JNI/process
 * call is confined to Dispatchers.IO.
 */
class YtDlpEngine(private val context: Context) {
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    // The library adds --no-cache-dir unless the request supplies one. Keeping
    // the player JS cached makes repeated searches dramatically faster.
    private val cacheDir: File
        get() = File(context.cacheDir, "ytdlp-cache").apply { mkdirs() }

    suspend fun ensureInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        if (initialized) return@withContext Result.success(Unit)
        initMutex.withLock {
            if (initialized) return@withLock Result.success(Unit)
            runCatching {
                YoutubeDL.init(context)
                FFmpeg.init(context)
                initialized = true
            }.onFailure {
                Log.e(TAG, "engine init failed", it)
            }
        }
    }

    suspend fun fetchInfo(url: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            check(initialized) { "The download engine is not ready yet." }

            MetadataCache.read(context, url)?.let { cached ->
                Log.d(TAG, "getInfo cache hit for $url")
                return@runCatching cached
            }

            val startedAt = SystemClock.elapsedRealtime()

            // Fast path: one YouTube player-bootstrap request, no JS challenge.
            YouTubeWebMetadata.fetch(url)?.let { metadata ->
                MetadataCache.write(context, url, metadata)
                Log.d(
                    TAG,
                    "web getInfo ${SystemClock.elapsedRealtime() - startedAt}ms " +
                        "formats=${metadata.formats.size} id=${metadata.id}"
                )
                return@runCatching metadata
            }

            val request = YoutubeDLRequest(url)
                .addOption("--ignore-config")
                .addOption("--no-playlist")
                .addOption("--no-warnings")
                .addOption("--no-colors")
                .addOption("--no-check-formats")
                .addOption("--cache-dir", cacheDir.absolutePath)
                .addOption("--socket-timeout", "10")
                .addOption("--retries", "1")
                .addOption("--fragment-retries", "1")
                .addOption("--extractor-retries", "1")

            val metadata = YoutubeDL.getInfo(request).toMetadata(url)
            MetadataCache.write(context, url, metadata)
            Log.d(
                TAG,
                "getInfo ${SystemClock.elapsedRealtime() - startedAt}ms " +
                    "formats=${metadata.formats.size} id=${metadata.id}"
            )
            metadata
        }
    }

    /**
     * Reads the tag of the latest stable yt-dlp release. The library itself
     * updates from this exact GitHub endpoint, so the comparison is apples to
     * apples. Falls back to the redirect target when the API is rate limited.
     */
    suspend fun latestVersion(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { fetchLatestFromApi() }
            .recoverCatching { fetchLatestFromRedirect() }
    }

    private fun fetchLatestFromApi(): String {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LightDownloader")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                error("GitHub returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            TAG_REGEX.find(body)?.groupValues?.getOrNull(1)
                ?: error("GitHub response did not contain a release tag")
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestFromRedirect(): String {
        val connection = (URL(LATEST_RELEASE_PAGE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            requestMethod = "HEAD"
            setRequestProperty("User-Agent", "LightDownloader")
        }
        return try {
            val location = connection.getHeaderField("Location")
                ?: error("Could not reach the yt-dlp release page")
            location.substringAfterLast("/tag/", missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
                ?: error("Could not read the latest yt-dlp version")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        request: DownloadRequest,
        stagingDir: File,
        processId: String,
        onProgress: (percent: Float, etaSeconds: Long, line: String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            check(initialized) { "The download engine is not ready yet." }
            if (!stagingDir.exists() && !stagingDir.mkdirs()) {
                error("Could not create a temporary download folder.")
            }

            val safeBaseName = FileNames.sanitize(request.customFileName, request.title)
                .replace("%", "_")
            val outputTemplate = File(stagingDir, "$safeBaseName.%(ext)s").absolutePath

            val ytRequest = YoutubeDLRequest(request.sourceUrl).apply {
                addOption("--ignore-config")
                addOption("--no-playlist")
                addOption("--newline")
                addOption("--no-colors")
                addOption("--no-warnings")
                addOption("--embed-thumbnail")
                addOption("--embed-metadata")
                addOption("--convert-thumbnails", "jpg")
                addOption("--cache-dir", cacheDir.absolutePath)
                addOption("-o", outputTemplate)

                when (request.category) {
                    MediaCategory.VIDEO -> {
                        addOption("-f", "bv*+ba/b")
                        addOption("-S", "res:${request.videoHeight},vcodec:h264")
                        addOption("--merge-output-format", "mp4")
                    }

                    MediaCategory.MUSIC -> {
                        addOption("-f", "ba/b")
                        addOption("-x")
                        addOption("--audio-format", request.audioFormat.extension)
                        addOption("--audio-quality", "0")
                    }
                }
            }

            var lastProgressAt = 0L
            val response = YoutubeDL.execute(
                ytRequest,
                processId,
                redirectErrorStream = false,
                callback = { percent, eta, line ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressAt >= 120L || percent >= 99.9f) {
                        lastProgressAt = now
                        onProgress(percent.coerceIn(0f, 100f), eta, line)
                    }
                }
            )

            if (response.exitCode != 0) {
                error(response.err.ifBlank { "yt-dlp exited with code ${response.exitCode}" })
            }

            stagingDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in MEDIA_EXTENSIONS }
                .maxByOrNull { it.length() }
                ?: error("The download finished, but no media file was produced.")
        }
    }

    suspend fun updateEngine(): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            check(initialized) { "The download engine is not ready yet." }
            val status = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)?.name
            MetadataCache.clear(context)
            status
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.destroyProcessById(processId) }
    }

    fun version(): String? = runCatching { YoutubeDL.version(context) }.getOrNull()

    private fun VideoInfo.toMetadata(fallbackUrl: String): VideoMetadata {
        val mappedFormats = formats.orEmpty().map { format ->
            SourceFormat(
                formatId = format.formatId.orEmpty(),
                extension = format.ext,
                width = format.width,
                height = format.height,
                fps = format.fps,
                vcodec = format.vcodec,
                acodec = format.acodec,
                filesize = format.fileSize,
                filesizeApprox = format.fileSizeApproximate,
                tbrKbps = format.tbr,
                abrKbps = format.abr,
                asrHz = format.asr
            )
        }
        return VideoMetadata(
            id = id ?: displayId ?: fallbackUrl.hashCode().toString(),
            title = title ?: fulltitle ?: "Untitled media",
            uploader = uploader,
            durationSeconds = duration.coerceAtLeast(0),
            thumbnailUrl = thumbnail,
            webpageUrl = webpageUrl ?: fallbackUrl,
            formats = mappedFormats
        )
    }

    companion object {
        private const val TAG = "YtDlpEngine"
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        private const val LATEST_RELEASE_PAGE =
            "https://github.com/yt-dlp/yt-dlp/releases/latest"
        private val TAG_REGEX = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")

        private val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mov", "m4v",
            "mp3", "m4a", "opus", "ogg", "aac", "flac", "wav"
        )
    }
}
