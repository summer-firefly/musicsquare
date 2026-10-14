package com.musicsquare.app.data.source

import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.network.HttpClientProvider
import com.musicsquare.app.util.AppJson
import com.musicsquare.app.util.QualityUtils
import com.musicsquare.app.util.asArrayOrNull
import com.musicsquare.app.util.asObjectOrNull
import com.musicsquare.app.util.str
import com.musicsquare.app.util.strOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class KuwoMusicSource(
    private val client: okhttp3.OkHttpClient = HttpClientProvider.client,
) : MusicSourceClient {

    override suspend fun search(keyword: String, limit: Int, page: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val url =
                "https://oiapi.net/api/Kuwo?msg=${keyword.encode()}&page=1&limit=$limit"
            val body = getBody(url) ?: return@withContext emptyList()
            val root = runCatching { AppJson.parseToJsonElement(body).asObjectOrNull() }.getOrNull()
                ?: return@withContext emptyList()
            val data = root["data"].asArrayOrNull() ?: return@withContext emptyList()

            data.mapIndexedNotNull { idx, element ->
                val obj = element.asObjectOrNull() ?: return@mapIndexedNotNull null
                val rid = obj.strOrNull("rid") ?: return@mapIndexedNotNull null
                Track(
                    uid = "kuwo-$rid",
                    source = MusicSourceId.KUWO,
                    displayIndex = idx + 1,
                    keyword = keyword,
                    songId = rid,
                    title = obj.str("song"),
                    artist = obj.str("singer"),
                    album = obj.str("album"),
                    cover = obj.strOrNull("picture"),
                )
            }
        }

    override suspend fun fetchDetails(track: Track): Track = withContext(Dispatchers.IO) {
        val n = track.displayIndex.coerceAtLeast(1)
        val url =
            "https://oiapi.net/api/Kuwo?msg=${track.keyword.encode()}&n=$n&br=1"
        val body = getBody(url) ?: return@withContext track
        val root = runCatching { AppJson.parseToJsonElement(body).asObjectOrNull() }.getOrNull()
            ?: return@withContext track
        val d = root["data"].asObjectOrNull() ?: return@withContext track

        val audioUrl = d.strOrNull("url") ?: track.audioUrl
        val qualityText = "${d.str("level")} ${d.str("format")}".lowercase()
        val (quality, qualityLabel) = when {
            Regex("lossless|无损|flac|wav|ape|alac|aiff|zp|sq").containsMatchIn(qualityText) ->
                "lossless" to "LOSSLESS"
            else -> {
                val q = QualityUtils.inferQualityFromUrl(audioUrl)
                q.tag to q.label
            }
        }

        track.copy(
            title = d.strOrNull("song") ?: track.title,
            artist = d.strOrNull("singer") ?: track.artist,
            album = d.strOrNull("album") ?: track.album,
            cover = d.strOrNull("picture") ?: track.cover,
            songId = d.strOrNull("rid") ?: track.songId,
            audioUrl = audioUrl,
            lrc = d.strOrNull("lyric", "lrc", "lyrics") ?: track.lrc,
            lrcUrl = null,
            detailsLoaded = true,
            quality = quality,
            qualityLabel = qualityLabel,
        )
    }

    private fun getBody(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun String.encode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
