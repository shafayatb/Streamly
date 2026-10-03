package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.player.VideoPlayer
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A [VideoPlayer] that applies commands to its state at once, like a stream that never stalls. */
class FakeVideoPlayer : VideoPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Every video id passed to [load], in order. */
    val loadedVideoIds = mutableListOf<String>()

    var retries = 0
        private set

    /** Every start position passed to [load], in order. */
    val loadedStartPositions = mutableListOf<Duration>()

    /** The status a video reports right after [load]; `BUFFERING` models a stream still filling its buffer. */
    var loadStatus = PlaybackStatus.READY

    override fun load(video: Video, playWhenReady: Boolean, startPosition: Duration) {
        loadedVideoIds += video.id
        loadedStartPositions += startPosition
        _state.update {
            PlaybackState(
                videoId = video.id,
                status = loadStatus,
                playWhenReady = playWhenReady,
                position = startPosition,
                duration = video.duration,
                isLive = video.isLive,
                isMuted = it.isMuted,
            )
        }
    }

    override fun play() = _state.update {
        if (it.isEnded) {
            it.copy(playWhenReady = true, status = PlaybackStatus.READY, position = Duration.ZERO)
        } else {
            it.copy(playWhenReady = true)
        }
    }

    override fun pause() = _state.update { it.copy(playWhenReady = false) }

    override fun seekTo(position: Duration) = _state.update { it.copy(position = position) }

    override fun setMuted(muted: Boolean) = _state.update { it.copy(isMuted = muted) }

    override fun retry() {
        retries++
        _state.update { it.copy(status = PlaybackStatus.BUFFERING, error = null) }
    }

    override fun stop() = _state.update { PlaybackState(isMuted = it.isMuted) }

    /** Simulates something the real player reports on its own, such as buffering or an error. */
    fun report(transform: (PlaybackState) -> PlaybackState) = _state.update(transform)
}
