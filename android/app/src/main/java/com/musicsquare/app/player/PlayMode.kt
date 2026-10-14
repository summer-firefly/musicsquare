package com.musicsquare.app.player

enum class PlayMode {
    LIST,
    SINGLE,
    SHUFFLE,
    ;

    fun next(): PlayMode = when (this) {
        LIST -> SINGLE
        SINGLE -> SHUFFLE
        SHUFFLE -> LIST
    }

    val label: String
        get() = when (this) {
            LIST -> "列表循环"
            SINGLE -> "单曲循环"
            SHUFFLE -> "随机播放"
        }
}
