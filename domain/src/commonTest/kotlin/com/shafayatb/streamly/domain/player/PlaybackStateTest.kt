package com.shafayatb.streamly.domain.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStateTest {

    @Test
    fun playingNeedsBothARequestAndReadyMedia() {
        assertTrue(PlaybackState(playWhenReady = true, status = PlaybackStatus.READY).isPlaying)
        assertFalse(PlaybackState(playWhenReady = false, status = PlaybackStatus.READY).isPlaying)
        assertFalse(PlaybackState(playWhenReady = true, status = PlaybackStatus.BUFFERING).isPlaying)
        assertFalse(PlaybackState(playWhenReady = true, status = PlaybackStatus.ENDED).isPlaying)
    }

    @Test
    fun bufferingAndEndedFollowTheStatus() {
        assertTrue(PlaybackState(status = PlaybackStatus.BUFFERING).isBuffering)
        assertFalse(PlaybackState(status = PlaybackStatus.READY).isBuffering)
        assertTrue(PlaybackState(status = PlaybackStatus.ENDED).isEnded)
        assertFalse(PlaybackState(status = PlaybackStatus.IDLE).isEnded)
    }
}
