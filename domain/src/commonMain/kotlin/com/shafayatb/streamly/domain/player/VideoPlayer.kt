package com.shafayatb.streamly.domain.player

import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's one long-form video player. It outlives screens and configuration changes, so every
 * player screen reuses the same instance: a screen takes it over by calling [load].
 *
 * Commands act on whatever is loaded. A screen that may have been replaced by another one should
 * check [PlaybackState.videoId] before controlling the player, so it never pauses or stops a video
 * that a newer screen started.
 *
 * Call it from the main thread.
 */
public interface VideoPlayer {
    public val state: StateFlow<PlaybackState>

    /**
     * Replaces whatever is loaded with [video], from its start (or the live edge). Playback begins
     * as soon as enough is buffered if [playWhenReady] is true.
     */
    public fun load(video: Video, playWhenReady: Boolean)

    /** Starts or resumes playback. A video that has ended starts again from the beginning. */
    public fun play()

    public fun pause()

    /** Clamped to the video's length by the player. Ignored for live streams without a window. */
    public fun seekTo(position: Duration)

    /** Muting outlasts the current video: the next video loads muted too. */
    public fun setMuted(muted: Boolean)

    /** Prepares again after [PlaybackState.error], continuing from the last position. */
    public fun retry()

    /**
     * Stops playback, unloads the video, and frees decoders and buffers. The instance stays
     * usable: the next [load] reuses it.
     */
    public fun stop()
}
