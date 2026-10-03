package com.shafayatb.streamly.history

sealed interface WatchHistoryIntent {
    data class Open(val videoId: String) : WatchHistoryIntent
    data class Remove(val videoId: String) : WatchHistoryIntent
    data object RequestClear : WatchHistoryIntent
    data object ConfirmClear : WatchHistoryIntent
    data object DismissClear : WatchHistoryIntent

    /** Reads the history again after it failed to load. */
    data object RetryLoad : WatchHistoryIntent
    data object NavigateBack : WatchHistoryIntent
}
