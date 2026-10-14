package com.musicsquare.app.util

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

object LrcParser {
    private val lineRegex =
        Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?](.*)""")

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val matches = lineRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) return@forEach
            val text = matches.last().groupValues.getOrNull(4)?.trim().orEmpty()
            if (text.isEmpty()) return@forEach
            matches.forEach { match ->
                val min = match.groupValues[1].toLongOrNull() ?: return@forEach
                val sec = match.groupValues[2].toLongOrNull() ?: return@forEach
                val frac = match.groupValues[3]
                val ms = when {
                    frac.isBlank() -> 0L
                    frac.length <= 2 -> frac.padEnd(2, '0').toLong() * 10
                    else -> frac.padEnd(3, '0').take(3).toLong()
                }
                out += LyricLine(timeMs = min * 60_000 + sec * 1_000 + ms, text = text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var idx = -1
        for (i in lines.indices) {
            if (positionMs + 50 >= lines[i].timeMs) idx = i else break
        }
        return idx
    }
}
