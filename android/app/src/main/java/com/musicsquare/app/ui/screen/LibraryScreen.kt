package com.musicsquare.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.musicsquare.app.data.model.StoredPlaylist
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.model.toTrack
import com.musicsquare.app.player.PlayerUiState
import com.musicsquare.app.ui.DownloadUiState
import com.musicsquare.app.ui.LibraryUiState
import com.musicsquare.app.ui.PlaybackExtras
import com.musicsquare.app.ui.components.TrackRow
import com.musicsquare.app.ui.i18n.LocalAppStrings

@Composable
fun LibraryScreen(
    libraryState: LibraryUiState,
    playerState: PlayerUiState,
    extras: PlaybackExtras,
    downloadState: DownloadUiState,
    onSelectPlaylist: (String?) -> Unit,
    onShowDownloads: (Boolean) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayFavorites: () -> Unit,
    onPlayDownloads: () -> Unit,
    onPlayPlaylist: (StoredPlaylist) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    onAddCurrentToPlaylist: (String) -> Unit,
) {
    val s = LocalAppStrings.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val selected = libraryState.playlists.firstOrNull { it.id == libraryState.selectedPlaylistId }
    val showingDownloads = libraryState.showDownloads

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null || showingDownloads) {
                IconButton(
                    onClick = {
                        if (showingDownloads) onShowDownloads(false) else onSelectPlaylist(null)
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        showingDownloads -> s.downloads
                        selected != null -> selected.name
                        else -> s.myLibrary
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = when {
                        showingDownloads -> s.tracksCount(libraryState.downloads.size)
                        selected != null -> s.tracksCount(selected.tracks.size)
                        else -> s.myLibraryHint
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected == null && !showingDownloads) {
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = s.createPlaylist)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            showingDownloads -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Button(
                            onClick = onPlayDownloads,
                            enabled = libraryState.downloads.isNotEmpty(),
                        ) {
                            Text(s.playAll)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(libraryState.downloads, key = { it.uid }) { track ->
                        TrackRow(
                            track = track,
                            selected = playerState.currentMediaId == track.uid,
                            favored = track.uid in extras.favoriteUids,
                            downloaded = true,
                            downloading = false,
                            onClick = { onPlayTrack(track, libraryState.downloads) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onDownload = { onDeleteDownload(track.uid) },
                            downloadActionIsDelete = true,
                        )
                    }
                }
            }
            selected == null -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        LibraryCard(
                            title = s.myFavorites,
                            subtitle = s.tracksCount(libraryState.favorites.size),
                            onClick = { },
                            trailing = {
                                IconButton(
                                    onClick = onPlayFavorites,
                                    enabled = libraryState.favorites.isNotEmpty(),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = s.playFavorites)
                                }
                            },
                        )
                    }
                    items(libraryState.favorites, key = { it.uid }) { track ->
                        TrackRow(
                            track = track,
                            selected = playerState.currentMediaId == track.uid,
                            favored = true,
                            downloaded = track.uid in downloadState.downloadedUids,
                            downloading = track.uid in downloadState.downloadingUids,
                            onClick = { onPlayTrack(track, libraryState.favorites) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onDownload = { onDownloadTrack(track) },
                        )
                    }
                    item {
                        LibraryCard(
                            title = s.downloads,
                            subtitle = s.tracksCount(libraryState.downloads.size),
                            onClick = { onShowDownloads(true) },
                            trailing = {
                                Row {
                                    IconButton(
                                        onClick = onPlayDownloads,
                                        enabled = libraryState.downloads.isNotEmpty(),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = s.playDownloads)
                                    }
                                    IconButton(onClick = { onShowDownloads(true) }) {
                                        Icon(Icons.Default.Download, contentDescription = s.downloads)
                                    }
                                }
                            },
                        )
                    }
                    item {
                        Text(
                            text = s.customPlaylists,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(libraryState.playlists, key = { it.id }) { playlist ->
                        LibraryCard(
                            title = playlist.name,
                            subtitle = s.tracksCount(playlist.tracks.size),
                            onClick = { onSelectPlaylist(playlist.id) },
                            trailing = {
                                Row {
                                    IconButton(
                                        onClick = { onPlayPlaylist(playlist) },
                                        enabled = playlist.tracks.isNotEmpty(),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = s.playPlaylist)
                                    }
                                    IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = s.deletePlaylist)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onPlayPlaylist(selected) },
                                enabled = selected.tracks.isNotEmpty(),
                            ) {
                                Text(s.playAll)
                            }
                            if (extras.currentTrack != null) {
                                Button(onClick = { onAddCurrentToPlaylist(selected.id) }) {
                                    Text(s.addCurrentSong)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(selected.tracks, key = { it.uid }) { stored ->
                        val track = stored.toTrack()
                        TrackRow(
                            track = track,
                            selected = playerState.currentMediaId == track.uid,
                            favored = track.uid in extras.favoriteUids,
                            downloaded = track.uid in downloadState.downloadedUids,
                            downloading = track.uid in downloadState.downloadingUids,
                            onClick = {
                                onPlayTrack(track, selected.tracks.map { it.toTrack() })
                            },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onDownload = { onDownloadTrack(track) },
                        )
                        TextButton(onClick = { onRemoveFromPlaylist(selected.id, track.uid) }) {
                            Text(s.removeFromPlaylist)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(s.createPlaylist) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text(s.playlistNameHint) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePlaylist(newName)
                        newName = ""
                        showCreateDialog = false
                    }
                ) { Text(s.create) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(s.cancel) }
            },
        )
    }
}

@Composable
private fun LibraryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}
