package com.musicsquare.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.musicsquare.app.data.model.MusicSourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("musicsquare_settings")

data class AppSettings(
    val enabledSources: Set<MusicSourceId> = setOf(
        MusicSourceId.NETEASE,
        MusicSourceId.QQ,
        MusicSourceId.KUWO,
    ),
    val perSourceLimit: Int = 10,
    val searchHistory: List<String> = emptyList(),
)

class SettingsRepository(private val context: Context) {
    private val sourcesKey = stringSetPreferencesKey("enabled_sources")
    private val limitKey = intPreferencesKey("per_source_limit")
    private val historyKey = stringPreferencesKey("search_history")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val sourceIds = prefs[sourcesKey] ?: setOf("netease", "qq", "kuwo")
        val historyRaw = prefs[historyKey].orEmpty()
        AppSettings(
            enabledSources = sourceIds.map { MusicSourceId.fromId(it) }.toSet()
                .ifEmpty { setOf(MusicSourceId.NETEASE, MusicSourceId.QQ, MusicSourceId.KUWO) },
            perSourceLimit = (prefs[limitKey] ?: 10).coerceIn(5, 30),
            searchHistory = historyRaw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(12),
        )
    }

    suspend fun setEnabledSources(sources: Set<MusicSourceId>) {
        context.settingsDataStore.edit { prefs ->
            prefs[sourcesKey] = sources.map { it.id }.toSet()
        }
    }

    suspend fun setPerSourceLimit(limit: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[limitKey] = limit.coerceIn(5, 30)
        }
    }

    suspend fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[historyKey].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(trimmed, ignoreCase = true) }
            prefs[historyKey] = (listOf(trimmed) + current).take(12).joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }

    suspend fun clearCacheFiles(context: Context) {
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
    }
}
