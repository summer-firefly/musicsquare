package com.musicsquare.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musicsquare.app.data.local.AppSettings
import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.ui.i18n.AppLanguage
import com.musicsquare.app.ui.i18n.LocalAppStrings
import kotlinx.coroutines.launch
import java.nio.charset.Charset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    downloadCount: Int,
    onToggleSource: (MusicSourceId) -> Unit,
    onSetLimit: (Int) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onClearHistory: () -> Unit,
    onClearCache: () -> Unit,
    onClearDownloads: () -> Unit,
    onExport: suspend () -> String,
    onImport: (String) -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charset.forName("UTF-8"))
            }
        }.getOrNull()?.let(onImport)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = onExport()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charset.forName("UTF-8")))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = s.settingsTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = s.settingsSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(s.languageTitle, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = settings.language == AppLanguage.ZH,
                onClick = { onSetLanguage(AppLanguage.ZH) },
                label = { Text(s.languageZh) },
            )
            FilterChip(
                selected = settings.language == AppLanguage.EN,
                onClick = { onSetLanguage(AppLanguage.EN) },
                label = { Text(s.languageEn) },
            )
        }

        Text(s.defaultSources, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MusicSourceId.entries.forEach { source ->
                FilterChip(
                    selected = source in settings.enabledSources,
                    onClick = { onToggleSource(source) },
                    label = { Text(s.sourceLabel(source)) },
                )
            }
        }

        Text(s.perSourceLimit(settings.perSourceLimit), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = settings.perSourceLimit.toFloat(),
            onValueChange = { onSetLimit(it.toInt()) },
            valueRange = 5f..30f,
            steps = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(s.searchHistory, style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (settings.searchHistory.isEmpty()) {
                s.noHistory
            } else {
                settings.searchHistory.joinToString(" · ")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
            Text(s.clearSearchHistory)
        }

        Text(s.downloadsManage, style = MaterialTheme.typography.titleMedium)
        Text(
            text = s.downloadsCount(downloadCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onClearDownloads,
            enabled = downloadCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.clearDownloads)
        }

        Text(s.playlistData, style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { exportLauncher.launch("blue-music-playlists.json") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.exportLibrary)
        }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.importLibrary)
        }

        OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
            Text(s.clearCache)
        }

        Text(
            text = s.copyrightNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
