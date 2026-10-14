package com.musicsquare.app.data.repo

import com.musicsquare.app.data.model.RecommendCatalog
import com.musicsquare.app.data.model.RecommendPlaylist
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.source.NeteaseMusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Home recommendations backed by Netease chart playlists via Meting.
 * [loadPool] fetches a larger catalog so the UI can rotate batches on refresh.
 */
class RecommendRepository(
    private val netease: NeteaseMusicSource = NeteaseMusicSource(),
) {
    data class ChartPlaylist(
        val id: String,
        val name: String,
        val subtitle: String,
    )

    private val charts = listOf(
        ChartPlaylist("3778678", "热歌榜", "网易云 · 近期热门"),
        ChartPlaylist("3779629", "新歌榜", "网易云 · 最新单曲"),
        ChartPlaylist("19723756", "飙升榜", "网易云 · 快速上升"),
        ChartPlaylist("2884035", "原创榜", "网易云 · 原创精选"),
        ChartPlaylist("991319590", "说唱榜", "网易云 · 说唱精选"),
        ChartPlaylist("71384707", "古典榜", "网易云 · 古典精选"),
        ChartPlaylist("1978921795", "听歌识曲榜", "网易云 · 热搜热听"),
        ChartPlaylist("5059642708", "网络热歌榜", "网易云 · 网络热歌"),
    )

    suspend fun loadPool(poolLimitPerChart: Int = 50): RecommendCatalog =
        coroutineScope {
            val chartResults = charts.map { chart ->
                async {
                    val tracks = runCatching {
                        netease.fetchPlaylist(chart.id, poolLimitPerChart)
                    }.getOrElse { emptyList() }
                    chart to tracks
                }
            }.awaitAll()

            val playlists = chartResults.mapNotNull { (chart, tracks) ->
                if (tracks.isEmpty()) return@mapNotNull null
                RecommendPlaylist(
                    id = chart.id,
                    name = chart.name,
                    subtitle = chart.subtitle,
                    cover = tracks.firstOrNull()?.cover,
                    trackCount = tracks.size,
                    tracks = tracks,
                )
            }

            val hotTracks = LinkedHashMap<String, Track>()
            // Prefer 热歌榜 first, then weave the rest for a mixed pool.
            chartResults.forEach { (_, tracks) ->
                tracks.forEach { track -> hotTracks.putIfAbsent(track.uid, track) }
            }

            RecommendCatalog(
                hotTracks = hotTracks.values.toList(),
                hotPlaylists = playlists,
            )
        }

    suspend fun loadPlaylistTracks(playlistId: String, limit: Int = 40) =
        runCatching { netease.fetchPlaylist(playlistId, limit) }.getOrElse { emptyList() }

    companion object {
        const val HOT_SONG_PLAYLIST_ID = "3778678"
    }
}
