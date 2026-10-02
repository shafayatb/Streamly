package com.shafayatb.streamly.downloads

sealed interface DownloadsIntent {
    /** Plays a completed download; ignored for one still in progress. */
    data class Open(val videoId: String) : DownloadsIntent
    data class Cancel(val videoId: String) : DownloadsIntent
    data class Retry(val videoId: String) : DownloadsIntent
    data class RequestRemove(val videoId: String) : DownloadsIntent
    data object ConfirmRemove : DownloadsIntent
    data object DismissRemove : DownloadsIntent

    /** Reads the downloads again after they failed to load. */
    data object RetryLoad : DownloadsIntent
}
