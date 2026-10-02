package com.shafayatb.streamly.shorts

sealed interface ShortsIntent {
    /**
     * The pager came to rest on [page]. This is what starts playback, including for the first
     * page, so a pager restored to a later page never plays page 0 first.
     */
    data class PageSettled(val page: Int) : ShortsIntent

    /** A tap on the video. */
    data object TogglePlayPause : ShortsIntent
    data object ToggleMute : ShortsIntent

    data class ToggleLike(val shortId: String) : ShortsIntent
    data class Comment(val shortId: String) : ShortsIntent
    data class Share(val shortId: String) : ShortsIntent

    /** Reloads the shorts after they failed to load or came back empty. */
    data object Retry : ShortsIntent

    /** Prepares the current short again after a playback error. */
    data object RetryPlayback : ShortsIntent

    /** The screen became visible: started, or back from the background. */
    data object ScreenShown : ShortsIntent

    /** The app went to the background or the user left Shorts. Not sent for rotation. */
    data object ScreenHidden : ShortsIntent
}
