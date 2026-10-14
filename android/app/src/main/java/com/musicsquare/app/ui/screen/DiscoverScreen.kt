package com.musicsquare.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.data.model.RecommendPlaylist
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.player.PlayerUiState
import com.musicsquare.app.ui.DownloadUiState
import com.musicsquare.app.ui.PlaybackExtras
import com.musicsquare.app.ui.RecommendUiState
import com.musicsquare.app.ui.SearchUiState
import com.musicsquare.app.ui.components.RecommendSection
import com.musicsquare.app.ui.components.TrackRow
import com.musicsquare.app.ui.i18n.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    searchState: SearchUiState,
    recommendState: RecommendUiState,
    playerState: PlayerUiState,
    extras: PlaybackExtras,
    downloadState: DownloadUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSource: (MusicSourceId) -> Unit,
    onToggleSourcesExpanded: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onHistoryClick: (String) -> Unit,
    onRefreshRecommend: () -> Unit,
    onPlayRecommendTrack: (Track) -> Unit,
    onPlayRecommendPlaylist: (RecommendPlaylist) -> Unit,
) {
    val s = LocalAppStrings.current
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DiscoverHeader(
                    keyword = searchState.keyword,
                    isSearching = searchState.isSearching,
                    sourcesExpanded = searchState.sourcesExpanded,
                    enabledSources = searchState.enabledSources,
                    searchHistory = searchState.searchHistory,
                    showHistory = searchState.results.isEmpty() && !searchState.isSearching,
                    statusMessage = searchState.statusMessage,
                    onKeywordChange = onKeywordChange,
                    onSearch = onSearch,
                    onToggleSource = onToggleSource,
                    onToggleSourcesExpanded = onToggleSourcesExpanded,
                    onHistoryClick = onHistoryClick,
                )
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
                    Text(s.searchResults, style = MaterialTheme.typography.titleMedium)
                }
                items(searchState.results, key = { it.uid }) { track ->
                    TrackRow(
                        track = track,
                        selected = playerState.currentMediaId == track.uid,
                        favored = track.uid in extras.favoriteUids,
                        downloaded = track.uid in downloadState.downloadedUids,
                        downloading = track.uid in downloadState.downloadingUids,
                        onClick = { onPlayTrack(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onDownload = { onDownloadTrack(track) },
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
                                Text(s.loadMore)
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
                        downloadedUids = downloadState.downloadedUids,
                        downloadingUids = downloadState.downloadingUids,
                        onRefresh = onRefreshRecommend,
                        onPlayTrack = onPlayRecommendTrack,
                        onToggleFavorite = onToggleFavorite,
                        onDownloadTrack = onDownloadTrack,
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
                    Text(s.loadingAudio, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverHeader(
    keyword: String,
    isSearching: Boolean,
    sourcesExpanded: Boolean,
    enabledSources: Map<MusicSourceId, Boolean>,
    searchHistory: List<String>,
    showHistory: Boolean,
    statusMessage: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleSource: (MusicSourceId) -> Unit,
    onToggleSourcesExpanded: () -> Unit,
    onHistoryClick: (String) -> Unit,
) {
    val s = LocalAppStrings.current
    val activeSourceCount = enabledSources.count { it.value }
    val interaction = remember { MutableInteractionSource() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Blue Music",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = s.brandTagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onToggleSourcesExpanded,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = "${s.searchSources} $activeSourceCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (sourcesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        SearchBar(
            value = keyword,
            onValueChange = onKeywordChange,
            onSearch = onSearch,
            enabled = !isSearching,
            interactionSource = interaction,
        )

        if (sourcesExpanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MusicSourceId.entries.forEach { source ->
                    val selected = enabledSources[source] == true
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleSource(source) },
                        label = { Text(s.sourceLabel(source)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }

        if (showHistory && searchHistory.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = s.recentSearches,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    searchHistory.take(6).forEach { item ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                                .clickable { onHistoryClick(item) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        if (statusMessage.isNotBlank() && (isSearching || keyword.isNotBlank())) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
) {
    val shape = RoundedCornerShape(28.dp)
    val borderBrush = Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = LocalAppStrings.current.searchPlaceholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                enabled = enabled,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    },
                )
                .clickable(enabled = enabled, onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            if (!enabled) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
