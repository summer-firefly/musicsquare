package com.musicsquare.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.repo.MusicRepository
import com.musicsquare.app.player.PlayerController
import com.musicsquare.app.player.PlayerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SearchUiState(
    val keyword: String = "",
    val enabledSources: Map<MusicSourceId, Boolean> = mapOf(
        MusicSourceId.NETEASE to true,
        MusicSourceId.QQ to true,
        MusicSourceId.KUWO to true,
        MusicSourceId.JOOX to false,
    ),
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isPreparingPlay: Boolean = false,
    val statusMessage: String = "尚未搜索，试试输入「林俊杰」？",
    val errorMessage: String? = null,
)

class MusicViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = MusicRepository()
    private val playerController = PlayerController(application.applicationContext)

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    private var progressJob: Job? = null
    private var queueResolveJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { playerController.connect() }
            startProgressTicker()
        }
    }

    fun updateKeyword(value: String) {
        _searchState.update { it.copy(keyword = value) }
    }

    fun toggleSource(source: MusicSourceId) {
        _searchState.update { state ->
            val next = state.enabledSources.toMutableMap()
            next[source] = !(next[source] ?: false)
            state.copy(enabledSources = next)
        }
    }

    fun search() {
        val keyword = _searchState.value.keyword.trim()
        val enabled = _searchState.value.enabledSources
            .filterValues { it }
            .keys
        if (keyword.isEmpty()) {
            _searchState.update { it.copy(errorMessage = "请先输入搜索关键词") }
            return
        }
        if (enabled.isEmpty()) {
            _searchState.update { it.copy(errorMessage = "请至少选择一个音源") }
            return
        }

        viewModelScope.launch {
            _searchState.update {
                it.copy(
                    isSearching = true,
                    errorMessage = null,
                    statusMessage = "搜索中…",
                )
            }
            val results = runCatching {
                repository.search(
                    keyword = keyword,
                    enabledSources = enabled,
                    limitPerSource = 10,
                    reset = true,
                )
            }.getOrElse { emptyList() }

            _searchState.update {
                it.copy(
                    isSearching = false,
                    results = results,
                    statusMessage = if (results.isEmpty()) "没有找到结果" else "搜索完成，共 ${results.size} 首",
                    errorMessage = if (results.isEmpty()) "未找到相关歌曲，请换个关键词试试" else null,
                )
            }
        }
    }

    fun playTrack(track: Track) {
        viewModelScope.launch {
            _searchState.update {
                it.copy(isPreparingPlay = true, errorMessage = null, statusMessage = "正在加载音频…")
            }
            val playable = runCatching { repository.ensurePlayable(track) }.getOrElse { track }
            if (playable.audioUrl.isNullOrBlank()) {
                _searchState.update {
                    it.copy(
                        isPreparingPlay = false,
                        errorMessage = "播放失败，无法获取音频地址",
                        statusMessage = "播放失败",
                    )
                }
                return@launch
            }

            _searchState.update { state ->
                state.copy(
                    results = state.results.map { if (it.uid == playable.uid) playable else it },
                    isPreparingPlay = false,
                    statusMessage = "正在播放：${playable.title}",
                )
            }

            playerController.setQueueAndPlay(listOf(playable), 0)
            resolveFullQueue(_searchState.value.results, playable.uid)
        }
    }

    private fun resolveFullQueue(tracks: List<Track>, currentUid: String) {
        queueResolveJob?.cancel()
        queueResolveJob = viewModelScope.launch {
            val resolved = coroutineScope {
                tracks.map { track ->
                    async {
                        if (track.uid == currentUid && !track.audioUrl.isNullOrBlank()) {
                            track
                        } else {
                            runCatching { repository.ensurePlayable(track) }.getOrElse { track }
                        }
                    }
                }.awaitAll()
            }
            val playable = resolved.filter { !it.audioUrl.isNullOrBlank() }
            if (playable.size <= 1) {
                _searchState.update { it.copy(results = resolved) }
                return@launch
            }
            playerController.updateQueuePreservingCurrent(playable, currentUid)
            _searchState.update { it.copy(results = resolved) }
        }
    }

    fun playPause() = playerController.playPause()
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun clearError() {
        _searchState.update { it.copy(errorMessage = null) }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                playerController.pollProgress()
            }
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        queueResolveJob?.cancel()
        playerController.disconnect()
        super.onCleared()
    }
}
