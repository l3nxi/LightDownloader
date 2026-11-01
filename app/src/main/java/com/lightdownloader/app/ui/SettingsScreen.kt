package com.lightdownloader.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightdownloader.app.BuildConfig
import com.lightdownloader.app.domain.DownloadTarget
import com.lightdownloader.app.domain.EngineState
import com.lightdownloader.app.domain.EngineUpdateState

@Composable
fun SettingsScreen(
    engineState: EngineState,
    updateState: EngineUpdateState,
    target: DownloadTarget,
    onCheckUpdate: (Boolean) -> Unit,
    onUpdateEngine: () -> Unit,
    onRetryEngine: () -> Unit,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit
) {
    LaunchedEffect(Unit) {
        onCheckUpdate(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.displaySmall
                )
            }
        }

        item(key = "engine-label") { SectionLabel("ENGINE") }
        item(key = "engine") {
            EngineCard(
                engineState = engineState,
                updateState = updateState,
                onCheckUpdate = onCheckUpdate,
                onUpdateEngine = onUpdateEngine,
                onRetryEngine = onRetryEngine
            )
        }

        item(key = "storage-label") { SectionLabel("STORAGE") }
        item(key = "storage") {
            StorageCard(
                target = target,
                onPickFolder = onPickFolder,
                onResetFolder = onResetFolder
            )
        }

        item(key = "about-label") { SectionLabel("ABOUT") }
        item(key = "about") { AboutCard(engineState = engineState) }

        item(key = "bottom") { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 2.dp)
    )
}

@Composable
private fun EngineCard(
    engineState: EngineState,
    updateState: EngineUpdateState,
    onCheckUpdate: (Boolean) -> Unit,
    onUpdateEngine: () -> Unit,
    onRetryEngine: () -> Unit
) {
    val installedVersion = (engineState as? EngineState.Ready)?.ytDlpVersion
    val busy = updateState is EngineUpdateState.Checking ||
        updateState is EngineUpdateState.Updating ||
        engineState is EngineState.Preparing

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(engineState)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "yt-dlp + FFmpeg",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = engineStatusLine(engineState, installedVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            UpdateStatusRow(updateState = updateState, installedVersion = installedVersion)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onCheckUpdate(true) },
                    enabled = !busy && engineState is EngineState.Ready,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Check")
                }

                if (updateState is EngineUpdateState.Available) {
                    Button(
                        onClick = onUpdateEngine,
                        enabled = !busy,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Update")
                    }
                }

                if (engineState is EngineState.Failed) {
                    TextButton(onClick = onRetryEngine) {
                        Text("Retry")
                    }
                }
            }

            Text(
                text = "Only checks the official stable release. Nothing downloads until you tap Update.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusDot(engineState: EngineState) {
    when (engineState) {
        EngineState.Idle, EngineState.Preparing -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
        }

        is EngineState.Ready -> {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }

        is EngineState.Failed -> {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private fun engineStatusLine(state: EngineState, installed: String?): String = when (state) {
    EngineState.Idle -> "Starting…"
    EngineState.Preparing -> "Unpacking Python and FFmpeg"
    is EngineState.Ready -> installed?.takeIf { it.isNotBlank() }
        ?.let { "yt-dlp $it" } ?: "Ready"
    is EngineState.Failed -> state.message
}

@Composable
private fun UpdateStatusRow(
    updateState: EngineUpdateState,
    installedVersion: String?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (updateState) {
            EngineUpdateState.Unknown -> {
                Text(
                    text = "Version check runs automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            EngineUpdateState.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            EngineUpdateState.Updating -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Updating yt-dlp…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is EngineUpdateState.Available -> {
                Column {
                    Text(
                        text = "Update available",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${installedVersion ?: "unknown"} → ${updateState.latest}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is EngineUpdateState.UpToDate -> {
                Text(
                    text = installedVersion?.let { "Up to date · $it" }
                        ?: "Up to date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is EngineUpdateState.Failed -> {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = updateState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StorageCard(
    target: DownloadTarget,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SAVE LOCATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = targetLabel(target),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onPickFolder) {
                    Text("Change")
                }
            }

            if (target is DownloadTarget.Tree) {
                TextButton(
                    onClick = onResetFolder,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Use Downloads/LightDownloader",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard(engineState: EngineState) {
    val version = (engineState as? EngineState.Ready)?.ytDlpVersion
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "LightDownloader ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "yt-dlp ${version ?: "—"} · FFmpeg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Files stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
