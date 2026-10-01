package com.shafayatb.streamly.player

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText

@Immutable
data class PlayerState(
    val content: PlayerContent = PlayerContent.Loading,
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
)
