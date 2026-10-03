package com.shafayatb.streamly.player

import com.shafayatb.streamly.core.presentation.UiText

sealed interface PlayerEvent {
    data object NavigateBack : PlayerEvent

    /** Plays [videoId] in place of the current video, without stacking another player. */
    data class NavigateToVideo(val videoId: String) : PlayerEvent

    data class ShowMessage(val message: UiText) : PlayerEvent
}
