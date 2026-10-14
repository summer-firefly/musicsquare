package com.musicsquare.app.data.repo

import com.musicsquare.app.data.model.RecommendCatalog
import com.musicsquare.app.data.model.RecommendPlaylist
import com.musicsquare.app.data.source.NeteaseMusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Home recommendations backed by Netease chart playlists via Meting.
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
    )

    suspend fun load(hotSongLimit: Int = 12, playlistPreviewLimit: Int = 20): RecommendCatalog =
        coroutineScope {
            val hotDeferred = async {
                runCatching { netease.fetchPlaylist(HOT_SONG_PLAYLIST_ID, hotSongLimit) }
                    .getOrElse { emptyList() }
            }
            val playlistDeferred = charts.map { chart ->
                async {
                    val tracks = runCatching {
                        netease.fetchPlaylist(chart.id, playlistPreviewLimit)
                    }.getOrElse { emptyList() }
                    RecommendPlaylist(
                        id = chart.id,
                        name = chart.name,
                        subtitle = chart.subtitle,
                        cover = tracks.firstOrNull()?.cover,
                        trackCount = tracks.size,
                        tracks = tracks,
                    )
                }
            }
            RecommendCatalog(
                hotTracks = hotDeferred.await(),
                hotPlaylists = playlistDeferred.awaitAll().filter { it.tracks.isNotEmpty() },
            )
        }

    suspend fun loadPlaylistTracks(playlistId: String, limit: Int = 40) =
        runCatching { netease.fetchPlaylist(playlistId, limit) }.getOrElse { emptyList() }

    companion object {
        const val HOT_SONG_PLAYLIST_ID = "3778678"
    }
}
