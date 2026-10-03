package com.shafayatb.streamly.home

sealed interface HomeEvent {
    data class NavigateToPlayer(val videoId: String) : HomeEvent
}
