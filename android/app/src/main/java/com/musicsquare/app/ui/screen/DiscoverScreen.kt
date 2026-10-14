package com.musicsquare.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.data.model.RecommendPlaylist
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.player.PlayerUiState
import com.musicsquare.app.ui.PlaybackExtras
import com.musicsquare.app.ui.RecommendUiState
import com.musicsquare.app.ui.SearchUiState
import com.musicsquare.app.ui.components.RecommendSection
import com.musicsquare.app.ui.components.TrackRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    searchState: SearchUiState,
    recommendState: RecommendUiState,
    playerState: PlayerUiState,
    extras: PlaybackExtras,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSource: (MusicSourceId) -> Unit,
    onToggleSourcesExpanded: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onHistoryClick: (String) -> Unit,
    onRefreshRecommend: () -> Unit,
    onPlayRecommendTrack: (Track) -> Unit,
    onPlayRecommendPlaylist: (RecommendPlaylist) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Blue Music",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "发现好音乐 · 多源在线聚合",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchState.keyword,
                            onValueChange = onKeywordChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("输入歌名 / 歌手") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                        Button(onClick = onSearch, enabled = !searchState.isSearching) {
                            Text("搜索")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleSourcesExpanded),
                    ) {
                        Text(
                            text = "音源筛选",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (searchState.sourcesExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = null,
                        )
                    }

                    if (searchState.sourcesExpanded) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MusicSourceId.entries.forEach { source ->
                                val selected = searchState.enabledSources[source] == true
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleSource(source) },
                                    label = { Text(source.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }

                    if (searchState.searchHistory.isNotEmpty() && searchState.results.isEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            searchState.searchHistory.take(6).forEach { item ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onHistoryClick(item) },
                                    label = { Text(item) },
                                )
                            }
                        }
                    }

                    Text(
                        text = searchState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (searchState.isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (searchState.results.isNotEmpty()) {
                item {
                    Text("搜索结果", style = MaterialTheme.typography.titleMedium)
                }
                items(searchState.results, key = { it.uid }) { track ->
                    TrackRow(
                        track = track,
                        selected = playerState.currentMediaId == track.uid,
                        favored = track.uid in extras.favoriteUids,
                        onClick = { onPlayTrack(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                    )
                }
                if (searchState.canLoadMore) {
                    item {
                        OutlinedButton(
                            onClick = onLoadMore,
                            enabled = !searchState.isLoadingMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            if (searchState.isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("加载更多")
                            }
                        }
                    }
                }
            } else {
                item {
                    RecommendSection(
                        state = recommendState,
                        currentMediaId = playerState.currentMediaId,
                        favoriteUids = extras.favoriteUids,
                        onRefresh = onRefreshRecommend,
                        onPlayTrack = onPlayRecommendTrack,
                        onToggleFavorite = onToggleFavorite,
                        onPlayPlaylist = onPlayRecommendPlaylist,
                    )
                }
            }
        }

        if (searchState.isPreparingPlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("正在加载音频…", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
