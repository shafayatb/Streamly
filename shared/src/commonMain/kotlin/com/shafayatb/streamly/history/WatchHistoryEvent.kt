package com.shafayatb.streamly.history

import com.shafayatb.streamly.core.presentation.UiText

sealed interface WatchHistoryEvent {
    data class NavigateToPlayer(val videoId: String) : WatchHistoryEvent
    data object NavigateBack : WatchHistoryEvent
    data class ShowMessage(val message: UiText) : WatchHistoryEvent
}
