package com.musicsquare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicsquare.app.data.model.RecommendPlaylist
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.ui.RecommendUiState
import com.musicsquare.app.ui.i18n.LocalAppStrings

@Composable
fun RecommendSection(
    state: RecommendUiState,
    currentMediaId: String?,
    favoriteUids: Set<String>,
    downloadedUids: Set<String>,
    downloadingUids: Set<String>,
    onRefresh: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onPlayPlaylist: (RecommendPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.todayRecommend,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = s.todayRecommendHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = s.refreshRecommend)
                }
            }
        }

        when {
            state.isLoading && state.hotTracks.isEmpty() && state.hotPlaylists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.errorMessage != null && state.hotTracks.isEmpty() -> {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRefresh) { Text(s.reload) }
            }
            else -> {
                if (state.hotPlaylists.isNotEmpty()) {
                    Text(s.hotPlaylists, style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.hotPlaylists.take(3).forEach { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                playLabel = s.playPlaylist,
                                onClick = { onPlayPlaylist(playlist) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep a stable 3-column grid when a batch has fewer items.
                        repeat((3 - state.hotPlaylists.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (state.hotTracks.isNotEmpty()) {
                    Text(s.hotTracks, style = MaterialTheme.typography.titleMedium)
                    state.hotTracks.forEach { track ->
                        TrackRow(
                            track = track,
                            selected = currentMediaId == track.uid,
                            favored = track.uid in favoriteUids,
                            downloaded = track.uid in downloadedUids,
                            downloading = track.uid in downloadingUids,
                            onClick = { onPlayTrack(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onDownload = { onDownloadTrack(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: RecommendPlaylist,
    playLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            AsyncImage(
                model = playlist.cover,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                            ),
                            startY = 48f,
                        ),
                    ),
            )
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, end = 36.dp, bottom = 8.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = playLabel,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        val tag = playlist.subtitle.substringAfter("· ").ifBlank { playlist.subtitle }
        Text(
            text = if (playlist.trackCount > 0) "$tag · ${playlist.trackCount} 首" else tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
