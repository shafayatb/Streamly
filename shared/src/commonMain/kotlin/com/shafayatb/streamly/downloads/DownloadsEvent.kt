package com.shafayatb.streamly.downloads

sealed interface DownloadsEvent {
    data class NavigateToPlayer(val videoId: String) : DownloadsEvent
}
