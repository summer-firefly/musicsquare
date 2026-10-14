package com.musicsquare.app.data.local

import android.content.Context
import com.musicsquare.app.data.model.LibrarySnapshot
import com.musicsquare.app.data.model.StoredPlaylist
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.model.toStored
import com.musicsquare.app.data.model.toTrack
import com.musicsquare.app.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

class LibraryRepository(context: Context) {
    private val file = File(context.filesDir, "pikachu-music-library-v1.json")
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(LibrarySnapshot())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) {
                _snapshot.value = LibrarySnapshot()
                return@withContext
            }
            val text = file.readText()
            _snapshot.value = runCatching {
                AppJson.decodeFromString(LibrarySnapshot.serializer(), text)
            }.getOrElse { LibrarySnapshot() }
        }
    }

    fun favorites(): List<Track> = _snapshot.value.favorites.map { it.toTrack() }

    fun playlists(): List<StoredPlaylist> = _snapshot.value.playlists

    fun isFavorite(uid: String): Boolean =
        _snapshot.value.favorites.any { it.uid == uid }

    suspend fun toggleFavorite(track: Track): Boolean {
        return mutex.withLock {
            val current = _snapshot.value
            val exists = current.favorites.any { it.uid == track.uid }
            val nextFavorites = if (exists) {
                current.favorites.filterNot { it.uid == track.uid }
            } else {
                current.favorites + track.toStored()
            }
            persist(current.copy(favorites = nextFavorites))
            !exists
        }
    }

    suspend fun createPlaylist(name: String): StoredPlaylist {
        return mutex.withLock {
            val playlist = StoredPlaylist(
                id = "pl-${UUID.randomUUID()}",
                name = name.trim().ifBlank { "未命名歌单" },
            )
            val current = _snapshot.value
            persist(current.copy(playlists = current.playlists + playlist))
            playlist
        }
    }

    suspend fun deletePlaylist(id: String) {
        mutex.withLock {
            val current = _snapshot.value
            persist(current.copy(playlists = current.playlists.filterNot { it.id == id }))
        }
    }

    suspend fun renamePlaylist(id: String, name: String) {
        mutex.withLock {
            val current = _snapshot.value
            persist(
                current.copy(
                    playlists = current.playlists.map {
                        if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it
                    }
                )
            )
        }
    }

    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean {
        return mutex.withLock {
            val current = _snapshot.value
            var added = false
            val playlists = current.playlists.map { pl ->
                if (pl.id != playlistId) return@map pl
                if (pl.tracks.any { it.uid == track.uid }) return@map pl
                added = true
                pl.copy(tracks = pl.tracks + track.toStored())
            }
            if (added) persist(current.copy(playlists = playlists))
            added
        }
    }

    suspend fun removeFromPlaylist(playlistId: String, trackUid: String) {
        mutex.withLock {
            val current = _snapshot.value
            persist(
                current.copy(
                    playlists = current.playlists.map { pl ->
                        if (pl.id != playlistId) pl
                        else pl.copy(tracks = pl.tracks.filterNot { it.uid == trackUid })
                    }
                )
            )
        }
    }

    suspend fun exportJson(): String = mutex.withLock {
        AppJson.encodeToString(LibrarySnapshot.serializer(), _snapshot.value)
    }

    suspend fun importJson(raw: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val imported = runCatching {
                AppJson.decodeFromString(LibrarySnapshot.serializer(), raw)
            }.getOrNull() ?: return@withLock false
            val mergedFavorites = (_snapshot.value.favorites + imported.favorites)
                .distinctBy { it.uid }
            val existing = _snapshot.value.playlists.associateBy { it.id }.toMutableMap()
            imported.playlists.forEach { pl ->
                val old = existing[pl.id]
                if (old == null) {
                    existing[pl.id] = pl
                } else {
                    existing[pl.id] = old.copy(
                        name = pl.name.ifBlank { old.name },
                        tracks = (old.tracks + pl.tracks).distinctBy { it.uid },
                    )
                }
            }
            persist(
                LibrarySnapshot(
                    version = 1,
                    savedAt = Instant.now().toString(),
                    favorites = mergedFavorites,
                    playlists = existing.values.toList(),
                )
            )
            true
        }
    }

    private fun persist(snapshot: LibrarySnapshot) {
        val next = snapshot.copy(savedAt = Instant.now().toString(), version = 1)
        file.writeText(AppJson.encodeToString(LibrarySnapshot.serializer(), next))
        _snapshot.value = next
    }
}
