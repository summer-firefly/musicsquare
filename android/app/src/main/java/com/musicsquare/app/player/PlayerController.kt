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
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
)

class PlayerController(private val context: Context) {
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

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
        publish(mediaController)
    }

    fun disconnect() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlayerUiState()
    }

    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        val items = tracks.mapNotNull { track ->
            runCatching { TrackMediaMapper.toMediaItem(track) }.getOrNull()
        }
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(0, items.lastIndex)
        c.setMediaItems(items, index, /* startPositionMs = */ 0L)
        c.prepare()
        c.play()
    }

    /**
     * Replace the queue while keeping the currently playing item/position when possible.
     */
    fun updateQueuePreservingCurrent(tracks: List<Track>, currentUid: String) {
        val c = controller ?: return
        val items = tracks.mapNotNull { track ->
            runCatching { TrackMediaMapper.toMediaItem(track) }.getOrNull()
        }
        if (items.isEmpty()) return

        val playingId = c.currentMediaItem?.mediaId ?: currentUid
        val position = c.currentPosition
        val wasPlaying = c.isPlaying
        val index = items.indexOfFirst { it.mediaId == playingId }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.mediaId == currentUid }.coerceAtLeast(0)

        c.setMediaItems(items, index, position.coerceAtLeast(0L))
        c.prepare()
        if (wasPlaying) c.play() else c.pause()
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

    fun pollProgress() {
        val c = controller ?: return
        publish(c)
    }

    private fun publish(player: Player) {
        val item: MediaItem? = player.currentMediaItem
        val meta = item?.mediaMetadata
        _state.value = PlayerUiState(
            connected = true,
            currentMediaId = item?.mediaId,
            title = meta?.title?.toString().orEmpty(),
            artist = meta?.artist?.toString().orEmpty(),
            artworkUri = meta?.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            queueSize = player.mediaItemCount,
        )
    }
}
