package com.musicsquare.app.data.model

data class RecommendPlaylist(
    val id: String,
    val name: String,
    val subtitle: String,
    val cover: String? = null,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList(),
)

data class RecommendCatalog(
    val hotTracks: List<Track> = emptyList(),
    val hotPlaylists: List<RecommendPlaylist> = emptyList(),
)
