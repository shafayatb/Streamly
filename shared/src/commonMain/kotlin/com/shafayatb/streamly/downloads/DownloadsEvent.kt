package com.shafayatb.streamly.downloads

import com.shafayatb.streamly.core.presentation.UiText

sealed interface DownloadsEvent {
    data class NavigateToPlayer(val videoId: String) : DownloadsEvent
    data class ShowMessage(val message: UiText) : DownloadsEvent
}
