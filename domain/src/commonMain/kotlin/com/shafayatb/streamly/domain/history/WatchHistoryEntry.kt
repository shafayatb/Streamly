package com.shafayatb.streamly.domain.history

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A video an account has watched, with enough of its metadata to list it without the catalog
 * (offline, or after the video leaves the catalog).
 */
public data class WatchHistoryEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** `null` for live streams. */
    val duration: Duration?,
    /** Where playback was last saved; always zero for live streams. */
    val position: Duration,
    /** When it was last watched. */
    val watchedAt: Instant,
) {
    public val isLive: Boolean get() = duration == null

    /** Close enough to the end that the next viewing starts over, as video apps do. */
    public val isFinished: Boolean
        get() = duration != null &&
            (position >= duration - FinishedMargin || position >= duration * FinishedFraction)

    /** Where the player should start: the saved position, or zero for live, barely started, or finished videos. */
    public val resumePosition: Duration
        get() = if (isLive || isFinished || position < MinimumResume) Duration.ZERO else position

    /** How much has been watched, 0–1, for a progress bar; `null` for live streams. */
    public val progress: Float?
        get() {
            val total = duration ?: return null
            if (isFinished) return 1f
            return (position / total).toFloat().coerceIn(0f, 1f)
        }

    public companion object {
        public val MinimumResume: Duration = 5.seconds
        public val FinishedMargin: Duration = 10.seconds
        public const val FinishedFraction: Double = 0.95
    }
}
