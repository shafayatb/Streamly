package com.shafayatb.streamly.core.media.player

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.shafayatb.streamly.domain.player.PlaybackError
import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.PlaybackStatus
import kotlin.time.Duration.Companion.milliseconds

/**
 * A snapshot of this player for the domain. [isLive] can be known before the player has parsed the
 * playlist, so callers pass what they know and the player's own flag is added to it.
 */
internal fun Player.toPlaybackState(isLive: Boolean = false): PlaybackState {
    val live = isLive || isCurrentMediaItemLive
    return PlaybackState(
        videoId = currentMediaItem?.mediaId,
        status = playbackStatusOf(playbackState),
        playWhenReady = playWhenReady,
        position = currentPosition.coerceAtLeast(0).milliseconds,
        duration = duration.takeUnless { live || it == C.TIME_UNSET }?.milliseconds,
        isLive = live,
        isMuted = volume == 0f,
        error = playerError?.let { playbackErrorOf(it.errorCode) },
    )
}

internal fun playbackStatusOf(@Player.State state: Int): PlaybackStatus = when (state) {
    Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
    Player.STATE_READY -> PlaybackStatus.READY
    Player.STATE_ENDED -> PlaybackStatus.ENDED
    else -> PlaybackStatus.IDLE
}

/** Groups Media3's error codes by what the user can do about them. */
internal fun playbackErrorOf(@PlaybackException.ErrorCode errorCode: Int): PlaybackError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_TIMEOUT,
    -> PlaybackError.NETWORK

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    -> PlaybackError.SOURCE_UNAVAILABLE

    in PARSING_AND_DECODING_ERRORS -> PlaybackError.UNSUPPORTED_FORMAT

    else -> PlaybackError.UNKNOWN
}

// Media3 numbers parsing errors 3xxx and decoding errors 4xxx.
private val PARSING_AND_DECODING_ERRORS = 3000..4999
