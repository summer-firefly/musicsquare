package com.musicsquare.app.data.local

import android.content.Context
import android.os.Environment
import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.network.HttpClientProvider
import com.musicsquare.app.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
data class DownloadedTrack(
    val uid: String,
    val source: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val cover: String? = null,
    val songId: String = "",
    val localPath: String,
    val downloadedAt: String = "",
    val qualityLabel: String? = null,
)

@Serializable
private data class DownloadIndex(
    val version: Int = 1,
    val items: List<DownloadedTrack> = emptyList(),
)

class DownloadRepository(
    context: Context,
    private val client: OkHttpClient = downloadClient,
) {
    private val appContext = context.applicationContext
    private val musicDir: File =
        File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir,
            "BlueMusic",
        ).also { it.mkdirs() }
    private val indexFile = File(appContext.filesDir, "blue-music-downloads-v1.json")
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val items: StateFlow<List<DownloadedTrack>> = _items.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!indexFile.exists()) {
                _items.value = emptyList()
                return@withContext
            }
            val index = runCatching {
                AppJson.decodeFromString(DownloadIndex.serializer(), indexFile.readText())
            }.getOrElse { DownloadIndex() }
            val existing = index.items.filter { File(it.localPath).exists() }
            if (existing.size != index.items.size) {
                persistLocked(existing)
            } else {
                _items.value = existing
            }
        }
    }

    fun isDownloaded(uid: String): Boolean = _items.value.any { it.uid == uid }

    fun localPath(uid: String): String? =
        _items.value.firstOrNull { it.uid == uid }?.localPath?.takeIf { File(it).exists() }

    fun asTracks(): List<Track> = _items.value.map { it.toTrack() }

    fun downloadedUids(): Set<String> = _items.value.map { it.uid }.toSet()

    fun count(): Int = _items.value.size

    suspend fun download(track: Track, audioUrl: String): Result<DownloadedTrack> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _items.value.firstOrNull { it.uid == track.uid && File(it.localPath).exists() }
                    ?.let { return@withContext Result.success(it) }
                if (audioUrl.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("empty audio url"))
                }

                val request = Request.Builder().url(audioUrl).get().build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("http ${response.code}")
                        }
                        val body = response.body ?: error("empty body")
                        val ext = guessExtension(
                            contentType = response.header("Content-Type"),
                            url = audioUrl,
                        )
                        val target = File(musicDir, "${sanitize(track.uid)}.$ext")
                        body.byteStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (target.length() <= 0L) {
                            target.delete()
                            error("empty file")
                        }
                        val item = DownloadedTrack(
                            uid = track.uid,
                            source = track.source.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            cover = track.cover,
                            songId = track.songId,
                            localPath = target.absolutePath,
                            downloadedAt = Instant.now().toString(),
                            qualityLabel = track.qualityLabel,
                        )
                        persistLocked(_items.value.filterNot { it.uid == track.uid } + item)
                        item
                    }
                }
            }
        }

    suspend fun delete(uid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = _items.value.firstOrNull { it.uid == uid }
            if (target != null) {
                File(target.localPath).delete()
            }
            persistLocked(_items.value.filterNot { it.uid == uid })
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _items.value.forEach { File(it.localPath).delete() }
            musicDir.listFiles()?.forEach { it.delete() }
            persistLocked(emptyList())
        }
    }

    private fun persistLocked(items: List<DownloadedTrack>) {
        val index = DownloadIndex(version = 1, items = items)
        indexFile.writeText(AppJson.encodeToString(DownloadIndex.serializer(), index))
        _items.value = items
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "track" }

    private fun guessExtension(contentType: String?, url: String): String {
        val type = contentType.orEmpty().lowercase()
        return when {
            type.contains("mpeg") || type.contains("mp3") -> "mp3"
            type.contains("mp4") || type.contains("aac") || type.contains("m4a") -> "m4a"
            type.contains("flac") -> "flac"
            type.contains("ogg") || type.contains("opus") -> "ogg"
            url.contains(".m4a", ignoreCase = true) -> "m4a"
            url.contains(".flac", ignoreCase = true) -> "flac"
            else -> "mp3"
        }
    }

    companion object {
        private val downloadClient: OkHttpClient by lazy {
            HttpClientProvider.client.newBuilder()
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }
    }
}

fun DownloadedTrack.toTrack(): Track = Track(
    uid = uid,
    source = MusicSourceId.fromId(source),
    songId = songId,
    title = title,
    artist = artist,
    album = album,
    cover = cover,
    audioUrl = localPath,
    detailsLoaded = true,
    qualityLabel = qualityLabel,
)
