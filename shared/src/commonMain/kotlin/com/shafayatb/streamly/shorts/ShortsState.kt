package com.shafayatb.streamly.shorts

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ShortsState(
    val content: ShortsContent = ShortsContent.Loading,
    /** The page the pager last settled on. */
    val currentPage: Int = 0,
    /** The current short's playback; idle defaults until it holds a player. */
    val playback: ShortPlaybackUi = ShortPlaybackUi(),
    /** Shared by every short. */
    val isMuted: Boolean = false,
)

@Immutable
sealed interface ShortsContent {
    data object Loading : ShortsContent
    data object Empty : ShortsContent
    data class Loaded(val shorts: ImmutableList<ShortUi>) : ShortsContent
    data class Error(val message: UiText) : ShortsContent
}

@Immutable
data class ShortUi(
    val id: String,
    val title: String,
    val channelName: String,
    val channelHandle: String,
    /** Abbreviated, and including the user's own like. */
    val likes: String,
    val comments: String,
    val isLiked: Boolean,
)

@Immutable
data class ShortPlaybackUi(
    val isBuffering: Boolean = false,
    /** Paused by the user (or not started yet), so the page shows a play indicator. */
    val isPaused: Boolean = false,
    val error: UiText? = null,
)
