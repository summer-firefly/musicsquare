package com.musicsquare.app.data.model

enum class MusicSourceId(val id: String, val label: String) {
    NETEASE("netease", "网易云"),
    QQ("qq", "QQ"),
    KUWO("kuwo", "酷我"),
    JOOX("joox", "JOOX");

    companion object {
        fun fromId(id: String): MusicSourceId =
            entries.firstOrNull { it.id == id } ?: NETEASE
    }
}

data class Track(
    val uid: String,
    val source: MusicSourceId,
    val displayIndex: Int = 1,
    val keyword: String = "",
    val songId: String = "",
    val songMid: String = "",
    val qqId: String = "",
    val qqSearchKey: String = "",
    val qqIndex: Int = 0,
    val jooxIndex: Int = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val cover: String? = null,
    val audioUrl: String? = null,
    val lrc: String? = null,
    val lrcUrl: String? = null,
    val detailsLoaded: Boolean = false,
    val quality: String? = null,
    val qualityLabel: String? = null,
    val pay: String? = null,
)

data class QualityInfo(
    val tag: String?,
    val label: String,
)
