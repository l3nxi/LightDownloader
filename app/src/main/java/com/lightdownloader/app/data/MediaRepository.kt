package com.lightdownloader.app.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lightdownloader.app.data.local.AppDatabase
import com.lightdownloader.app.data.local.DownloadRecord
import com.lightdownloader.app.data.local.PlaybackState
import com.lightdownloader.app.data.storage.StoragePublisher
import com.lightdownloader.app.data.ytdlp.YtDlpEngine
import com.lightdownloader.app.domain.DownloadRequest
import com.lightdownloader.app.domain.DownloadState
import com.lightdownloader.app.domain.DownloadTarget
import com.lightdownloader.app.domain.EngineState
import com.lightdownloader.app.domain.EngineUpdateState
import com.lightdownloader.app.domain.VideoMetadata
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class LibraryItem(
    val record: DownloadRecord,
    val resumePositionMs: Long,
    val playbackDurationMs: Long
)

class MediaRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val appContext = context.applicationContext
    private val engine = YtDlpEngine(appContext)
    private val publisher = StoragePublisher(appContext)
    private val prefs = appContext.getSharedPreferences("lightdownloader", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _engineUpdate = MutableStateFlow<EngineUpdateState>(EngineUpdateState.Unknown)
    val engineUpdate: StateFlow<EngineUpdateState> = _engineUpdate.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var downloadJob: Job? = null

    @Volatile
    private var activeProcessId: String? = null

    @Volatile
    private var cancelRequested = false

    // Serialize the 1 Hz player checkpoints instead of launching one writer per tick.
    private val playbackUpdates = Channel<PlaybackState>(Channel.CONFLATED)

    init {
        scope.launch {
            for (state in playbackUpdates) {
                runCatching { database.playback().upsert(state) }
            }
        }
    }

    val library: Flow<List<LibraryItem>> = combine(
        database.downloads().observeAll(),
        database.playback().observeAll()
    ) { records, playbackStates ->
        val byMediaId = playbackStates.associateBy { it.mediaId }
        records.map { record ->
            val playback = byMediaId[record.id]
            LibraryItem(
                record = record,
                resumePositionMs = playback?.positionMs ?: 0L,
                playbackDurationMs = playback?.durationMs ?: record.durationMs
            )
        }
    }

    fun prepareEngine() {
        val current = _engineState.value
        if (current is EngineState.Preparing || current is EngineState.Ready) return

        _engineState.value = EngineState.Preparing
        scope.launch {
            engine.ensureInitialized().fold(
                onSuccess = {
                    _engineState.value = EngineState.Ready(engine.version())
                    checkEngineUpdate(force = false)
                },
                onFailure = {
                    _engineState.value = EngineState.Failed(it.userMessage())
                }
            )
        }
    }

    fun retryEngine() {
        _engineState.value = EngineState.Idle
        prepareEngine()
    }

    /**
     * Compares the installed yt-dlp with the latest stable release. No download
     * happens here; the user only sees an update action when one is available.
     */
    fun checkEngineUpdate(force: Boolean = false) {
        val state = _engineUpdate.value
        if (state is EngineUpdateState.Checking || state is EngineUpdateState.Updating) return
        if (_engineState.value is EngineState.Preparing) return

        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_VERSION_CHECK, 0L)
        if (!force && now - lastCheck < VERSION_CHECK_INTERVAL_MS) return

        _engineUpdate.value = EngineUpdateState.Checking
        scope.launch {
            val initialized = engine.ensureInitialized()
            if (initialized.isFailure) {
                _engineUpdate.value = EngineUpdateState.Failed(
                    initialized.exceptionOrNull()?.userMessage() ?: "Engine initialization failed"
                )
                return@launch
            }

            val installed = engine.version()
            if (_engineState.value !is EngineState.Ready) {
                _engineState.value = EngineState.Ready(installed)
            }

            engine.latestVersion().fold(
                onSuccess = { latest ->
                    prefs.edit().putLong(KEY_LAST_VERSION_CHECK, now).apply()
                    _engineUpdate.value = if (isNewerVersion(latest, installed)) {
                        EngineUpdateState.Available(installed, latest)
                    } else {
                        EngineUpdateState.UpToDate(installed, latest)
                    }
                },
                onFailure = { error ->
                    _engineUpdate.value = EngineUpdateState.Failed(error.userMessage())
                }
            )
        }
    }

    fun updateEngine() {
        if (_engineState.value is EngineState.Preparing) return
        if (_engineUpdate.value is EngineUpdateState.Updating) return

        _engineState.value = EngineState.Preparing
        _engineUpdate.value = EngineUpdateState.Updating
        scope.launch {
            val initialized = engine.ensureInitialized()
            if (initialized.isFailure) {
                val failure = initialized.exceptionOrNull()
                    ?: IllegalStateException("Engine initialization failed")
                _engineState.value = EngineState.Failed(failure.userMessage())
                _engineUpdate.value = EngineUpdateState.Failed(failure.userMessage())
                return@launch
            }

            val result = engine.updateEngine()
            val installed = engine.version()
            _engineState.value = EngineState.Ready(installed)
            result.fold(
                onSuccess = { status ->
                    prefs.edit().putLong(KEY_LAST_VERSION_CHECK, System.currentTimeMillis()).apply()
                    _engineUpdate.value = EngineUpdateState.UpToDate(installed, installed)
                    _messages.tryEmit(
                        if (status == "DONE") {
                            "yt-dlp updated${installed?.let { " to $it" } ?: ""}."
                        } else {
                            "yt-dlp is already up to date."
                        }
                    )
                },
                onFailure = { error ->
                    _engineUpdate.value = EngineUpdateState.Failed(error.userMessage())
                    _messages.tryEmit("Update failed: ${error.userMessage()}")
                }
            )
        }
    }

    suspend fun fetchMetadata(url: String): Result<VideoMetadata> {
        val initialized = engine.ensureInitialized()
        if (initialized.isFailure) {
            val failure = initialized.exceptionOrNull()
                ?: IllegalStateException("Engine initialization failed")
            _engineState.value = EngineState.Failed(failure.userMessage())
            return Result.failure(failure)
        }

        if (_engineState.value !is EngineState.Ready) {
            _engineState.value = EngineState.Ready(engine.version())
        }

        return engine.fetchInfo(url)
    }

    fun startDownload(request: DownloadRequest) {
        if (downloadJob?.isActive == true) return
        cancelRequested = false
        _downloadState.value = DownloadState.Running(
            percent = 0f,
            etaSeconds = -1L,
            phase = "Starting download",
            statusLine = null
        )

        downloadJob = scope.launch {
            val processId = UUID.randomUUID().toString()
            activeProcessId = processId
            val stagingRoot = File(
                appContext.getExternalFilesDir(null) ?: appContext.cacheDir,
                "staging"
            )
            val staging = File(stagingRoot, "${System.currentTimeMillis()}_${processId.take(8)}")

            try {
                engine.ensureInitialized().getOrThrow()

                val mediaFile = engine.download(request, staging, processId) { percent, eta, line ->
                    _downloadState.value = DownloadState.Running(
                        percent = percent,
                        etaSeconds = eta,
                        phase = if (percent >= 99.5f) "Finalizing" else "Downloading",
                        statusLine = line?.cleanProgressLine()
                    )
                }.getOrThrow()

                _downloadState.value = DownloadState.Publishing(mediaFile.name)
                val published = publisher.publish(mediaFile, request.target)

                val displayTitle = request.customFileName.trim().ifBlank { request.title }
                val record = DownloadRecord(
                    title = displayTitle,
                    sourceTitle = request.title,
                    filePath = published.displayPath,
                    contentUri = published.contentUri.toString(),
                    thumbnailUrl = request.thumbnailUrl,
                    timestamp = System.currentTimeMillis(),
                    mediaType = request.category.name,
                    fileName = published.fileName,
                    sizeBytes = published.sizeBytes,
                    durationMs = request.durationSeconds * 1000L,
                    sourceUrl = request.sourceUrl,
                    videoId = request.videoId
                )
                val id = database.downloads().insert(record)
                database.playback().upsert(
                    PlaybackState(
                        mediaId = id,
                        videoId = request.videoId,
                        positionMs = 0L,
                        durationMs = record.durationMs,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                _downloadState.value = DownloadState.Success(
                    recordId = id,
                    fileName = published.fileName,
                    displayPath = published.displayPath
                )
                _messages.tryEmit("Saved to ${published.displayPath}")
            } catch (cancelled: CancellationException) {
                _downloadState.value = DownloadState.Cancelled
                throw cancelled
            } catch (throwable: Throwable) {
                if (cancelRequested || throwable is YoutubeDL.CanceledException) {
                    _downloadState.value = DownloadState.Cancelled
                } else {
                    val message = throwable.userMessage()
                    _downloadState.value = DownloadState.Failed(message)
                }
            } finally {
                activeProcessId = null
                runCatching { staging.deleteRecursively() }
            }
        }
    }

    fun cancelDownload() {
        cancelRequested = true
        activeProcessId?.let(engine::cancel)
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Cancelled
    }

    fun clearDownloadState() {
        if (_downloadState.value is DownloadState.Running ||
            _downloadState.value is DownloadState.Publishing
        ) {
            return
        }
        _downloadState.value = null
    }

    /**
     * Removes the media file from wherever it was published, then drops the
     * library and playback rows. Missing files are treated as already deleted.
     */
    suspend fun deleteDownload(mediaId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val record = database.downloads().findById(mediaId) ?: return@runCatching
            val uri = Uri.parse(record.contentUri)
            val removed = deleteFile(uri)
            if (!removed && fileExists(uri)) {
                error("The file could not be deleted from storage.")
            }
            database.playback().delete(mediaId)
            database.downloads().deleteById(mediaId)
            prefs.edit().remove(KEY_POSITION_PREFIX + mediaId).apply()
        }
    }

    private fun deleteFile(uri: Uri): Boolean = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            val viaResolver = runCatching {
                appContext.contentResolver.delete(uri, null, null)
            }.getOrDefault(0)
            if (viaResolver > 0) {
                true
            } else {
                runCatching {
                    DocumentFile.fromSingleUri(appContext, uri)?.delete() == true
                }.getOrDefault(false)
            }
        }

        ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).delete() } ?: true
        else -> false
    }

    private fun fileExists(uri: Uri): Boolean = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)

        ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).exists() } == true
        else -> false
    }

    suspend fun recordFor(mediaId: Long): DownloadRecord? =
        withContext(Dispatchers.IO) { database.downloads().findById(mediaId) }

    suspend fun playbackFor(mediaId: Long): Long = withContext(Dispatchers.IO) {
        val fromDatabase = runCatching { database.playback().find(mediaId)?.positionMs }
            .getOrNull()
            ?: 0L
        val mirror = prefs.getLong(KEY_POSITION_PREFIX + mediaId, 0L)
        maxOf(fromDatabase, mirror)
    }

    fun savePlayback(mediaId: Long, videoId: String, positionMs: Long, durationMs: Long) {
        if (mediaId <= 0L) return
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)

        // SharedPreferences gives an immediate crash-safe mirror; Room remains the
        // queryable source of truth for the library/resume UI.
        prefs.edit().putLong(KEY_POSITION_PREFIX + mediaId, safePosition).apply()
        playbackUpdates.trySend(
            PlaybackState(
                mediaId = mediaId,
                videoId = videoId,
                positionMs = safePosition,
                durationMs = safeDuration,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun currentTarget(): DownloadTarget {
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return DownloadTarget.PublicDownloads
        val name = prefs.getString(KEY_TREE_NAME, null) ?: return DownloadTarget.PublicDownloads
        val uri = Uri.parse(uriString)
        val hasWrite = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (!hasWrite) {
            clearSavedTree()
            return DownloadTarget.PublicDownloads
        }
        return DownloadTarget.Tree(uri, name)
    }

    suspend fun resolveTree(uri: Uri): Result<DownloadTarget.Tree> = withContext(Dispatchers.IO) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val tree = DocumentFile.fromTreeUri(appContext, uri)
                ?: error("That folder can't be opened.")
            val name = tree.name?.takeIf { it.isNotBlank() }
                ?: error("That folder can't be used.")
            val target = DownloadTarget.Tree(uri, name)
            prefs.edit()
                .putString(KEY_TREE_URI, uri.toString())
                .putString(KEY_TREE_NAME, name)
                .apply()
            target
        }
    }

    fun resetTarget(): DownloadTarget {
        clearSavedTree()
        return DownloadTarget.PublicDownloads
    }

    private fun clearSavedTree() {
        prefs.edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_TREE_NAME)
            .apply()
    }

    companion object {
        private const val KEY_LAST_VERSION_CHECK = "last_version_check"
        private const val KEY_TREE_URI = "saf_tree_uri"
        private const val KEY_TREE_NAME = "saf_tree_name"
        private const val KEY_POSITION_PREFIX = "position_"
        private const val VERSION_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

        private fun isNewerVersion(latest: String, installed: String?): Boolean {
            if (installed.isNullOrBlank()) return true
            val latestParts = versionParts(latest)
            val installedParts = versionParts(installed)
            val size = maxOf(latestParts.size, installedParts.size)
            for (index in 0 until size) {
                val newPart = latestParts.getOrElse(index) { 0L }
                val oldPart = installedParts.getOrElse(index) { 0L }
                if (newPart != oldPart) return newPart > oldPart
            }
            return false
        }

        private fun versionParts(version: String): List<Long> =
            version.split(Regex("[^0-9]+")).mapNotNull { it.toLongOrNull() }
    }
}
