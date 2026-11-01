package com.lightdownloader.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lightdownloader.app.SearchUiState
import com.lightdownloader.app.domain.AudioFormat
import com.lightdownloader.app.domain.DownloadState
import com.lightdownloader.app.domain.DownloadTarget
import com.lightdownloader.app.domain.EngineState
import com.lightdownloader.app.domain.FileNames
import com.lightdownloader.app.domain.FormatCatalog
import com.lightdownloader.app.domain.FormatOption
import com.lightdownloader.app.domain.MediaCategory
import com.lightdownloader.app.domain.VideoMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    engineState: EngineState,
    downloadState: DownloadState?,
    onUrlChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFileNameChange: (String) -> Unit,
    onCategoryChange: (MediaCategory) -> Unit,
    onVideoHeightChange: (Int) -> Unit,
    onAudioFormatChange: (AudioFormat) -> Unit,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownload: () -> Unit,
    onRetryEngine: () -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val metadata = state.metadata
    val videoOptions = remember(metadata) {
        metadata?.let(FormatCatalog::videoOptions).orEmpty()
    }
    val audioOptions = remember(metadata) {
        metadata?.let(FormatCatalog::audioOptions).orEmpty()
    }
    val selectedEstimate = remember(
        metadata,
        state.category,
        state.selectedVideoHeight,
        state.selectedAudioFormat
    ) {
        metadata?.let {
            FormatCatalog.selectedEstimate(
                it,
                state.category,
                state.selectedVideoHeight,
                state.selectedAudioFormat
            )
        } ?: 0L
    }
    val selectedOptionAvailable = if (state.category == MediaCategory.VIDEO) {
        videoOptions.any {
            it.label == "${state.selectedVideoHeight}p" && it.available
        }
    } else {
        audioOptions.any {
            it.label == state.selectedAudioFormat.label && it.available
        }
    }
    val isBusy = downloadState is DownloadState.Running ||
        downloadState is DownloadState.Publishing
    val canDownload = metadata != null &&
        state.customFileName.isNotBlank() &&
        selectedOptionAvailable &&
        engineState is EngineState.Ready &&
        !isBusy

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Header()
        }

        if (engineState !is EngineState.Ready) {
            item(key = "engine") {
                EngineNotice(
                    state = engineState,
                    onRetry = onRetryEngine
                )
            }
        }

        item(key = "url") {
            UrlBar(
                value = state.url,
                isFetching = state.isFetchingInfo,
                onValueChange = onUrlChange,
                onSearch = {
                    focusManager.clearFocus()
                    onSearch()
                }
            )
        }

        if (state.infoError != null) {
            item(key = "info-error") {
                InfoError(message = state.infoError)
            }
        }

        if (metadata != null) {
            item(key = "options") {
                DownloadOptionCard(
                    metadata = metadata,
                    state = state,
                    videoOptions = videoOptions,
                    audioOptions = audioOptions,
                    selectedEstimate = selectedEstimate,
                    selectedOptionAvailable = selectedOptionAvailable,
                    canDownload = canDownload,
                    engineState = engineState,
                    onFileNameChange = onFileNameChange,
                    onCategoryChange = onCategoryChange,
                    onVideoHeightChange = onVideoHeightChange,
                    onAudioFormatChange = onAudioFormatChange,
                    onPickFolder = onPickFolder,
                    onResetFolder = onResetFolder,
                    onDownload = onDownload
                )
            }
        } else if (!state.isFetchingInfo) {
            item(key = "empty") {
                EmptyHint()
            }
        }

        item(key = "download-state") {
            DownloadPanel(
                state = downloadState,
                onCancel = onCancelDownload,
                onDismiss = onDismissDownload,
                onRetry = onDownload,
                onOpenPlayer = onOpenPlayer
            )
        }

        item(key = "bottom-space") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "LIGHTDOWNLOADER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "New download",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Paste a link. Pick a format.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EngineNotice(
    state: EngineState,
    onRetry: () -> Unit
) {
    val failed = state is EngineState.Failed
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (failed) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            }

            Spacer(Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (failed) "Engine unavailable" else "Preparing media engine",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (failed) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (state is EngineState.Failed) {
                        state.message
                    } else {
                        "First launch unpacks Python and FFmpeg. This happens once."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (failed) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun UrlBar(
    value: String,
    isFetching: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Paste a link", maxLines = 1) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (value.isNotEmpty() && !isFetching) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear link")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )

        FilledIconButton(
            onClick = onSearch,
            enabled = value.isNotBlank() && !isFetching,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(56.dp)
        ) {
            if (isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Rounded.Search, contentDescription = "Fetch link")
            }
        }
    }
}

@Composable
private fun InfoError(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Paste a link to begin",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The thumbnail, title, format sizes and save location appear here before anything downloads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionCard(
    metadata: VideoMetadata,
    state: SearchUiState,
    videoOptions: List<FormatOption>,
    audioOptions: List<FormatOption>,
    selectedEstimate: Long,
    selectedOptionAvailable: Boolean,
    canDownload: Boolean,
    engineState: EngineState,
    onFileNameChange: (String) -> Unit,
    onCategoryChange: (MediaCategory) -> Unit,
    onVideoHeightChange: (Int) -> Unit,
    onAudioFormatChange: (AudioFormat) -> Unit,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onDownload: () -> Unit
) {
    val extension = FileNames.extensionFor(state.category, state.selectedAudioFormat)
    val safeName = FileNames.sanitize(state.customFileName, metadata.title)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Thumbnail(metadata)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = metadata.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    metadata.uploader?.takeIf { it.isNotBlank() }?.let { uploader ->
                        Text(
                            text = uploader,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "FILE NAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )
                    OutlinedTextField(
                        value = state.customFileName,
                        onValueChange = onFileNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        suffix = {
                            Text(
                                text = ".$extension",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingText = {
                            Text(
                                text = "Saves as $safeName.$extension",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.category == MediaCategory.VIDEO,
                        onClick = { onCategoryChange(MediaCategory.VIDEO) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Video")
                    }
                    SegmentedButton(
                        selected = state.category == MediaCategory.MUSIC,
                        onClick = { onCategoryChange(MediaCategory.MUSIC) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Music")
                    }
                }

                Crossfade(
                    targetState = state.category,
                    label = "format-options"
                ) { category ->
                    when (category) {
                        MediaCategory.VIDEO -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                videoOptions.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        pair.forEach { option ->
                                            val height = option.label
                                                .removeSuffix("p")
                                                .toIntOrNull() ?: 0
                                            FormatOptionCard(
                                                option = option,
                                                selected = state.selectedVideoHeight == height,
                                                onClick = { onVideoHeightChange(height) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (pair.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        MediaCategory.MUSIC -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                audioOptions.forEach { option ->
                                    val format = AudioFormat.entries.firstOrNull {
                                        it.label == option.label
                                    } ?: return@forEach
                                    FormatOptionCard(
                                        option = option,
                                        selected = state.selectedAudioFormat == format,
                                        onClick = { onAudioFormatChange(format) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                FolderPanel(
                    target = state.target,
                    onPickFolder = onPickFolder,
                    onResetFolder = onResetFolder
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val buttonLabel = when {
                        engineState is EngineState.Preparing -> "Preparing engine…"
                        !selectedOptionAvailable -> "Format unavailable"
                        selectedEstimate > 0L ->
                            "Download · ${FormatCatalog.formatSize(selectedEstimate)}"
                        else -> "Download"
                    }
                    Button(
                        onClick = onDownload,
                        enabled = canDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(buttonLabel, fontWeight = FontWeight.SemiBold)
                    }

                    if (engineState is EngineState.Failed) {
                        Text(
                            text = engineState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(metadata: VideoMetadata) {
    val context = LocalContext.current
    val thumbnailRequest = remember(metadata.thumbnailUrl) {
        metadata.thumbnailUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(220)
                .build()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (thumbnailRequest != null) {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = "Thumbnail for ${metadata.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        startY = 120f
                    )
                )
        )

        if (metadata.durationSeconds > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = formatClock(metadata.durationSeconds * 1000L),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    option: FormatOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondary = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        enabled = option.available,
        shape = RoundedCornerShape(10.dp),
        color = container,
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content
                )
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (option.available) {
                    FormatCatalog.formatSize(option.estimatedBytes)
                } else {
                    option.detail ?: "Not available"
                },
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderPanel(
    target: DownloadTarget,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SAVE TO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = targetLabel(target),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onPickFolder) {
                    Text("Change Folder")
                }
            }

            if (target is DownloadTarget.Tree) {
                TextButton(
                    onClick = onResetFolder,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Use Downloads/LightDownloader instead",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadPanel(
    state: DownloadState?,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
        exit = fadeOut()
    ) {
        when (state) {
            is DownloadState.Running -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.phase,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${state.percent.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.percent > 0f) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val eta = formatEtaLabel(state.etaSeconds)
                        if (eta.isNotBlank()) {
                            Text(
                                text = eta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = state.statusLine.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }

            is DownloadState.Publishing -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Saving to storage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = state.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            is DownloadState.Success -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saved",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = state.displayPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { onOpenPlayer(state.recordId) }) {
                            Text("Play")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                }
            }

            is DownloadState.Failed -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Download failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = onRetry) { Text("Try again") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }

            is DownloadState.Cancelled -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Download cancelled",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }

            null -> Unit
        }
    }
}
