package com.musicsquare.app.ui.i18n

enum class AppLanguage(val code: String) {
    ZH("zh"),
    EN("en"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ZH
    }
}
