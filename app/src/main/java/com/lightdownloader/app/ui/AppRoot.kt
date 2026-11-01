package com.lightdownloader.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lightdownloader.app.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun LightDownloaderRoot(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val engineUpdate by viewModel.engineUpdate.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        launch {
            viewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
        launch {
            viewModel.notices.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    val legacyPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.notify(
                "Storage permission is needed to save into public Downloads on Android 9 and below."
            )
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("home") {
            HomeScreen(
                searchState = searchState,
                engineState = engineState,
                engineUpdate = engineUpdate,
                downloadState = downloadState,
                library = library,
                snackbarHostState = snackbarHostState,
                onUrlChange = viewModel::onUrlChange,
                onSearch = viewModel::search,
                onFileNameChange = viewModel::onFileNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onVideoHeightChange = viewModel::onVideoHeightChange,
                onAudioFormatChange = viewModel::onAudioFormatChange,
                onPickFolder = { folderPicker.launch(null) },
                onResetFolder = viewModel::resetTarget,
                onDownload = viewModel::startDownload,
                onCancelDownload = viewModel::cancelDownload,
                onDismissDownload = viewModel::dismissDownload,
                onRetryEngine = viewModel::retryEngine,
                onUpdateEngine = viewModel::updateEngine,
                onCheckEngineUpdate = viewModel::checkEngineUpdate,
                onDeleteDownload = viewModel::deleteDownload,
                onOpenPlayer = { id -> navController.navigate("player/$id") }
            )
        }

        composable(
            route = "player/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            PlayerScreen(
                mediaId = entry.arguments?.getLong("id") ?: 0L,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    searchState: com.lightdownloader.app.SearchUiState,
    engineState: com.lightdownloader.app.domain.EngineState,
    engineUpdate: com.lightdownloader.app.domain.EngineUpdateState,
    downloadState: com.lightdownloader.app.domain.DownloadState?,
    library: List<com.lightdownloader.app.data.LibraryItem>,
    snackbarHostState: SnackbarHostState,
    onUrlChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFileNameChange: (String) -> Unit,
    onCategoryChange: (com.lightdownloader.app.domain.MediaCategory) -> Unit,
    onVideoHeightChange: (Int) -> Unit,
    onAudioFormatChange: (com.lightdownloader.app.domain.AudioFormat) -> Unit,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownload: () -> Unit,
    onRetryEngine: () -> Unit,
    onUpdateEngine: () -> Unit,
    onCheckEngineUpdate: (Boolean) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val navColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                indicatorColor = Color.Transparent,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                NavigationBarItem(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    colors = navColors,
                    icon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    colors = navColors,
                    icon = {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                    },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    colors = navColors,
                    icon = {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                    },
                    label = { Text("Settings") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tabIndex) {
                0 -> SearchScreen(
                    state = searchState,
                    engineState = engineState,
                    downloadState = downloadState,
                    onUrlChange = onUrlChange,
                    onSearch = onSearch,
                    onFileNameChange = onFileNameChange,
                    onCategoryChange = onCategoryChange,
                    onVideoHeightChange = onVideoHeightChange,
                    onAudioFormatChange = onAudioFormatChange,
                    onPickFolder = onPickFolder,
                    onResetFolder = onResetFolder,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDismissDownload = onDismissDownload,
                    onRetryEngine = onRetryEngine,
                    onOpenPlayer = onOpenPlayer
                )

                1 -> LibraryScreen(
                    items = library,
                    onOpen = onOpenPlayer,
                    onDelete = onDeleteDownload,
                    onGoSearch = { tabIndex = 0 }
                )

                else -> SettingsScreen(
                    engineState = engineState,
                    updateState = engineUpdate,
                    target = searchState.target,
                    onCheckUpdate = onCheckEngineUpdate,
                    onUpdateEngine = onUpdateEngine,
                    onRetryEngine = onRetryEngine,
                    onPickFolder = onPickFolder,
                    onResetFolder = onResetFolder
                )
            }
        }
    }
}
