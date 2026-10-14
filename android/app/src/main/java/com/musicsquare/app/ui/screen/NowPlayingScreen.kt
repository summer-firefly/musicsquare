package com.musicsquare.app.ui.screen

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.player.PlayMode
import com.musicsquare.app.player.PlayerUiState
import com.musicsquare.app.ui.PlaybackExtras
import com.musicsquare.app.ui.components.LyricsView
import com.musicsquare.app.ui.components.TrackRow
import com.musicsquare.app.util.formatTimeMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerState: PlayerUiState,
    extras: PlaybackExtras,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onToggleCoverLyrics: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onCloseQueue: () -> Unit,
    onPlayFromQueue: (Track) -> Unit,
) {
    val duration = playerState.durationMs.toFloat().coerceAtLeast(0f)
    var scrubbing by remember { mutableFloatStateOf(Float.NaN) }
    val sliderValue = if (!scrubbing.isNaN()) scrubbing else {
        if (duration > 0f) (playerState.positionMs / duration).coerceIn(0f, 1f) else 0f
    }
    val favored = extras.currentTrack?.uid?.let { it in extras.favoriteUids } == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B2340), Color(0xFF02030A), Color(0xFF211A38))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
                }
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (favored) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (favored) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = onToggleQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放队列")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                    .clickable(onClick = onToggleCoverLyrics),
                contentAlignment = Alignment.Center,
            ) {
                if (extras.showCover) {
                    AsyncImage(
                        model = playerState.artworkUri ?: extras.currentTrack?.cover,
                        contentDescription = playerState.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.background),
                    )
                } else {
                    LyricsView(
                        lines = extras.lyricLines,
                        currentIndex = extras.currentLyricIndex,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = if (extras.showCover) "点击封面查看歌词" else "点击歌词返回封面",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = playerState.title.ifBlank { extras.currentTrack?.title.orEmpty() }.ifBlank { "未知歌曲" },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playerState.artist.ifBlank { extras.currentTrack?.artist.orEmpty() }.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Slider(
                value = sliderValue,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    if (!scrubbing.isNaN() && duration > 0f) {
                        onSeek((scrubbing * duration).toLong())
                    }
                    scrubbing = Float.NaN
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTimeMs(playerState.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatTimeMs(playerState.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCyclePlayMode) {
                    Icon(
                        imageVector = when (playerState.playMode) {
                            PlayMode.LIST -> Icons.Default.Repeat
                            PlayMode.SINGLE -> Icons.Default.RepeatOne
                            PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        },
                        contentDescription = playerState.playMode.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(40.dp))
                }
                Box(modifier = Modifier.size(48.dp))
            }
        }
    }

    if (extras.showQueue) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseQueue,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "当前队列（${extras.queue.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            extras.queue.forEach { track ->
                TrackRow(
                    track = track,
                    selected = track.uid == playerState.currentMediaId,
                    onClick = { onPlayFromQueue(track) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
