package com.shafayatb.streamly.domain.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WatchHistoryEntryTest {

    private fun entry(position: Duration, duration: Duration? = 10.minutes) = WatchHistoryEntry(
        videoId = "v",
        title = "Video",
        channelName = "Channel",
        thumbnailUrl = "",
        duration = duration,
        position = position,
        watchedAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun resumesASavedPosition() {
        val entry = entry(3.minutes)

        assertEquals(3.minutes, entry.resumePosition)
        assertEquals(0.3f, entry.progress)
        assertFalse(entry.isFinished)
    }

    @Test
    fun startsOverWhenBarelyStarted() {
        assertEquals(Duration.ZERO, entry(4.seconds).resumePosition)
        assertEquals(5.seconds, entry(5.seconds).resumePosition)
    }

    @Test
    fun past95PercentCountsAsFinished() {
        // 95% of 10 minutes is 9:30.
        assertEquals(9.minutes + 29.seconds, entry(9.minutes + 29.seconds).resumePosition)
        val finished = entry(9.minutes + 30.seconds)
        assertTrue(finished.isFinished)
        assertEquals(Duration.ZERO, finished.resumePosition)
        assertEquals(1f, finished.progress)
    }

    @Test
    fun theLastTenSecondsOfAShortVideoCountAsFinished() {
        // For 2 minutes, the 10-second margin (1:50) comes before 95% (1:54).
        assertEquals(109.seconds, entry(109.seconds, duration = 2.minutes).resumePosition)
        assertTrue(entry(110.seconds, duration = 2.minutes).isFinished)
    }

    @Test
    fun aLongVideoResumesUntil95Percent() {
        assertEquals(56.minutes, entry(56.minutes, duration = 1.hours).resumePosition)
        assertTrue(entry(57.minutes, duration = 1.hours).isFinished)
    }

    @Test
    fun anEndedVideoStartsOver() {
        val ended = entry(10.minutes)

        assertEquals(Duration.ZERO, ended.resumePosition)
        assertEquals(1f, ended.progress)
    }

    @Test
    fun liveNeverResumesAndHasNoProgress() {
        val live = entry(3.minutes, duration = null)

        assertTrue(live.isLive)
        assertFalse(live.isFinished)
        assertEquals(Duration.ZERO, live.resumePosition)
        assertNull(live.progress)
    }
}
