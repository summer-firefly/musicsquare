package com.musicsquare.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicsquare.app.data.local.AppSettings
import com.musicsquare.app.data.local.DownloadRepository
import com.musicsquare.app.data.local.LibraryRepository
import com.musicsquare.app.data.local.SettingsRepository
import com.musicsquare.app.data.model.MusicSourceId
import com.musicsquare.app.data.model.RecommendCatalog
import com.musicsquare.app.data.model.RecommendPlaylist
import com.musicsquare.app.data.model.StoredPlaylist
import com.musicsquare.app.data.model.Track
import com.musicsquare.app.data.model.toTrack
import com.musicsquare.app.data.repo.MusicRepository
import com.musicsquare.app.data.repo.RecommendRepository
import com.musicsquare.app.player.PlayerController
import com.musicsquare.app.player.PlayerUiState
import com.musicsquare.app.ui.i18n.AppLanguage
import com.musicsquare.app.ui.i18n.AppStrings
import com.musicsquare.app.util.LrcParser
import com.musicsquare.app.util.LyricLine
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
    val perSourceLimit: Int = 10,
    val page: Int = 1,
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isPreparingPlay: Boolean = false,
    val canLoadMore: Boolean = true,
    val sourcesExpanded: Boolean = false,
    val statusMessage: String = "尚未搜索，试试输入「林俊杰」？",
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val searchHistory: List<String> = emptyList(),
)

data class PlaybackExtras(
    val showNowPlaying: Boolean = false,
    val showQueue: Boolean = false,
    val showCover: Boolean = true,
    val queue: List<Track> = emptyList(),
    val currentTrack: Track? = null,
    val lyricLines: List<LyricLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val favoriteUids: Set<String> = emptySet(),
)

data class LibraryUiState(
    val favorites: List<Track> = emptyList(),
    val playlists: List<StoredPlaylist> = emptyList(),
    val downloads: List<Track> = emptyList(),
    val selectedPlaylistId: String? = null,
    val showDownloads: Boolean = false,
)

data class RecommendUiState(
    val isLoading: Boolean = false,
    val hotTracks: List<Track> = emptyList(),
    val hotPlaylists: List<RecommendPlaylist> = emptyList(),
    val batchIndex: Int = 0,
    val errorMessage: String? = null,
)

data class DownloadUiState(
    val downloadedUids: Set<String> = emptySet(),
    val downloadingUids: Set<String> = emptySet(),
)

class MusicViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = MusicRepository()
    private val recommendRepository = RecommendRepository()
    private val libraryRepository = LibraryRepository(application.applicationContext)
    private val downloadRepository = DownloadRepository(application.applicationContext)
    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val playerController = PlayerController(application.applicationContext)

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _playbackExtras = MutableStateFlow(PlaybackExtras())
    val playbackExtras: StateFlow<PlaybackExtras> = _playbackExtras.asStateFlow()

    private val _libraryState = MutableStateFlow(LibraryUiState())
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _recommendState = MutableStateFlow(RecommendUiState())
    val recommendState: StateFlow<RecommendUiState> = _recommendState.asStateFlow()

    private val _downloadState = MutableStateFlow(DownloadUiState())
    val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private var progressJob: Job? = null
    private var queueResolveJob: Job? = null
    private var recommendJob: Job? = null
    private val trackBag = linkedMapOf<String, Track>()
    private var recommendTrackPool: List<Track> = emptyList()
    private var recommendPlaylistPool: List<RecommendPlaylist> = emptyList()
    private var recommendBatch: Int = 0
    private var strings: AppStrings = AppStrings.forLanguage(AppLanguage.ZH)

    init {
        viewModelScope.launch {
            runCatching { playerController.connect() }
            libraryRepository.load()
            downloadRepository.load()
            refreshLibraryUi()
            refreshDownloadUi()
            startProgressTicker()
            loadRecommendations()
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                strings = AppStrings.forLanguage(settings.language)
                _searchState.update { state ->
                    state.copy(
                        enabledSources = MusicSourceId.entries.associateWith { it in settings.enabledSources },
                        perSourceLimit = settings.perSourceLimit,
                        searchHistory = settings.searchHistory,
                        statusMessage = if (state.results.isEmpty() && !state.isSearching) {
                            strings.statusIdleSearch
                        } else {
                            state.statusMessage
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            playerController.state.collect { player ->
                val uid = player.currentMediaId
                val track = uid?.let {
                    trackBag[it] ?: playerController.queueTracks().firstOrNull { t -> t.uid == it }
                }
                val lines = LrcParser.parse(track?.lrc)
                val idx = LrcParser.indexAt(lines, player.positionMs)
                _playbackExtras.update {
                    it.copy(
                        currentTrack = track,
                        lyricLines = lines,
                        currentLyricIndex = idx,
                        queue = playerController.queueTracks(),
                        favoriteUids = libraryRepository.favorites().map { f -> f.uid }.toSet(),
                    )
                }
            }
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
        viewModelScope.launch {
            val enabled = _searchState.value.enabledSources.filterValues { it }.keys
            settingsRepository.setEnabledSources(enabled)
        }
    }

    fun toggleSourcesExpanded() {
        _searchState.update { it.copy(sourcesExpanded = !it.sourcesExpanded) }
    }

    fun loadRecommendations() {
        refreshRecommendations(forceReload = true)
    }

    /**
     * 「换一批」：优先在已缓存的推荐池里轮换；池为空或强制刷新时重新拉取。
     */
    fun refreshRecommendations(forceReload: Boolean = false) {
        recommendJob?.cancel()
        recommendJob = viewModelScope.launch {
            _recommendState.update { it.copy(isLoading = true, errorMessage = null) }
            val poolEmpty = recommendTrackPool.isEmpty() || recommendPlaylistPool.isEmpty()
            val periodicReload = !forceReload &&
                !poolEmpty &&
                recommendBatch > 0 &&
                recommendBatch % RELOAD_EVERY_BATCHES == 0
            val needReload = forceReload || poolEmpty || periodicReload

            if (needReload) {
                val catalog = runCatching { recommendRepository.loadPool() }
                    .getOrElse { RecommendCatalog() }
                recommendTrackPool = catalog.hotTracks
                recommendPlaylistPool = catalog.hotPlaylists
                catalog.hotTracks.forEach { trackBag[it.uid] = it }
                catalog.hotPlaylists.forEach { pl ->
                    pl.tracks.forEach { trackBag[it.uid] = it }
                }
            }

            recommendBatch = when {
                forceReload || poolEmpty -> 0
                else -> recommendBatch + 1
            }

            val hotTracks = nextWindow(recommendTrackPool, HOT_TRACK_BATCH, recommendBatch)
            val hotPlaylists = nextPlaylistBatch(
                recommendPlaylistPool,
                PLAYLIST_BATCH,
                recommendBatch,
            )
            _recommendState.update {
                it.copy(
                    isLoading = false,
                    hotTracks = hotTracks,
                    hotPlaylists = hotPlaylists,
                    batchIndex = recommendBatch,
                    errorMessage = if (hotTracks.isEmpty() && hotPlaylists.isEmpty()) {
                        strings.recommendLoadFailed
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun <T> nextWindow(pool: List<T>, size: Int, batch: Int): List<T> {
        if (pool.isEmpty()) return emptyList()
        if (pool.size <= size) {
            // Small pools still rotate order so refresh feels different.
            val offset = batch % pool.size
            return pool.drop(offset) + pool.take(offset)
        }
        val start = (batch * size) % pool.size
        return List(size) { index -> pool[(start + index) % pool.size] }
    }

    private fun nextPlaylistBatch(
        pool: List<RecommendPlaylist>,
        size: Int,
        batch: Int,
    ): List<RecommendPlaylist> {
        // Advance by a full page so every refresh replaces the whole row.
        return nextWindow(pool, size, batch).mapIndexed { index, playlist ->
            val tracks = playlist.tracks
            if (tracks.isEmpty()) return@mapIndexed playlist
            val coverIndex = (batch * size + index) % tracks.size
            val rotated = tracks.drop(coverIndex) + tracks.take(coverIndex)
            playlist.copy(
                cover = rotated.firstOrNull()?.cover ?: playlist.cover,
                tracks = rotated,
            )
        }
    }

    fun playRecommendTrack(track: Track) {
        val queue = _recommendState.value.hotTracks.ifEmpty { listOf(track) }
        playTrack(track, queue)
    }

    fun playRecommendPlaylist(playlist: RecommendPlaylist) {
        viewModelScope.launch {
            _searchState.update {
                it.copy(isPreparingPlay = true, statusMessage = strings.loadingAudio)
            }
            val tracks = playlist.tracks.ifEmpty {
                recommendRepository.loadPlaylistTracks(playlist.id)
            }
            tracks.forEach { trackBag[it.uid] = it }
            val first = tracks.firstOrNull()
            if (first == null) {
                _searchState.update {
                    it.copy(
                        isPreparingPlay = false,
                        errorMessage = strings.toastEmptyPlaylist,
                        statusMessage = strings.toastEmptyPlaylist,
                    )
                }
                return@launch
            }
            _recommendState.update { state ->
                state.copy(
                    hotPlaylists = state.hotPlaylists.map {
                        if (it.id == playlist.id) it.copy(tracks = tracks, trackCount = tracks.size) else it
                    }
                )
            }
            playTrack(first, tracks)
        }
    }

    fun search(reset: Boolean = true) {
        val keyword = _searchState.value.keyword.trim()
        val enabled = _searchState.value.enabledSources.filterValues { it }.keys
        if (keyword.isEmpty()) {
            _searchState.update { it.copy(errorMessage = strings.toastNeedKeyword) }
            return
        }
        if (enabled.isEmpty()) {
            _searchState.update { it.copy(errorMessage = strings.searchSources) }
            return
        }

        viewModelScope.launch {
            val page = if (reset) 1 else _searchState.value.page + 1
            _searchState.update {
                it.copy(
                    isSearching = reset,
                    isLoadingMore = !reset,
                    errorMessage = null,
                    statusMessage = if (reset) strings.toastSearching else strings.toastLoadMore,
                    page = page,
                )
            }
            settingsRepository.addSearchHistory(keyword)
            val limit = _searchState.value.perSourceLimit
            val results = runCatching {
                repository.search(
                    keyword = keyword,
                    enabledSources = enabled,
                    limitPerSource = limit * page,
                    page = page,
                    reset = reset,
                )
            }.getOrElse { emptyList() }

            results.forEach { trackBag[it.uid] = it }
            val added = if (reset) results.size else results.size - _searchState.value.results.size
            _searchState.update {
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    results = results,
                    canLoadMore = added > 0,
                    statusMessage = if (results.isEmpty()) {
                        strings.toastNoResults
                    } else {
                        strings.toastResultsCount(results.size)
                    },
                    errorMessage = if (results.isEmpty() && reset) strings.toastNoResults else null,
                    toastMessage = if (!reset && added <= 0) strings.toastNoMore else null,
                )
            }
        }
    }

    fun loadMore() {
        if (_searchState.value.isLoadingMore || _searchState.value.isSearching || !_searchState.value.canLoadMore) return
        search(reset = false)
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        viewModelScope.launch {
            _searchState.update {
                it.copy(isPreparingPlay = true, errorMessage = null, statusMessage = strings.loadingAudio)
            }
            val playable = resolvePlayable(track)
            trackBag[playable.uid] = playable
            if (playable.audioUrl.isNullOrBlank()) {
                _searchState.update {
                    it.copy(
                        isPreparingPlay = false,
                        errorMessage = strings.toastPlayFail,
                        statusMessage = strings.toastPlayFail,
                    )
                }
                return@launch
            }

            val baseQueue = queue ?: _searchState.value.results.ifEmpty { listOf(playable) }
            val index = baseQueue.indexOfFirst { it.uid == playable.uid }.coerceAtLeast(0)
            _searchState.update { state ->
                state.copy(
                    results = state.results.map { if (it.uid == playable.uid) playable else it },
                    isPreparingPlay = false,
                    statusMessage = strings.toastPlaying(playable.title),
                )
            }
            _playbackExtras.update {
                it.copy(currentTrack = playable, lyricLines = LrcParser.parse(playable.lrc))
            }

            val seeded = baseQueue.toMutableList()
            if (index in seeded.indices) seeded[index] = playable else seeded.add(0, playable)
            playerController.setQueueAndPlay(listOf(playable), 0)
            resolveFullQueue(seeded, playable.uid)
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
                            resolvePlayable(track)
                        }.also { trackBag[it.uid] = it }
                    }
                }.awaitAll()
            }
            val playable = resolved.filter { !it.audioUrl.isNullOrBlank() }
            if (playable.size <= 1) {
                _searchState.update { state ->
                    state.copy(results = mergeResults(state.results, resolved))
                }
                _playbackExtras.update { it.copy(queue = playable.ifEmpty { listOfNotNull(trackBag[currentUid]) }) }
                return@launch
            }
            playerController.updateQueuePreservingCurrent(playable, currentUid)
            _searchState.update { state ->
                state.copy(results = mergeResults(state.results, resolved))
            }
            _playbackExtras.update { it.copy(queue = playable) }
        }
    }

    private fun mergeResults(current: List<Track>, resolved: List<Track>): List<Track> {
        val map = resolved.associateBy { it.uid }
        return current.map { map[it.uid] ?: it }
    }

    fun openNowPlaying() = _playbackExtras.update { it.copy(showNowPlaying = true) }
    fun closeNowPlaying() = _playbackExtras.update { it.copy(showNowPlaying = false, showQueue = false) }
    fun toggleQueueSheet() = _playbackExtras.update { it.copy(showQueue = !it.showQueue) }
    fun closeQueueSheet() = _playbackExtras.update { it.copy(showQueue = false) }
    fun toggleCoverLyrics() = _playbackExtras.update { it.copy(showCover = !it.showCover) }

    fun playPause() = playerController.playPause()
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun cyclePlayMode() {
        val mode = playerController.cyclePlayMode()
        toast(strings.toastPlayMode(strings.playModeLabel(mode)))
    }

    fun downloadTrack(track: Track? = _playbackExtras.value.currentTrack) {
        if (track == null) return
        if (downloadRepository.isDownloaded(track.uid)) {
            toast(strings.toastDownloadExists)
            return
        }
        if (track.uid in _downloadState.value.downloadingUids) return
        viewModelScope.launch {
            _downloadState.update { it.copy(downloadingUids = it.downloadingUids + track.uid) }
            val playable = resolvePlayable(track)
            trackBag[playable.uid] = playable
            val url = playable.audioUrl
            val result = if (url.isNullOrBlank()) {
                Result.failure(IllegalStateException("no url"))
            } else {
                downloadRepository.download(playable, url)
            }
            _downloadState.update { it.copy(downloadingUids = it.downloadingUids - track.uid) }
            refreshDownloadUi()
            toast(if (result.isSuccess) strings.toastDownloadOk else strings.toastDownloadFail)
        }
    }

    fun deleteDownload(uid: String) {
        viewModelScope.launch {
            downloadRepository.delete(uid)
            refreshDownloadUi()
            toast(strings.toastDeletedDownload)
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            downloadRepository.clearAll()
            refreshDownloadUi()
            toast(strings.toastDownloadsCleared)
        }
    }

    fun playDownloads() {
        val list = downloadRepository.asTracks()
        val first = list.firstOrNull() ?: return
        playTrack(first, list)
    }

    fun showDownloads(show: Boolean) {
        _libraryState.update { it.copy(showDownloads = show, selectedPlaylistId = null) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            strings = AppStrings.forLanguage(language)
            toast(
                if (language == AppLanguage.ZH) {
                    AppStrings.forLanguage(AppLanguage.ZH).languageZh
                } else {
                    AppStrings.forLanguage(AppLanguage.EN).languageEn
                }
            )
        }
    }

    private suspend fun resolvePlayable(track: Track): Track {
        val local = downloadRepository.localPath(track.uid)
        if (local != null) {
            return track.copy(audioUrl = local, detailsLoaded = true)
        }
        return runCatching { repository.ensurePlayable(track) }.getOrElse { track }
    }

    fun playFromQueue(track: Track) {
        val queue = _playbackExtras.value.queue.ifEmpty { playerController.queueTracks() }
        if (queue.any { it.uid == track.uid && !it.audioUrl.isNullOrBlank() }) {
            playerController.playMediaId(track.uid)
            closeQueueSheet()
        } else {
            playTrack(track, queue)
            closeQueueSheet()
        }
    }

    fun toggleFavorite(track: Track? = _playbackExtras.value.currentTrack) {
        if (track == null) return
        viewModelScope.launch {
            val added = libraryRepository.toggleFavorite(track)
            refreshLibraryUi()
            toast(if (added) strings.toastAddedFavorite else strings.toastRemovedFavorite)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            libraryRepository.createPlaylist(name)
            refreshLibraryUi()
            toast(strings.toastPlaylistCreated)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(id)
            _libraryState.update {
                it.copy(selectedPlaylistId = it.selectedPlaylistId?.takeIf { sid -> sid != id })
            }
            refreshLibraryUi()
            toast(strings.toastPlaylistDeleted)
        }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch {
            libraryRepository.renamePlaylist(id, name)
            refreshLibraryUi()
        }
    }

    fun selectPlaylist(id: String?) {
        _libraryState.update { it.copy(selectedPlaylistId = id, showDownloads = false) }
    }

    fun addCurrentToPlaylist(playlistId: String) {
        val track = _playbackExtras.value.currentTrack ?: return
        viewModelScope.launch {
            val added = libraryRepository.addToPlaylist(playlistId, track)
            refreshLibraryUi()
            toast(if (added) strings.toastAddedToPlaylist else strings.toastAlreadyInPlaylist)
        }
    }

    fun removeFromPlaylist(playlistId: String, trackUid: String) {
        viewModelScope.launch {
            libraryRepository.removeFromPlaylist(playlistId, trackUid)
            refreshLibraryUi()
        }
    }

    fun playFavorites() {
        val list = libraryRepository.favorites()
        val first = list.firstOrNull() ?: return
        playTrack(first, list)
    }

    fun playPlaylist(playlist: StoredPlaylist) {
        val list = playlist.tracks.map { it.toTrack() }
        val first = list.firstOrNull() ?: return
        playTrack(first, list)
    }

    suspend fun exportLibraryJson(): String = libraryRepository.exportJson()

    fun importLibraryJson(raw: String) {
        viewModelScope.launch {
            val ok = libraryRepository.importJson(raw)
            refreshLibraryUi()
            toast(if (ok) strings.toastImportOk else strings.toastImportFail)
        }
    }

    fun applyHistory(keyword: String) {
        _searchState.update { it.copy(keyword = keyword) }
        search(reset = true)
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            settingsRepository.clearSearchHistory()
            toast(strings.toastHistoryCleared)
        }
    }

    fun setPerSourceLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setPerSourceLimit(limit)
            toast(strings.toastLimit(limit))
        }
    }

    fun setEnabledSources(sources: Set<MusicSourceId>) {
        viewModelScope.launch {
            settingsRepository.setEnabledSources(sources)
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            settingsRepository.clearCacheFiles(getApplication())
            toast(strings.toastCacheCleared)
        }
    }

    fun clearError() {
        _searchState.update { it.copy(errorMessage = null) }
    }

    fun consumeToast() {
        _searchState.update { it.copy(toastMessage = null) }
    }

    private fun toast(msg: String) {
        _searchState.update { it.copy(toastMessage = msg) }
    }

    private fun refreshLibraryUi() {
        _libraryState.update {
            it.copy(
                favorites = libraryRepository.favorites(),
                playlists = libraryRepository.playlists(),
                downloads = downloadRepository.asTracks(),
            )
        }
        _playbackExtras.update {
            it.copy(favoriteUids = libraryRepository.favorites().map { t -> t.uid }.toSet())
        }
    }

    private fun refreshDownloadUi() {
        val downloads = downloadRepository.asTracks()
        _downloadState.update {
            it.copy(downloadedUids = downloadRepository.downloadedUids())
        }
        _libraryState.update { it.copy(downloads = downloads) }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(400)
                playerController.pollProgress()
            }
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        queueResolveJob?.cancel()
        recommendJob?.cancel()
        playerController.disconnect()
        super.onCleared()
    }

    companion object {
        private const val HOT_TRACK_BATCH = 12
        private const val PLAYLIST_BATCH = 3
        private const val RELOAD_EVERY_BATCHES = 6
    }
}
