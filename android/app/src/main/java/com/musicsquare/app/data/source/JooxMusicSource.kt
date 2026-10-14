package com.musicsquare.app.data.source

import com.musicsquare.app.BuildConfig
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request

class JooxMusicSource(
    private val client: okhttp3.OkHttpClient = HttpClientProvider.client,
    private val token: String = BuildConfig.JOOX_TOKEN,
    private val br: Int = BuildConfig.JOOX_BR,
) : MusicSourceClient {

    override suspend fun search(keyword: String, limit: Int, page: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val url =
                "https://apicx.asia/api/joox_music?msg=${keyword.encode()}&token=${token.encode()}&br=$br"
            val body = getBody(url) ?: return@withContext emptyList()
            val root = runCatching { AppJson.parseToJsonElement(body).asObjectOrNull() }.getOrNull()
                ?: return@withContext emptyList()
            if (!isSuccessCode(root)) return@withContext emptyList()
            val songs = root["data"]?.asObjectOrNull()?.get("songs").asArrayOrNull()
                ?: return@withContext emptyList()

            songs.take(limit).mapIndexedNotNull { idx, element ->
                val obj = element.asObjectOrNull() ?: return@mapIndexedNotNull null
                val songMid = obj.str("songmid")
                val songId = obj.strOrNull("歌曲ID") ?: songMid.ifBlank { "${idx + 1}" }
                val uid = "joox-${songMid.ifBlank { songId }}"
                Track(
                    uid = uid,
                    source = MusicSourceId.JOOX,
                    displayIndex = idx + 1,
                    keyword = keyword,
                    jooxIndex = idx + 1,
                    songId = songId,
                    songMid = songMid,
                    title = obj.str("歌曲名称"),
                    artist = obj.str("歌手"),
                    album = obj.str("专辑"),
                    lrc = obj.strOrNull("歌词内容"),
                )
            }
        }

    override suspend fun fetchDetails(track: Track): Track = withContext(Dispatchers.IO) {
        val n = track.jooxIndex.takeIf { it > 0 } ?: track.displayIndex.coerceAtLeast(1)
        val url =
            "https://apicx.asia/api/joox_music?msg=${track.keyword.encode()}&n=$n&token=${token.encode()}&br=$br"
        val body = getBody(url) ?: return@withContext track
        val root = runCatching { AppJson.parseToJsonElement(body).asObjectOrNull() }.getOrNull()
            ?: return@withContext track
        if (!isSuccessCode(root)) return@withContext track
        val d = root["data"].asObjectOrNull() ?: return@withContext track
        val playLinks = d["播放链接"].asObjectOrNull()
        val best = pickJooxPlayUrl(playLinks)

        var updated = track.copy(
            title = d.strOrNull("歌曲名称") ?: track.title,
            artist = d.strOrNull("歌手") ?: track.artist,
            album = d.strOrNull("专辑") ?: track.album,
            songId = d.strOrNull("歌曲ID") ?: track.songId,
            songMid = d.strOrNull("songmid") ?: track.songMid,
            audioUrl = best.url ?: track.audioUrl,
            lrc = d.strOrNull("歌词内容") ?: track.lrc,
            detailsLoaded = true,
            quality = best.tag,
            qualityLabel = best.label,
        )

        if (best.tag.isNullOrBlank() && !updated.audioUrl.isNullOrBlank()) {
            val q = QualityUtils.inferQualityFromUrl(updated.audioUrl)
            updated = updated.copy(quality = q.tag, qualityLabel = q.label)
        }
        updated
    }

    private data class BestUrl(val url: String?, val tag: String?, val label: String?)

    private fun pickJooxPlayUrl(links: JsonObject?): BestUrl {
        if (links == null) return BestUrl(null, null, null)
        val order = listOf(
            "Atmos全景声", "无损FLAC", "Hi-Res无损", "母带无损",
            "OGG 320", "MP3 320", "AAC 192", "OGG 192", "MP3 128", "AAC 96", "AAC 48"
        )
        for (name in order) {
            val u = links.strOrNull(name) ?: continue
            val lossless = Regex("母带|无损|flac|hi-res|atmos", RegexOption.IGNORE_CASE)
                .containsMatchIn(name) || Regex("\\.flac(?:\\?|$)", RegexOption.IGNORE_CASE)
                .containsMatchIn(u)
            if (lossless) return BestUrl(u, "lossless", "LOSSLESS")
            val m = Regex("(\\d+)$").find(name)
            if (m != null) {
                val kbps = m.groupValues[1]
                return BestUrl(u, "${kbps}k", "${kbps}K")
            }
            return BestUrl(u, null, null)
        }
        return BestUrl(null, null, null)
    }

    private fun isSuccessCode(root: JsonObject): Boolean {
        val code = root["code"] as? JsonPrimitive ?: return false
        return code.contentOrNull == "200" || code.contentOrNull?.toIntOrNull() == 200
    }

    private fun getBody(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun String.encode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
