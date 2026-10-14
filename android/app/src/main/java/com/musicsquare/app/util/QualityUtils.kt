package com.musicsquare.app.util

import com.musicsquare.app.data.model.QualityInfo

object QualityUtils {
    fun inferQualityFromUrl(url: String?): QualityInfo {
        if (url.isNullOrBlank()) return QualityInfo(null, "")
        val base = url.substringBefore('?').lowercase()
        val ext = base.substringAfterLast('.', missingDelimiterValue = "")
        val lossless = setOf("flac", "wav", "ape", "alac", "aiff")
        return if (ext in lossless) {
            QualityInfo("lossless", "LOSSLESS")
        } else {
            QualityInfo("320k", "320K")
        }
    }
}
