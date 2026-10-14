package com.musicsquare.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.musicsquare.app.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PlayerUiState(
    val connected: Boolean = false,
    val currentMediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
    val playMode: PlayMode = PlayMode.LIST,
)

class PlayerController(private val context: Context) {
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var orderedTracks: List<Track> = emptyList()
    private var playMode: PlayMode = PlayMode.LIST

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        val mediaController = suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { cont.resume(it) }
                        .onFailure { cont.cancel(it) }
                },
                MoreExecutors.directExecutor()
            )
            cont.invokeOnCancellation {
                MediaController.releaseFuture(future)
            }
        }
        controller = mediaController
        mediaController.addListener(listener)
        applyPlayModeToPlayer()
        publish(mediaController)
    }

    fun disconnect() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlayerUiState()
    }

    fun queueTracks(): List<Track> = orderedTracks

    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        orderedTracks = tracks
        val playable = tracks.filter { !it.audioUrl.isNullOrBlank() }
        if (playable.isEmpty()) return
        val startUid = tracks.getOrNull(startIndex)?.uid
        val mediaTracks = mediaOrder(playable, startUid)
        val items = mediaTracks.mapNotNull { runCatching { TrackMediaMapper.toMediaItem(it) }.getOrNull() }
        if (items.isEmpty()) return
        val index = items.indexOfFirst { it.mediaId == startUid }.coerceAtLeast(0)
        c.setMediaItems(items, index, 0L)
        applyPlayModeToPlayer()
        c.prepare()
        c.play()
    }

    fun updateQueuePreservingCurrent(tracks: List<Track>, currentUid: String) {
        val c = controller ?: return
        orderedTracks = tracks
        val playable = tracks.filter { !it.audioUrl.isNullOrBlank() }
        if (playable.isEmpty()) return
        val mediaTracks = mediaOrder(playable, currentUid)
        val items = mediaTracks.mapNotNull { runCatching { TrackMediaMapper.toMediaItem(it) }.getOrNull() }
        if (items.isEmpty()) return

        val playingId = c.currentMediaItem?.mediaId ?: currentUid
        val position = c.currentPosition
        val wasPlaying = c.isPlaying
        val index = items.indexOfFirst { it.mediaId == playingId }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.mediaId == currentUid }.coerceAtLeast(0)

        c.setMediaItems(items, index, position.coerceAtLeast(0L))
        applyPlayModeToPlayer()
        c.prepare()
        if (wasPlaying) c.play() else c.pause()
    }

    fun cyclePlayMode(): PlayMode {
        playMode = playMode.next()
        val currentUid = controller?.currentMediaItem?.mediaId
        if (orderedTracks.isNotEmpty() && currentUid != null) {
            updateQueuePreservingCurrent(orderedTracks, currentUid)
        } else {
            applyPlayModeToPlayer()
            controller?.let { publish(it) }
        }
        return playMode
    }

    fun setPlayMode(mode: PlayMode) {
        if (playMode == mode) return
        playMode = mode
        val currentUid = controller?.currentMediaItem?.mediaId
        if (orderedTracks.isNotEmpty() && currentUid != null) {
            updateQueuePreservingCurrent(orderedTracks, currentUid)
        } else {
            applyPlayModeToPlayer()
            controller?.let { publish(it) }
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun playMediaId(mediaId: String) {
        val c = controller ?: return
        val index = (0 until c.mediaItemCount).firstOrNull { i ->
            c.getMediaItemAt(i).mediaId == mediaId
        } ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun pollProgress() {
        val c = controller ?: return
        publish(c)
    }

    private fun mediaOrder(playable: List<Track>, currentUid: String?): List<Track> {
        return when (playMode) {
            PlayMode.LIST, PlayMode.SINGLE -> playable
            PlayMode.SHUFFLE -> {
                val current = playable.firstOrNull { it.uid == currentUid }
                val rest = playable.filterNot { it.uid == currentUid }.shuffled()
                if (current != null) listOf(current) + rest else rest
            }
        }
    }

    private fun applyPlayModeToPlayer() {
        val c = controller ?: return
        c.repeatMode = when (playMode) {
            PlayMode.SINGLE -> Player.REPEAT_MODE_ONE
            PlayMode.LIST, PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL
        }
        c.shuffleModeEnabled = false
    }

    private fun publish(player: Player) {
        val item: MediaItem? = player.currentMediaItem
        val meta = item?.mediaMetadata
        val buffering = player.playbackState == Player.STATE_BUFFERING
        _state.value = PlayerUiState(
            connected = true,
            currentMediaId = item?.mediaId,
            title = meta?.title?.toString().orEmpty(),
            artist = meta?.artist?.toString().orEmpty(),
            artworkUri = meta?.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = buffering,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            queueSize = player.mediaItemCount,
            playMode = playMode,
        )
    }
}
