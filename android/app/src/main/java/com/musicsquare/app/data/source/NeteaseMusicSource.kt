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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class NeteaseMusicSource(
    private val client: okhttp3.OkHttpClient = HttpClientProvider.client,
) : MusicSourceClient {

    override suspend fun search(keyword: String, limit: Int, page: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val requestLimit = (page.coerceAtLeast(1) * limit.coerceAtLeast(1))
            val url =
                "https://api.qijieya.cn/meting/?type=search&id=${keyword.encode()}&limit=$requestLimit&server=netease"
            parseTrackList(getBody(url), keywordFallback = keyword)
        }

    suspend fun fetchPlaylist(playlistId: String, limit: Int = 30): List<Track> =
        withContext(Dispatchers.IO) {
            val url =
                "https://api.qijieya.cn/meting/?type=playlist&id=${playlistId.encode()}&server=netease"
            parseTrackList(getBody(url), keywordFallback = "playlist:$playlistId")
                .take(limit.coerceAtLeast(1))
        }

    override suspend fun fetchDetails(track: Track): Track = withContext(Dispatchers.IO) {
        var updated = track
        if (updated.songId.isNotBlank()) {
            if (updated.audioUrl.isNullOrBlank()) {
                updated = updated.copy(
                    audioUrl = "https://api.qijieya.cn/meting/?server=netease&type=url&id=${updated.songId.encode()}"
                )
            }
            if (updated.lrcUrl.isNullOrBlank()) {
                updated = updated.copy(
                    lrcUrl = "https://api.qijieya.cn/meting/?server=netease&type=lrc&id=${updated.songId.encode()}"
                )
            }
        }

        val quality = QualityUtils.inferQualityFromUrl(updated.audioUrl)
        updated = updated.copy(quality = quality.tag, qualityLabel = quality.label)

        if (updated.lrc.isNullOrBlank() && !updated.lrcUrl.isNullOrBlank()) {
            val lyricBody = getBody(updated.lrcUrl!!)
            if (!lyricBody.isNullOrBlank()) {
                val lyric = parseLyricPayload(lyricBody)
                if (!lyric.isNullOrBlank()) {
                    updated = updated.copy(lrc = lyric)
                }
            }
        }

        updated.copy(detailsLoaded = true)
    }

    private fun parseTrackList(body: String?, keywordFallback: String): List<Track> {
        if (body.isNullOrBlank()) return emptyList()
        val array = runCatching { AppJson.parseToJsonElement(body).asArrayOrNull() }.getOrNull()
            ?: return emptyList()
        return array.mapIndexedNotNull { idx, element ->
            val obj = element.asObjectOrNull() ?: return@mapIndexedNotNull null
            val audioUrl = obj.strOrNull("url")
            val songId = pickQueryParam(audioUrl, "id").ifBlank { "$keywordFallback-${idx + 1}" }
            Track(
                uid = "netease-$songId",
                source = MusicSourceId.NETEASE,
                displayIndex = idx + 1,
                keyword = keywordFallback,
                songId = songId,
                title = obj.str("name"),
                artist = obj.str("artist"),
                cover = obj.strOrNull("pic"),
                audioUrl = audioUrl,
                lrcUrl = obj.strOrNull("lrc"),
            )
        }
    }

    private fun parseLyricPayload(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return trimmed
        return runCatching {
            val element = AppJson.parseToJsonElement(trimmed)
            when (element) {
                is JsonArray -> null
                else -> {
                    val obj = element.asObjectOrNull() ?: return@runCatching null
                    obj.strOrNull("lrc", "lyric")
                        ?: obj["data"]?.asObjectOrNull()?.strOrNull("lrc", "lyric")
                        ?: obj["data"]?.let { if (it is JsonPrimitive) it.content else null }
                }
            }
        }.getOrNull() ?: trimmed.takeIf { it.contains('[') }
    }

    private fun pickQueryParam(rawUrl: String?, key: String): String {
        if (rawUrl.isNullOrBlank()) return ""
        return runCatching {
            rawUrl.toHttpUrl().queryParameter(key).orEmpty()
        }.getOrElse {
            Regex("[?&]$key=([^&]+)").find(rawUrl)?.groupValues?.getOrNull(1)
                ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
                .orEmpty()
        }
    }

    private fun getBody(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun String.encode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
