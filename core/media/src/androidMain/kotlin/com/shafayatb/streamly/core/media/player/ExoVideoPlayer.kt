package com.shafayatb.streamly.core.media.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.util.EventLogger
import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.VideoPlayer
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [VideoPlayer] backed by one [ExoPlayer] for the whole app. Koin creates it as an application
 * singleton, so it survives configuration changes and every player screen reuses it; it is
 * released only when Koin closes.
 *
 * The [ExoPlayer] is built on first use, and only [VideoSurface] in this module sees it.
 */
@OptIn(UnstableApi::class)
public class ExoVideoPlayer internal constructor(
    context: Context,
    private val dataSourceFactory: DataSource.Factory,
    private val logEvents: Boolean,
) : VideoPlayer {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionUpdates: Job? = null
    private var loadedVideoIsLive = false

    private var player: ExoPlayer? = null

    /** Built on first use rather than at app start, which most sessions never need. */
    internal val exoPlayer: ExoPlayer
        get() = player ?: buildPlayer().also { player = it }

    override fun load(video: Video, playWhenReady: Boolean) {
        loadedVideoIsLive = video.isLive
        val mediaItem = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(video.hlsUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        exoPlayer.run {
            setMediaItem(mediaItem)
            this.playWhenReady = playWhenReady
            prepare()
        }
        publishState()
    }

    override fun play() {
        player?.run {
            when (playbackState) {
                Player.STATE_ENDED -> seekToDefaultPosition()
                Player.STATE_IDLE -> prepare()
            }
            play()
        }
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(position: Duration) {
        val player = player ?: return
        player.seekTo(position.inWholeMilliseconds)
        publishState()
    }

    override fun setMuted(muted: Boolean) {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    override fun retry() {
        player?.prepare()
    }

    override fun stop() {
        val player = player ?: return
        player.stop()
        player.clearMediaItems()
        loadedVideoIsLive = false
        publishState()
    }

    /** Frees the decoder and network resources for good. Only Koin calls this, when it closes. */
    internal fun release() {
        scope.cancel()
        player?.release()
        player = null
    }

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        // Every catalog stream is HLS. The default track selector and bandwidth meter switch
        // between the playlist's variants as the measured bandwidth changes.
        .setMediaSourceFactory(HlsMediaSource.Factory(dataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            addListener(PlayerListener())
            // Logs each video format switch with its bitrate, the evidence that ABR works.
            if (logEvents) addAnalyticsListener(EventLogger("StreamlyPlayer"))
        }

    private fun publishState() {
        val player = exoPlayer
        val isLive = loadedVideoIsLive || player.isCurrentMediaItemLive
        _state.value = _state.value.copy(
            videoId = player.currentMediaItem?.mediaId,
            status = playbackStatusOf(player.playbackState),
            playWhenReady = player.playWhenReady,
            position = player.currentPosition.coerceAtLeast(0).milliseconds,
            duration = player.duration
                .takeUnless { isLive || it == C.TIME_UNSET }
                ?.milliseconds,
            isLive = isLive,
            isMuted = player.volume == 0f,
            error = player.playerError?.let { playbackErrorOf(it.errorCode) },
        )
        if (player.isPlaying) startPositionUpdates() else stopPositionUpdates()
    }

    // ExoPlayer has no position callback, so poll while the position is actually moving.
    private fun startPositionUpdates() {
        if (positionUpdates?.isActive == true) return
        positionUpdates = scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL)
                publishState()
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdates?.cancel()
        positionUpdates = null
    }

    private inner class PlayerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
        }

        override fun onPlayerError(error: PlaybackException) {
            // A live stream paused too long falls out of its window; rejoin at the live edge.
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
            }
        }
    }

    private companion object {
        val POSITION_UPDATE_INTERVAL = 500.milliseconds
    }
}
