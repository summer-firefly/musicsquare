package com.musicsquare.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LibrarySnapshot(
    val version: Int = 1,
    val savedAt: String = "",
    val favorites: List<StoredTrack> = emptyList(),
    val playlists: List<StoredPlaylist> = emptyList(),
)

@Serializable
data class StoredPlaylist(
    val id: String,
    val name: String,
    val tracks: List<StoredTrack> = emptyList(),
)

@Serializable
data class StoredTrack(
    val uid: String,
    val source: String,
    val displayIndex: Int = 1,
    val keyword: String = "",
    val songid: String = "",
    val songMid: String = "",
    val qqId: String = "",
    val qqSearchKey: String = "",
    val qqIndex: Int = 0,
    val jooxIndex: Int = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val cover: String? = null,
    val quality: String? = null,
    val qualityLabel: String? = null,
    val pay: String? = null,
)

fun Track.toStored(): StoredTrack = StoredTrack(
    uid = uid,
    source = source.id,
    displayIndex = displayIndex,
    keyword = keyword,
    songid = songId,
    songMid = songMid,
    qqId = qqId,
    qqSearchKey = qqSearchKey,
    qqIndex = qqIndex,
    jooxIndex = jooxIndex,
    title = title,
    artist = artist,
    album = album,
    cover = cover,
    quality = quality,
    qualityLabel = qualityLabel,
    pay = pay,
)

fun StoredTrack.toTrack(): Track = Track(
    uid = uid,
    source = MusicSourceId.fromId(source),
    displayIndex = displayIndex,
    keyword = keyword,
    songId = songid,
    songMid = songMid,
    qqId = qqId,
    qqSearchKey = qqSearchKey,
    qqIndex = qqIndex,
    jooxIndex = jooxIndex,
    title = title,
    artist = artist,
    album = album,
    cover = cover,
    quality = quality,
    qualityLabel = qualityLabel,
    pay = pay,
    detailsLoaded = false,
    audioUrl = null,
    lrc = null,
    lrcUrl = null,
)
