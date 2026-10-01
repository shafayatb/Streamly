package com.shafayatb.streamly.home

sealed interface HomeIntent {
    data class SelectFilter(val filter: FeedFilter) : HomeIntent
    data class OpenVideo(val videoId: String) : HomeIntent
    data object Retry : HomeIntent
}
