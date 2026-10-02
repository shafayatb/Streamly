package com.shafayatb.streamly.player

import kotlin.time.Duration

sealed interface PlayerIntent {
    data object TogglePlayPause : PlayerIntent
    data class SeekTo(val position: Duration) : PlayerIntent
    data object ToggleMute : PlayerIntent

    /** Reloads the video's details after they failed to load. */
    data object RetryDetails : PlayerIntent

    /** Prepares the stream again after a playback error. */
    data object RetryPlayback : PlayerIntent
    data object RetryUpNext : PlayerIntent

    data class SelectUpNext(val videoId: String) : PlayerIntent
    data object NavigateBack : PlayerIntent

    data object ToggleLike : PlayerIntent
    data object ToggleSubscribe : PlayerIntent
    data object Share : PlayerIntent

    /** The screen became visible: started, back from the background, or returned to. */
    data object ScreenShown : PlayerIntent

    /** The app went to the background or the user left the screen. Not sent for rotation. */
    data object ScreenHidden : PlayerIntent
}
