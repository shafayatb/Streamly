package com.shafayatb.streamly.domain.player

import com.shafayatb.streamly.domain.util.Error
import kotlin.time.Duration

/** A snapshot of a [VideoPlayer]. */
public data class PlaybackState(
    /** The loaded video, or `null` when nothing is loaded. */
    val videoId: String? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    /**
     * Whether playback was requested. It stays true while the player buffers towards playing, so
     * a play/pause control should follow this rather than [isPlaying].
     */
    val playWhenReady: Boolean = false,
    val position: Duration = Duration.ZERO,
    /** `null` until the player knows it, and always `null` for live streams. */
    val duration: Duration? = null,
    val isLive: Boolean = false,
    val isMuted: Boolean = false,
    /** Set while playback has failed; [VideoPlayer.retry] clears it. */
    val error: PlaybackError? = null,
) {
    val isPlaying: Boolean get() = playWhenReady && status == PlaybackStatus.READY
    val isBuffering: Boolean get() = status == PlaybackStatus.BUFFERING
    val isEnded: Boolean get() = status == PlaybackStatus.ENDED
}

public enum class PlaybackStatus {
    /** Nothing loaded, stopped, or failed. */
    IDLE,

    /** Waiting for enough media to play from the current position. */
    BUFFERING,

    /** Able to play from the current position; playing if [PlaybackState.playWhenReady]. */
    READY,

    /** Reached the end of the video. */
    ENDED,
}

public enum class PlaybackError : Error {
    /** The connection failed or timed out. Retrying may succeed once the network is back. */
    NETWORK,

    /** The server refused or no longer has the stream, e.g. HTTP 404. */
    SOURCE_UNAVAILABLE,

    /** The stream is malformed or the device cannot decode it. Retrying will not help. */
    UNSUPPORTED_FORMAT,

    UNKNOWN,
}
