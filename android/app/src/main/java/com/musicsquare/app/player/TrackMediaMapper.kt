package com.musicsquare.app.player

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicsquare.app.data.model.Track

object TrackMediaMapper {
    const val EXTRA_SOURCE = "source"
    const val EXTRA_ARTIST = "artist"

    fun toMediaItem(track: Track): MediaItem {
        val audio = track.audioUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Track has no audioUrl: ${track.uid}")

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title.ifBlank { "Unknown" })
            .setArtist(track.artist.ifBlank { track.source.label })
            .setAlbumTitle(track.album.takeIf { it.isNotBlank() })
            .setArtworkUri(track.cover?.toUri())
            .setExtras(
                android.os.Bundle().apply {
                    putString(EXTRA_SOURCE, track.source.id)
                    putString(EXTRA_ARTIST, track.artist)
                }
            )
            .build()

        return MediaItem.Builder()
            .setMediaId(track.uid)
            .setUri(Uri.parse(audio))
            .setMediaMetadata(metadata)
            .build()
    }
}
