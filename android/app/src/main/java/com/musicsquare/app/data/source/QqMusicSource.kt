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

class QqMusicSource(
    private val client: okhttp3.OkHttpClient = HttpClientProvider.client,
) : MusicSourceClient {

    override suspend fun search(keyword: String, limit: Int, page: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val url =
                "https://tang.api.s01s.cn/music_open_api.php?msg=${keyword.encode()}&type=json"
            val body = getBody(url) ?: return@withContext emptyList()
            val data = runCatching {
                AppJson.parseToJsonElement(body).asArrayOrNull()
            }.getOrNull() ?: return@withContext emptyList()

            data.take(limit).mapIndexedNotNull { idx, element ->
                val obj = element.asObjectOrNull() ?: return@mapIndexedNotNull null
                val mid = obj.strOrNull("song_mid") ?: return@mapIndexedNotNull null
                val indexInList = idx + 1
                Track(
                    uid = "qq-$mid",
                    source = MusicSourceId.QQ,
                    displayIndex = indexInList,
                    keyword = keyword,
                    qqSearchKey = keyword,
                    qqIndex = indexInList,
                    qqId = mid,
                    songId = mid,
                    songMid = mid,
                    title = obj.str("song_title"),
                    artist = obj.str("singer_name"),
                    pay = obj.strOrNull("pay"),
                )
            }
        }

    override suspend fun fetchDetails(track: Track): Track = withContext(Dispatchers.IO) {
        val msg = track.qqSearchKey.ifBlank { track.keyword }
            .ifBlank { "${track.title} ${track.artist}".trim() }
        val mid = track.qqId.ifBlank { track.songMid.ifBlank { track.songId } }
        if (mid.isBlank()) return@withContext track

        val url =
            "https://tang.api.s01s.cn/music_open_api.php?msg=${msg.encode()}&type=json&mid=${mid.encode()}"
        val body = getBody(url) ?: return@withContext track
        val d = runCatching { AppJson.parseToJsonElement(body).asObjectOrNull() }.getOrNull()
            ?: return@withContext track
        if (d.strOrNull("song_mid").isNullOrBlank()) return@withContext track

        val best = pickBestPlayUrl(d)
        var updated = track.copy(
            title = d.strOrNull("song_title", "song_name") ?: track.title,
            artist = d.strOrNull("singer_name") ?: track.artist,
            album = d.strOrNull("album_name", "album_title") ?: track.album,
            cover = d.strOrNull("album_pic", "singer_pic") ?: track.cover,
            audioUrl = best.url ?: track.audioUrl,
            lrc = d.strOrNull("song_lyric", "lyric") ?: track.lrc,
            quality = best.tag,
            qualityLabel = best.label,
            detailsLoaded = true,
        )

        if (!updated.audioUrl.isNullOrBlank()) {
            val q = QualityUtils.inferQualityFromUrl(updated.audioUrl)
            if (q.label.isNotBlank()) {
                updated = updated.copy(quality = q.tag, qualityLabel = q.label)
            }
        }
        updated
    }

    private data class BestUrl(
        val url: String?,
        val tag: String?,
        val label: String?,
    )

    private fun pickBestPlayUrl(d: kotlinx.serialization.json.JsonObject): BestUrl {
        fun pick(key: String, tag: String, label: String): BestUrl? {
            val url = d.strOrNull(key) ?: return null
            return BestUrl(url, tag, label)
        }
        return pick("song_play_url_sq", "lossless", "LOSSLESS")
            ?: pick("song_play_url_pq", "lossless", "LOSSLESS")
            ?: pick("song_play_url_accom", "hq", "HQ")
            ?: pick("song_play_url_hq", "hq", "HQ")
            ?: pick("song_play_url_standard", "standard", "STD")
            ?: pick("song_play_url_fq", "low", "LOW")
            ?: BestUrl(d.strOrNull("song_play_url"), null, null)
    }

    private fun getBody(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun String.encode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
