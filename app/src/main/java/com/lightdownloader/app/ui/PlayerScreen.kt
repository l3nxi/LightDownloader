package com.lightdownloader.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lightdownloader.app.MainViewModel
import com.lightdownloader.app.data.local.DownloadRecord
import com.lightdownloader.app.domain.MediaCategory
import com.lightdownloader.app.ui.theme.Amber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(
    mediaId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var record by remember(mediaId) { mutableStateOf<DownloadRecord?>(null) }
    var startPosition by remember(mediaId) { mutableLongStateOf(0L) }
    var loading by remember(mediaId) { mutableStateOf(true) }

    LaunchedEffect(mediaId) {
        val loadedRecord = viewModel.recordFor(mediaId)
        val savedPosition = viewModel.playbackFor(mediaId)
        record = loadedRecord
        startPosition = savedPosition
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Amber,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val loaded = record
            if (loaded == null) {
                MissingMedia(onBack = onBack)
            } else {
                key(loaded.id) {
                    PlayerContent(
                        record = loaded,
                        startPosition = startPosition,
                        viewModel = viewModel,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerContent(
    record: DownloadRecord,
    startPosition: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isAudio = record.mediaType == MediaCategory.MUSIC.name

    val player = remember(record.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(record.contentUri))
            prepare()
            if (startPosition > 0L) seekTo(startPosition)
            playWhenReady = true
        }
    }

    val thumbnailRequest = remember(record.thumbnailUrl) {
        record.thumbnailUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(200)
                .build()
        }
    }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var durationMs by remember {
        mutableLongStateOf(record.durationMs.coerceAtLeast(0L))
    }
    var positionMs by remember {
        mutableLongStateOf(startPosition.coerceAtLeast(0L))
    }
    var buffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showResumeChip by remember { mutableStateOf(startPosition > 1_000L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }

    fun showControls() {
        controlsVisible = true
        interactionTick++
    }

    fun toggleOrientation() {
        val act = activity ?: return
        act.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        showControls()
    }

    fun persistPlayback() {
        val playerDuration = player.duration
        val safeDuration = if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
            playerDuration
        } else {
            durationMs
        }
        viewModel.savePlayback(
            mediaId = record.id,
            videoId = record.videoId,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = safeDuration
        )
    }

    fun seekBy(deltaMs: Long, showSeekFeedback: Boolean) {
        val playerDuration = player.duration
        val upperBound = if (playerDuration == C.TIME_UNSET || playerDuration <= 0L) {
            Long.MAX_VALUE
        } else {
            playerDuration
        }
        val target = (player.currentPosition + deltaMs).coerceIn(0L, upperBound)
        player.seekTo(target)
        positionMs = target
        if (showSeekFeedback) {
            seekFeedback = SeekFeedback(deltaMs, System.nanoTime())
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    persistPlayback()
                    showControls()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val playerDuration = player.duration
                    if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                        durationMs = playerDuration
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "Playback failed."
            }
        }

        player.addListener(listener)
        onDispose {
            persistPlayback()
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val playerDuration = player.duration
            if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                durationMs = playerDuration
            }
            delay(250)
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(2_000)
            if (player.isPlaying) persistPlayback()
        }
    }

    LaunchedEffect(Unit) {
        if (showResumeChip) {
            delay(5_000)
            showResumeChip = false
        }
    }

    LaunchedEffect(controlsVisible, interactionTick, isScrubbing) {
        if (controlsVisible) {
            delay(3_000)
            if (!isScrubbing) controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(800)
            seekFeedback = null
        }
    }

    LaunchedEffect(isLandscape, activity) {
        val act = activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (isLandscape) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity) {
        onDispose {
            val act = activity ?: return@onDispose
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.getInsetsController(act.window, act.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                persistPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        persistPlayback()
        onBack()
    }

    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
    val displayPosition = if (isScrubbing) {
        scrubPosition
    } else {
        positionMs.toFloat().coerceIn(0f, sliderMax)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isAudio) {
            if (thumbnailRequest != null) {
                AsyncImage(
                    model = thumbnailRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.32f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tap once to toggle the overlay, double tap left/right to seek 10s.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) interactionTick++
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x >= size.width / 2f
                            controlsVisible = false
                            seekBy(
                                deltaMs = if (forward) 10_000L else -10_000L,
                                showSeekFeedback = true
                            )
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        persistPlayback()
                        onBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${mediaTypeLabel(record.mediaType)} · ${formatClock(durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                IconButton(onClick = { toggleOrientation() }) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = if (isLandscape) "Portrait" else "Landscape",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(
                    icon = Icons.Rounded.Replay10,
                    label = "Rewind 10 seconds",
                    onClick = { seekBy(-10_000L, showSeekFeedback = false) }
                )
                FilledIconButton(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        showControls()
                    },
                    modifier = Modifier.size(78.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Amber,
                        contentColor = Color(0xFF1A1400)
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(42.dp)
                    )
                }
                ControlButton(
                    icon = Icons.Rounded.Forward10,
                    label = "Skip 10 seconds forward",
                    onClick = { seekBy(10_000L, showSeekFeedback = false) }
                )
            }
        }

        if (buffering && playbackError == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 58.dp),
                color = Amber,
                trackColor = Color.Transparent
            )
        }

        if (showResumeChip) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 68.dp)
            ) {
                Text(
                    text = "Resumed at ${formatClock(startPosition)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        seekFeedback?.let { feedback ->
            val forward = feedback.deltaMs > 0L
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp),
                contentAlignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.66f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (forward) {
                                Icons.Rounded.Forward10
                            } else {
                                Icons.Rounded.Replay10
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "10 seconds",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                playbackError?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF8A80),
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                playbackError = null
                                player.prepare()
                                player.play()
                                showControls()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Amber)
                        ) {
                            Text("Retry")
                        }
                    }
                }

                Slider(
                    value = displayPosition,
                    onValueChange = {
                        showControls()
                        isScrubbing = true
                        scrubPosition = it
                    },
                    onValueChangeFinished = {
                        player.seekTo(scrubPosition.toLong())
                        positionMs = scrubPosition.toLong()
                        isScrubbing = false
                        showControls()
                    },
                    valueRange = 0f..sliderMax,
                    enabled = durationMs > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Amber,
                        activeTrackColor = Amber,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatClock(displayPosition.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatClock(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = record.filePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.50f),
        contentColor = Color.White,
        modifier = Modifier.size(56.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun MissingMedia(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Media not found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The file may have been moved or deleted.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) {
            Text("Back to library")
        }
    }
}

private data class SeekFeedback(val deltaMs: Long, val nonce: Long)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
