package com.shafayatb.streamly.player

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.home.VideoCardUi
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class PlayerState(
    val content: PlayerContent = PlayerContent.Loading,
    val playback: PlaybackUi = PlaybackUi(),
    val upNext: UpNextContent = UpNextContent.Loading,
    val isLiked: Boolean = false,
    val isSubscribed: Boolean = false,
)

@Immutable
sealed interface PlayerContent {
    data object Loading : PlayerContent
    data class Loaded(val video: VideoDetailsUi) : PlayerContent
    data class Error(val message: UiText) : PlayerContent
}

@Immutable
data class VideoDetailsUi(
    val id: String,
    val title: String,
    val channelName: String,
    val description: String,
    val thumbnailUrl: String,
    val views: UiText,
    val age: UiText,
    val isLive: Boolean,
)

/** The shared player as this screen sees it: idle defaults while it plays another video. */
@Immutable
data class PlaybackUi(
    /** Drives the play/pause control: true while playing or buffering towards playing. */
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val isLive: Boolean = false,
    val isMuted: Boolean = false,
    val position: Duration = Duration.ZERO,
    /** `null` until known, and for live streams, which show a live badge instead of a scrubber. */
    val duration: Duration? = null,
    val positionText: String = "0:00",
    val durationText: String? = null,
    val error: UiText? = null,
)

@Immutable
sealed interface UpNextContent {
    data object Loading : UpNextContent
    data class Loaded(val videos: ImmutableList<VideoCardUi>) : UpNextContent
    data class Error(val message: UiText) : UpNextContent
}
