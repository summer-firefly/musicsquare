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
import com.musicsquare.app.ui.LibraryUiState
import com.musicsquare.app.ui.PlaybackExtras
import com.musicsquare.app.ui.components.TrackRow

@Composable
fun LibraryScreen(
    libraryState: LibraryUiState,
    playerState: PlayerUiState,
    extras: PlaybackExtras,
    onSelectPlaylist: (String?) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayFavorites: () -> Unit,
    onPlayPlaylist: (StoredPlaylist) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    onAddCurrentToPlaylist: (String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val selected = libraryState.playlists.firstOrNull { it.id == libraryState.selectedPlaylistId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                IconButton(onClick = { onSelectPlaylist(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selected?.name ?: "我的",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (selected == null) "收藏与自建歌单" else "${selected.tracks.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected == null) {
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建歌单")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selected == null) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    LibraryCard(
                        title = "我的收藏",
                        subtitle = "${libraryState.favorites.size} 首",
                        onClick = { /* expand favorites inline below */ },
                        trailing = {
                            IconButton(onClick = onPlayFavorites, enabled = libraryState.favorites.isNotEmpty()) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "播放收藏")
                            }
                        },
                    )
                }
                items(libraryState.favorites, key = { it.uid }) { track ->
                    TrackRow(
                        track = track,
                        selected = playerState.currentMediaId == track.uid,
                        favored = true,
                        onClick = { onPlayTrack(track, libraryState.favorites) },
                        onToggleFavorite = { onToggleFavorite(track) },
                    )
                }
                item {
                    Text(
                        text = "自建歌单",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(libraryState.playlists, key = { it.id }) { playlist ->
                    LibraryCard(
                        title = playlist.name,
                        subtitle = "${playlist.tracks.size} 首",
                        onClick = { onSelectPlaylist(playlist.id) },
                        trailing = {
                            Row {
                                IconButton(onClick = { onPlayPlaylist(playlist) }, enabled = playlist.tracks.isNotEmpty()) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "播放歌单")
                                }
                                IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除歌单")
                                }
                            }
                        },
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlayPlaylist(selected) }, enabled = selected.tracks.isNotEmpty()) {
                            Text("播放全部")
                        }
                        if (extras.currentTrack != null) {
                            Button(onClick = { onAddCurrentToPlaylist(selected.id) }) {
                                Text("加入当前歌曲")
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
                        onClick = {
                            onPlayTrack(track, selected.tracks.map { it.toTrack() })
                        },
                        onToggleFavorite = { onToggleFavorite(track) },
                    )
                    TextButton(onClick = { onRemoveFromPlaylist(selected.id, track.uid) }) {
                        Text("从歌单移除")
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("例如：通勤歌单") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePlaylist(newName)
                        newName = ""
                        showCreateDialog = false
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
