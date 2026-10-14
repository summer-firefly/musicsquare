package com.musicsquare.app.data.repo

import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.source.JooxMusicSource
import com.musicsquare.app.data.source.KuwoMusicSource
import com.musicsquare.app.data.source.MusicSourceClient
import com.musicsquare.app.data.source.NeteaseMusicSource
import com.musicsquare.app.data.source.QqMusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MusicRepository(
    private val clients: Map<MusicSourceId, MusicSourceClient> = mapOf(
        MusicSourceId.NETEASE to NeteaseMusicSource(),
        MusicSourceId.QQ to QqMusicSource(),
        MusicSourceId.KUWO to KuwoMusicSource(),
        MusicSourceId.JOOX to JooxMusicSource(),
    ),
) {
    private val mutex = Mutex()
    private val trackCache = linkedMapOf<String, Track>()

    suspend fun search(
        keyword: String,
        enabledSources: Set<MusicSourceId>,
        limitPerSource: Int = 10,
        page: Int = 1,
        reset: Boolean = true,
    ): List<Track> = coroutineScope {
        val trimmed = keyword.trim()
        require(trimmed.isNotEmpty()) { "keyword empty" }
        require(enabledSources.isNotEmpty()) { "no source" }

        if (reset) {
            mutex.withLock { trackCache.clear() }
        }

        val nested = enabledSources.map { source ->
            async {
                runCatching {
                    clients.getValue(source).search(trimmed, limitPerSource, page)
                }.getOrElse { emptyList() }
            }
        }.awaitAll()

        mutex.withLock {
            nested.flatten().forEach { track ->
                if (!trackCache.containsKey(track.uid)) {
                    trackCache[track.uid] = track
                }
            }
        }

        val grouped = MusicSourceId.entries.associateWith { mutableListOf<Track>() }.toMutableMap()
        mutex.withLock {
            trackCache.values.forEach { track ->
                grouped.getOrPut(track.source) { mutableListOf() }.add(track)
            }
        }
        grouped.values.forEach { list -> list.sortBy { it.displayIndex } }
        interleave(grouped)
    }

    suspend fun ensurePlayable(track: Track): Track {
        mutex.withLock {
            val cached = trackCache[track.uid]
            if (cached != null && cached.detailsLoaded && !cached.audioUrl.isNullOrBlank()) {
                return cached
            }
        }

        val client = clients[track.source] ?: return track
        val detailed = runCatching { client.fetchDetails(track) }.getOrElse { track }
        mutex.withLock {
            trackCache[detailed.uid] = detailed
        }
        return detailed
    }

    fun cachedTracks(): List<Track> = trackCache.values.toList()

    private fun interleave(grouped: Map<MusicSourceId, List<Track>>): List<Track> {
        val order = listOf(
            MusicSourceId.NETEASE,
            MusicSourceId.QQ,
            MusicSourceId.KUWO,
            MusicSourceId.JOOX,
        )
        val indices = order.associateWith { 0 }.toMutableMap()
        val out = mutableListOf<Track>()
        var added = true
        while (added) {
            added = false
            for (source in order) {
                val list = grouped[source].orEmpty()
                val i = indices.getValue(source)
                if (i < list.size) {
                    out += list[i]
                    indices[source] = i + 1
                    added = true
                }
            }
        }
        return out
    }
}
