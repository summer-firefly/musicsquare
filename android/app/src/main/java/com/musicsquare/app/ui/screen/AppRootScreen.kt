package com.musicsquare.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.ui.MusicViewModel
import com.musicsquare.app.ui.components.PlayerBar

private object Routes {
    const val Discover = "discover"
    const val Library = "library"
    const val Settings = "settings"
}

@Composable
fun AppRootScreen(viewModel: MusicViewModel) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val extras by viewModel.playbackExtras.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Routes.Discover

    LaunchedEffect(searchState.errorMessage, searchState.toastMessage) {
        val msg = searchState.errorMessage ?: searchState.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
        viewModel.consumeToast()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B2340), Color(0xFF02030A), Color(0xFF211A38))
                )
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    PlayerBar(
                        state = playerState,
                        preparing = searchState.isPreparingPlay,
                        onOpenNowPlaying = viewModel::openNowPlaying,
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::skipNext,
                        modifier = Modifier,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == Routes.Discover,
                            onClick = {
                                navController.navigate(Routes.Discover) {
                                    popUpTo(Routes.Discover) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(Icons.Default.Explore, null) },
                            label = { Text("发现") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.Library,
                            onClick = {
                                navController.navigate(Routes.Library) {
                                    popUpTo(Routes.Discover) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(Icons.Default.LibraryMusic, null) },
                            label = { Text("我的") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.Settings,
                            onClick = {
                                navController.navigate(Routes.Settings) {
                                    popUpTo(Routes.Discover) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(Icons.Default.Settings, null) },
                            label = { Text("设置") },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.Discover,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(padding),
            ) {
                composable(Routes.Discover) {
                    DiscoverScreen(
                        searchState = searchState,
                        playerState = playerState,
                        extras = extras,
                        onKeywordChange = viewModel::updateKeyword,
                        onSearch = { viewModel.search(reset = true) },
                        onLoadMore = viewModel::loadMore,
                        onToggleSource = viewModel::toggleSource,
                        onToggleSourcesExpanded = viewModel::toggleSourcesExpanded,
                        onPlayTrack = { viewModel.playTrack(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onHistoryClick = viewModel::applyHistory,
                    )
                }
                composable(Routes.Library) {
                    LibraryScreen(
                        libraryState = libraryState,
                        playerState = playerState,
                        extras = extras,
                        onSelectPlaylist = viewModel::selectPlaylist,
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onCreatePlaylist = viewModel::createPlaylist,
                        onDeletePlaylist = viewModel::deletePlaylist,
                        onPlayFavorites = viewModel::playFavorites,
                        onPlayPlaylist = viewModel::playPlaylist,
                        onRemoveFromPlaylist = viewModel::removeFromPlaylist,
                        onAddCurrentToPlaylist = viewModel::addCurrentToPlaylist,
                    )
                }
                composable(Routes.Settings) {
                    SettingsScreen(
                        settings = settings,
                        onToggleSource = { source ->
                            val next = settings.enabledSources.toMutableSet()
                            if (!next.add(source)) next.remove(source)
                            if (next.isEmpty()) next.add(MusicSourceId.NETEASE)
                            viewModel.setEnabledSources(next)
                        },
                        onSetLimit = viewModel::setPerSourceLimit,
                        onClearHistory = viewModel::clearSearchHistory,
                        onClearCache = viewModel::clearAppCache,
                        onExport = { viewModel.exportLibraryJson() },
                        onImport = viewModel::importLibraryJson,
                    )
                }
            }
        }

        if (extras.showNowPlaying) {
            NowPlayingScreen(
                playerState = playerState,
                extras = extras,
                onClose = viewModel::closeNowPlaying,
                onPlayPause = viewModel::playPause,
                onPrev = viewModel::skipPrevious,
                onNext = viewModel::skipNext,
                onSeek = viewModel::seekTo,
                onCyclePlayMode = viewModel::cyclePlayMode,
                onToggleCoverLyrics = viewModel::toggleCoverLyrics,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onToggleQueue = viewModel::toggleQueueSheet,
                onCloseQueue = viewModel::closeQueueSheet,
                onPlayFromQueue = viewModel::playFromQueue,
            )
        }
    }
}
