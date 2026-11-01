package com.lightdownloader.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightdownloader.app.data.LibraryItem
import com.lightdownloader.app.data.MediaRepository
import com.lightdownloader.app.data.userMessage
import com.lightdownloader.app.domain.AudioFormat
import com.lightdownloader.app.domain.DownloadRequest
import com.lightdownloader.app.domain.DownloadState
import com.lightdownloader.app.domain.DownloadTarget
import com.lightdownloader.app.domain.EngineState
import com.lightdownloader.app.domain.EngineUpdateState
import com.lightdownloader.app.domain.FileNames
import com.lightdownloader.app.domain.FormatCatalog
import com.lightdownloader.app.domain.MediaCategory
import com.lightdownloader.app.domain.UrlTools
import com.lightdownloader.app.domain.VideoMetadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val url: String = "",
    val isFetchingInfo: Boolean = false,
    val metadata: VideoMetadata? = null,
    val infoError: String? = null,
    val customFileName: String = "",
    val category: MediaCategory = MediaCategory.VIDEO,
    val selectedVideoHeight: Int = 720,
    val selectedAudioFormat: AudioFormat = AudioFormat.MP3,
    val target: DownloadTarget = DownloadTarget.PublicDownloads
)

class MainViewModel(private val repository: MediaRepository) : ViewModel() {

    private val _searchState = MutableStateFlow(
        SearchUiState(target = repository.currentTarget())
    )
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices = _notices.asSharedFlow()

    val messages = repository.messages
    val engineState: StateFlow<EngineState> = repository.engineState
    val engineUpdate: StateFlow<EngineUpdateState> = repository.engineUpdate
    val downloadState: StateFlow<DownloadState?> = repository.downloadState

    val library: StateFlow<List<LibraryItem>> = repository.library.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    init {
        repository.prepareEngine()
    }

    private var searchJob: Job? = null

    fun onUrlChange(value: String) {
        val current = _searchState.value
        val normalized = UrlTools.normalize(value)
        val sameVideo = current.metadata?.let { metadata ->
            normalized != null &&
                (normalized == metadata.webpageUrl ||
                    UrlTools.videoId(normalized) == metadata.id)
        } == true

        searchJob?.cancel()

        if (value.isBlank()) {
            _searchState.update {
                it.copy(
                    url = "",
                    metadata = null,
                    customFileName = "",
                    infoError = null,
                    isFetchingInfo = false
                )
            }
            return
        }

        _searchState.update {
            if (sameVideo) {
                it.copy(url = value, infoError = null)
            } else {
                it.copy(
                    url = value,
                    metadata = null,
                    customFileName = "",
                    infoError = null,
                    isFetchingInfo = false
                )
            }
        }

        // Start fetching as soon as a complete link is pasted. The short delay
        // keeps clipboard/typing bursts from spawning several extractions.
        if (!sameVideo && normalized != null && UrlTools.isCompleteVideoLink(normalized)) {
            searchJob = viewModelScope.launch {
                delay(AUTO_SEARCH_DEBOUNCE_MS)
                if (_searchState.value.url == value) {
                    runSearch(normalized, updateField = false)
                }
            }
        }
    }

    fun onFileNameChange(value: String) {
        _searchState.update { it.copy(customFileName = value) }
    }

    fun onCategoryChange(category: MediaCategory) {
        _searchState.update { current ->
            if (category == MediaCategory.VIDEO && current.metadata != null) {
                val options = FormatCatalog.videoOptions(current.metadata)
                val preferred = if (options.any {
                        it.available && it.label == "${current.selectedVideoHeight}p"
                    }
                ) {
                    current.selectedVideoHeight
                } else {
                    FormatCatalog.VIDEO_HEIGHTS.firstOrNull { height ->
                        options.any { it.available && it.label == "${height}p" }
                    } ?: current.selectedVideoHeight
                }
                current.copy(category = category, selectedVideoHeight = preferred)
            } else {
                current.copy(category = category)
            }
        }
    }

    fun onVideoHeightChange(height: Int) {
        _searchState.update { it.copy(selectedVideoHeight = height) }
    }

    fun onAudioFormatChange(format: AudioFormat) {
        _searchState.update { it.copy(selectedAudioFormat = format) }
    }

    fun search() {
        val current = _searchState.value
        if (current.isFetchingInfo) return

        val normalized = UrlTools.normalize(current.url)
        if (normalized == null) {
            _searchState.update {
                it.copy(infoError = "Enter a valid http(s) link.")
            }
            return
        }

        val sameVideo = current.metadata?.let { metadata ->
            normalized == metadata.webpageUrl ||
                UrlTools.videoId(normalized) == metadata.id
        } == true
        if (sameVideo) {
            _searchState.update { it.copy(url = normalized, infoError = null) }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            runSearch(normalized, updateField = true)
        }
    }

    private suspend fun runSearch(normalized: String, updateField: Boolean) {
        _searchState.update { current ->
            current.copy(
                url = if (updateField) normalized else current.url,
                isFetchingInfo = true,
                infoError = null,
                metadata = null,
                customFileName = ""
            )
        }

        val result = repository.fetchMetadata(normalized)
        currentCoroutineContext().ensureActive()

        result.fold(
            onSuccess = { metadata ->
                val options = FormatCatalog.videoOptions(metadata)
                val hasVideo = options.any { it.available }
                val preferredHeight = listOf(720, 1080, 480, 360).firstOrNull { height ->
                    options.any { it.available && it.label == "${height}p" }
                } ?: 720
                _searchState.update {
                    it.copy(
                        isFetchingInfo = false,
                        metadata = metadata,
                        infoError = null,
                        customFileName = metadata.title,
                        category = if (hasVideo) MediaCategory.VIDEO else MediaCategory.MUSIC,
                        selectedVideoHeight = preferredHeight
                    )
                }
            },
            onFailure = { error ->
                _searchState.update {
                    it.copy(
                        isFetchingInfo = false,
                        metadata = null,
                        infoError = error.userMessage()
                    )
                }
            }
        )
    }

    fun startDownload() {
        val current = _searchState.value
        val metadata = current.metadata ?: return

        val option = if (current.category == MediaCategory.VIDEO) {
            FormatCatalog.videoOptions(metadata)
                .firstOrNull { it.label == "${current.selectedVideoHeight}p" }
        } else {
            FormatCatalog.audioOptions(metadata)
                .firstOrNull { it.label == current.selectedAudioFormat.label }
        }

        if (option == null || !option.available) {
            _notices.tryEmit("That format is not available for this link.")
            return
        }

        val safeName = FileNames.sanitize(current.customFileName, metadata.title)
        if (safeName.isBlank()) {
            _notices.tryEmit("Enter a file name first.")
            return
        }

        repository.startDownload(
            DownloadRequest(
                sourceUrl = metadata.webpageUrl,
                videoId = metadata.id,
                title = metadata.title,
                customFileName = current.customFileName.trim(),
                category = current.category,
                videoHeight = current.selectedVideoHeight,
                audioFormat = current.selectedAudioFormat,
                target = current.target,
                thumbnailUrl = metadata.thumbnailUrl,
                durationSeconds = metadata.durationSeconds,
                estimatedBytes = option.estimatedBytes
            )
        )
    }

    fun cancelDownload() {
        repository.cancelDownload()
    }

    fun dismissDownload() {
        repository.clearDownloadState()
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            repository.resolveTree(uri).fold(
                onSuccess = { target ->
                    _searchState.update { it.copy(target = target) }
                },
                onFailure = {
                    _notices.tryEmit("Folder selection failed: ${it.userMessage()}")
                }
            )
        }
    }

    fun resetTarget() {
        _searchState.update { it.copy(target = repository.resetTarget()) }
    }

    fun retryEngine() {
        repository.retryEngine()
    }

    fun updateEngine() {
        repository.updateEngine()
    }

    fun checkEngineUpdate(force: Boolean = false) {
        repository.checkEngineUpdate(force)
    }

    fun notify(message: String) {
        _notices.tryEmit(message)
    }

    fun deleteDownload(mediaId: Long) {
        viewModelScope.launch {
            repository.deleteDownload(mediaId).fold(
                onSuccess = { _notices.tryEmit("Deleted from storage.") },
                onFailure = { _notices.tryEmit("Couldn't delete: ${it.userMessage()}") }
            )
        }
    }

    suspend fun recordFor(mediaId: Long) = repository.recordFor(mediaId)

    suspend fun playbackFor(mediaId: Long) = repository.playbackFor(mediaId)

    fun savePlayback(mediaId: Long, videoId: String, positionMs: Long, durationMs: Long) {
        repository.savePlayback(mediaId, videoId, positionMs, durationMs)
    }

    companion object {
        private const val AUTO_SEARCH_DEBOUNCE_MS = 500L

        fun factory(repository: MediaRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(repository) }
        }
    }
}
