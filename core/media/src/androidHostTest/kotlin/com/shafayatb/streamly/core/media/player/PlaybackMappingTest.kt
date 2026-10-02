package com.shafayatb.streamly.core.media.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.shafayatb.streamly.domain.player.PlaybackError
import com.shafayatb.streamly.domain.player.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMappingTest {

    @Test
    fun mapsEveryPlayerState() {
        assertEquals(PlaybackStatus.IDLE, playbackStatusOf(Player.STATE_IDLE))
        assertEquals(PlaybackStatus.BUFFERING, playbackStatusOf(Player.STATE_BUFFERING))
        assertEquals(PlaybackStatus.READY, playbackStatusOf(Player.STATE_READY))
        assertEquals(PlaybackStatus.ENDED, playbackStatusOf(Player.STATE_ENDED))
    }

    @Test
    fun connectionFailuresAreNetworkErrors() {
        listOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
        ).forEach { assertEquals(PlaybackError.NETWORK, playbackErrorOf(it), "code $it") }
    }

    @Test
    fun refusedRequestsMeanTheSourceIsUnavailable() {
        listOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        ).forEach { assertEquals(PlaybackError.SOURCE_UNAVAILABLE, playbackErrorOf(it), "code $it") }
    }

    @Test
    fun parsingAndDecodingFailuresAreUnsupportedFormats() {
        listOf(
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ).forEach { assertEquals(PlaybackError.UNSUPPORTED_FORMAT, playbackErrorOf(it), "code $it") }
    }

    @Test
    fun anythingElseIsUnknown() {
        assertEquals(PlaybackError.UNKNOWN, playbackErrorOf(PlaybackException.ERROR_CODE_UNSPECIFIED))
        assertEquals(PlaybackError.UNKNOWN, playbackErrorOf(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
    }
}
